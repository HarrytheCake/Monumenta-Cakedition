package io.github.harrythecake.cakeui;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Which vanilla HUD pieces should be on screen right now (for the HUD editor, ADR 0010 Q10): when one
 * should be but CakeUI's hook for it didn't run, another mod draws that piece itself (AsteorBar's bars,
 * say), so the editor can't move it and shows it greyed out.
 */
final class HudExpect {
	private HudExpect() {
	}

	static boolean expected(String port) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.gameMode == null || mc.options.hideGui || player.isSpectator()) {
			return false;
		}
		boolean survival = mc.gameMode.canHurtPlayer();
		return switch (port) {
			case "hotbar" -> true;
			case "health" -> survival;
			// riding: vanilla swaps hunger for the mount's health and the XP bar for the jump bar
			case "hunger", "experience" -> survival && player.getVehicle() == null;
			case "armor" -> survival && player.getArmorValue() > 0;
			case "air" -> survival && (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply());
			case "mountHealth" -> player.getVehicle() instanceof LivingEntity;
			default -> false;
		};
	}
}
