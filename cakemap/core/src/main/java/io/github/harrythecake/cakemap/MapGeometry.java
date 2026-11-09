package io.github.harrythecake.cakemap;

import java.util.ArrayList;
import java.util.List;

/**
 * The map's geometry without Minecraft: where the map is on screen ({@link View}), shapes as
 * convex polygons, and clipping a polygon to a region's square. MapDraw draws with it.
 */
public final class MapGeometry {
	/** Where the map is: world point at the screen point, GUI pixels per block, rotation. */
	public static final class View {
		public double centerX;
		public double centerZ;
		public double scale = 1;
		/** Radians; 0 = north up. */
		public double angle;
		public double screenX;
		public double screenY;

		public double[] toScreen(double wx, double wz) {
			double dx = (wx - centerX) * scale;
			double dz = (wz - centerZ) * scale;
			double c = Math.cos(angle);
			double s = Math.sin(angle);
			return new double[] {screenX + dx * c - dz * s, screenY + dx * s + dz * c};
		}

		public double[] toWorld(double sx, double sy) {
			double dx = sx - screenX;
			double dy = sy - screenY;
			double c = Math.cos(-angle);
			double s = Math.sin(-angle);
			return new double[] {centerX + (dx * c - dy * s) / scale, centerZ + (dx * s + dy * c) / scale};
		}
	}

	private MapGeometry() {
	}

	/** A circle as a polygon (screen coordinates). */
	public static List<double[]> circle(double cx, double cy, double r, int segments) {
		List<double[]> out = new ArrayList<>(segments);
		for (int i = 0; i < segments; i++) {
			double a = Math.PI * 2 * i / segments;
			out.add(new double[] {cx + Math.cos(a) * r, cy + Math.sin(a) * r});
		}
		return out;
	}

	public static List<double[]> rect(double x, double y, double w, double h) {
		List<double[]> out = new ArrayList<>(4);
		out.add(new double[] {x, y});
		out.add(new double[] {x + w, y});
		out.add(new double[] {x + w, y + h});
		out.add(new double[] {x, y + h});
		return out;
	}

	/** Sutherland–Hodgman: a convex polygon clipped to an axis-aligned box. */
	public static List<double[]> clip(List<double[]> poly, double x0, double z0, double x1, double z1) {
		List<double[]> out = poly;
		out = clipEdge(out, 0, x0, true);
		out = clipEdge(out, 0, x1, false);
		out = clipEdge(out, 1, z0, true);
		out = clipEdge(out, 1, z1, false);
		return out;
	}

	private static List<double[]> clipEdge(List<double[]> in, int axis, double bound, boolean keepAbove) {
		List<double[]> out = new ArrayList<>(in.size() + 2);
		int n = in.size();
		for (int i = 0; i < n; i++) {
			double[] cur = in.get(i);
			double[] prev = in.get((i + n - 1) % n);
			boolean curIn = keepAbove ? cur[axis] >= bound : cur[axis] <= bound;
			boolean prevIn = keepAbove ? prev[axis] >= bound : prev[axis] <= bound;
			if (curIn) {
				if (!prevIn) {
					out.add(cross(prev, cur, axis, bound));
				}
				out.add(cur);
			} else if (prevIn) {
				out.add(cross(prev, cur, axis, bound));
			}
		}
		return out;
	}

	private static double[] cross(double[] a, double[] b, int axis, double bound) {
		double t = (bound - a[axis]) / (b[axis] - a[axis]);
		return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
	}
}
