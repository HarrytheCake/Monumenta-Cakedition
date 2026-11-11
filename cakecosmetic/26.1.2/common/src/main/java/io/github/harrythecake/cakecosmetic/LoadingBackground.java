package io.github.harrythecake.cakecosmetic;

import java.util.List;
import java.util.Random;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The loading screens' background (CakeUI port loading_screen/background): a slideshow over the
 * pool, 5 s per picture with a 0.5 s fade (Loading Backgrounds' look). A new run of loading screens
 * (none drawn for 1.5 s) starts a new shuffled order. The Vanilla tile shows vanilla's background
 * for its turn (no fade to or from it). A picture still loading shows vanilla and its time waits.
 */
final class LoadingBackground {
	static final long STAY_MS = 5000;
	static final long FADE_MS = 500;
	private static final Random RANDOM = new Random();
	private static List<String> order;
	private static int index;
	private static long shownSince;
	private static long lastFrame;

	private LoadingBackground() {
	}

	/** The id shown now, or "vanilla", for layouts: {cakecosmetic.loading}. */
	static String current() {
		List<String> o = order;
		return o == null || o.isEmpty() ? Choices.VANILLA : o.get(index % o.size());
	}

	static void invalidate() {
		order = null;
	}

	/** CakeUI's filler. */
	static boolean fill(GuiGraphicsExtractor ctx, Screen screen, float alpha) {
		long now = Util.getMillis();
		if (order == null || now - lastFrame > 1500) {
			order = Picker.order(Picker.pool(Unlocks.choices().loading, Unlocks.pickableBackgrounds()), RANDOM);
			index = 0;
			shownSince = now;
		}
		lastFrame = now;
		int n = order.size();
		if (n == 0) {
			return false;
		}
		String curId = order.get(index);
		boolean curVanilla = Choices.VANILLA.equals(curId);
		BackgroundTextures.Tex cur = texture(curId);
		if (!curVanilla && cur == null) {
			shownSince = now;
			return false;
		}
		if (n > 1) {
			String nextId = order.get((index + 1) % n);
			boolean nextVanilla = Choices.VANILLA.equals(nextId);
			BackgroundTextures.Tex next = texture(nextId); // also starts loading it ahead
			if (now - shownSince >= STAY_MS && (nextVanilla || next != null)) {
				if (curVanilla || nextVanilla) {
					advance(now);
				} else {
					float f = Math.min(1, (now - shownSince - STAY_MS) / (float) FADE_MS);
					BackgroundTextures.drawCover(ctx, cur, 0, 0, screen.width, screen.height, alpha);
					BackgroundTextures.drawCover(ctx, next, 0, 0, screen.width, screen.height, alpha * f);
					if (f >= 1) {
						advance(now);
					}
					return true;
				}
				curId = order.get(index);
				cur = texture(curId);
			}
		}
		if (cur == null) {
			return false;
		}
		BackgroundTextures.drawCover(ctx, cur, 0, 0, screen.width, screen.height, alpha);
		return true;
	}

	private static void advance(long now) {
		index = (index + 1) % order.size();
		shownSince = now;
	}

	private static BackgroundTextures.Tex texture(String id) {
		if (Choices.VANILLA.equals(id)) {
			return null;
		}
		Backgrounds.Background b = Backgrounds.get(id);
		return b == null ? null : BackgroundTextures.full(b);
	}
}
