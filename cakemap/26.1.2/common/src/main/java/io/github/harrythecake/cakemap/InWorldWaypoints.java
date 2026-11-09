package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Waypoints in the world view: an icon at the waypoint's place on screen, its distance, and its
 * name when the crosshair is near it. Drawn as CakeUI's "waypoints" HUD piece with the camera
 * matrices of the last world frame. Built-in waypoints show only when bookmarked (setting); the
 * tracked waypoint always shows, with its name, on top.
 */
public final class InWorldWaypoints {
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean ready;

	private InWorldWaypoints() {
	}

	/** After the world is drawn (the loader's event): keep the camera of this frame. */
	public static void capture(Matrix4fc view, Matrix4fc projection, Vec3 cameraPos) {
		VIEW.set(view);
		PROJECTION.set(projection);
		camera = cameraPos;
		ready = true;
	}

	public static void draw(GuiGraphicsExtractor ctx, int w, int h, float delta) {
		Minecraft mc = Minecraft.getInstance();
		MapSettings s = CakeMap.settings();
		MapSession session = CakeMap.session();
		if (!ready || !s.waypointsInWorld || session == null || mc.player == null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		List<Waypoint> shown = new ArrayList<>();
		Waypoint tracked = session.waypoints.tracked();
		for (Waypoint wp : session.shownWaypoints(session.dimension())) {
			if (wp != tracked && wp.builtin && s.builtinInWorldOnlyBookmarked && !wp.bookmarked) {
				continue;
			}
			shown.add(wp);
		}
		Vec3 eye = mc.player.getEyePosition(delta);
		// by layer (tracked and compass target last), then far ones first, so near ones are drawn on top
		shown.sort((a, b) -> {
			int l = Integer.compare(session.waypoints.layer(a), session.waypoints.layer(b));
			return l != 0 ? l : Double.compare(b.distanceSq(eye.x, eye.y, eye.z), a.distanceSq(eye.x, eye.y, eye.z));
		});
		Matrix4f mvp = new Matrix4f(PROJECTION).mul(VIEW);
		int[] mini = s.minimap ? Minimap.box(w, h) : null;
		Vector4f v = new Vector4f();
		for (Waypoint wp : shown) {
			v.set((float) (wp.x + 0.5 - camera.x), (float) (wp.y + 1.2 - camera.y), (float) (wp.z + 0.5 - camera.z), 1F);
			mvp.transform(v);
			if (v.w <= 0.05F) {
				continue; // behind the camera
			}
			double sx = (v.x / v.w + 1) / 2 * w;
			double sy = (1 - v.y / v.w) / 2 * h;
			if (sx < -20 || sx > w + 20 || sy < -20 || sy > h + 20) {
				continue;
			}
			if (wp != tracked && mini != null && sx > mini[0] - 8 && sx < mini[0] + mini[2] + 8 && sy > mini[1] - 8 && sy < mini[1] + mini[2] + 20) {
				continue; // under the minimap: the minimap shows it
			}
			double dist = Math.sqrt(wp.distanceSq(eye.x, eye.y, eye.z));
			Icons.draw(ctx, wp, sx, sy, wp == tracked ? 13 : 11, wp == tracked ? 1F : 0.9F);
			String d = dist >= 1000 ? String.format("%.1fk", dist / 1000) : Math.round(dist) + "m";
			ctx.centeredText(mc.font, d, (int) Math.round(sx), (int) Math.round(sy) + 7, 0xFFE8E8E8);
			double cx = sx - w / 2.0;
			double cy = sy - h / 2.0;
			if (cx * cx + cy * cy < 40 * 40 || wp.temporary() || wp == tracked) {
				String name = wp.name.isEmpty() ? net.minecraft.network.chat.Component.translatable(wp.kind().translationKey()).getString() : wp.name;
				int tw = mc.font.width(name);
				int tx = (int) Math.round(sx) - tw / 2;
				int ty = (int) Math.round(sy) - 18;
				ctx.fill(tx - 2, ty - 2, tx + tw + 2, ty + 9, 0x90000000);
				ctx.text(mc.font, name, tx, ty, wp.color != 0 ? 0xFF000000 | wp.color : 0xFFFFFFFF);
			}
		}
	}
}
