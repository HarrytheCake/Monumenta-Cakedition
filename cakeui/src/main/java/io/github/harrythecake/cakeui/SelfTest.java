package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.mixin.MouseInvoker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;

/**
 * Development only, off unless the JVM runs with -Dcakeui.selftest=1 (Loom's runClient sets it on
 * request): on the title screen it takes screenshots with the mouse in a few places, then quits.
 */
final class SelfTest {
	/** {seconds, mouse x, mouse y as fractions of the window, or -1 to leave it} → screenshot name. */
	private static final double[][] STEPS = {
		{4.0, 0.5, 0.5},
		{6.0, 0.62, 0.32},
		{8.0, 0.1, 0.9},
	};
	private static int ticks;
	private static int step;

	private SelfTest() {
	}

	static void init() {
		if (System.getProperty("cakeui.selftest", "").isEmpty()) {
			return;
		}
		CakeUI.LOGGER.info("CakeUI self-test: will take {} screenshots of the title screen and quit", STEPS.length);
		ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!(client.currentScreen instanceof TitleScreen)) {
			return;
		}
		ticks++;
		double seconds = ticks / 20.0;
		if (step < STEPS.length) {
			double[] s = STEPS[step];
			// move the mouse a second before the shot so hover transitions settle
			if (seconds >= s[0] - 1.0 && s[1] >= 0) {
				var w = client.getWindow();
				((MouseInvoker) client.mouse).cakeui$onCursorPos(w.getHandle(), s[1] * w.getWidth(), s[2] * w.getHeight());
			}
			if (seconds >= s[0]) {
				String name = "cakeui-selftest-" + step + ".png";
				ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(),
						text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
				step++;
			}
		} else if (seconds >= STEPS[STEPS.length - 1][0] + 2) {
			CakeUI.LOGGER.info("CakeUI self-test: done, {} problem(s): {}", Ui.problems().size(), Ui.problems());
			client.scheduleStop();
		}
	}
}
