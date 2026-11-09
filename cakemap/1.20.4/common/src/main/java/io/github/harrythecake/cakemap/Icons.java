package io.github.harrythecake.cakemap;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointColors;
import io.github.harrythecake.cakemap.core.WaypointKind;
import org.joml.Matrix4f;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Waypoint icons: {@code textures/waypoint/<icon>.svg} (resource packs may replace them), drawn
 * at the exact number of screen pixels they cover. Each (icon, colour, pixel size) is rasterised
 * once with JSVG and kept as a small texture, and the icon is placed on whole screen pixels, so it
 * stays sharp at any zoom and GUI scale. A bookmarked waypoint gets {@code star.svg} at its top
 * left. Player waypoints are recoloured: the colour named by {@code player.svg}'s
 * {@code <!-- cakemap-recolor: #rrggbb -->} comment (else {@code #3a8dff}) becomes the waypoint's colour.
 */
public final class Icons {
	public static final ResourceLocation ARROW = new ResourceLocation(CakeMap.MOD_ID, "textures/gui/arrow.png");
	public static final ResourceLocation ICON = new ResourceLocation(CakeMap.MOD_ID, "icon.png");
	public static final String STAR = "star";
	private static final int MAX_TEXTURES = 400;

	private static final Map<String, String> SOURCES = new HashMap<>();
	private static final Map<String, SVGDocument> DOCUMENTS = new HashMap<>();
	private static final LinkedHashMap<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(64, 0.75F, true);

	private Icons() {
	}

	/** Resource packs changed: forget every rasterised icon. Render thread. */
	public static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (ResourceLocation id : TEXTURES.values()) {
			mc.getTextureManager().release(id);
		}
		TEXTURES.clear();
		DOCUMENTS.clear();
		SOURCES.clear();
	}

	/** The colour a waypoint's icon is drawn in (0 = the icon's own colours). */
	public static int tint(Waypoint w) {
		WaypointKind k = w.kind();
		return (k == WaypointKind.PLAYER || k == WaypointKind.PLAYER_TEMPORARY) && w.color != 0 ? w.color : 0;
	}

	/** Draws a waypoint's icon centred on (x, y), {@code size} GUI pixels wide; a star when bookmarked. */
	public static void draw(GuiGraphics ctx, Waypoint w, double x, double y, float size, float alpha) {
		drawIcon(ctx, w.kind().icon(), tint(w), x, y, size, alpha);
		if (w.bookmarked) {
			float star = Math.max(5F, size * 0.55F);
			drawIcon(ctx, STAR, 0, x - size / 2.0 + star * 0.35, y - size / 2.0 + star * 0.35, star, alpha);
		}
	}

	/** Draws one SVG icon centred on (x, y), {@code size} GUI pixels wide, snapped to whole screen pixels. */
	public static void drawIcon(GuiGraphics ctx, String icon, int color, double x, double y, float size, float alpha) {
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.getWindow().getGuiScale();
		Matrix4f m = ctx.pose().last().pose();
		double ms = Math.abs(m.m00()) < 1e-6 ? 1 : m.m00(); // layouts may scale a HUD piece (uniformly)
		int px = (int) Math.max(4, Math.min(256, Math.round(size * ms * scale)));
		ResourceLocation tex = texture(icon, color, px);
		if (tex == null) {
			return;
		}
		// the icon's top left on whole screen pixels: GUI → screen, round, back
		double gx = (m.m00() * x + m.m30()) * scale - px / 2.0;
		double gy = (m.m11() * y + m.m31()) * scale - px / 2.0;
		double lx = (Math.round(gx) / scale - m.m30()) / ms;
		double ly = (Math.round(gy) / scale - m.m31()) / ms;
		float w = (float) (px / scale / ms);
		RenderSystem.enableBlend();
		ctx.setColor(1, 1, 1, alpha);
		ctx.pose().pushPose();
		ctx.pose().translate(lx, ly, 0);
		ctx.pose().scale(w / px, w / px, 1);
		ctx.blit(tex, 0, 0, 0, 0, px, px, px, px);
		ctx.pose().popPose();
		ctx.setColor(1, 1, 1, 1);
	}

	private static ResourceLocation texture(String icon, int color, int px) {
		String key = icon + "_" + String.format(Locale.ROOT, "%06x", color & 0xFFFFFF) + "_" + px;
		ResourceLocation id = TEXTURES.get(key);
		if (id != null) {
			return id;
		}
		SVGDocument doc = document(icon, color);
		if (doc == null) {
			return null;
		}
		BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		try {
			doc.render((Component) null, g, new ViewBox(0, 0, px, px));
		} catch (RuntimeException e) {
			CakeMap.LOG.warn("CakeMap: can't draw icon {}: {}", icon, e.toString());
		} finally {
			g.dispose();
		}
		NativeImage n = new NativeImage(px, px, false);
		for (int yy = 0; yy < px; yy++) {
			for (int xx = 0; xx < px; xx++) {
				int argb = img.getRGB(xx, yy);
				// NativeImage wants ABGR
				n.setPixelRGBA(xx, yy, (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));
			}
		}
		id = new ResourceLocation(CakeMap.MOD_ID, "svg/" + key);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(n));
		TEXTURES.put(key, id);
		if (TEXTURES.size() > MAX_TEXTURES) {
			var it = TEXTURES.entrySet().iterator();
			Map.Entry<String, ResourceLocation> eldest = it.next();
			Minecraft.getInstance().getTextureManager().release(eldest.getValue());
			it.remove();
		}
		return id;
	}

	private static SVGDocument document(String icon, int color) {
		String key = icon + "_" + Integer.toHexString(color & 0xFFFFFF);
		if (DOCUMENTS.containsKey(key)) {
			return DOCUMENTS.get(key);
		}
		String src = SOURCES.computeIfAbsent(icon, Icons::read);
		SVGDocument doc = null;
		if (src != null) {
			if (color != 0) {
				src = WaypointColors.recolorSvg(src, color);
			}
			try {
				doc = new SVGLoader().load(new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)), null, LoaderContext.createDefault());
			} catch (RuntimeException e) {
				CakeMap.LOG.warn("CakeMap: icon {} is not a valid SVG: {}", icon, e.toString());
			}
		}
		DOCUMENTS.put(key, doc);
		return doc;
	}

	private static String read(String icon) {
		ResourceLocation id = new ResourceLocation(CakeMap.MOD_ID, "textures/waypoint/" + icon + ".svg");
		Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(id);
		if (r.isEmpty()) {
			CakeMap.LOG.warn("CakeMap: no icon {}", id);
			return null;
		}
		try (InputStream in = r.get().open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			CakeMap.LOG.warn("CakeMap: can't read icon {}: {}", id, e.toString());
			return null;
		}
	}

	/** The player arrow (pointing up in its texture), turned by {@code degrees}. */
	public static void arrow(GuiGraphics ctx, double x, double y, float size, float degrees) {
		RenderSystem.enableBlend();
		ctx.pose().pushPose();
		ctx.pose().translate(x, y, 0);
		ctx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
		ctx.pose().scale(size / 16F, size / 16F, 1);
		ctx.blit(ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
		ctx.pose().popPose();
	}
}
