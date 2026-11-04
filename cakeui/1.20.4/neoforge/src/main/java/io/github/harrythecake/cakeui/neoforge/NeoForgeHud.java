package io.github.harrythecake.cakeui.neoforge;

import io.github.harrythecake.cakeui.CakeUIClient;
import io.github.harrythecake.cakeui.HudPart;
import io.github.harrythecake.cakeui.HudTiming;
import io.github.harrythecake.cakeui.PluginHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent;
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

/**
 * The HUD ports on NeoForge 1.20.4: NeoForge draws the HUD as named overlays (ExtendedGui), each
 * with a Pre/Post event, so every vanilla piece is one overlay: Pre hides it (cancel) or moves,
 * scales and fades it, Post undoes that and draws the port's elements.
 */
final class NeoForgeHud {
	/** Overlay to port, with the point a layout's scale grows the piece from. */
	private record Piece(String port, Pivot pivot) {
	}

	private interface Pivot {
		double[] at(int w, int h);
	}

	private static final Map<ResourceLocation, Piece> PIECES = new HashMap<>();
	private static boolean backDrawn;

	static {
		put(VanillaGuiOverlay.VIGNETTE, "vignette", (w, h) -> new double[] {w / 2.0, h / 2.0});
		put(VanillaGuiOverlay.SPYGLASS, "spyglass", (w, h) -> new double[] {w / 2.0, h / 2.0});
		put(VanillaGuiOverlay.HELMET, "pumpkin", (w, h) -> new double[] {w / 2.0, h / 2.0});
		put(VanillaGuiOverlay.HOTBAR, "hotbar", (w, h) -> new double[] {w / 2.0, h - 11});
		put(VanillaGuiOverlay.CROSSHAIR, "crosshair", (w, h) -> new double[] {w / 2.0, h / 2.0});
		put(VanillaGuiOverlay.BOSS_EVENT_PROGRESS, "bossBars", (w, h) -> new double[] {w / 2.0, 12});
		put(VanillaGuiOverlay.PLAYER_HEALTH, "health", (w, h) -> new double[] {w / 2.0 - 91, h - 39});
		put(VanillaGuiOverlay.ARMOR_LEVEL, "armor", (w, h) -> new double[] {w / 2.0 - 91, h - 49});
		put(VanillaGuiOverlay.FOOD_LEVEL, "hunger", (w, h) -> new double[] {w / 2.0 + 91, h - 39});
		put(VanillaGuiOverlay.AIR_LEVEL, "air", (w, h) -> new double[] {w / 2.0 + 91, h - 49});
		put(VanillaGuiOverlay.MOUNT_HEALTH, "mountHealth", (w, h) -> new double[] {w / 2.0 + 91, h - 39});
		put(VanillaGuiOverlay.EXPERIENCE_BAR, "experience", (w, h) -> new double[] {w / 2.0, h - 29});
		put(VanillaGuiOverlay.ITEM_NAME, "heldItemName", (w, h) -> new double[] {w / 2.0, h - 59});
		put(VanillaGuiOverlay.POTION_ICONS, "statusEffects", (w, h) -> new double[] {w, 0});
		put(VanillaGuiOverlay.SCOREBOARD, "scoreboard", (w, h) -> new double[] {w, h / 2.0});
		put(VanillaGuiOverlay.PLAYER_LIST, "tabList", (w, h) -> new double[] {w / 2.0, 0});
		put(VanillaGuiOverlay.RECORD_OVERLAY, "actionBar", (w, h) -> new double[] {w / 2.0, h - 68});
		put(VanillaGuiOverlay.TITLE_TEXT, "title", (w, h) -> new double[] {w / 2.0, h / 2.0});
	}

	private static void put(VanillaGuiOverlay overlay, String port, Pivot pivot) {
		PIECES.put(overlay.id(), new Piece(port, pivot));
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
			GuiGraphics g = e.getGuiGraphics();
			PluginHud.drawAll(g, e.getPartialTick());
			HudPart.drawLayer(g, "front");
			HudTiming.end();
			CakeUIClient.afterHud(g);
		});
		// last, and only when no other mod cancelled the overlay: a pushed transform must always be popped in Post
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderGuiOverlayEvent.Pre.class, e -> {
			ResourceLocation id = e.getOverlay().id();
			GuiGraphics g = e.getGuiGraphics();
			if (!backDrawn && id.equals(VanillaGuiOverlay.HOTBAR.id())) {
				// the HUD's back layer: after the full-screen overlays, before the hotbar
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
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderGuiOverlayEvent.Post.class, e -> {
			Piece p = PIECES.get(e.getOverlay().id());
			if (p != null) {
				HudPart.post(p.port, e.getGuiGraphics());
			}
		});
	}
}
