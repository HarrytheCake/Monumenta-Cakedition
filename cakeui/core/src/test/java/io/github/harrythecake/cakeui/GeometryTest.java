package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.Easing;
import io.github.harrythecake.cakeui.model.Geometry;
import io.github.harrythecake.cakeui.model.Layout;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryTest {
	@Test
	void anchorsPlaceBoxes() {
		double[] br = Geometry.anchor("bottom_right");
		// a 100 wide box anchored bottom-right with origin bottom-right, 10 in from the edge
		assertEquals(290, Geometry.place(400, br[0], -10, 100, br[0]));
		double[] c = Geometry.anchor("center");
		assertEquals(150, Geometry.place(400, c[0], 0, 100, c[0]));
		assertArrayEquals(new double[] {0, 0}, Geometry.anchor("nonsense"));
	}

	@Test
	void designScaleModes() {
		Layout l = new Layout("t", Path.of("."));
		l.designWidth = 1920;
		l.designHeight = 1080;
		// GUI scale 3 on a 2560×1080 ultrawide → 853×360 GUI pixels
		assertArrayEquals(new double[] {360 / 1080.0, 360 / 1080.0}, l.designScale(853, 360), 1e-9);
		l.scaleMode = Layout.ScaleMode.FILL;
		assertArrayEquals(new double[] {853 / 1920.0, 853 / 1920.0}, l.designScale(853, 360), 1e-9);
		l.scaleMode = Layout.ScaleMode.STRETCH;
		assertArrayEquals(new double[] {853 / 1920.0, 360 / 1080.0}, l.designScale(853, 360), 1e-9);
		l.designWidth = 0;
		assertArrayEquals(new double[] {1, 1}, l.designScale(853, 360), 1e-9);
	}

	@Test
	void flatMatrixRoundTrips() {
		Geometry.Mat m = new Geometry.Mat().translate(50, 20, 0).rotateZ(30).scale(2, 2);
		assertTrue(m.isFlat());
		double[] p = m.apply(10, 5, 0);
		double[] back = m.invert2d(p[0], p[1]);
		assertNotNull(back);
		assertEquals(10, back[0], 1e-9);
		assertEquals(5, back[1], 1e-9);
	}

	@Test
	void tiltedPlaneHitTestInvertsProjection() {
		double d = 800;
		double px = 320;
		double py = 180;
		// a 200×100 panel at (100, 50), turned 35° around its own centre on Y and 10° on X
		Geometry.Mat m = new Geometry.Mat().translate(100, 50, 0).translate(100, 50, 0).rotateY(35).rotateX(10).translate(-100, -50, 0);
		assertFalse(m.isFlat());
		for (double[] uv : new double[][] {{0, 0}, {200, 0}, {200, 100}, {37, 81}}) {
			double[] w = m.apply(uv[0], uv[1], 0);
			double[] s = Geometry.project(w[0], w[1], w[2], d, px, py);
			double[] back = Geometry.unproject(m, s[0], s[1], d, px, py);
			assertNotNull(back);
			assertEquals(uv[0], back[0], 1e-6);
			assertEquals(uv[1], back[1], 1e-6);
		}
	}

	@Test
	void projectionMatrixKeepsTheFlatPlane() {
		double w = 640;
		double h = 360;
		float[] p = Geometry.projection(w, h, 1000, 200, 100);
		// column-major: clip = P · (x, y, 0, 1)
		double x = 123;
		double y = 77;
		double cx = p[0] * x + p[4] * y + p[12];
		double cy = p[1] * x + p[5] * y + p[13];
		double cw = p[3] * x + p[7] * y + p[15];
		assertEquals(2 * x / w - 1, cx / cw, 1e-5);
		assertEquals(1 - 2 * y / h, cy / cw, 1e-5);
		// a point nearer the camera spreads away from the vanishing point
		double z = 200;
		double nx = (p[0] * x + p[8] * z + p[12]) / (p[3] * x + p[11] * z + p[15]);
		double[] s = Geometry.project(x, y, z, 1000, 200, 100);
		assertEquals(2 * s[0] / w - 1, nx, 1e-5);
	}

	@Test
	void easingsEndWhereTheyShould() {
		for (String e : new String[] {"linear", "ease", "ease_in", "ease_out", "ease_in_out", "back_out", "elastic_out", "bounce_out"}) {
			assertEquals(0, Easing.apply(e, 0), 1e-9, e);
			assertEquals(1, Easing.apply(e, 1), 1e-9, e);
		}
		assertTrue(Easing.apply("back_out", 0.6) > 1, "back_out overshoots");
	}
}
