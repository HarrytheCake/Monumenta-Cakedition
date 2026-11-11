package io.github.harrythecake.caketext.collector;

import static io.github.harrythecake.caketext.core.dev.SelfTestKit.check;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.on;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.press;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.run;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.screenshot;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.sleep;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.type;

import io.github.harrythecake.caketext.collector.ui.CollectorScreen;
import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.dev.SelfTestKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Dev self-test of the Collector ({@code -Dcaketext.selftest=1}; smoke.py with {@code --world}). After the first join
 * it types {@code /ctcollect status} (lines in chat and the log) and {@code /ctcollect} (settings screen), opens the sources
 * screen with its button and takes screenshots (run folder's screenshots/). Records nothing (singleplayer is off by
 * default). Ends with the log line "caketext collector self-test done".
 */
final class SelfTest {
	private static final String TEST = "caketext collector self-test";
	private static boolean started;

	private SelfTest() {
	}

	static void start(Minecraft client) {
		if (!SelfTestKit.ENABLED || started) {
			return;
		}
		started = true;
		SelfTestKit.start(TEST, () -> execute(client));
	}

	private static void execute(Minecraft client) {
		int passed = 0;
		try {
			sleep(4000);
			// the status lines show in the screenshot and in the log ("[CHAT] CakeText Collector …")
			type(client, "ctcollect status");
			sleep(1000);
			run(client, () -> client.setScreen(new ChatScreen("")));
			sleep(1500);
			screenshot(client, "caketext-selftest-collector-status.png");
			run(client, () -> client.setScreen(null));

			type(client, "ctcollect");
			sleep(1500);
			passed += check(TEST, "/ctcollect opens the settings", on(client, () -> client.screen instanceof CollectorScreen));
			screenshot(client, "caketext-selftest-collector.png");

			boolean sources = press(client, "caketext_collector.option.sources");
			sleep(1000);
			passed += check(TEST, "sources screen", sources && on(client, () -> client.screen != null
					&& client.screen.getClass().getSimpleName().equals("SourcesScreen")));
			screenshot(client, "caketext-selftest-sources.png");
			run(client, () -> client.setScreen(null));
		} catch (Throwable e) {
			CakeText.LOG.error("{} failed", TEST, e);
		}
		CakeText.LOG.info("{} done: {} of 2 checks passed", TEST, passed);
	}
}
