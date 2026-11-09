package io.github.harrythecake.cakemap.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * What CakeMap knows about one region of 512 x 512 blocks: per column the top block (a block-state
 * string from the region's palette), its biome, its height, and an overlay (water or another
 * see-through block) with its depth. Palette index 0 = nothing known (unexplored). Pure Java, so
 * the importer and the tests use it without the game.
 */
public final class RegionData {
	public static final int SIZE = 512;
	public static final int AREA = SIZE * SIZE;
	private static final int MAGIC = 0x434D5231; // "CMR1"
	private static final int VERSION = 1;

	public final int rx;
	public final int rz;
	private final List<String> states = new ArrayList<>();
	private final Map<String, Integer> stateIndex = new HashMap<>();
	private final List<String> biomes = new ArrayList<>();
	private final Map<String, Integer> biomeIndex = new HashMap<>();
	/** Palette index of the top block per column, at {@link #index}(x, z) = z * 512 + x. */
	public final short[] state = new short[AREA];
	public final byte[] biome = new byte[AREA];
	public final short[] height = new short[AREA];
	/** Palette index of the overlay block (0 = none). */
	public final short[] overlay = new short[AREA];
	/** Overlay depth in blocks (0 with no overlay), at most 255. */
	public final byte[] depth = new byte[AREA];
	/** Bumped on every change; textures compare it with the version they were built from. */
	public volatile int revision;

	public RegionData(int rx, int rz) {
		this.rx = rx;
		this.rz = rz;
		states.add("");
		stateIndex.put("", 0);
		biomes.add("");
		biomeIndex.put("", 0);
	}

	public static int index(int x, int z) {
		return z * SIZE + x;
	}

	public synchronized int stateId(String s) {
		Integer i = stateIndex.get(s);
		if (i != null) {
			return i;
		}
		if (states.size() >= Short.MAX_VALUE) {
			return 0;
		}
		states.add(s);
		stateIndex.put(s, states.size() - 1);
		return states.size() - 1;
	}

	public synchronized int biomeId(String b) {
		Integer i = biomeIndex.get(b);
		if (i != null) {
			return i;
		}
		if (biomes.size() >= 255) {
			return 0;
		}
		biomes.add(b);
		biomeIndex.put(b, biomes.size() - 1);
		return biomes.size() - 1;
	}

	public synchronized List<String> states() {
		return new ArrayList<>(states);
	}

	public synchronized List<String> biomes() {
		return new ArrayList<>(biomes);
	}

	public String stateAt(int i) {
		return states().get(state[i]);
	}

	/** Sets one column; x and z are 0…511 inside the region. Returns true when anything changed. */
	public boolean set(int x, int z, String top, String biomeName, int h, String over, int overDepth) {
		int i = index(x, z);
		short s = (short) stateId(top);
		byte b = (byte) biomeId(biomeName == null ? "" : biomeName);
		short o = (short) (over == null ? 0 : stateId(over));
		byte d = (byte) Math.min(255, Math.max(0, over == null ? 0 : overDepth));
		if (state[i] == s && biome[i] == b && height[i] == h && overlay[i] == o && depth[i] == d) {
			return false;
		}
		state[i] = s;
		biome[i] = b;
		height[i] = (short) h;
		overlay[i] = o;
		depth[i] = d;
		revision++;
		return true;
	}

	public boolean known(int i) {
		return state[i] != 0;
	}

	public int knownCount() {
		int n = 0;
		for (short s : state) {
			if (s != 0) {
				n++;
			}
		}
		return n;
	}

	/** Copies every known column of {@code other} over this one (an import never removes what is known). */
	public void mergeFrom(RegionData other, boolean overwrite) {
		List<String> os = other.states();
		List<String> ob = other.biomes();
		int[] sMap = new int[os.size()];
		for (int k = 0; k < os.size(); k++) {
			sMap[k] = k == 0 ? 0 : stateId(os.get(k));
		}
		int[] bMap = new int[ob.size()];
		for (int k = 0; k < ob.size(); k++) {
			bMap[k] = k == 0 ? 0 : biomeId(ob.get(k));
		}
		for (int i = 0; i < AREA; i++) {
			if (other.state[i] == 0 || (!overwrite && state[i] != 0)) {
				continue;
			}
			state[i] = (short) sMap[other.state[i]];
			biome[i] = (byte) bMap[other.biome[i] & 0xFF];
			height[i] = other.height[i];
			overlay[i] = (short) sMap[other.overlay[i]];
			depth[i] = other.depth[i];
		}
		revision++;
	}

	// ---- file format: magic, version, then a deflated body ----

	public synchronized void write(OutputStream raw) throws IOException {
		DataOutputStream head = new DataOutputStream(raw);
		head.writeInt(MAGIC);
		head.writeInt(VERSION);
		head.writeInt(rx);
		head.writeInt(rz);
		head.flush();
		DeflaterOutputStream z = new DeflaterOutputStream(raw, new Deflater(Deflater.BEST_SPEED), 1 << 16);
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(z, 1 << 16));
		out.writeInt(states.size());
		for (String s : states) {
			out.writeUTF(s);
		}
		out.writeInt(biomes.size());
		for (String b : biomes) {
			out.writeUTF(b);
		}
		for (int i = 0; i < AREA; i++) {
			out.writeShort(state[i]);
		}
		out.write(biome);
		for (int i = 0; i < AREA; i++) {
			out.writeShort(height[i]);
		}
		for (int i = 0; i < AREA; i++) {
			out.writeShort(overlay[i]);
		}
		out.write(depth);
		out.flush();
		z.finish();
	}

	public static RegionData read(InputStream raw) throws IOException {
		DataInputStream head = new DataInputStream(raw);
		if (head.readInt() != MAGIC) {
			throw new IOException("not a CakeMap region");
		}
		int version = head.readInt();
		if (version != VERSION) {
			throw new IOException("unknown CakeMap region version " + version);
		}
		RegionData r = new RegionData(head.readInt(), head.readInt());
		DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw), 1 << 16));
		int ns = in.readInt();
		r.states.clear();
		r.stateIndex.clear();
		for (int k = 0; k < ns; k++) {
			String s = in.readUTF();
			r.states.add(s);
			r.stateIndex.putIfAbsent(s, k);
		}
		int nb = in.readInt();
		r.biomes.clear();
		r.biomeIndex.clear();
		for (int k = 0; k < nb; k++) {
			String b = in.readUTF();
			r.biomes.add(b);
			r.biomeIndex.putIfAbsent(b, k);
		}
		for (int i = 0; i < AREA; i++) {
			r.state[i] = check(in.readShort(), ns);
		}
		in.readFully(r.biome);
		for (int i = 0; i < AREA; i++) {
			r.height[i] = in.readShort();
		}
		for (int i = 0; i < AREA; i++) {
			r.overlay[i] = check(in.readShort(), ns);
		}
		in.readFully(r.depth);
		for (int i = 0; i < AREA; i++) {
			if ((r.biome[i] & 0xFF) >= nb) {
				r.biome[i] = 0;
			}
		}
		return r;
	}

	private static short check(short v, int n) {
		return v < 0 || v >= n ? 0 : v;
	}
}
