package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.GameValues;
import io.github.harrythecake.cakeui.render.Renderer;
import io.github.harrythecake.cakeui.platform.Services;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

/** The game values CakeUI itself provides. */
final class McValues {
	private static final long START = Util.getMillis();
	private static String packVersion;

	private McValues() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private static void player(String name, Function<LocalPlayer, Object> f, Object fallback) {
		GameValues.register(name, () -> {
			LocalPlayer p = mc().player;
			return p == null ? fallback : f.apply(p);
		});
	}

	private static void reg(String name, Supplier<Object> s) {
		GameValues.register(name, s);
	}

	static void register() {
		reg("time", () -> (Util.getMillis() - START) / 1000.0);
		reg("mouse.x", () -> {
			Renderer r = Ui.renderer();
			return r == null ? 0.0 : r.mouseNX();
		});
		reg("mouse.y", () -> {
			Renderer r = Ui.renderer();
			return r == null ? 0.0 : r.mouseNY();
		});
		reg("mouse.px", () -> mc().mouseHandler.xpos() / mc().getWindow().getGuiScale());
		reg("mouse.py", () -> mc().mouseHandler.ypos() / mc().getWindow().getGuiScale());
		reg("screen.width", () -> mc().getWindow().getGuiScaledWidth());
		reg("screen.height", () -> mc().getWindow().getGuiScaledHeight());
		reg("screen.scale", () -> mc().getWindow().getGuiScale());
		reg("fps", () -> mc().getFps());
		reg("lang", () -> mc().getLanguageManager().getSelected());
		reg("mc.version", () -> SharedConstants.getCurrentVersion().getName());
		reg("pack.version", McValues::packVersion);
		reg("devMode", Ui::devMode);
		reg("inWorld", () -> mc().level != null);
		reg("menu.fade", TitleMenu::fade);
		reg("menu.splash", TitleMenu::splash);
		reg("menu.version", TitleMenu::version);
		reg("window.title", WindowPart::vanillaTitle);
		reg("loading.kind", LoadingPart::kind);
		reg("loading.progress", LoadingPart::progress);
		reg("loading.text", LoadingPart::text);
		reg("launch.progress", LaunchPart::progress);
		reg("launch.fade", LaunchPart::fade);
		reg("launch.reloading", LaunchPart::reloading);
		reg("hud.hidden", () -> mc().options.hideGui);
		reg("hud.actionbar", HudPart::actionBar);
		reg("hud.title", HudPart::title);
		reg("hud.subtitle", HudPart::subtitle);

		reg("server.name", () -> {
			ServerData s = mc().getCurrentServer();
			return s == null ? "" : s.name;
		});
		reg("server.address", () -> {
			ServerData s = mc().getCurrentServer();
			return s == null ? "" : s.ip;
		});
		reg("world.time", () -> mc().level == null ? 0L : mc().level.getDayTime() % 24000L);
		reg("world.day", () -> mc().level == null ? 0L : mc().level.getDayTime() / 24000L);

		player("player.name", p -> p.getGameProfile().getName(), "");
		player("player.health", p -> p.getHealth(), 0.0);
		player("player.maxHealth", p -> p.getMaxHealth(), 20.0);
		player("player.absorption", p -> p.getAbsorptionAmount(), 0.0);
		player("player.food", p -> p.getFoodData().getFoodLevel(), 20);
		player("player.saturation", p -> p.getFoodData().getSaturationLevel(), 0.0);
		player("player.armor", p -> p.getArmorValue(), 0);
		player("player.toughness", p -> p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0.0);
		player("player.air", p -> p.getAirSupply(), 300);
		player("player.maxAir", p -> p.getMaxAirSupply(), 300);
		player("player.level", p -> p.experienceLevel, 0);
		player("player.xp", p -> p.experienceProgress, 0.0);
		player("player.x", p -> p.getX(), 0.0);
		player("player.y", p -> p.getY(), 0.0);
		player("player.z", p -> p.getZ(), 0.0);
		player("player.yaw", p -> p.getYRot(), 0.0);
		player("player.slot", p -> p.getInventory().selected, 0);
		player("player.sprinting", p -> p.isSprinting(), false);
		player("player.sneaking", p -> p.isShiftKeyDown(), false);
		player("player.onFire", p -> p.isOnFire(), false);
	}

	/** The Cakedition pack version written by Cakedition Updater, if any. */
	private static String packVersion() {
		if (packVersion == null) {
			Path f = Services.PLATFORM.configDir().resolve("cakeupdater").resolve("pack-version.txt");
			try {
				packVersion = Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8).trim() : "";
			} catch (Exception e) {
				packVersion = "";
			}
		}
		return packVersion;
	}
}
