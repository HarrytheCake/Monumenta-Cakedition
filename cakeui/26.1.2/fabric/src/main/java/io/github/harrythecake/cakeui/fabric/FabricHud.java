package io.github.harrythecake.cakeui.fabric;

import io.github.harrythecake.cakeui.HudPart;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * The HUD ports on Fabric 26.1: each vanilla HUD element is wrapped through Fabric's
 * HudElementRegistry, like NeoForge's layer events: before it, HudPart.pre hides it or moves and
 * scales it; after it, HudPart.post undoes that and draws the port's elements.
 */
final class FabricHud {
	private interface Pivot {
		double[] at(int w, int h);
	}

	private FabricHud() {
	}

	static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("cakeui", "back"),
				(g, delta) -> HudPart.drawLayer(g, "back"));
		wrap(VanillaHudElements.CROSSHAIR, "crosshair", (w, h) -> new double[] {w / 2.0, h / 2.0});
		wrap(VanillaHudElements.HOTBAR, "hotbar", (w, h) -> new double[] {w / 2.0, h - 11});
		wrap(VanillaHudElements.HEALTH_BAR, "health", (w, h) -> new double[] {w / 2.0 - 91, h - 39});
		wrap(VanillaHudElements.ARMOR_BAR, "armor", (w, h) -> new double[] {w / 2.0 - 91, h - 49});
		wrap(VanillaHudElements.FOOD_BAR, "hunger", (w, h) -> new double[] {w / 2.0 + 91, h - 39});
		wrap(VanillaHudElements.AIR_BAR, "air", (w, h) -> new double[] {w / 2.0 + 91, h - 49});
		wrap(VanillaHudElements.MOUNT_HEALTH, "mountHealth", (w, h) -> new double[] {w / 2.0 + 91, h - 39});
		wrap(VanillaHudElements.INFO_BAR, "experience", (w, h) -> new double[] {w / 2.0, h - 29});
		wrap(VanillaHudElements.EXPERIENCE_LEVEL, "experience", (w, h) -> new double[] {w / 2.0, h - 29});
		wrap(VanillaHudElements.HELD_ITEM_TOOLTIP, "heldItemName", (w, h) -> new double[] {w / 2.0, h - 59});
		wrap(VanillaHudElements.MOB_EFFECTS, "statusEffects", (w, h) -> new double[] {w, 0});
		wrap(VanillaHudElements.BOSS_BAR, "bossBars", (w, h) -> new double[] {w / 2.0, 12});
		wrap(VanillaHudElements.SCOREBOARD, "scoreboard", (w, h) -> new double[] {w, h / 2.0});
		wrap(VanillaHudElements.PLAYER_LIST, "tabList", (w, h) -> new double[] {w / 2.0, 0});
	}

	private static void wrap(Identifier element, String port, Pivot pivot) {
		HudElementRegistry.replaceElement(element, vanilla -> (g, delta) -> {
			if (!HudPart.active()) {
				vanilla.extractRenderState(g, delta);
				return;
			}
			double[] at = pivot.at(g.guiWidth(), g.guiHeight());
			if (HudPart.pre(port, g, at[0], at[1])) {
				return;
			}
			vanilla.extractRenderState(g, delta);
			HudPart.post(port, g);
		});
	}
}
