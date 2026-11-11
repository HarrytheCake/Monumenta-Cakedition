package io.github.harrythecake.caketext.translator.mt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SentencePiece unigram tokenizer, read from a {@code .spm} model file (protobuf).
 *
 * <p>Normalization uses the model's own precompiled character map (a darts-clone double-array trie over UTF-8,
 * the same data the C++ library uses), so pieces match the reference tokenizer.
 */
final class SentencePiece {
	private static final String SPACE = "▁";
	private static final int NORMAL = 1;
	private static final int UNKNOWN = 2;
	private static final int USER_DEFINED = 4;
	private static final float UNK_PENALTY = 10f;

	private final Map<String, Float> scores = new HashMap<>();
	private final int maxPieceLength;
	private final float unkScore;
	private final String unkPiece;
	private final boolean addDummyPrefix;
	private final boolean removeExtraWhitespaces;
	private final int[] trie;
	private final byte[] normalized;

	private SentencePiece(List<String> pieces, List<Float> pieceScores, List<Integer> types, byte[] charsmap,
			boolean addDummyPrefix, boolean removeExtraWhitespaces) {
		float min = Float.MAX_VALUE;
		int maxLen = 1;
		String unk = "<unk>";
		for (int i = 0; i < pieces.size(); i++) {
			int type = types.get(i);
			if (type == UNKNOWN) {
				unk = pieces.get(i);
			}
			if (type != NORMAL && type != USER_DEFINED) {
				continue;
			}
			String p = pieces.get(i);
			scores.put(p, pieceScores.get(i));
			maxLen = Math.max(maxLen, p.codePointCount(0, p.length()));
			if (type == NORMAL) {
				min = Math.min(min, pieceScores.get(i));
			}
		}
		this.maxPieceLength = maxLen;
		this.unkScore = min - UNK_PENALTY;
		this.unkPiece = unk;
		this.addDummyPrefix = addDummyPrefix;
		this.removeExtraWhitespaces = removeExtraWhitespaces;
		if (charsmap != null && charsmap.length >= 4) {
			ByteBuffer b = ByteBuffer.wrap(charsmap).order(ByteOrder.LITTLE_ENDIAN);
			int trieBytes = b.getInt();
			this.trie = new int[trieBytes / 4];
			b.asIntBuffer().get(trie);
			this.normalized = new byte[charsmap.length - 4 - trieBytes];
			System.arraycopy(charsmap, 4 + trieBytes, normalized, 0, normalized.length);
		} else {
			this.trie = null;
			this.normalized = null;
		}
	}

	static SentencePiece load(byte[] model) throws IOException {
		List<String> pieces = new ArrayList<>();
		List<Float> scores = new ArrayList<>();
		List<Integer> types = new ArrayList<>();
		byte[] charsmap = null;
		boolean dummyPrefix = true;
		boolean removeWhitespace = true;
		Proto p = new Proto(model, 0, model.length);
		while (p.more()) {
			int key = (int) p.varint();
			int field = key >>> 3;
			if (field == 1 && (key & 7) == 2) {
				Proto piece = p.message();
				String text = "";
				float score = 0;
				int type = NORMAL;
				while (piece.more()) {
					int k = (int) piece.varint();
					switch (k >>> 3) {
						case 1 -> text = piece.string();
						case 2 -> score = piece.float32();
						case 3 -> type = (int) piece.varint();
						default -> piece.skip(k & 7);
					}
				}
				pieces.add(text);
				scores.add(score);
				types.add(type);
			} else if (field == 3 && (key & 7) == 2) {
				Proto norm = p.message();
				while (norm.more()) {
					int k = (int) norm.varint();
					switch (k >>> 3) {
						case 2 -> charsmap = norm.bytes();
						case 3 -> dummyPrefix = norm.varint() != 0;
						case 4 -> removeWhitespace = norm.varint() != 0;
						default -> norm.skip(k & 7);
					}
				}
			} else {
				p.skip(key & 7);
			}
		}
		if (pieces.isEmpty()) {
			throw new IOException("no pieces in SentencePiece model");
		}
		return new SentencePiece(pieces, scores, types, charsmap, dummyPrefix, removeWhitespace);
	}

	/** Pieces for {@code text}; consecutive unknown characters become one unknown piece. */
	List<String> encode(String text) {
		String norm = normalize(text);
		if (norm.isEmpty()) {
			return List.of();
		}
		int[] cps = norm.codePoints().toArray();
		int n = cps.length;
		int[] offsets = new int[n + 1];
		for (int i = 0; i < n; i++) {
			offsets[i + 1] = offsets[i] + Character.charCount(cps[i]);
		}
		float[] best = new float[n + 1];
		int[] from = new int[n + 1];
		boolean[] unk = new boolean[n + 1];
		java.util.Arrays.fill(best, Float.NEGATIVE_INFINITY);
		best[0] = 0;
		for (int start = 0; start < n; start++) {
			if (best[start] == Float.NEGATIVE_INFINITY) {
				continue;
			}
			boolean single = false;
			for (int len = 1; len <= maxPieceLength && start + len <= n; len++) {
				Float s = scores.get(norm.substring(offsets[start], offsets[start + len]));
				if (s == null) {
					continue;
				}
				if (len == 1) {
					single = true;
				}
				float total = best[start] + s;
				if (total > best[start + len]) {
					best[start + len] = total;
					from[start + len] = start;
					unk[start + len] = false;
				}
			}
			if (!single) {
				float total = best[start] + unkScore;
				if (total > best[start + 1]) {
					best[start + 1] = total;
					from[start + 1] = start;
					unk[start + 1] = true;
				}
			}
		}
		List<String> out = new ArrayList<>();
		List<Boolean> outUnk = new ArrayList<>();
		for (int end = n; end > 0; end = from[end]) {
			out.add(norm.substring(offsets[from[end]], offsets[end]));
			outUnk.add(unk[end]);
		}
		java.util.Collections.reverse(out);
		java.util.Collections.reverse(outUnk);
		List<String> merged = new ArrayList<>();
		for (int i = 0; i < out.size(); i++) {
			if (outUnk.get(i) && i > 0 && outUnk.get(i - 1)) {
				merged.set(merged.size() - 1, merged.get(merged.size() - 1) + out.get(i));
			} else {
				merged.add(out.get(i));
			}
		}
		return merged;
	}

	String unknownPiece() {
		return unkPiece;
	}

	/** Character map, whitespace cleanup, dummy prefix, spaces to {@code ▁}. */
	String normalize(String text) {
		byte[] in = text.getBytes(StandardCharsets.UTF_8);
		StringBuilder out = new StringBuilder();
		int i = 0;
		if (removeExtraWhitespaces) {
			while (i < in.length && in[i] == ' ') {
				i++;
			}
		}
		boolean lastSpace = false;
		if (addDummyPrefix) {
			out.append(' ');
			lastSpace = true;
		}
		while (i < in.length) {
			int[] match = longestMatch(in, i);
			String chunk;
			int consumed;
			if (match != null) {
				chunk = cString(match[1]);
				consumed = match[0];
			} else {
				consumed = utf8Length(in, i);
				chunk = new String(in, i, consumed, StandardCharsets.UTF_8);
			}
			i += consumed;
			for (int j = 0; j < chunk.length(); j++) {
				char c = chunk.charAt(j);
				if (c == ' ') {
					if (removeExtraWhitespaces && lastSpace) {
						continue;
					}
					lastSpace = true;
				} else {
					lastSpace = false;
				}
				out.append(c);
			}
		}
		if (removeExtraWhitespaces) {
			while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
				out.setLength(out.length() - 1);
			}
		}
		return out.toString().replace(" ", SPACE);
	}

	/** Darts-clone common-prefix search: {consumed bytes, value} of the longest match, or null. */
	private int[] longestMatch(byte[] key, int start) {
		if (trie == null) {
			return null;
		}
		int[] result = null;
		int nodePos = 0;
		int unit = trie[nodePos];
		nodePos ^= offset(unit);
		for (int i = start; i < key.length; i++) {
			int c = key[i] & 0xff;
			if (c == 0) {
				break;
			}
			nodePos ^= c;
			if (nodePos < 0 || nodePos >= trie.length) {
				break;
			}
			unit = trie[nodePos];
			if (label(unit) != c) {
				break;
			}
			nodePos ^= offset(unit);
			if (hasLeaf(unit)) {
				result = new int[] {i - start + 1, trie[nodePos] & 0x7fffffff};
			}
		}
		return result;
	}

	private static boolean hasLeaf(int unit) {
		return ((unit >>> 8) & 1) == 1;
	}

	private static int label(int unit) {
		return unit & (0x80000000 | 0xff);
	}

	private static int offset(int unit) {
		return (unit >>> 10) << ((unit & (1 << 9)) >>> 6);
	}

	private String cString(int offset) {
		int end = offset;
		while (end < normalized.length && normalized[end] != 0) {
			end++;
		}
		return new String(normalized, offset, end - offset, StandardCharsets.UTF_8);
	}

	private static int utf8Length(byte[] b, int i) {
		int c = b[i] & 0xff;
		int n = c < 0x80 ? 1 : c < 0xe0 ? 2 : c < 0xf0 ? 3 : 4;
		return Math.min(n, b.length - i);
	}

	/** Minimal protobuf wire reader. */
	private static final class Proto {
		private final byte[] b;
		private int pos;
		private final int end;

		Proto(byte[] b, int pos, int end) {
			this.b = b;
			this.pos = pos;
			this.end = end;
		}

		boolean more() {
			return pos < end;
		}

		long varint() {
			long result = 0;
			int shift = 0;
			while (true) {
				int c = b[pos++] & 0xff;
				result |= (long) (c & 0x7f) << shift;
				if (c < 0x80) {
					return result;
				}
				shift += 7;
			}
		}

		float float32() {
			int v = (b[pos] & 0xff) | (b[pos + 1] & 0xff) << 8 | (b[pos + 2] & 0xff) << 16 | (b[pos + 3] & 0xff) << 24;
			pos += 4;
			return Float.intBitsToFloat(v);
		}

		Proto message() {
			int len = (int) varint();
			Proto p = new Proto(b, pos, pos + len);
			pos += len;
			return p;
		}

		byte[] bytes() {
			int len = (int) varint();
			byte[] out = new byte[len];
			System.arraycopy(b, pos, out, 0, len);
			pos += len;
			return out;
		}

		String string() {
			return new String(bytes(), StandardCharsets.UTF_8);
		}

		void skip(int wireType) {
			switch (wireType) {
				case 0 -> varint();
				case 1 -> pos += 8;
				case 2 -> {
					// not "pos += varint()": that reads pos before varint() moves it
					int len = (int) varint();
					pos += len;
				}
				case 5 -> pos += 4;
				default -> throw new IllegalStateException("protobuf: wire type " + wireType);
			}
		}
	}
}
