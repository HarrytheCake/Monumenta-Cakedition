package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.mixin.LevelLoadingScreenAccessor;
import io.github.harrythecake.cakeui.mixin.ProgressScreenAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.util.Mth;

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
		if (screen instanceof ServerReconfigScreen) {
			return "reconfiguring";
		}
		if (screen instanceof LevelLoadingScreen l) {
			// 26.1: one screen for both; a singleplayer world being prepared reports progress
			return ((LevelLoadingScreenAccessor) l).cakeui$tracker().hasProgress() ? "world" : "terrain";
		}
		if (screen instanceof ProgressScreen) {
			return "progress";
		}
		if (screen instanceof GenericMessageScreen) {
			return "message";
		}
		return null;
	}

	static String kind() {
		String k = kind(Minecraft.getInstance().screen);
		return k == null ? "" : k;
	}

	/** 0…1 when the screen knows how far it is (world generation, progress screens), else -1. */
	static double progress() {
		Screen s = Minecraft.getInstance().screen;
		if (s instanceof LevelLoadingScreen l) {
			var tracker = ((LevelLoadingScreenAccessor) l).cakeui$tracker();
			return tracker.hasProgress() ? Mth.clamp(tracker.serverProgress(), 0, 1) : -1;
		}
		if (s instanceof ProgressScreen p) {
			return Mth.clamp(((ProgressScreenAccessor) p).cakeui$progress(), 0, 100) / 100.0;
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
