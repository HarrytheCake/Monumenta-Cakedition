package io.github.harrythecake.caketext.translator;

import static io.github.harrythecake.caketext.core.dev.SelfTestKit.check;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.on;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.press;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.run;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.screenshot;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.sleep;
import static io.github.harrythecake.caketext.core.dev.SelfTestKit.type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.dev.SelfTestKit;
import io.github.harrythecake.caketext.translator.api.TranslatorApi;
import io.github.harrythecake.caketext.translator.mixin.ChatHudAccessor;
import io.github.harrythecake.caketext.translator.ui.TranslatorScreen;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * Dev self-test of the Translator ({@code -Dcaketext.selftest=1}; smoke.py with {@code --world}). After the first
 * join it writes a made-up strings file, reloads, and checks a dictionary hit through the service, the API for other
 * mods and a chat line (hover card); then types {@code /cttranslate} and opens the engine and surfaces screens with
 * their buttons, taking screenshots (run folder's screenshots/). Removes its strings file at the end. Ends with the log
 * line "caketext translator self-test done".
 */
final class SelfTest {
	private static final String TEST = "caketext translator self-test";
	/** Made up for the test; not a server's text. */
	private static final String SRC = "CakeText self-test line";
	private static final String DST = "CakeText 自检行";
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

	private static void runTest(Minecraft client, int[] passed) throws IOException {
		sleep(4000);
		TranslationService service = TranslatorMod.service();
		Path file = CakeText.translations(service.config().language).resolve("selftest.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{\"" + SRC + "\": \"" + DST + "\"}", StandardCharsets.UTF_8);
		try {
			run(client, service::reload);

			TranslationService.Result r = on(client, () -> service.translate(Component.literal(SRC), Surfaces.CHAT, null));
			passed[0] += check(TEST, "dictionary hit", r != null && r.text().getString().contains(DST));

			Object[] api = on(client, () -> TranslatorApi.translate(TranslatorConfig.WIKI, new String[] {SRC}, new boolean[] {false}));
			passed[0] += check(TEST, "API v" + TranslatorApi.version() + " translate",
					api != null && api.length > 0 && String.valueOf(api[0]).contains(DST));

			run(client, () -> client.gui.getChat().addMessage(Component.literal(SRC)));
			boolean hover = on(client, () -> {
				var messages = ((ChatHudAccessor) client.gui.getChat()).caketext$messages();
				GuiMessage last = messages.isEmpty() ? null : messages.get(0);
				return last != null && (last.content().getString().contains(DST) || hoverHas(last.content(), DST));
			});
			passed[0] += check(TEST, "chat line carries the translation", hover);
			run(client, () -> client.setScreen(new ChatScreen("")));
			sleep(1500);
			screenshot(client, "caketext-selftest-chat.png");

			type(client, "cttranslate");
			sleep(1500);
			boolean opened = on(client, () -> client.screen instanceof TranslatorScreen);
			passed[0] += check(TEST, "/cttranslate opens the settings", opened);
			screenshot(client, "caketext-selftest-translator.png");

			boolean engine = press(client, "caketext_translator.option.engine");
			sleep(1000);
			passed[0] += check(TEST, "engine screen", engine && on(client, () -> client.screen != null
					&& client.screen.getClass().getSimpleName().equals("EngineScreen")));
			screenshot(client, "caketext-selftest-engine.png");
			run(client, () -> client.screen.onClose());
			sleep(500);

			boolean surfaces = press(client, "caketext_translator.option.surfaces");
			sleep(1000);
			passed[0] += check(TEST, "surfaces screen", surfaces && on(client, () -> client.screen != null
					&& client.screen.getClass().getSimpleName().equals("SurfacesScreen")));
			screenshot(client, "caketext-selftest-surfaces.png");
			run(client, () -> client.setScreen(null));
		} finally {
			Files.deleteIfExists(file);
			run(client, service::reload);
		}
	}

	private static void execute(Minecraft client) {
		int[] passed = {0};
		try {
			runTest(client, passed);
		} catch (Throwable e) {
			CakeText.LOG.error("{} failed", TEST, e);
		}
		CakeText.LOG.info("{} done: {} of 6 checks passed", TEST, passed[0]);
	}

	private static boolean hoverHas(Component line, String text) {
		return line.visit((Style style, String s) -> {
			HoverEvent hover = style.getHoverEvent();
			Component shown = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_TEXT);
			return shown != null && shown.getString().contains(text) ? Optional.of(Boolean.TRUE) : Optional.empty();
		}, Style.EMPTY).orElse(false);
	}
}
