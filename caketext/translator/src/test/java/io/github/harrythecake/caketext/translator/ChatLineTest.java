package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.strings.StringEntry;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import io.github.harrythecake.caketext.core.template.PlayerNames;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** How chat lines come out for each display mode, with a small dictionary and no model. */
class ChatLineTest {
	private static final String LANG = "zz_chat";
	private static TranslationService service;

	@BeforeAll
	static void setUp() throws Exception {
		// HoverEvent needs the registries
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		StringsFile file = new StringsFile();
		file.entries.add(new StringEntry("Welcome to the Valley!", "欢迎来到山谷！"));
		file.entries.add(new StringEntry("anyone selling a key?", "有人卖钥匙吗？"));
		file.write(CakeText.translations(LANG).resolve("test.json"), false);
		TranslatorConfig config = new TranslatorConfig();
		config.language = LANG;
		config.markers = TranslatorConfig.Markers.OFF;
		service = new TranslationService(config);
		service.reload();
		PlayerNames.update(List.of("Steve"));
	}

	@AfterAll
	static void cleanUp() throws Exception {
		try (Stream<Path> walk = Files.walk(CakeText.translations(LANG))) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	/** Text of each run and the text of its hover card (or "-"). */
	private static List<String> runs(Text text) {
		List<String> out = new ArrayList<>();
		text.visit((style, s) -> {
			HoverEvent h = style.getHoverEvent();
			Text card = h == null ? null : h.getValue(HoverEvent.Action.SHOW_TEXT);
			out.add(s + " | " + (card == null ? "-" : card.getString().replace("\n", " / ")));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	@Test
	void serverChatShowsOriginalByDefault() {
		Text line = service.chatLine(Text.literal("Welcome to the Valley!"), false);
		assertEquals("Welcome to the Valley!", line.getString());
		assertTrue(runs(line).get(0).endsWith("欢迎来到山谷！"), runs(line).toString());
	}

	@Test
	void serverTextShowsTranslationWithOriginalOnHover() {
		service.config().chatDisplay = TranslatorConfig.Display.TRANSLATED;
		Text line = service.chatLine(Text.literal("Welcome to the Valley!"), false);
		assertEquals("欢迎来到山谷！", line.getString());
		String run = runs(line).get(0);
		// no language is loaded in tests, so the card shows the key itself
		assertTrue(run.contains("marker.original") && run.endsWith("Welcome to the Valley!"), run);

		service.config().chatDisplay = TranslatorConfig.Display.BOTH;
		assertEquals("欢迎来到山谷！\n  Welcome to the Valley!", service.chatLine(Text.literal("Welcome to the Valley!"), false).getString());

		service.config().chatDisplay = TranslatorConfig.Display.ORIGINAL;
		Text original = service.chatLine(Text.literal("Welcome to the Valley!"), false);
		assertEquals("Welcome to the Valley!", original.getString());
		assertTrue(runs(original).get(0).endsWith("欢迎来到山谷！"));
		service.config().display = TranslatorConfig.Display.TRANSLATED;
	}

	@Test
	void playerChatKeepsNameAndShowsOriginalByDefault() {
		Text line = Text.literal("[g] ").formatted(Formatting.GRAY)
				.append(Text.literal("Steve: ").formatted(Formatting.GOLD))
				.append(Text.literal("anyone selling a key?"));
		Text shown = service.chatLine(line, false);
		assertEquals("[g] Steve: anyone selling a key?", shown.getString());
		List<String> runs = runs(shown);
		// tags and name: untouched, no hover; message: original with the translation on hover
		assertEquals("[g]  | -", runs.get(0));
		assertEquals("Steve:  | -", runs.get(1));
		assertTrue(runs.get(2).startsWith("anyone selling a key? |") && runs.get(2).endsWith("有人卖钥匙吗？"), runs.get(2));

		service.config().playerChatDisplay = TranslatorConfig.Display.TRANSLATED;
		assertEquals("[g] Steve: 有人卖钥匙吗？", service.chatLine(line, false).getString());
		service.config().playerChatDisplay = TranslatorConfig.Display.ORIGINAL;

		service.config().playerChat = false;
		assertSame(line, service.chatLine(line, false));
		service.config().playerChat = true;
	}

	@Test
	void itemLinksKeepTheirOwnHover() {
		HoverEvent item = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Item card"));
		Text line = Text.literal("<Steve> ").append(Text.literal("anyone selling a key?").styled(s -> s.withHoverEvent(item)));
		Text shown = service.chatLine(line, true);
		assertTrue(runs(shown).get(1).endsWith("Item card"), runs(shown).toString());
	}

	@Test
	void untranslatedLinesAreReturnedAsIs() {
		Text line = Text.literal("Something nobody translated");
		assertSame(line, service.chatLine(line, false));
	}
}
