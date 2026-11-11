package io.github.harrythecake.cakemap;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointColors;
import io.github.harrythecake.cakemap.core.WaypointKind;
import org.joml.Matrix3x2f;

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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
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
	public static final Identifier ARROW = Identifier.fromNamespaceAndPath(CakeMap.MOD_ID, "textures/gui/arrow.png");
	public static final Identifier ICON = Identifier.fromNamespaceAndPath(CakeMap.MOD_ID, "icon.png");
	public static final String STAR = "star";
	/**
	 * POI icons are Minecraft's own experience orb (user, 2026-11-11, on trial): the third size of
	 * {@code textures/entity/experience_orb.png} (frames 16x16 in a 4x4 sheet; frame 2's art is the 12x12 at
	 * 34,2), drawn straight from the player's installed game and resource packs. Nothing of Mojang's ships
	 * with CakeMap. The texture is grey; the game tints orbs with a pulsing green, here held still at the
	 * middle of that pulse.
	 */
	private static final Identifier XP_ORB = Identifier.withDefaultNamespace("textures/entity/experience_orb.png");
	private static final String ORB_ICON = "poi";
	private static final int ORB_U = 34;
	private static final int ORB_V = 2;
	private static final int ORB_ART = 12;
	/** The orb's tint (128,255,26) as RGB: no shader colour since 1.21.6, so it goes into the vertex colour. */
	private static final int ORB_TINT = 128 << 16 | 255 << 8 | 26;
	/** The orb is drawn this much smaller than the other icons (user, 2026-11-11: "a bit too large", -25 %). */
	private static final float ORB_SCALE = 0.75F;
	private static final int MAX_TEXTURES = 400;

	private static final Map<String, String> SOURCES = new HashMap<>();
	private static final Map<String, SVGDocument> DOCUMENTS = new HashMap<>();
	/** Pixel-art icons ({@code textures/waypoint/<icon>.png}, 32x32; user, 2026-11-11), by icon; empty = none (use the SVG). */
	private static final Map<String, Optional<BufferedImage>> PNGS = new HashMap<>();
	private static final LinkedHashMap<String, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75F, true);

	private Icons() {
	}

	/** Resource packs changed: forget every rasterised icon. Render thread. */
	public static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (Identifier id : TEXTURES.values()) {
			mc.getTextureManager().release(id);
		}
		TEXTURES.clear();
		DOCUMENTS.clear();
		PNGS.clear();
		SOURCES.clear();
	}

	/** The colour a waypoint's icon is drawn in (0 = the icon's own colours). */
	public static int tint(Waypoint w) {
		WaypointKind k = w.kind();
		return (k == WaypointKind.PLAYER || k == WaypointKind.PLAYER_TEMPORARY) && w.color != 0 ? w.color : 0;
	}

	/** Draws a waypoint's icon centred on (x, y), {@code size} GUI pixels wide; a star when bookmarked. */
	public static void draw(GuiGraphicsExtractor ctx, Waypoint w, double x, double y, float size, float alpha) {
		drawIcon(ctx, w.kind().icon(), tint(w), x, y, size, alpha);
		if (w.bookmarked) {
			float star = Math.max(5F, size * 0.55F);
			drawIcon(ctx, STAR, 0, x - size / 2.0 + star * 0.35, y - size / 2.0 + star * 0.35, star, alpha);
		}
	}

	/** Draws one SVG icon centred on (x, y), {@code size} GUI pixels wide, snapped to whole screen pixels. */
	public static void drawIcon(GuiGraphicsExtractor ctx, String icon, int color, double x, double y, float size, float alpha) {
		if (icon.equals(ORB_ICON)) {
			drawOrb(ctx, x, y, size, alpha);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.getWindow().getGuiScale();
		Matrix3x2f m = new Matrix3x2f(ctx.pose());
		double ms = Math.abs(m.m00()) < 1e-6 ? 1 : m.m00(); // layouts may scale a HUD piece (uniformly)
		int px = (int) Math.max(4, Math.min(256, Math.round(size * ms * scale)));
		BufferedImage art = png(icon);
		if (art != null && px > art.getWidth()) {
			// pixel art bigger than drawn: a whole multiple of its pixels when one is within 15 %, so it stays even
			int even = Math.round(px / (float) art.getWidth()) * art.getWidth();
			px = Math.abs(even - px) <= px * 0.15 ? even : px;
		}
		Identifier tex = texture(icon, color, px);
		if (tex == null) {
			return;
		}
		// the icon's top left on whole screen pixels: GUI → screen, round, back
		double gx = (m.m00() * x + m.m20()) * scale - px / 2.0;
		double gy = (m.m11() * y + m.m21()) * scale - px / 2.0;
		double lx = (Math.round(gx) / scale - m.m20()) / ms;
		double ly = (Math.round(gy) / scale - m.m21()) / ms;
		float w = (float) (px / scale / ms);
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) lx, (float) ly);
		ctx.pose().scale(w / px, w / px);
		// no shader colour since 1.21.6: the alpha goes into the vertex colour
		ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, px, px, px, px, (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | 0xFFFFFF);
		ctx.pose().popMatrix();
	}

	/**
	 * The experience orb centred on (x, y), {@link #ORB_SCALE} of {@code size} GUI pixels wide, on whole screen
	 * pixels: a whole multiple of its 12 art pixels when one is within 15 % (the pixel art stays even), else
	 * that exact size (some art pixels a screen pixel wider than others).
	 */
	private static void drawOrb(GuiGraphicsExtractor ctx, double x, double y, float size, float alpha) {
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.getWindow().getGuiScale();
		Matrix3x2f m = new Matrix3x2f(ctx.pose());
		double ms = Math.abs(m.m00()) < 1e-6 ? 1 : m.m00();
		double want = Math.max(4, Math.min(256, size * ORB_SCALE * ms * scale));
		int even = Math.max(1, (int) Math.round(want / ORB_ART)) * ORB_ART;
		int px = Math.abs(even - want) <= want * 0.15 ? even : (int) Math.round(want);
		double gx = (m.m00() * x + m.m20()) * scale - px / 2.0;
		double gy = (m.m11() * y + m.m21()) * scale - px / 2.0;
		double lx = (Math.round(gx) / scale - m.m20()) / ms;
		double ly = (Math.round(gy) / scale - m.m21()) / ms;
		float w = (float) (px / scale / ms);
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) lx, (float) ly);
		ctx.pose().scale(w / px, w / px);
		// the 64x64 sheet's UVs: a higher-resolution resource pack orb lands on the same frame;
		// tint and alpha in the vertex colour (no shader colour since 1.21.6)
		int argb = (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | ORB_TINT;
		ctx.blit(RenderPipelines.GUI_TEXTURED, XP_ORB, 0, 0, ORB_U, ORB_V, px, px, ORB_ART, ORB_ART, 64, 64, argb);
		ctx.pose().popMatrix();
	}

	private static Identifier texture(String icon, int color, int px) {
		String key = icon + "_" + String.format(Locale.ROOT, "%06x", color & 0xFFFFFF) + "_" + px;
		Identifier id = TEXTURES.get(key);
		if (id != null) {
			return id;
		}
		BufferedImage art = png(icon);
		BufferedImage img;
		if (art != null) {
			img = scaleArt(art, px);
		} else {
			SVGDocument doc = document(icon, color);
			if (doc == null) {
				return null;
			}
			img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
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
		}
		NativeImage n = new NativeImage(px, px, false);
		for (int yy = 0; yy < px; yy++) {
			for (int xx = 0; xx < px; xx++) {
				int argb = img.getRGB(xx, yy);
				// NativeImage wants ABGR
				n.setPixelABGR(xx, yy, (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));
			}
		}
		id = Identifier.fromNamespaceAndPath(CakeMap.MOD_ID, "svg/" + key);
		String label = "cakemap icon " + key;
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> label, n));
		TEXTURES.put(key, id);
		if (TEXTURES.size() > MAX_TEXTURES) {
			var it = TEXTURES.entrySet().iterator();
			Map.Entry<String, Identifier> eldest = it.next();
			Minecraft.getInstance().getTextureManager().release(eldest.getValue());
			it.remove();
		}
		return id;
	}

	/** The icon's pixel art, or null when it has only an SVG. */
	private static BufferedImage png(String icon) {
		return PNGS.computeIfAbsent(icon, i -> {
			Identifier id = Identifier.fromNamespaceAndPath(CakeMap.MOD_ID, "textures/waypoint/" + i + ".png");
			Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(id);
			if (r.isEmpty()) {
				return Optional.empty();
			}
			try (InputStream in = r.get().open()) {
				BufferedImage read = javax.imageio.ImageIO.read(in);
				if (read == null) {
					return Optional.empty();
				}
				BufferedImage argb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = argb.createGraphics();
				g.drawImage(read, 0, 0, null);
				g.dispose();
				return Optional.of(argb);
			} catch (java.io.IOException e) {
				CakeMap.LOG.warn("CakeMap: can't read icon {}: {}", id, e.toString());
				return Optional.empty();
			}
		}).orElse(null);
	}

	/**
	 * Pixel art at px x px screen pixels: smaller = area-averaged (each screen pixel the mean of the art
	 * pixels it covers, colours weighted by their opacity), bigger = nearest (sharp pixels).
	 */
	private static BufferedImage scaleArt(BufferedImage art, int px) {
		int aw = art.getWidth();
		int ah = art.getHeight();
		BufferedImage out = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		if (px >= aw) {
			for (int y = 0; y < px; y++) {
				for (int x = 0; x < px; x++) {
					out.setRGB(x, y, art.getRGB(Math.min(aw - 1, x * aw / px), Math.min(ah - 1, y * ah / px)));
				}
			}
			return out;
		}
		double fx = (double) aw / px;
		double fy = (double) ah / px;
		for (int y = 0; y < px; y++) {
			for (int x = 0; x < px; x++) {
				double x0 = x * fx, x1 = x0 + fx, y0 = y * fy, y1 = y0 + fy;
				double a = 0, r = 0, g = 0, b = 0, area = 0;
				for (int sy = (int) y0; sy < Math.ceil(y1) && sy < ah; sy++) {
					double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
					for (int sx = (int) x0; sx < Math.ceil(x1) && sx < aw; sx++) {
						double w = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
						int c = art.getRGB(sx, sy);
						double ca = (c >>> 24) / 255.0 * w;
						a += ca;
						r += ((c >> 16) & 0xFF) * ca;
						g += ((c >> 8) & 0xFF) * ca;
						b += (c & 0xFF) * ca;
						area += w;
					}
				}
				if (a > 0) {
					int oa = (int) Math.round(a / area * 255);
					out.setRGB(x, y, oa << 24 | (int) Math.round(r / a) << 16 | (int) Math.round(g / a) << 8 | (int) Math.round(b / a));
				}
			}
		}
		return out;
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
		Identifier id = Identifier.fromNamespaceAndPath(CakeMap.MOD_ID, "textures/waypoint/" + icon + ".svg");
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
	public static void arrow(GuiGraphicsExtractor ctx, double x, double y, float size, float degrees) {
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x, (float) y);
		ctx.pose().rotate((float) Math.toRadians(degrees));
		ctx.pose().scale(size / 16F, size / 16F);
		ctx.blit(RenderPipelines.GUI_TEXTURED, ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
		ctx.pose().popMatrix();
	}
}
