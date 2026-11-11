package io.github.harrythecake.cakecosmetic;

import java.lang.ref.WeakReference;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The main menu's background (CakeUI port main_menu/background): one pick each time a main menu
 * opens. The next pick is chosen and loaded ahead (after a resource reload and on disconnect), so
 * the menu rarely waits for a picture.
 */
final class MenuBackground {
	private static final Random RANDOM = new Random();
	private static WeakReference<Screen> screen = new WeakReference<>(null);
	private static String current = Choices.VANILLA;
	private static String next;

	private MenuBackground() {
	}

	/** The id shown now ("vanilla" for the panorama), for layouts: {cakecosmetic.menu}. */
	static String current() {
		return current;
	}

	/** CakeUI's filler. */
	static boolean fill(GuiGraphicsExtractor ctx, Screen s, float alpha) {
		if (screen.get() != s) {
			screen = new WeakReference<>(s);
			current = next != null ? next : pick();
			next = null;
		}
		if (Choices.VANILLA.equals(current)) {
			return false;
		}
		Backgrounds.Background b = Backgrounds.get(current);
		if (b == null) {
			return false;
		}
		BackgroundTextures.Tex t = BackgroundTextures.full(b);
		if (t == null) {
			return false;
		}
		BackgroundTextures.drawCover(ctx, t, 0, 0, s.width, s.height, alpha);
		// a soft dark strip under the version and copyright lines, so they stay readable on bright pictures
		int a = Math.round(alpha * 0x70);
		ctx.fillGradient(0, s.height - 24, s.width, s.height, 0, a << 24);
		return true;
	}

	private static String pick() {
		return Picker.pickOne(Picker.pool(Unlocks.choices().mainMenu, Unlocks.pickableBackgrounds()), current, RANDOM);
	}

	/** Chooses the next main menu's background and starts loading it. */
	static void prepare() {
		next = pick();
		Backgrounds.Background b = Backgrounds.get(next);
		if (b != null) {
			BackgroundTextures.full(b);
		}
	}

	/** The choices or unlocks changed: the open main menu picks again. */
	static void invalidate() {
		screen = new WeakReference<>(null);
		next = null;
	}
}
