package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
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
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
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
	private static List<String> runs(Component text) {
		List<String> out = new ArrayList<>();
		text.visit((style, s) -> {
			HoverEvent h = style.getHoverEvent();
			Component card = h instanceof HoverEvent.ShowText show ? show.value() : null;
			out.add(s + " | " + (card == null ? "-" : card.getString().replace("\n", " / ")));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	@Test
	void serverChatShowsOriginalByDefault() {
		Component line = service.chatLine(Component.literal("Welcome to the Valley!"), false);
		assertEquals("Welcome to the Valley!", line.getString());
		assertTrue(runs(line).get(0).endsWith("欢迎来到山谷！"), runs(line).toString());
	}

	@Test
	void serverTextShowsTranslationWithOriginalOnHover() {
		service.config().chatDisplay = TranslatorConfig.Display.TRANSLATED;
		Component line = service.chatLine(Component.literal("Welcome to the Valley!"), false);
		assertEquals("欢迎来到山谷！", line.getString());
		String run = runs(line).get(0);
		// no language is loaded in tests, so the card shows the key itself
		assertTrue(run.contains("marker.original") && run.endsWith("Welcome to the Valley!"), run);

		service.config().chatDisplay = TranslatorConfig.Display.BOTH;
		assertEquals("欢迎来到山谷！\n  Welcome to the Valley!", service.chatLine(Component.literal("Welcome to the Valley!"), false).getString());

		service.config().chatDisplay = TranslatorConfig.Display.ORIGINAL;
		Component original = service.chatLine(Component.literal("Welcome to the Valley!"), false);
		assertEquals("Welcome to the Valley!", original.getString());
		assertTrue(runs(original).get(0).endsWith("欢迎来到山谷！"));
		service.config().display = TranslatorConfig.Display.TRANSLATED;
	}

	@Test
	void playerChatKeepsNameAndShowsOriginalByDefault() {
		Component line = Component.literal("[g] ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("Steve: ").withStyle(ChatFormatting.GOLD))
				.append(Component.literal("anyone selling a key?"));
		Component shown = service.chatLine(line, false);
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
		HoverEvent item = new HoverEvent.ShowText(Component.literal("Item card"));
		Component line = Component.literal("<Steve> ").append(Component.literal("anyone selling a key?").withStyle(s -> s.withHoverEvent(item)));
		Component shown = service.chatLine(line, true);
		assertTrue(runs(shown).get(1).endsWith("Item card"), runs(shown).toString());
	}

	@Test
	/** Names the server marks (hover, insertion, /msg click) never reach an engine, even when not in the tab list. */
	void styledNamesAreRemovedWithoutTheTabList() {
		// a player on another shard: not in the tab list, but the server marks the name
		java.util.UUID id = java.util.UUID.randomUUID();
		Component hover = Component.literal("qwertfox").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowEntity(
				new HoverEvent.EntityTooltipInfo(
						net.minecraft.world.entity.EntityType.PLAYER, id, Component.literal("qwertfox")))));
		Component insertion = Component.literal("[AB] zxcvbnm").setStyle(Style.EMPTY.withInsertion("zxcvbnm"));
		Component click = Component.literal("Mock_Pl4yer").setStyle(Style.EMPTY.withClickEvent(
				new net.minecraft.network.chat.ClickEvent.SuggestCommand("/msg Mock_Pl4yer ")));
		Component line = Component.empty().append(hover).append(Component.literal(" and ")).append(insertion).append(Component.literal(" met "))
				.append(click).append(Component.literal(" in Sierhaven"));
		io.github.harrythecake.caketext.core.template.TextTemplate t = io.github.harrythecake.caketext.core.template.TextTemplate.of(line, null);
		assertEquals(List.of("qwertfox", "zxcvbnm", "Mock_Pl4yer"), t.names());
		assertFalse(t.template().contains("qwertfox") || t.template().contains("zxcvbnm") || t.template().contains("Mock_Pl4yer"));
		assertTrue(t.template().contains("[AB] {n}"));
		assertEquals(line.getString(), t.render(t.template()).getString());
		// a non-player hover or a non-message click is no name
		Component npc = Component.literal("Aledar").setStyle(Style.EMPTY.withClickEvent(
				new net.minecraft.network.chat.ClickEvent.RunCommand("/join lfg")));
		assertEquals("Aledar", io.github.harrythecake.caketext.core.template.TextTemplate.of(npc, null).template());
	}

	@Test
	void untranslatedLinesAreReturnedAsIs() {
		Component line = Component.literal("Something nobody translated");
		assertSame(line, service.chatLine(line, false));
	}
}
