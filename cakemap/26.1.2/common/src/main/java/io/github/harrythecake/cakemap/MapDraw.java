package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import io.github.harrythecake.cakemap.core.RegionData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws map layers into any convex shape (square, circle, rotated square, the whole screen): the
 * shape is turned into world coordinates, clipped against each region, and each piece is drawn as
 * textured triangles. One code path for the minimap, the CakeUI element and the world map.
 * Since 1.21.6 the GUI records render states and draws them at the end of the frame: each fill and
 * each region's piece is one {@link Polys} render state with the current 2D pose.
 */
public final class MapDraw {
	private MapDraw() {
	}

	/** A circle as a polygon (screen coordinates). */
	public static List<double[]> circle(double cx, double cy, double r, int segments) {
		return MapGeometry.circle(cx, cy, r, segments);
	}

	public static List<double[]> rect(double x, double y, double w, double h) {
		return MapGeometry.rect(x, y, w, h);
	}

	/**
	 * Draws the map of {@code dim}: background, dynamic regions, the static map on top where it
	 * applies, then the cave layer when given.
	 */
	public static void map(GuiGraphicsExtractor ctx, MapGeometry.View v, List<double[]> shape, MapSession session, String dim, float alpha, boolean cave) {
		fill(ctx, shape, 0x1A1C1F, alpha * 0.92F);
		if (session == null) {
			return;
		}
		List<double[]> world = new ArrayList<>(shape.size());
		double minX = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (double[] p : shape) {
			double[] w = v.toWorld(p[0], p[1]);
			world.add(w);
			minX = Math.min(minX, w[0]);
			minZ = Math.min(minZ, w[1]);
			maxX = Math.max(maxX, w[0]);
			maxZ = Math.max(maxZ, w[1]);
		}
		double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		boolean full = v.scale * guiScale >= 0.75;
		int rx0 = Math.floorDiv((int) Math.floor(minX), RegionData.SIZE);
		int rx1 = Math.floorDiv((int) Math.floor(maxX), RegionData.SIZE);
		int rz0 = Math.floorDiv((int) Math.floor(minZ), RegionData.SIZE);
		int rz1 = Math.floorDiv((int) Math.floor(maxZ), RegionData.SIZE);
		if ((long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1) > 900) {
			return; // absurdly zoomed out: nothing sensible to draw
		}
		RegionStore store = session.store(dim);
		StaticMaps.Shard shard = session.showStatic(dim) ? StaticMaps.shard(session.server, dim) : null;
		for (int rz = rz0; rz <= rz1; rz++) {
			for (int rx = rx0; rx <= rx1; rx++) {
				MapTextures.Tex t = dynamic(session, store, dim, rx, rz, full);
				if (t != null && t.ready()) {
					tile(ctx, v, world, t.id(full), rx * RegionData.SIZE, rz * RegionData.SIZE, RegionData.SIZE, t.texels(full), alpha);
				}
				if (shard != null && shard.has(rx, rz)) {
					MapTextures.Tex st = StaticMaps.texture(shard, rx, rz, full);
					if (st != null && st.ready()) {
						tile(ctx, v, world, st.id(full), rx * RegionData.SIZE, rz * RegionData.SIZE, RegionData.SIZE, st.texels(full), alpha);
					}
				}
			}
		}
		if (cave && CaveLayer.id() != null && dim.equals(session.dimension())) {
			tile(ctx, v, world, CaveLayer.id(), CaveLayer.originX(), CaveLayer.originZ(), CaveLayer.SIZE, CaveLayer.SIZE, alpha);
		}
	}

	private static MapTextures.Tex dynamic(MapSession session, RegionStore store, String dim, int rx, int rz, boolean full) {
		RegionData inMemory = store.peek(rx, rz);
		if (inMemory == null && !store.hasRegion(rx, rz)) {
			return null;
		}
		int rev = inMemory == null ? 0 : inMemory.revision;
		return MapTextures.get("dyn/" + session.server + "/" + dim + "/" + rx + "_" + rz, rev, full,
				() -> store.forView(rx, rz).thenComposeAsync(MapTextures::shade, CakeMap::onRender));
	}

	/** One texture covering world square [x0, x0 + size) x [z0, z0 + size), clipped to the shape. */
	static void tile(GuiGraphicsExtractor ctx, MapGeometry.View v, List<double[]> worldShape, Identifier tex, double x0, double z0, double size, int texels,
			float alpha) {
		List<double[]> poly = MapGeometry.clip(worldShape, x0, z0, x0 + size, z0 + size);
		if (poly.size() < 3) {
			return;
		}
		AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
		Polys p = new Polys(RenderPipelines.GUI_TEXTURED,
				TextureSetup.singleTexture(t.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)), ctx.pose(), true);
		int color = Polys.argb(0xFFFFFF, alpha);
		float[] a = vertex(v, poly.get(0), x0, z0, size, texels);
		for (int i = 1; i < poly.size() - 1; i++) {
			float[] b = vertex(v, poly.get(i), x0, z0, size, texels);
			float[] c = vertex(v, poly.get(i + 1), x0, z0, size, texels);
			p.triangle(a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3], c[0], c[1], c[2], c[3], color);
		}
		p.submit(ctx);
	}

	/** {screen x, screen y, u, v} of a world point inside the tile. */
	private static float[] vertex(MapGeometry.View v, double[] w, double x0, double z0, double size, int texels) {
		double[] s = v.toScreen(w[0], w[1]);
		// half a texel in from the edges: nearest sampling at exactly 0 or 1 would wrap to the far side
		double e = 0.5 / texels;
		float u = (float) Math.max(e, Math.min(1 - e, (w[0] - x0) / size));
		float t = (float) Math.max(e, Math.min(1 - e, (w[1] - z0) / size));
		return new float[] {(float) s[0], (float) s[1], u, t};
	}

	/** A plain colour in a screen-space convex shape. */
	public static void fill(GuiGraphicsExtractor ctx, List<double[]> shape, int rgb, float alpha) {
		if (shape.size() < 3) {
			return;
		}
		Polys p = new Polys(RenderPipelines.GUI, TextureSetup.noTexture(), ctx.pose(), false);
		int color = Polys.argb(rgb, alpha);
		double[] a = shape.get(0);
		for (int i = 1; i < shape.size() - 1; i++) {
			p.triangle((float) a[0], (float) a[1], 0, 0, (float) shape.get(i)[0], (float) shape.get(i)[1], 0, 0, (float) shape.get(i + 1)[0],
					(float) shape.get(i + 1)[1], 0, 0, color);
		}
		p.submit(ctx);
	}

	/** The outline of a round screen-space shape, {@code width} GUI pixels inside it. */
	public static void outline(GuiGraphicsExtractor ctx, List<double[]> shape, double width, int rgb, float alpha) {
		int n = shape.size();
		if (n < 3) {
			return;
		}
		double cx = 0;
		double cy = 0;
		for (double[] p : shape) {
			cx += p[0];
			cy += p[1];
		}
		cx /= n;
		cy /= n;
		Polys out = new Polys(RenderPipelines.GUI, TextureSetup.noTexture(), ctx.pose(), false);
		int color = Polys.argb(rgb, alpha);
		for (int i = 0; i < n; i++) {
			double[] p = shape.get(i);
			double[] q = shape.get((i + 1) % n);
			double[] pi = inset(p, cx, cy, width);
			double[] qi = inset(q, cx, cy, width);
			out.triangle((float) p[0], (float) p[1], 0, 0, (float) q[0], (float) q[1], 0, 0, (float) qi[0], (float) qi[1], 0, 0, color);
			out.triangle((float) p[0], (float) p[1], 0, 0, (float) qi[0], (float) qi[1], 0, 0, (float) pi[0], (float) pi[1], 0, 0, color);
		}
		out.submit(ctx);
	}

	/** Moves a point `w` towards the centre (radially: right for circles; the square frame uses a plain border). */
	private static double[] inset(double[] p, double cx, double cy, double w) {
		double dx = cx - p[0];
		double dy = cy - p[1];
		double len = Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-6) {
			return p;
		}
		double k = Math.min(1, w / len);
		return new double[] {p[0] + dx * k, p[1] + dy * k};
	}
}
