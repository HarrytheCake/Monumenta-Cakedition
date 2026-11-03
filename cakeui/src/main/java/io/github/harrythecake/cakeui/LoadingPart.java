package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.mixin.LevelLoadingScreenAccessor;
import io.github.harrythecake.cakeui.mixin.ProgressScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The loading screen part: one part for every vanilla "please wait" screen. Which one is open is
 * `loading.kind`; `loading.progress` and `loading.text` describe it.
 */
public final class LoadingPart {
	private static final List<String> lines = new ArrayList<>();
	private static String text = "";

	private LoadingPart() {
	}

	/** The kind of loading screen, or null when the screen isn't one. */
	@Nullable
	public static String kind(@Nullable Screen screen) {
		if (screen instanceof ConnectScreen) {
			return "connecting";
		}
		if (screen instanceof DownloadingTerrainScreen) {
			return "terrain";
		}
		if (screen instanceof ReconfiguringScreen) {
			return "reconfiguring";
		}
		if (screen instanceof LevelLoadingScreen) {
			return "world";
		}
		if (screen instanceof ProgressScreen) {
			return "progress";
		}
		if (screen instanceof MessageScreen) {
			return "message";
		}
		return null;
	}

	static String kind() {
		String k = kind(MinecraftClient.getInstance().currentScreen);
		return k == null ? "" : k;
	}

	/** 0…1 when the screen knows how far it is (world generation, progress screens), else -1. */
	static double progress() {
		Screen s = MinecraftClient.getInstance().currentScreen;
		if (s instanceof LevelLoadingScreen l) {
			return MathHelper.clamp(((LevelLoadingScreenAccessor) l).cakeui$progress().getProgressPercentage(), 0, 100) / 100.0;
		}
		if (s instanceof ProgressScreen p) {
			return MathHelper.clamp(((ProgressScreenAccessor) p).cakeui$progress(), 0, 100) / 100.0;
		}
		return -1;
	}

	/** Vanilla's text lines of the last frame, one per line. */
	static String text() {
		return text;
	}

	/** Start of a loading screen's frame. */
	public static void beginFrame(Screen screen, int mouseX, int mouseY) {
		text = String.join("\n", lines);
		lines.clear();
		ScreenPorts.LOADING.beginFrame(screen, mouseX, mouseY);
	}

	/** A vanilla text line as it is drawn (kept for {loading.text}). */
	public static void line(String line) {
		lines.add(line);
	}
}
