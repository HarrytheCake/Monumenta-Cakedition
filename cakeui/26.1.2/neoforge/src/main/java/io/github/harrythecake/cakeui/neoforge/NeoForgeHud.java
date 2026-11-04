package io.github.harrythecake.cakeui.neoforge;

import io.github.harrythecake.cakeui.CakeUIClient;
import io.github.harrythecake.cakeui.HudPart;
import io.github.harrythecake.cakeui.HudTiming;
import io.github.harrythecake.cakeui.PluginHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

/**
 * The HUD ports on NeoForge 1.21.1 and 26.1: NeoForge draws the HUD as named layers, each with a Pre/Post
 * event, so most vanilla pieces are one layer: Pre hides it (cancel) or moves, scales and fades it,
 * Post undoes that and draws the port's elements. Vignette, spyglass and pumpkin share one layer
 * (camera overlays); they are wrapped one by one by the common GuiCameraOverlaysMixin instead.
 */
final class NeoForgeHud {
	/** Layer to port, with the point a layout's scale grows the piece from. */
	private record Piece(String port, Pivot pivot) {
	}

	private interface Pivot {
		double[] at(int w, int h);
	}

	private static final Map<Identifier, Piece> PIECES = new HashMap<>();
	private static boolean backDrawn;

	static {
		put(VanillaGuiLayers.HOTBAR, "hotbar", (w, h) -> new double[] {w / 2.0, h - 11});
		put(VanillaGuiLayers.CROSSHAIR, "crosshair", (w, h) -> new double[] {w / 2.0, h / 2.0});
		put(VanillaGuiLayers.BOSS_OVERLAY, "bossBars", (w, h) -> new double[] {w / 2.0, 12});
		put(VanillaGuiLayers.PLAYER_HEALTH, "health", (w, h) -> new double[] {w / 2.0 - 91, h - 39});
		put(VanillaGuiLayers.ARMOR_LEVEL, "armor", (w, h) -> new double[] {w / 2.0 - 91, h - 49});
		put(VanillaGuiLayers.FOOD_LEVEL, "hunger", (w, h) -> new double[] {w / 2.0 + 91, h - 39});
		put(VanillaGuiLayers.AIR_LEVEL, "air", (w, h) -> new double[] {w / 2.0 + 91, h - 49});
		put(VanillaGuiLayers.VEHICLE_HEALTH, "mountHealth", (w, h) -> new double[] {w / 2.0 + 91, h - 39});
		put(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, "experience", (w, h) -> new double[] {w / 2.0, h - 29});
		put(VanillaGuiLayers.CONTEXTUAL_INFO_BAR, "experience", (w, h) -> new double[] {w / 2.0, h - 29});
		put(VanillaGuiLayers.EXPERIENCE_LEVEL, "experience", (w, h) -> new double[] {w / 2.0, h - 29});
		put(VanillaGuiLayers.SELECTED_ITEM_NAME, "heldItemName", (w, h) -> new double[] {w / 2.0, h - 59});
		put(VanillaGuiLayers.EFFECTS, "statusEffects", (w, h) -> new double[] {w, 0});
		put(VanillaGuiLayers.SCOREBOARD_SIDEBAR, "scoreboard", (w, h) -> new double[] {w, h / 2.0});
		put(VanillaGuiLayers.TAB_LIST, "tabList", (w, h) -> new double[] {w / 2.0, 0});
		put(VanillaGuiLayers.OVERLAY_MESSAGE, "actionBar", (w, h) -> new double[] {w / 2.0, h - 68});
		put(VanillaGuiLayers.TITLE, "title", (w, h) -> new double[] {w / 2.0, h / 2.0});
	}

	private static void put(Identifier layer, String port, Pivot pivot) {
		PIECES.put(layer, new Piece(port, pivot));
	}

	private NeoForgeHud() {
	}

	static void register() {
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderGuiEvent.Pre.class, e -> {
			HudTiming.start();
			HudPart.beginFrame(e.getGuiGraphics());
			backDrawn = false;
		});
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderGuiEvent.Post.class, e -> {
			GuiGraphicsExtractor g = e.getGuiGraphics();
			PluginHud.drawAll(g, e.getPartialTick().getGameTimeDeltaPartialTick(false));
			HudPart.drawLayer(g, "front");
			HudTiming.end();
			CakeUIClient.afterHud(g);
		});
		// last, and only when no other mod cancelled the layer: a pushed transform must always be popped in Post
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderGuiLayerEvent.Pre.class, e -> {
			Identifier id = e.getName();
			GuiGraphicsExtractor g = e.getGuiGraphics();
			if (!backDrawn && id.equals(VanillaGuiLayers.CROSSHAIR)) {
				// the HUD's back layer: after the camera overlays, before crosshair and hotbar
				backDrawn = true;
				HudPart.drawLayer(g, "back");
			}
			if (e.isCanceled()) {
				return;
			}
			Piece p = PIECES.get(id);
			if (p == null || !HudPart.active()) {
				return;
			}
			if ((p.port.equals("actionBar") || p.port.equals("title")) && !HudPart.textShowing(p.port)) {
				return;
			}
			double[] at = p.pivot.at(g.guiWidth(), g.guiHeight());
			if (HudPart.pre(p.port, g, at[0], at[1])) {
				e.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderGuiLayerEvent.Post.class, e -> {
			Piece p = PIECES.get(e.getName());
			if (p != null) {
				HudPart.post(p.port, e.getGuiGraphics());
			}
		});
	}
}
