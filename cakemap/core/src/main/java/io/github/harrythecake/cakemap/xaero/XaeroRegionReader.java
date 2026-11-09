package io.github.harrythecake.cakemap.xaero;

import io.github.harrythecake.cakemap.core.BlockStates;
import io.github.harrythecake.cakemap.core.RegionData;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads one region of Xaero's World Map ({@code <rx>_<rz>.zip} holding {@code region.xaero}) into
 * a {@link RegionData}. The format was worked out from the data itself (see the local
 * docs/cakemap-design.md); only version 6.x is read, anything else is refused. Pure Java.
 */
public final class XaeroRegionReader {
	/** The only major version this reader knows (Xaero's World Map 1.3x, 2024). */
	static final int MAJOR = 6;
	private static final String GRASS = "minecraft:grass_block[snowy=false]";

	private XaeroRegionReader() {
	}

	/** Reads the zip file's region.xaero. */
	public static RegionData readZip(InputStream zip, int rx, int rz) throws IOException {
		ZipInputStream z = new ZipInputStream(zip);
		ZipEntry e;
		while ((e = z.getNextEntry()) != null) {
			if (e.getName().equals("region.xaero")) {
				return read(z.readAllBytes(), rx, rz);
			}
		}
		throw new IOException("no region.xaero in the zip");
	}

	public static RegionData read(byte[] bytes, int rx, int rz) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
		RegionData out = new RegionData(rx, rz);
		if (bytes.length == 0) {
			return out;
		}
		if ((in.readUnsignedByte()) != 0xFF) {
			throw new IOException("old Xaero region format (no version byte)");
		}
		int version = in.readInt();
		if ((version >>> 16) != MAJOR) {
			throw new IOException("Xaero region version " + (version >>> 16) + "." + (version & 0xFFFF) + " is not supported");
		}
		List<String> states = new ArrayList<>();
		List<String> biomes = new ArrayList<>();
		Pixel px = new Pixel();
		while (true) {
			int tile;
			try {
				tile = in.readUnsignedByte();
			} catch (EOFException end) {
				break;
			}
			int tx = tile >> 4;
			int tz = tile & 15;
			for (int k = 0; k < 16; k++) {
				int first = in.readInt();
				if (first == -1) {
					continue;
				}
				int cx = k / 4;
				int cz = k % 4;
				for (int i = 0; i < 256; i++) {
					readPixel(in, i == 0 ? first : in.readInt(), states, biomes, px);
					int x = tx * 64 + cx * 16 + i / 16;
					int z = tz * 64 + cz * 16 + i % 16;
					if (x < 512 && z < 512 && px.state != null) {
						out.set(x, z, px.state, px.biome, px.height, px.overlay, px.depth);
					}
				}
				in.readUnsignedByte(); // world interpretation version
				in.readInt(); // cave start
				in.readUnsignedByte(); // cave depth
			}
		}
		return out;
	}

	static final class Pixel {
		String state;
		String biome;
		int height;
		String overlay;
		int depth;
	}

	private static void readPixel(DataInputStream in, int p, List<String> states, List<String> biomes, Pixel px) throws IOException {
		px.state = GRASS;
		if ((p & 1) != 0) {
			px.state = readState(in, (p & (1 << 21)) != 0, states);
		}
		int low = (p >> 12) & 0xFF;
		int high = (p >> 25) & 0xF;
		int h = (high << 8) | low;
		if (h >= 2048) {
			h -= 4096; // 12-bit signed
		}
		px.height = h;
		int top = Integer.MIN_VALUE;
		if ((p & (1 << 24)) != 0) {
			top = in.readUnsignedByte();
		}
		px.overlay = null;
		px.depth = 0;
		if ((p & 2) != 0) {
			int n = in.readUnsignedByte();
			for (int k = 0; k < n; k++) {
				int o = in.readInt();
				String s = BlockStates.WATER;
				if ((o & 1) != 0) {
					s = readState(in, (o & (1 << 10)) != 0, states);
				}
				if (((o >> 8) & 3) == 3) {
					in.readInt(); // custom colour
				}
				if (k == 0) {
					px.overlay = s;
				}
			}
			if (px.overlay != null) {
				// the extra byte is the overlay's top (low 8 bits); depth = top - floor, at least 1
				int d = top == Integer.MIN_VALUE ? 1 : (top - (h & 0xFF)) & 0xFF;
				px.depth = Math.max(1, d);
			}
		}
		if (((p >> 2) & 3) == 3) {
			in.readInt(); // custom colour
		}
		px.biome = "";
		if ((p & (1 << 20)) != 0) {
			if ((p & (1 << 22)) != 0) {
				biomes.add(in.readUTF());
				px.biome = biomes.get(biomes.size() - 1);
			} else {
				int b = in.readInt();
				px.biome = b >= 0 && b < biomes.size() ? biomes.get(b) : "";
			}
		}
		if (px.state.isEmpty() || BlockStates.isAir(px.state)) {
			px.state = px.overlay != null ? px.overlay : null;
			if (px.state != null) {
				px.overlay = null;
				px.depth = 0;
			}
		}
	}

	private static String readState(DataInputStream in, boolean fresh, List<String> states) throws IOException {
		if (fresh) {
			String s = stateFromNbt(in);
			states.add(s);
			return s;
		}
		int i = in.readInt();
		return i >= 0 && i < states.size() ? states.get(i) : "";
	}

	// ---- just enough NBT for {Name, Properties} ----

	static String stateFromNbt(DataInputStream in) throws IOException {
		int type = in.readUnsignedByte();
		if (type != 10) {
			throw new IOException("block state is not a compound (" + type + ")");
		}
		in.readUTF();
		Object root = payload(in, 10);
		if (!(root instanceof Map<?, ?> m) || !(m.get("Name") instanceof String name)) {
			return "";
		}
		Map<String, String> props = new TreeMap<>();
		if (m.get("Properties") instanceof Map<?, ?> pm) {
			for (Map.Entry<?, ?> e : pm.entrySet()) {
				props.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
			}
		}
		return BlockStates.join(name, props);
	}

	private static Object payload(DataInputStream in, int type) throws IOException {
		switch (type) {
			case 1:
				return in.readByte();
			case 2:
				return in.readShort();
			case 3:
				return in.readInt();
			case 4:
				return in.readLong();
			case 5:
				return in.readFloat();
			case 6:
				return in.readDouble();
			case 7: {
				int n = in.readInt();
				in.skipNBytes(n);
				return null;
			}
			case 8:
				return in.readUTF();
			case 9: {
				int et = in.readUnsignedByte();
				int n = in.readInt();
				List<Object> list = new ArrayList<>();
				for (int k = 0; k < n; k++) {
					list.add(payload(in, et));
				}
				return list;
			}
			case 10: {
				Map<String, Object> o = new TreeMap<>();
				while (true) {
					int t = in.readUnsignedByte();
					if (t == 0) {
						return o;
					}
					String name = in.readUTF();
					o.put(name, payload(in, t));
				}
			}
			case 11: {
				int n = in.readInt();
				in.skipNBytes(4L * n);
				return null;
			}
			case 12: {
				int n = in.readInt();
				in.skipNBytes(8L * n);
				return null;
			}
			default:
				throw new IOException("unknown NBT tag " + type);
		}
	}
}
