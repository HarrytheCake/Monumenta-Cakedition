package io.github.harrythecake.cakeui.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * Where an edited HUD port goes. A port's "inner box" is where it is drawn without the editor's
 * transform (vanilla's place, or the layout's); the editor's transform maps it to the screen as
 * {@code screen = inner * s + t}. Pure math, GUI pixels.
 */
public final class Placement {
	private Placement() {
	}

	public record Box(double x, double y, double w, double h) {
		public double right() {
			return x + w;
		}

		public double bottom() {
			return y + h;
		}

		public double cx() {
			return x + w / 2;
		}

		public double cy() {
			return y + h / 2;
		}

		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}

		public Box union(Box o) {
			if (o == null) {
				return this;
			}
			double x1 = Math.min(x, o.x);
			double y1 = Math.min(y, o.y);
			return new Box(x1, y1, Math.max(right(), o.right()) - x1, Math.max(bottom(), o.bottom()) - y1);
		}
	}

	/** {@code screen = inner * s + (tx, ty)}. */
	public record Xform(double tx, double ty, double s) {
		public static final Xform IDENTITY = new Xform(0, 0, 1);

		public Box apply(Box b) {
			return new Box(b.x * s + tx, b.y * s + ty, b.w * s, b.h * s);
		}

		public double innerX(double screenX) {
			return (screenX - tx) / s;
		}

		public double innerY(double screenY) {
			return (screenY - ty) / s;
		}

		public boolean identity() {
			return tx == 0 && ty == 0 && s == 1;
		}
	}

	/** The editor's transform for a port whose inner box is {@code inner}, on a {@code w} x {@code h} screen. */
	public static Xform transform(PortEdit e, Box inner, double w, double h) {
		if (e == null) {
			return Xform.IDENTITY;
		}
		double s = e.scaleOr1();
		if (e.mode == null) {
			// only scaled: grow from the box's centre
			return new Xform(inner.cx() - inner.cx() * s, inner.cy() - inner.cy() * s, s);
		}
		double[] lt = topLeft(e, inner.w * s, inner.h * s, w, h);
		return new Xform(lt[0] - inner.x * s, lt[1] - inner.y * s, s);
	}

	/** The top-left corner of a piece of size sw x sh placed by {@code e}. */
	static double[] topLeft(PortEdit e, double sw, double sh, double w, double h) {
		return switch (e.mode) {
			case ANCHORED -> new double[] {e.ax * w + e.x - e.ax * sw, e.ay * h + e.y - e.ay * sh};
			case PERCENT -> new double[] {e.ax * Math.max(0, w - sw), e.ay * Math.max(0, h - sh)};
			case FIXED -> new double[] {e.x, e.y};
		};
	}

	/**
	 * Stores a dropped piece (top-left {@code left, top}, size on screen sw x sh) in {@code e} with
	 * {@code mode}. Anchored attaches it to the nearest of the 9 screen anchors.
	 */
	public static void place(PortEdit e, PortEdit.Mode mode, double left, double top, double sw, double sh, double w, double h) {
		e.mode = mode;
		switch (mode) {
			case ANCHORED -> {
				e.ax = nearest(left + sw / 2, w);
				e.ay = nearest(top + sh / 2, h);
				e.x = round(left + e.ax * sw - e.ax * w);
				e.y = round(top + e.ay * sh - e.ay * h);
			}
			case PERCENT -> {
				e.ax = fraction(left, w - sw);
				e.ay = fraction(top, h - sh);
				e.x = 0;
				e.y = 0;
			}
			case FIXED -> {
				e.ax = 0;
				e.ay = 0;
				e.x = round(left);
				e.y = round(top);
			}
		}
	}

	/** 0, 0.5 or 1: which third of the screen the point is in. */
	static double nearest(double c, double size) {
		return c < size / 3 ? 0 : c < size * 2 / 3 ? 0.5 : 1;
	}

	private static double fraction(double pos, double free) {
		if (free <= 0) {
			return 0.5;
		}
		return Math.round(Math.max(0, Math.min(1, pos / free)) * 10000) / 10000.0;
	}

	private static double round(double v) {
		return Math.round(v * 2) / 2.0;
	}

	// ---- snapping ----

	/** A guide line to draw while dragging: from (x1, y1) to (x2, y2). */
	public record Guide(double x1, double y1, double x2, double y2) {
	}

	public record Snapped(double left, double top, List<Guide> guides) {
	}

	/**
	 * Snaps a dragged box (screen coordinates) to the screen's edges and centre lines and to the
	 * other pieces' edges and centres, when within {@code threshold} pixels.
	 */
	public static Snapped snap(double left, double top, double bw, double bh, List<Box> others, double w, double h, double threshold) {
		List<Double> xs = new ArrayList<>(List.of(0.0, w / 2, w));
		List<Double> ys = new ArrayList<>(List.of(0.0, h / 2, h));
		for (Box o : others) {
			xs.add(o.x);
			xs.add(o.cx());
			xs.add(o.right());
			ys.add(o.y);
			ys.add(o.cy());
			ys.add(o.bottom());
		}
		double[] sx = best(new double[] {left, left + bw / 2, left + bw}, xs, threshold);
		double[] sy = best(new double[] {top, top + bh / 2, top + bh}, ys, threshold);
		double nl = left + sx[0];
		double nt = top + sy[0];
		List<Guide> guides = new ArrayList<>();
		if (sx[1] == 1) {
			guides.add(new Guide(sx[2], 0, sx[2], h));
		}
		if (sy[1] == 1) {
			guides.add(new Guide(0, sy[2], w, sy[2]));
		}
		return new Snapped(nl, nt, guides);
	}

	/** {shift, found (1/0), line}: the smallest move that puts one of the edges on a target. */
	private static double[] best(double[] edges, List<Double> targets, double threshold) {
		double bestShift = 0;
		double bestDist = threshold + 1e-9;
		double line = 0;
		boolean found = false;
		for (double e : edges) {
			for (double t : targets) {
				double d = Math.abs(t - e);
				if (d < bestDist) {
					bestDist = d;
					bestShift = t - e;
					line = t;
					found = true;
				}
			}
		}
		return new double[] {found ? bestShift : 0, found ? 1 : 0, line};
	}
}
