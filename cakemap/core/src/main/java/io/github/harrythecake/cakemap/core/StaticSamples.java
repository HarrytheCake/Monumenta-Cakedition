package io.github.harrythecake.cakemap.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * {@code samples.bin} of a static map: every region's heights at every 8th block, for
 * {@link MatchCheck}. Deflated: int count, then per region int rx, int rz, 64 x 64 shorts. Pure Java.
 */
public final class StaticSamples {
	private final Map<Long, short[]> regions = new HashMap<>();

	public static long key(int rx, int rz) {
		return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
	}

	public void put(int rx, int rz, short[] samples) {
		regions.put(key(rx, rz), samples);
	}

	public int size() {
		return regions.size();
	}

	/** The height sampled nearest to block (x, z), or Integer.MIN_VALUE. Only exact sample columns count. */
	public int heightAt(int x, int z) {
		if (Math.floorMod(x, 8) != 4 || Math.floorMod(z, 8) != 4) {
			return Integer.MIN_VALUE;
		}
		short[] s = regions.get(key(Math.floorDiv(x, 512), Math.floorDiv(z, 512)));
		if (s == null) {
			return Integer.MIN_VALUE;
		}
		short h = s[(Math.floorMod(z, 512) / 8) * 64 + Math.floorMod(x, 512) / 8];
		return h == Short.MIN_VALUE ? Integer.MIN_VALUE : h;
	}

	public void write(OutputStream raw) throws IOException {
		DeflaterOutputStream z = new DeflaterOutputStream(raw);
		DataOutputStream out = new DataOutputStream(z);
		out.writeInt(regions.size());
		for (Map.Entry<Long, short[]> e : regions.entrySet()) {
			out.writeInt((int) (e.getKey() >> 32));
			out.writeInt((int) (long) e.getKey());
			for (short h : e.getValue()) {
				out.writeShort(h);
			}
		}
		out.flush();
		z.finish();
	}

	public static StaticSamples read(InputStream raw) throws IOException {
		DataInputStream in = new DataInputStream(new InflaterInputStream(raw));
		StaticSamples s = new StaticSamples();
		int n = in.readInt();
		if (n < 0 || n > 100_000) {
			throw new IOException("bad samples.bin");
		}
		for (int k = 0; k < n; k++) {
			int rx = in.readInt();
			int rz = in.readInt();
			short[] h = new short[64 * 64];
			for (int i = 0; i < h.length; i++) {
				h[i] = in.readShort();
			}
			s.put(rx, rz, h);
		}
		return s;
	}
}
