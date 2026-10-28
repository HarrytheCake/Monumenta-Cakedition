package io.github.harrythecake.cakewiki;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Development only: with {@code -Dcakewiki.devShot=Page|item:Name|Page@300|Page!open} (set through
 * {@code ./gradlew runClient -PdevShot=...}) the dev client opens each page from the title screen, waits for it
 * and its pictures, saves {@code screenshots/cakewiki-<n>.png} and finally quits. Does nothing in a normal game.
 * {@code item:} looks a name up like the item key does; {@code @y} scrolls; {@code !open} opens every collapsible.
 */
final class DevShots {
	private final Deque<String> steps;
	private WikiScreen screen;
	private int wait, shot;

	private DevShots(String spec) {
		steps = new ArrayDeque<>(Arrays.asList(spec.split("\\|")));
	}

	static void startIfAsked() {
		String spec = System.getProperty("cakewiki.devShot", "");
		if (spec.isBlank()) return;
		DevShots d = new DevShots(spec);
		ClientTickEvents.END_CLIENT_TICK.register(d::tick);
	}

	private void tick(MinecraftClient mc) {
		if (screen == null) {
			if (!(mc.currentScreen instanceof TitleScreen) || mc.getOverlay() != null) return;
			next(mc);
			return;
		}
		if (screen.loading()) {
			if (++wait < 400) return; // 20 s
		}
		if (wait < 1000) { // loaded: give pictures up to 8 s
			wait = 1000;
			return;
		}
		if (++wait < 1160 && (wait < 1040 || CakeWiki.reader().images.pending() > 0)) return;
		CakeWiki.LOG.info("devShot {} at capture: {}", shot + 1, screen.devState());
		ScreenshotRecorder.saveScreenshot(mc.runDirectory, "cakewiki-" + (++shot) + ".png", mc.getFramebuffer(),
				msg -> CakeWiki.LOG.info("devShot: {}", msg.getString()));
		next(mc);
	}

	private void next(MinecraftClient mc) {
		wait = 0;
		String step = steps.poll();
		if (step == null) {
			CakeWiki.LOG.info("devShot: done");
			mc.scheduleStop();
			screen = null;
			steps.add("__stopped__"); // keep the tick handler idle
			return;
		}
		if (step.equals("__stopped__")) return;
		int scroll = 0;
		boolean openAll = step.endsWith("!open");
		if (openAll) step = step.substring(0, step.length() - 5);
		int at = step.lastIndexOf('@');
		if (at > 0) {
			scroll = Integer.parseInt(step.substring(at + 1));
			step = step.substring(0, at);
		}
		CakeWiki.LOG.info("devShot {}: {}", shot + 1, step);
		screen = new WikiScreen(CakeWiki.reader(), () -> mc.setScreen(null));
		mc.setScreen(screen);
		if (step.startsWith("item:")) screen.lookup(step.substring(5));
		else screen.reopen(new Reader.Visit(step, scroll));
		if (openAll) screen.devOpenAll();
	}
}
