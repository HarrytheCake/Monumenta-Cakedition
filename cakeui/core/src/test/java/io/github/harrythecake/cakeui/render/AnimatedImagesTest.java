package io.github.harrythecake.cakeui.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test images (made with Pillow): 8 x 8, three frames of 100, 200 and 300 ms; frame i is red, green,
 * then blue from column 2i on, transparent left of it (the encoders store only what changed, so this
 * checks the compositing too). lossy.webp has no alpha, so its left columns show the previous frame.
 */
class AnimatedImagesTest {
	private static byte[] read(String name) throws IOException {
		try (InputStream in = AnimatedImagesTest.class.getResourceAsStream("/anim/" + name)) {
			return in.readAllBytes();
		}
	}

	private static int at(AnimatedImages.Anim a, int frame, int x, int y) {
		return a.frames().get(frame)[y * a.width() + x];
	}

	private static void checkFrames(AnimatedImages.Anim a) {
		assertEquals(8, a.width());
		assertEquals(8, a.height());
		assertEquals(3, a.frames().size());
		assertArrayEquals(new int[] {100, 200, 300}, a.delaysMs());
		assertEquals(0xFFFF0000, at(a, 0, 0, 0));
		assertEquals(0xFF00FF00, at(a, 1, 5, 5));
		assertEquals(0xFF0000FF, at(a, 2, 7, 0));
	}

	@Test
	void animatedWebpLossless() throws IOException {
		AnimatedImages.Anim a = AnimatedImages.decode(read("test.webp"), "test.webp");
		checkFrames(a);
		// left of the blue part the frame is transparent: what was there stays (blended) or is cleared
		int left = at(a, 2, 0, 0);
		assertTrue(left == 0 || left == 0xFF00FF00 || left == 0xFFFF0000, Integer.toHexString(left));
	}

	@Test
	void animatedWebpLossy() throws IOException {
		AnimatedImages.Anim a = AnimatedImages.decode(read("lossy.webp"), "lossy.webp");
		assertEquals(3, a.frames().size());
		assertArrayEquals(new int[] {100, 200, 300}, a.delaysMs());
		int c = at(a, 2, 7, 4); // lossy: near blue
		assertTrue(((c >> 16) & 255) < 60 && (c & 255) > 190, Integer.toHexString(c));
	}

	@Test
	void animatedGif() throws IOException {
		checkFrames(AnimatedImages.decode(read("test.gif"), "test.gif"));
	}

	@Test
	void frameAtLoops() throws IOException {
		AnimatedImages.Anim a = AnimatedImages.decode(read("test.gif"), "test.gif");
		assertEquals(0, a.frameAt(0));
		assertEquals(0, a.frameAt(99));
		assertEquals(1, a.frameAt(100));
		assertEquals(2, a.frameAt(599));
		assertEquals(0, a.frameAt(600));
		assertTrue(AnimatedImages.handles("x.GIF") && AnimatedImages.handles("a/b.webp") && !AnimatedImages.handles("x.png"));
	}

	@Test
	void notAnImage() {
		assertThrows(IOException.class, () -> AnimatedImages.decode(new byte[] {1, 2, 3}, "x.webp"));
	}
}
