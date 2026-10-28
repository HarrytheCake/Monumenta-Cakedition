package io.github.harrythecake.cakewiki.image;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decodes wiki pictures (PNG, GIF incl. animated, JPEG) into ARGB frames with ImageIO.
 * Frames are composed by hand in int arrays: no {@code Graphics2D}, which can start AWT and clash with GLFW on macOS.
 */
public final class ImageDecoder {
	/** Larger pictures are scaled down to this on their longer side (the reader shows them at half size anyway). */
	public static final int MAX_SIDE = 1024;
	public static final int MAX_FRAMES = 200;

	/** One frame: {@code argb} is {@code width*height} pixels, row by row; {@code delayMs} until the next frame. */
	public record Frame(int[] argb, int delayMs) {}

	/** {@code naturalWidth/Height}: the picture's size before any scaling down. */
	public record Decoded(int width, int height, int naturalWidth, int naturalHeight, List<Frame> frames) {
		public boolean animated() {
			return frames.size() > 1;
		}
	}

	private ImageDecoder() {}

	public static Decoded decode(byte[] bytes) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) throw new IOException("not a picture format Java can read");
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, false, false);
				return reader.getFormatName().equalsIgnoreCase("gif") ? gif(reader) : still(reader);
			} finally {
				reader.dispose();
			}
		}
	}

	private static Decoded still(ImageReader reader) throws IOException {
		BufferedImage img = reader.read(0);
		int w = img.getWidth(), h = img.getHeight();
		return fit(w, h, List.of(new Frame(img.getRGB(0, 0, w, h, null, 0, w), 0)));
	}

	private static Decoded gif(ImageReader reader) throws IOException {
		int count = Math.min(reader.getNumImages(true), MAX_FRAMES);
		int w = 0, h = 0;
		Node screen = child(reader.getStreamMetadata(), "javax_imageio_gif_stream_1.0", "LogicalScreenDescriptor");
		if (screen != null) {
			w = intAttr(screen, "logicalScreenWidth", 0);
			h = intAttr(screen, "logicalScreenHeight", 0);
		}
		List<BufferedImage> raw = new ArrayList<>();
		List<IIOMetadata> meta = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			raw.add(reader.read(i));
			meta.add(reader.getImageMetadata(i));
		}
		if (w <= 0 || h <= 0) { // some files leave the logical screen empty: use the frames' extent
			for (int i = 0; i < count; i++) {
				Node d = child(meta.get(i), "javax_imageio_gif_image_1.0", "ImageDescriptor");
				w = Math.max(w, intAttr(d, "imageLeftPosition", 0) + raw.get(i).getWidth());
				h = Math.max(h, intAttr(d, "imageTopPosition", 0) + raw.get(i).getHeight());
			}
		}
		int[] canvas = new int[w * h];
		List<Frame> frames = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BufferedImage f = raw.get(i);
			Node desc = child(meta.get(i), "javax_imageio_gif_image_1.0", "ImageDescriptor");
			Node gce = child(meta.get(i), "javax_imageio_gif_image_1.0", "GraphicControlExtension");
			int left = intAttr(desc, "imageLeftPosition", 0), top = intAttr(desc, "imageTopPosition", 0);
			int fw = f.getWidth(), fh = f.getHeight();
			String disposal = gce != null ? attr(gce, "disposalMethod") : "none";
			int delay = gce != null ? intAttr(gce, "delayTime", 10) * 10 : 100;
			int[] previous = "restoreToPrevious".equals(disposal) ? canvas.clone() : null;

			int[] px = f.getRGB(0, 0, fw, fh, null, 0, fw);
			for (int y = 0; y < fh; y++) {
				int cy = top + y;
				if (cy < 0 || cy >= h) continue;
				for (int x = 0; x < fw; x++) {
					int cx = left + x;
					int p = px[y * fw + x];
					if (cx >= 0 && cx < w && (p >>> 24) != 0) canvas[cy * w + cx] = p;
				}
			}
			// browsers treat 0–1 hundredths as "fast": 100 ms is what they show
			frames.add(new Frame(canvas.clone(), delay <= 10 ? 100 : delay));

			if ("restoreToBackgroundColor".equals(disposal)) {
				for (int y = Math.max(0, top); y < Math.min(h, top + fh); y++) {
					for (int x = Math.max(0, left); x < Math.min(w, left + fw); x++) canvas[y * w + x] = 0;
				}
			} else if (previous != null) {
				canvas = previous;
			}
		}
		return fit(w, h, frames);
	}

	/** Scales frames down (nearest neighbour, which suits pixel art) when a side exceeds {@link #MAX_SIDE}. */
	private static Decoded fit(int w, int h, List<Frame> frames) {
		if (w <= MAX_SIDE && h <= MAX_SIDE) return new Decoded(w, h, w, h, frames);
		double s = (double) MAX_SIDE / Math.max(w, h);
		int nw = Math.max(1, (int) (w * s)), nh = Math.max(1, (int) (h * s));
		List<Frame> out = new ArrayList<>();
		for (Frame f : frames) {
			int[] px = new int[nw * nh];
			for (int y = 0; y < nh; y++) {
				int sy = Math.min(h - 1, (int) (y / s));
				for (int x = 0; x < nw; x++) px[y * nw + x] = f.argb()[sy * w + Math.min(w - 1, (int) (x / s))];
			}
			out.add(new Frame(px, f.delayMs()));
		}
		return new Decoded(nw, nh, w, h, out);
	}

	private static Node child(IIOMetadata meta, String format, String name) {
		if (meta == null) return null;
		for (Node n = meta.getAsTree(format).getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeName().equals(name)) return n;
		}
		return null;
	}

	private static String attr(Node node, String name) {
		if (node == null) return null;
		NamedNodeMap attrs = node.getAttributes();
		Node a = attrs != null ? attrs.getNamedItem(name) : null;
		return a != null ? a.getNodeValue() : null;
	}

	private static int intAttr(Node node, String name, int fallback) {
		try {
			String v = attr(node, name);
			return v != null ? Integer.parseInt(v) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
