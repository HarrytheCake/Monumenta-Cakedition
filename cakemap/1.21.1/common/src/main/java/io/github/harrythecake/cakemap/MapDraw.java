package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.harrythecake.cakemap.core.RegionData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws map layers into any convex shape (square, circle, rotated square, the whole screen): the
 * shape is turned into world coordinates, clipped against each region, and each piece is drawn as
 * textured triangles. One code path for the minimap, the CakeUI element and the world map.
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
	public static void map(GuiGraphics ctx, MapGeometry.View v, List<double[]> shape, MapSession session, String dim, float alpha, boolean cave) {
		Matrix4f m = ctx.pose().last().pose();
		fill(m, shape, 0x1A1C1F, alpha * 0.92F);
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
					tile(m, v, world, t.id(full), rx * RegionData.SIZE, rz * RegionData.SIZE, RegionData.SIZE, t.texels(full), alpha);
				}
				if (shard != null && shard.has(rx, rz)) {
					MapTextures.Tex st = StaticMaps.texture(shard, rx, rz, full);
					if (st != null && st.ready()) {
						tile(m, v, world, st.id(full), rx * RegionData.SIZE, rz * RegionData.SIZE, RegionData.SIZE, st.texels(full), alpha);
					}
				}
			}
		}
		if (cave && CaveLayer.id() != null && dim.equals(session.dimension())) {
			tile(m, v, world, CaveLayer.id(), CaveLayer.originX(), CaveLayer.originZ(), CaveLayer.SIZE, CaveLayer.SIZE, alpha);
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
	static void tile(Matrix4f m, MapGeometry.View v, List<double[]> worldShape, ResourceLocation tex, double x0, double z0, double size, int texels, float alpha) {
		List<double[]> poly = MapGeometry.clip(worldShape, x0, z0, x0 + size, z0 + size);
		if (poly.size() < 3) {
			return;
		}
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, tex);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
		double[] a = poly.get(0);
		for (int i = 1; i < poly.size() - 1; i++) {
			vertex(bb, m, v, a, x0, z0, size, texels, alpha);
			vertex(bb, m, v, poly.get(i), x0, z0, size, texels, alpha);
			vertex(bb, m, v, poly.get(i + 1), x0, z0, size, texels, alpha);
		}
		RenderSystem.disableCull(); // our shapes wind either way
		BufferUploader.drawWithShader(bb.buildOrThrow());
		RenderSystem.enableCull();
	}

	private static void vertex(BufferBuilder bb, Matrix4f m, MapGeometry.View v, double[] w, double x0, double z0, double size, int texels, float alpha) {
		double[] s = v.toScreen(w[0], w[1]);
		// half a texel in from the edges: nearest sampling at exactly 0 or 1 would wrap to the far side
		double e = 0.5 / texels;
		float u = (float) Math.max(e, Math.min(1 - e, (w[0] - x0) / size));
		float t = (float) Math.max(e, Math.min(1 - e, (w[1] - z0) / size));
		bb.addVertex(m, (float) s[0], (float) s[1], 0).setUv(u, t).setColor(1F, 1F, 1F, alpha);
	}

	/** A plain colour in a screen-space convex shape. */
	public static void fill(Matrix4f m, List<double[]> shape, int rgb, float alpha) {
		if (shape.size() < 3) {
			return;
		}
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
		float r = ((rgb >> 16) & 0xFF) / 255F;
		float g = ((rgb >> 8) & 0xFF) / 255F;
		float b = (rgb & 0xFF) / 255F;
		double[] a = shape.get(0);
		for (int i = 1; i < shape.size() - 1; i++) {
			bb.addVertex(m, (float) a[0], (float) a[1], 0).setColor(r, g, b, alpha);
			bb.addVertex(m, (float) shape.get(i)[0], (float) shape.get(i)[1], 0).setColor(r, g, b, alpha);
			bb.addVertex(m, (float) shape.get(i + 1)[0], (float) shape.get(i + 1)[1], 0).setColor(r, g, b, alpha);
		}
		RenderSystem.disableCull(); // our shapes wind either way
		BufferUploader.drawWithShader(bb.buildOrThrow());
		RenderSystem.enableCull();
	}

	/** The outline of a round screen-space shape, {@code width} GUI pixels inside it. */
	public static void outline(Matrix4f m, List<double[]> shape, double width, int rgb, float alpha) {
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
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
		float r = ((rgb >> 16) & 0xFF) / 255F;
		float g = ((rgb >> 8) & 0xFF) / 255F;
		float b = (rgb & 0xFF) / 255F;
		for (int i = 0; i < n; i++) {
			double[] p = shape.get(i);
			double[] q = shape.get((i + 1) % n);
			double[] pi = inset(p, cx, cy, width);
			double[] qi = inset(q, cx, cy, width);
			quad(bb, m, p, q, qi, pi, r, g, b, alpha);
		}
		RenderSystem.disableCull(); // our shapes wind either way
		BufferUploader.drawWithShader(bb.buildOrThrow());
		RenderSystem.enableCull();
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

	private static void quad(BufferBuilder bb, Matrix4f m, double[] a, double[] b, double[] c, double[] d, float r, float g, float bl, float al) {
		bb.addVertex(m, (float) a[0], (float) a[1], 0).setColor(r, g, bl, al);
		bb.addVertex(m, (float) b[0], (float) b[1], 0).setColor(r, g, bl, al);
		bb.addVertex(m, (float) c[0], (float) c[1], 0).setColor(r, g, bl, al);
		bb.addVertex(m, (float) a[0], (float) a[1], 0).setColor(r, g, bl, al);
		bb.addVertex(m, (float) c[0], (float) c[1], 0).setColor(r, g, bl, al);
		bb.addVertex(m, (float) d[0], (float) d[1], 0).setColor(r, g, bl, al);
	}
}
