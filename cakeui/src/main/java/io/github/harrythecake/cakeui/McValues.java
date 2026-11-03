package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.GameValues;
import io.github.harrythecake.cakeui.render.Renderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

/** The game values CakeUI itself provides. */
final class McValues {
	private static final long START = Util.getMeasuringTimeMs();
	private static String packVersion;

	private McValues() {
	}

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	private static void player(String name, Function<ClientPlayerEntity, Object> f, Object fallback) {
		GameValues.register(name, () -> {
			ClientPlayerEntity p = mc().player;
			return p == null ? fallback : f.apply(p);
		});
	}

	private static void reg(String name, Supplier<Object> s) {
		GameValues.register(name, s);
	}

	static void register() {
		reg("time", () -> (Util.getMeasuringTimeMs() - START) / 1000.0);
		reg("mouse.x", () -> {
			Renderer r = Ui.renderer();
			return r == null ? 0.0 : r.mouseNX();
		});
		reg("mouse.y", () -> {
			Renderer r = Ui.renderer();
			return r == null ? 0.0 : r.mouseNY();
		});
		reg("mouse.px", () -> mc().mouse.getX() / mc().getWindow().getScaleFactor());
		reg("mouse.py", () -> mc().mouse.getY() / mc().getWindow().getScaleFactor());
		reg("screen.width", () -> mc().getWindow().getScaledWidth());
		reg("screen.height", () -> mc().getWindow().getScaledHeight());
		reg("screen.scale", () -> mc().getWindow().getScaleFactor());
		reg("fps", () -> mc().getCurrentFps());
		reg("lang", () -> mc().getLanguageManager().getLanguage());
		reg("mc.version", () -> SharedConstants.getGameVersion().getName());
		reg("pack.version", McValues::packVersion);
		reg("devMode", Ui::devMode);
		reg("inWorld", () -> mc().world != null);
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
		reg("hud.hidden", () -> mc().options.hudHidden);
		reg("hud.actionbar", HudPart::actionBar);
		reg("hud.title", HudPart::title);
		reg("hud.subtitle", HudPart::subtitle);

		reg("server.name", () -> {
			ServerInfo s = mc().getCurrentServerEntry();
			return s == null ? "" : s.name;
		});
		reg("server.address", () -> {
			ServerInfo s = mc().getCurrentServerEntry();
			return s == null ? "" : s.address;
		});
		reg("world.time", () -> mc().world == null ? 0L : mc().world.getTimeOfDay() % 24000L);
		reg("world.day", () -> mc().world == null ? 0L : mc().world.getTimeOfDay() / 24000L);

		player("player.name", p -> p.getGameProfile().getName(), "");
		player("player.health", p -> p.getHealth(), 0.0);
		player("player.maxHealth", p -> p.getMaxHealth(), 20.0);
		player("player.absorption", p -> p.getAbsorptionAmount(), 0.0);
		player("player.food", p -> p.getHungerManager().getFoodLevel(), 20);
		player("player.saturation", p -> p.getHungerManager().getSaturationLevel(), 0.0);
		player("player.armor", p -> p.getArmor(), 0);
		player("player.toughness", p -> p.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS), 0.0);
		player("player.air", p -> p.getAir(), 300);
		player("player.maxAir", p -> p.getMaxAir(), 300);
		player("player.level", p -> p.experienceLevel, 0);
		player("player.xp", p -> p.experienceProgress, 0.0);
		player("player.x", p -> p.getX(), 0.0);
		player("player.y", p -> p.getY(), 0.0);
		player("player.z", p -> p.getZ(), 0.0);
		player("player.yaw", p -> p.getYaw(), 0.0);
		player("player.slot", p -> p.getInventory().selectedSlot, 0);
		player("player.sprinting", p -> p.isSprinting(), false);
		player("player.sneaking", p -> p.isSneaking(), false);
		player("player.onFire", p -> p.isOnFire(), false);
	}

	/** The Cakedition pack version written by Cakedition Updater, if any. */
	private static String packVersion() {
		if (packVersion == null) {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("cakeupdater").resolve("pack-version.txt");
			try {
				packVersion = Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8).trim() : "";
			} catch (Exception e) {
				packVersion = "";
			}
		}
		return packVersion;
	}
}
