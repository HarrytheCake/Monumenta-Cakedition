package io.github.harrythecake.cakeui.render;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Animated GIF and WebP images for image elements (E4, ADR 0010 Q36): decoded into whole frames (each
 * one composited the way the file says: offsets, blending, disposal) with each frame's time. GIF uses
 * Java's own reader; WebP's frames are cut out of the file and each decoded by TwelveMonkeys' WebP
 * reader (BSD-3, bundled). Big files are cut down ({@link #MAX_FRAMES}, {@link #MAX_PIXELS}). Pure Java.
 */
public final class AnimatedImages {
	public static final int MAX_FRAMES = 256;
	/** Frames x width x height at most (64 MB of pixels). */
	public static final long MAX_PIXELS = 16L * 1024 * 1024;
	public static final int MAX_SIDE = 1024;

	/** Frames as ARGB pixels (row by row), each shown for {@code delaysMs[i]}. */
	public record Anim(int width, int height, List<int[]> frames, int[] delaysMs) {
		public int totalMs() {
			int t = 0;
			for (int d : delaysMs) {
				t += d;
			}
			return Math.max(1, t);
		}

		/** The frame shown {@code ms} after the start (looping). */
		public int frameAt(long ms) {
			long t = Math.floorMod(ms, (long) totalMs());
			for (int i = 0; i < delaysMs.length; i++) {
				t -= delaysMs[i];
				if (t < 0) {
					return i;
				}
			}
			return delaysMs.length - 1;
		}
	}

	private AnimatedImages() {
	}

	/** True for the file types this class reads. */
	public static boolean handles(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".gif") || n.endsWith(".webp");
	}

	public static Anim decode(byte[] data, String name) throws IOException {
		return name.toLowerCase(Locale.ROOT).endsWith(".gif") ? gif(data) : webp(data);
	}

	/** A frame's time: browsers show 0–10 ms frames for 100 ms; so do we. */
	private static int delay(int ms) {
		return ms <= 10 ? 100 : ms;
	}

	private static void check(int w, int h, int frames) throws IOException {
		if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE) {
			throw new IOException("image too big (" + w + " x " + h + ", at most " + MAX_SIDE + " each way)");
		}
		if ((long) w * h * frames > MAX_PIXELS) {
			throw new IOException("too many frames for this size");
		}
	}

	// ---- GIF ----

	private static Anim gif(byte[] data) throws IOException {
		Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
		if (!it.hasNext()) {
			throw new IOException("no GIF reader");
		}
		ImageReader r = it.next();
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			r.setInput(in, false);
			int count = Math.min(MAX_FRAMES, r.getNumImages(true));
			int w = 0;
			int h = 0;
			IIOMetadata sm = r.getStreamMetadata();
			if (sm != null) {
				Node lsd = child(sm.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
				if (lsd != null) {
					w = intAttr(lsd, "logicalScreenWidth", 0);
					h = intAttr(lsd, "logicalScreenHeight", 0);
				}
			}
			if (w <= 0 || h <= 0) {
				w = r.getWidth(0);
				h = r.getHeight(0);
			}
			check(w, h, count);
			int[] canvas = new int[w * h];
			List<int[]> frames = new ArrayList<>();
			int[] delays = new int[count];
			for (int i = 0; i < count; i++) {
				BufferedImage img = r.read(i);
				Node root = r.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
				Node desc = child(root, "ImageDescriptor");
				Node gce = child(root, "GraphicControlExtension");
				int fx = desc == null ? 0 : intAttr(desc, "imageLeftPosition", 0);
				int fy = desc == null ? 0 : intAttr(desc, "imageTopPosition", 0);
				String disposal = gce == null ? "none" : attr(gce, "disposalMethod", "none");
				delays[i] = delay(gce == null ? 100 : intAttr(gce, "delayTime", 10) * 10);
				int[] before = disposal.equals("restoreToPrevious") ? canvas.clone() : null;
				blend(canvas, w, h, img, fx, fy, true);
				frames.add(canvas.clone());
				if (disposal.equals("restoreToBackgroundColor")) {
					clear(canvas, w, h, fx, fy, img.getWidth(), img.getHeight());
				} else if (before != null) {
					canvas = before;
				}
			}
			return new Anim(w, h, frames, delays);
		} finally {
			r.dispose();
		}
	}

	private static Node child(Node n, String name) {
		for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
			if (c.getNodeName().equals(name)) {
				return c;
			}
		}
		return null;
	}

	private static String attr(Node n, String name, String def) {
		Node a = n.getAttributes() == null ? null : n.getAttributes().getNamedItem(name);
		return a == null ? def : a.getNodeValue();
	}

	private static int intAttr(Node n, String name, int def) {
		try {
			return Integer.parseInt(attr(n, name, String.valueOf(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	// ---- WebP ----

	private static Anim webp(byte[] data) throws IOException {
		ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		if (data.length < 12 || !tag(data, 0).equals("RIFF") || !tag(data, 8).equals("WEBP")) {
			throw new IOException("not a WebP file");
		}
		int canvasW = 0;
		int canvasH = 0;
		boolean animated = false;
		List<int[]> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		int[] canvas = null;
		int pos = 12;
		while (pos + 8 <= data.length && frames.size() < MAX_FRAMES) {
			String fourcc = tag(data, pos);
			int size = b.getInt(pos + 4);
			int body = pos + 8;
			if (size < 0 || body + size > data.length) {
				break;
			}
			switch (fourcc) {
				case "VP8X" -> {
					animated = (data[body] & 0x02) != 0;
					canvasW = u24(data, body + 4) + 1;
					canvasH = u24(data, body + 7) + 1;
					check(canvasW, canvasH, 1);
				}
				case "ANMF" -> {
					if (canvas == null) {
						check(canvasW, canvasH, 1);
						canvas = new int[canvasW * canvasH];
					}
					int fx = u24(data, body) * 2;
					int fy = u24(data, body + 3) * 2;
					int fw = u24(data, body + 6) + 1;
					int fh = u24(data, body + 9) + 1;
					int duration = u24(data, body + 12);
					int flags = data[body + 15] & 0xFF;
					boolean noBlend = (flags & 0x02) != 0;
					boolean dispose = (flags & 0x01) != 0;
					BufferedImage img = still(frameFile(data, body + 16, size - 16, fw, fh));
					blend(canvas, canvasW, canvasH, img, fx, fy, !noBlend);
					frames.add(canvas.clone());
					delays.add(delay(duration));
					check(canvasW, canvasH, frames.size());
					if (dispose) {
						clear(canvas, canvasW, canvasH, fx, fy, fw, fh);
					}
				}
				default -> {
				}
			}
			pos = body + size + (size & 1);
		}
		if (!animated || frames.isEmpty()) {
			// a still WebP: one frame
			BufferedImage img = still(data);
			check(img.getWidth(), img.getHeight(), 1);
			int[] px = new int[img.getWidth() * img.getHeight()];
			blend(px, img.getWidth(), img.getHeight(), img, 0, 0, false);
			return new Anim(img.getWidth(), img.getHeight(), List.of(px), new int[] {100});
		}
		int[] d = new int[delays.size()];
		for (int i = 0; i < d.length; i++) {
			d[i] = delays.get(i);
		}
		return new Anim(canvasW, canvasH, frames, d);
	}

	/** One ANMF frame's chunks (ALPH + VP8, or VP8L) as a WebP file of its own. */
	private static byte[] frameFile(byte[] data, int from, int length, int w, int h) {
		boolean alpha = tag(data, from).equals("ALPH");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteBuffer head = ByteBuffer.allocate(alpha ? 30 : 12).order(ByteOrder.LITTLE_ENDIAN);
		head.put("RIFF".getBytes(StandardCharsets.US_ASCII));
		head.putInt(4 + (alpha ? 18 : 0) + length);
		head.put("WEBP".getBytes(StandardCharsets.US_ASCII));
		if (alpha) {
			// a lossy frame with an alpha chunk needs the extended header saying so
			head.put("VP8X".getBytes(StandardCharsets.US_ASCII));
			head.putInt(10);
			head.put((byte) 0x10);
			head.put(new byte[3]);
			put24(head, w - 1);
			put24(head, h - 1);
		}
		out.write(head.array(), 0, head.position());
		out.write(data, from, length);
		return out.toByteArray();
	}

	private static BufferedImage still(byte[] file) throws IOException {
		ImageReader r = new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi().createReaderInstance(null);
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
			r.setInput(in);
			return r.read(0);
		} finally {
			r.dispose();
		}
	}

	private static String tag(byte[] d, int at) {
		return at + 4 <= d.length ? new String(d, at, 4, StandardCharsets.US_ASCII) : "";
	}

	private static int u24(byte[] d, int at) {
		return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8 | (d[at + 2] & 0xFF) << 16;
	}

	private static void put24(ByteBuffer b, int v) {
		b.put((byte) v);
		b.put((byte) (v >> 8));
		b.put((byte) (v >> 16));
	}

	// ---- compositing ----

	/** Draws a frame onto the canvas at (fx, fy): alpha-blended, or replacing what is there. */
	private static void blend(int[] canvas, int w, int h, BufferedImage img, int fx, int fy, boolean alphaBlend) {
		int iw = img.getWidth();
		int ih = img.getHeight();
		int[] src = img.getRGB(0, 0, iw, ih, null, 0, iw);
		for (int y = 0; y < ih; y++) {
			int cy = fy + y;
			if (cy < 0 || cy >= h) {
				continue;
			}
			for (int x = 0; x < iw; x++) {
				int cx = fx + x;
				if (cx < 0 || cx >= w) {
					continue;
				}
				int s = src[y * iw + x];
				int i = cy * w + cx;
				canvas[i] = alphaBlend ? over(s, canvas[i]) : s;
			}
		}
	}

	private static int over(int s, int d) {
		int sa = s >>> 24;
		if (sa == 255) {
			return s;
		}
		if (sa == 0) {
			return d;
		}
		int da = d >>> 24;
		int oa = sa + da * (255 - sa) / 255;
		if (oa == 0) {
			return 0;
		}
		int r = (((s >> 16) & 255) * sa + ((d >> 16) & 255) * da * (255 - sa) / 255) / oa;
		int g = (((s >> 8) & 255) * sa + ((d >> 8) & 255) * da * (255 - sa) / 255) / oa;
		int bl = ((s & 255) * sa + (d & 255) * da * (255 - sa) / 255) / oa;
		return oa << 24 | r << 16 | g << 8 | bl;
	}

	private static void clear(int[] canvas, int w, int h, int fx, int fy, int fw, int fh) {
		for (int y = Math.max(0, fy); y < Math.min(h, fy + fh); y++) {
			for (int x = Math.max(0, fx); x < Math.min(w, fx + fw); x++) {
				canvas[y * w + x] = 0;
			}
		}
	}
}
