package io.github.harrythecake.cakewiki.image;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ImageDecoderTest {
	private static BufferedImage solid(int w, int h, int argb) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, argb);
		return img;
	}

	@Test
	void decodesPng() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(solid(3, 2, 0xFF336699), "png", out);
		ImageDecoder.Decoded d = ImageDecoder.decode(out.toByteArray());
		assertEquals(3, d.width());
		assertEquals(2, d.height());
		assertFalse(d.animated());
		assertEquals(0xFF336699, d.frames().get(0).argb()[5]);
	}

	@Test
	void composesAnimatedGifFramesWithOffsetsAndDelays() throws IOException {
		// frame 1: 4x4 red; frame 2: 2x2 blue at (2,2), keeping frame 1 underneath
		byte[] gif = gif(new BufferedImage[] {solid(4, 4, 0xFFFF0000), solid(2, 2, 0xFF0000FF)},
				new int[][] {{0, 0}, {2, 2}}, new int[] {5, 20});
		ImageDecoder.Decoded d = ImageDecoder.decode(gif);
		assertTrue(d.animated());
		assertEquals(4, d.width());
		int[] second = d.frames().get(1).argb();
		assertEquals(0xFFFF0000, second[0], "earlier frame still shows where the new one doesn't cover");
		assertEquals(0xFF0000FF, second[3 * 4 + 3]);
		assertEquals(50, d.frames().get(0).delayMs());
		assertEquals(200, d.frames().get(1).delayMs());
	}

	@Test
	void scalesHugePicturesDown() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(solid(2048, 512, 0xFF00FF00), "png", out);
		ImageDecoder.Decoded d = ImageDecoder.decode(out.toByteArray());
		assertEquals(ImageDecoder.MAX_SIDE, d.width());
		assertEquals(256, d.height());
		assertEquals(2048, d.naturalWidth());
	}

	@Test
	void rejectsNonPictures() {
		assertThrows(IOException.class, () -> ImageDecoder.decode("<html>".getBytes()));
	}

	/** Writes an animated GIF with per-frame offsets and delays (hundredths of a second). */
	private static byte[] gif(BufferedImage[] frames, int[][] offsets, int[] delays) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(out);
			writer.prepareWriteSequence(null);
			for (int i = 0; i < frames.length; i++) {
				ImageWriteParam param = writer.getDefaultWriteParam();
				IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frames[i]), param);
				String format = meta.getNativeMetadataFormatName();
				IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
				IIOMetadataNode gce = node(root, "GraphicControlExtension");
				gce.setAttribute("disposalMethod", "doNotDispose");
				gce.setAttribute("delayTime", String.valueOf(delays[i]));
				IIOMetadataNode desc = node(root, "ImageDescriptor");
				desc.setAttribute("imageLeftPosition", String.valueOf(offsets[i][0]));
				desc.setAttribute("imageTopPosition", String.valueOf(offsets[i][1]));
				meta.setFromTree(format, root);
				writer.writeToSequence(new IIOImage(frames[i], null, meta), param);
			}
			writer.endWriteSequence();
		}
		return bytes.toByteArray();
	}

	private static IIOMetadataNode node(IIOMetadataNode root, String name) {
		for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeName().equals(name)) return (IIOMetadataNode) n;
		}
		IIOMetadataNode n = new IIOMetadataNode(name);
		root.appendChild(n);
		return n;
	}
}
