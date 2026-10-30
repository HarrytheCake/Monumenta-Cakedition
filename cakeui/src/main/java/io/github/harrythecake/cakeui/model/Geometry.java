package io.github.harrythecake.cakeui.model;

/**
 * Placement and 3D math in GUI pixels, with no Minecraft classes so it can be tested.
 *
 * <p>3D: an element's model matrix maps its local (u, v, 0) to layer space (x, y, z), z toward the
 * viewer. A camera at distance d in front of the point (px, py) sees x' = px + (x − px)·d/(d − z).
 */
public final class Geometry {
	private Geometry() {
	}

	/** {fx, fy} in 0, 0.5, 1 for an anchor name; unknown names are top_left. */
	public static double[] anchor(String name) {
		if (name == null) {
			return new double[] {0, 0};
		}
		return switch (name) {
			case "top" -> new double[] {0.5, 0};
			case "top_right" -> new double[] {1, 0};
			case "left" -> new double[] {0, 0.5};
			case "center" -> new double[] {0.5, 0.5};
			case "right" -> new double[] {1, 0.5};
			case "bottom_left" -> new double[] {0, 1};
			case "bottom" -> new double[] {0.5, 1};
			case "bottom_right" -> new double[] {1, 1};
			default -> new double[] {0, 0};
		};
	}

	/** Where a box starts: the parent's anchor point, plus the offset, minus the box's own origin point. */
	public static double place(double parentSize, double anchorFactor, double offset, double size, double originFactor) {
		return parentSize * anchorFactor + offset - size * originFactor;
	}

	/** A 4×4 matrix, row-major, applied to column vectors. */
	public static final class Mat {
		public final double[] m = new double[16];

		public Mat() {
			m[0] = m[5] = m[10] = m[15] = 1;
		}

		public Mat copy() {
			Mat c = new Mat();
			System.arraycopy(m, 0, c.m, 0, 16);
			return c;
		}

		/** this = this × other. */
		public Mat mul(Mat o) {
			double[] r = new double[16];
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					double s = 0;
					for (int k = 0; k < 4; k++) {
						s += m[i * 4 + k] * o.m[k * 4 + j];
					}
					r[i * 4 + j] = s;
				}
			}
			System.arraycopy(r, 0, m, 0, 16);
			return this;
		}

		public Mat translate(double x, double y, double z) {
			Mat t = new Mat();
			t.m[3] = x;
			t.m[7] = y;
			t.m[11] = z;
			return mul(t);
		}

		public Mat scale(double x, double y) {
			Mat t = new Mat();
			t.m[0] = x;
			t.m[5] = y;
			return mul(t);
		}

		public Mat skew(double xDeg, double yDeg) {
			Mat t = new Mat();
			t.m[1] = Math.tan(Math.toRadians(xDeg));
			t.m[4] = Math.tan(Math.toRadians(yDeg));
			return mul(t);
		}

		public Mat rotateX(double deg) {
			double c = Math.cos(Math.toRadians(deg));
			double s = Math.sin(Math.toRadians(deg));
			Mat t = new Mat();
			t.m[5] = c;
			t.m[6] = -s;
			t.m[9] = s;
			t.m[10] = c;
			return mul(t);
		}

		public Mat rotateY(double deg) {
			double c = Math.cos(Math.toRadians(deg));
			double s = Math.sin(Math.toRadians(deg));
			Mat t = new Mat();
			t.m[0] = c;
			t.m[2] = s;
			t.m[8] = -s;
			t.m[10] = c;
			return mul(t);
		}

		/** Positive degrees turn clockwise on screen (y points down). */
		public Mat rotateZ(double deg) {
			double c = Math.cos(Math.toRadians(deg));
			double s = Math.sin(Math.toRadians(deg));
			Mat t = new Mat();
			t.m[0] = c;
			t.m[1] = -s;
			t.m[4] = s;
			t.m[5] = c;
			return mul(t);
		}

		public double[] apply(double x, double y, double z) {
			return new double[] {
				m[0] * x + m[1] * y + m[2] * z + m[3],
				m[4] * x + m[5] * y + m[6] * z + m[7],
				m[8] * x + m[9] * y + m[10] * z + m[11]
			};
		}

		/** True when this matrix only moves, scales, skews or turns in the screen plane. */
		public boolean isFlat() {
			return m[2] == 0 && m[6] == 0 && m[8] == 0 && m[9] == 0 && m[11] == 0 && Math.abs(m[10] - 1) < 1e-12;
		}

		/** Inverse of the 2D part (x, y), for mapping the mouse into a flat element; null if singular. */
		public double[] invert2d(double x, double y) {
			double a = m[0], b = m[1], c = m[3], d = m[4], e = m[5], f = m[7];
			double det = a * e - b * d;
			if (Math.abs(det) < 1e-12) {
				return null;
			}
			double rx = x - c;
			double ry = y - f;
			return new double[] {(e * rx - b * ry) / det, (-d * rx + a * ry) / det};
		}
	}

	/** Where layer-space point (x, y, z) lands on the screen; null when it is behind the camera. */
	public static double[] project(double x, double y, double z, double d, double px, double py) {
		if (z >= d) {
			return null;
		}
		double k = d / (d - z);
		return new double[] {px + (x - px) * k, py + (y - py) * k};
	}

	/**
	 * The local (u, v) on the element's plane that lands on screen point (sx, sy), seen through the
	 * camera (d, px, py). Null when the plane is edge-on to the camera.
	 */
	public static double[] unproject(Mat model, double sx, double sy, double d, double px, double py) {
		double ox = model.m[3], oy = model.m[7], oz = model.m[11];
		double ux = model.m[0], uy = model.m[4], uz = model.m[8];
		double vx = model.m[1], vy = model.m[5], vz = model.m[9];
		double ax = sx - px;
		double ay = sy - py;
		// (sx − px)(d − z) = d (x − px), x = o + u·U + v·V; same for y
		double a11 = -ax * uz - d * ux;
		double a12 = -ax * vz - d * vx;
		double b1 = d * (ox - px) - ax * (d - oz);
		double a21 = -ay * uz - d * uy;
		double a22 = -ay * vz - d * vy;
		double b2 = d * (oy - py) - ay * (d - oz);
		double det = a11 * a22 - a12 * a21;
		if (Math.abs(det) < 1e-9) {
			return null;
		}
		return new double[] {(b1 * a22 - a12 * b2) / det, (a11 * b2 - b1 * a21) / det};
	}

	/**
	 * The projection matrix (column-major, for OpenGL) that draws layer space with the camera
	 * (d, px, py) on a W×H GUI screen: z = 0 lands exactly where the flat GUI would draw it.
	 */
	public static float[] projection(double w, double h, double d, double px, double py) {
		double near = Math.max(1, d * 0.02);
		double far = d + 20000;
		double a = 2 * d / w;
		double b = 1 - 2 * px / w;
		double ay = 2 * d / h;
		double c = 2 * py / h - 1;
		double za = -(far + near) / (far - near);
		double zb = -2 * far * near / (far - near);
		// rows: clip = M · (x, y, z, 1)
		double[][] r = {
			{a, 0, b, -a * px - b * d},
			{0, -ay, c, ay * py - c * d},
			{0, 0, za, -za * d + zb},
			{0, 0, -1, d}
		};
		float[] out = new float[16];
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				out[col * 4 + row] = (float) r[row][col];
			}
		}
		return out;
	}
}
