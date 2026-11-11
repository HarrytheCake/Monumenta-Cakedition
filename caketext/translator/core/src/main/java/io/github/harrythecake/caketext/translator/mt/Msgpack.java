package io.github.harrythecake.caketext.translator.mt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough msgpack to read a Flax checkpoint ({@code flax_model.msgpack}): maps, arrays, strings, numbers,
 * and Flax's ndarray extension ({@code [shape, dtype, bytes]}). Pure data, no code paths like pickle.
 */
final class Msgpack {
	/** An array from the file; {@code data} is a view into the mapped file, little-endian. */
	record NdArray(int[] shape, String dtype, ByteBuffer data) {
		int size() {
			int n = 1;
			for (int d : shape) {
				n *= d;
			}
			return n;
		}

		float[] toFloats() throws IOException {
			if (!dtype.equals("float32")) {
				throw new IOException("expected float32, got " + dtype);
			}
			float[] out = new float[size()];
			data.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);
			return out;
		}
	}

	private final ByteBuffer buf;

	private Msgpack(ByteBuffer buf) {
		this.buf = buf.order(ByteOrder.BIG_ENDIAN);
	}

	static Object parse(ByteBuffer buf) throws IOException {
		return new Msgpack(buf.duplicate()).value();
	}

	private Object value() throws IOException {
		int b = buf.get() & 0xff;
		if (b <= 0x7f) {
			return (long) b;
		}
		if (b >= 0xe0) {
			return (long) (byte) b;
		}
		if (b >= 0x80 && b <= 0x8f) {
			return map(b & 0x0f);
		}
		if (b >= 0x90 && b <= 0x9f) {
			return array(b & 0x0f);
		}
		if (b >= 0xa0 && b <= 0xbf) {
			return string(b & 0x1f);
		}
		return switch (b) {
			case 0xc0 -> null;
			case 0xc2 -> false;
			case 0xc3 -> true;
			case 0xc4 -> slice(buf.get() & 0xff);
			case 0xc5 -> slice(buf.getShort() & 0xffff);
			case 0xc6 -> slice(length(buf.getInt()));
			case 0xc7 -> ext(buf.get() & 0xff);
			case 0xc8 -> ext(buf.getShort() & 0xffff);
			case 0xc9 -> ext(length(buf.getInt()));
			case 0xca -> (double) buf.getFloat();
			case 0xcb -> buf.getDouble();
			case 0xcc -> (long) (buf.get() & 0xff);
			case 0xcd -> (long) (buf.getShort() & 0xffff);
			case 0xce -> buf.getInt() & 0xffffffffL;
			case 0xcf, 0xd3 -> buf.getLong();
			case 0xd0 -> (long) buf.get();
			case 0xd1 -> (long) buf.getShort();
			case 0xd2 -> (long) buf.getInt();
			case 0xd4 -> ext(1);
			case 0xd5 -> ext(2);
			case 0xd6 -> ext(4);
			case 0xd7 -> ext(8);
			case 0xd8 -> ext(16);
			case 0xd9 -> string(buf.get() & 0xff);
			case 0xda -> string(buf.getShort() & 0xffff);
			case 0xdb -> string(length(buf.getInt()));
			case 0xdc -> array(buf.getShort() & 0xffff);
			case 0xdd -> array(length(buf.getInt()));
			case 0xde -> map(buf.getShort() & 0xffff);
			case 0xdf -> map(length(buf.getInt()));
			default -> throw new IOException("msgpack: unsupported type 0x" + Integer.toHexString(b));
		};
	}

	private static int length(int n) throws IOException {
		if (n < 0) {
			throw new IOException("msgpack: length over 2 GB");
		}
		return n;
	}

	private Map<String, Object> map(int n) throws IOException {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			map.put(String.valueOf(value()), value());
		}
		return map;
	}

	private List<Object> array(int n) throws IOException {
		List<Object> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(value());
		}
		return list;
	}

	private String string(int n) {
		byte[] bytes = new byte[n];
		buf.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private ByteBuffer slice(int n) {
		ByteBuffer out = buf.slice();
		out.limit(n);
		buf.position(buf.position() + n);
		return out;
	}

	/** Flax ndarray (ext type 1): msgpack {@code [shape, dtype, data]} inside. Other ext types are skipped. */
	private Object ext(int n) throws IOException {
		int type = buf.get();
		ByteBuffer payload = slice(n);
		if (type != 1) {
			return null;
		}
		Object inner = new Msgpack(payload).value();
		if (!(inner instanceof List<?> list) || list.size() != 3 || !(list.get(2) instanceof ByteBuffer data)) {
			throw new IOException("msgpack: unexpected ndarray layout");
		}
		List<?> dims = (List<?>) list.get(0);
		int[] shape = new int[dims.size()];
		for (int i = 0; i < shape.length; i++) {
			shape[i] = ((Long) dims.get(i)).intValue();
		}
		return new NdArray(shape, (String) list.get(1), data);
	}
}
