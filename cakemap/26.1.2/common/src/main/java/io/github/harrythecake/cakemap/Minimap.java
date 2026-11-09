package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.Waypoint;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The minimap: CakeUI's "minimap" HUD piece (corner and size from the settings) and the
 * {@code cakemap:minimap} element (a layout's own box). Only the player's own marker is drawn:
 * no entities, no other players.
 */
public final class Minimap {
	private static final int FRAME = 0xFF0E0F11;
	/** The thick ring's band and its thin highlight (greys like the pixel-art icons). */
	private static final int RING_BAND = 0x5C5C5C;
	private static final int RING_LIGHT = 0x9A9A9A;

	private Minimap() {
	}

	/** GUI pixels of the thick ring for a minimap of radius {@code outer}: 4 at the default size. */
	static double ringWidth(double outer) {
		return Math.max(3, Math.min(7, Math.round(outer / 14)));
	}

	/**
	 * The minimap in CakeUI's HUD editor (CakeUI 3+): its box is the map plus the coordinates line; dropping it
	 * picks the nearest corner and the offsets from it, the handle sets the size; CakeMap's settings keep it.
	 */
	static final io.github.harrythecake.cakeui.api.Movable MOVABLE = new io.github.harrythecake.cakeui.api.Movable() {
		@Override
		public String id() {
			return "minimap";
		}

		@Override
		public net.minecraft.network.chat.Component label() {
			return net.minecraft.network.chat.Component.translatable("cakemap.movable.minimap");
		}

		@Override
		public double[] box(int w, int h) {
			MapSettings s = CakeMap.settings();
			if (!s.minimap) {
				return null;
			}
			int[] b = Minimap.box(w, h);
			return new double[] {b[0], b[1], b[2], b[2] + (s.coordinates ? 11 : 0)};
		}

		@Override
		public void place(double x, double y, double width, double height, int w, int h) {
			MapSettings s = CakeMap.settings();
			int textRoom = s.coordinates ? 11 : 0;
			int size = (int) Math.max(48, Math.min(256, Math.round(width)));
			s.size = size;
			size = Math.min(size, Math.min(w, h) - 8);
			boolean left = x + size / 2.0 < w / 2.0;
			boolean top = y + (size + textRoom) / 2.0 < h / 2.0;
			s.corner = top ? (left ? MapSettings.Corner.TOP_LEFT : MapSettings.Corner.TOP_RIGHT)
					: (left ? MapSettings.Corner.BOTTOM_LEFT : MapSettings.Corner.BOTTOM_RIGHT);
			s.offsetX = (int) Math.max(0, Math.min(400, Math.round(left ? x : w - size - x)));
			s.offsetY = (int) Math.max(0, Math.min(400, Math.round(top ? y : h - size - textRoom - y)));
		}

		@Override
		public boolean resizable() {
			return true;
		}

		@Override
		public void save() {
			CakeMap.saveSettings();
		}

		@Override
		public void cancel() {
			CakeMap.saveSettings();
		}
	};

	/** {x, y, size} of the HUD minimap on a screen of w x h GUI pixels. */
	public static int[] box(int w, int h) {
		MapSettings s = CakeMap.settings();
		int size = Math.min(s.size, Math.min(w, h) - 8);
		int textRoom = s.coordinates ? 11 : 0;
		int x = switch (s.corner) {
			case TOP_LEFT, BOTTOM_LEFT -> s.offsetX;
			default -> w - size - s.offsetX;
		};
		int y = switch (s.corner) {
			case TOP_LEFT, TOP_RIGHT -> s.offsetY;
			default -> h - size - s.offsetY - textRoom;
		};
		return new int[] {x, y, size};
	}

	/** The HUD piece. */
	public static void drawHud(GuiGraphicsExtractor ctx, int w, int h, float delta) {
		Minecraft mc = Minecraft.getInstance();
		MapSettings s = CakeMap.settings();
		if (!s.minimap || mc.player == null || mc.level == null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		int[] b = box(w, h);
		int size = b[2];
		if (CakeMap.enlarged()) {
			size = Math.min(w, h) - 40;
			b[0] = (w - size) / 2;
			b[1] = (h - size) / 2;
		}
		draw(ctx, b[0], b[1], size, size, s.shape, s.zoom, s.rotate, (float) s.opacity, s.coordinates, s.waypointsOnMinimap, delta);
	}

	public static void draw(GuiGraphicsExtractor ctx, double x, double y, double w, double h, MapSettings.Shape shape, double zoom, boolean rotate,
			float alpha, boolean coordinates, boolean waypoints, float delta) {
		MapTextures.frame();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		MapSession session = CakeMap.session();
		if (p == null || session == null) {
			return;
		}
		Vec3 pos = p.getPosition(delta);
		float yaw = p.getViewYRot(delta);
		MapGeometry.View v = new MapGeometry.View();
		v.centerX = pos.x;
		v.centerZ = pos.z;
		v.scale = zoom;
		v.angle = rotate ? Math.PI - Math.toRadians(yaw) : 0;
		v.screenX = x + w / 2.0;
		v.screenY = y + h / 2.0;
		boolean round = shape == MapSettings.Shape.CIRCLE;
		boolean ring = round && CakeMap.settings().frame == MapSettings.Frame.RING;
		double outer = Math.min(w, h) / 2.0;
		// the thick ring sits inside the box: the map gets smaller, the minimap's footprint stays
		double band = ring ? ringWidth(outer) : 0;
		double r = outer - band;
		int segments = (int) Math.max(72, Math.min(256, outer * 1.5));
		List<double[]> poly = round ? MapDraw.circle(v.screenX, v.screenY, r, segments) : MapDraw.rect(x, y, w, h);
		String dim = session.dimension();
		MapDraw.map(ctx, v, poly, session, dim, alpha, CaveLayer.active());
		float frameAlpha = Math.min(1, alpha + 0.2F);
		if (ring) {
			List<double[]> rim = MapDraw.circle(v.screenX, v.screenY, outer, segments);
			MapDraw.outline(ctx, rim, band, RING_BAND, frameAlpha);
			MapDraw.outline(ctx, MapDraw.circle(v.screenX, v.screenY, outer - band * 0.35, segments), 0.75, RING_LIGHT, frameAlpha);
			MapDraw.outline(ctx, rim, 1, FRAME & 0xFFFFFF, frameAlpha);
			MapDraw.outline(ctx, MapDraw.circle(v.screenX, v.screenY, r + 1, segments), 1, FRAME & 0xFFFFFF, frameAlpha);
		} else if (round) {
			MapDraw.outline(ctx, poly, 1.5, FRAME & 0xFFFFFF, frameAlpha);
		} else {
			ctx.outline((int) Math.floor(x) - 1, (int) Math.floor(y) - 1, (int) Math.round(w) + 2, (int) Math.round(h) + 2, FRAME);
		}
		if (waypoints) {
			drawWaypoints(ctx, v, session, dim, x, y, w, h, round, r, pos, alpha);
		}
		if (rotate || ring) {
			double[] n = v.toScreen(v.centerX, v.centerZ - 100000);
			double dx = n[0] - v.screenX;
			double dy = n[1] - v.screenY;
			double len = Math.sqrt(dx * dx + dy * dy);
			if (ring) {
				// N on the ring, on a small dark disc (both north up and turning)
				double nr = outer - band / 2;
				double nx = v.screenX + dx / len * nr;
				double ny = v.screenY + dy / len * nr;
				MapDraw.fill(ctx, MapDraw.circle(nx, ny, Math.max(5, band * 0.9), 20), FRAME & 0xFFFFFF, frameAlpha);
				ctx.centeredText(mc.font, "N", (int) Math.round(nx) + 1, (int) Math.round(ny) - 3, 0xFFFFFFFF);
			} else {
				double[] at = edge(v.screenX, v.screenY, dx / len, dy / len, x, y, w, h, round, r, 4);
				ctx.centeredText(mc.font, "N", (int) Math.round(at[0]), (int) Math.round(at[1]) - 4, 0xFFFFFFFF);
			}
		}
		if (CakeMap.settings().marker == MapSettings.Marker.HEAD) {
			PlayerFaceExtractor.extractRenderState(ctx, p.getSkin(), (int) Math.round(v.screenX) - 4, (int) Math.round(v.screenY) - 4, 8);
		} else {
			float deg = (float) (yaw + 180 + Math.toDegrees(v.angle));
			Icons.arrow(ctx, v.screenX, v.screenY, 10, deg);
		}
		if (coordinates) {
			String c = p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ();
			ctx.centeredText(mc.font, c, (int) Math.round(x + w / 2), (int) Math.round(y + h) + 3, 0xFFFFFFFF);
		}
	}

	private static void drawWaypoints(GuiGraphicsExtractor ctx, MapGeometry.View v, MapSession session, String dim, double x, double y, double w, double h,
			boolean round, double r, Vec3 player, float alpha) {
		double size = Math.max(7, Math.min(11, Math.min(w, h) / 11));
		for (Waypoint wp : session.shownWaypoints(dim)) {
			double[] s = v.toScreen(wp.x + 0.5, wp.z + 0.5);
			double dx = s[0] - v.screenX;
			double dy = s[1] - v.screenY;
			boolean inside = round ? dx * dx + dy * dy <= (r - size / 2) * (r - size / 2)
					: Math.abs(dx) <= w / 2 - size / 2 && Math.abs(dy) <= h / 2 - size / 2;
			if (!inside) {
				if (!wp.bookmarked && !wp.temporary() && !session.waypoints.isTracked(wp)) {
					continue;
				}
				double len = Math.sqrt(dx * dx + dy * dy);
				if (len < 1e-6) {
					continue;
				}
				s = edge(v.screenX, v.screenY, dx / len, dy / len, x, y, w, h, round, r, size / 2 + 1);
			}
			Icons.draw(ctx, wp, s[0], s[1], (float) size, alpha);
		}
	}

	/** The point on the shape's edge (pulled in by {@code inset}) in direction (dx, dy) from the centre. */
	static double[] edge(double cx, double cy, double dx, double dy, double x, double y, double w, double h, boolean round, double r, double inset) {
		if (round) {
			return new double[] {cx + dx * (r - inset), cy + dy * (r - inset)};
		}
		double hw = w / 2 - inset;
		double hh = h / 2 - inset;
		double t = Math.min(Math.abs(dx) < 1e-9 ? Double.MAX_VALUE : hw / Math.abs(dx), Math.abs(dy) < 1e-9 ? Double.MAX_VALUE : hh / Math.abs(dy));
		return new double[] {cx + dx * t, cy + dy * t};
	}

	/** For the settings screen and the HUD toast. */
	public static Component describe() {
		MapSettings s = CakeMap.settings();
		return Component.translatable("cakemap.minimap.describe", s.size, s.zoom);
	}
}
