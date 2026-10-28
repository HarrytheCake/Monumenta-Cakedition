package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Holding Alt in tooltips (both ways round), and the parts API other mods (CakeWiki) use. */
class TooltipAltAndPartsTest {
	private static final String LANG = "zz_alt";
	private static TranslationService service;

	@BeforeAll
	static void setUp() throws Exception {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		Path dir = CakeText.translations(LANG);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("strings.json"), """
				{ "format": "caketext/strings", "version": 1, "lang": "zh_cn", "entries": [
				  { "src": "When in Main Hand:", "dst": "主手持有时：" },
				  { "src": "Dropped by {n} in {n}.", "dst": "{n2}中的{n1}掉落。" },
				  { "src": "+{~}% Attack Speed", "dst": "+{~}% 攻击速度" }
				] }
				""", StandardCharsets.UTF_8);
		TranslatorConfig config = new TranslatorConfig();
		config.language = LANG;
		config.markers = TranslatorConfig.Markers.OFF;
		config.machine = false; // nothing here may reach an engine
		service = new TranslationService(config);
		service.reload();
	}

	@AfterAll
	static void cleanUp() throws Exception {
		try (Stream<Path> walk = Files.walk(CakeText.translations(LANG))) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	private static List<String> tooltip(boolean alt) {
		List<Text> texts = new ArrayList<>(List.of(Text.literal("Epic Sword"), Text.literal("When in Main Hand:")));
		service.translateTooltip(new ItemStack(Items.DIAMOND_SWORD), texts, alt);
		return texts.stream().map(Text::getString).toList();
	}

	@Test
	void altShowsTheOriginal() {
		service.config().tooltipAlt = TranslatorConfig.TooltipAlt.ORIGINAL_ON_ALT;
		assertEquals(List.of("Epic Sword", "主手持有时：", "caketext_translator.tooltip.alt_original"), tooltip(false));
		assertEquals(List.of("Epic Sword", "When in Main Hand:"), tooltip(true));
	}

	@Test
	void altShowsTheTranslation() {
		service.config().tooltipAlt = TranslatorConfig.TooltipAlt.TRANSLATION_ON_ALT;
		assertEquals(List.of("Epic Sword", "When in Main Hand:", "caketext_translator.tooltip.alt_translation"), tooltip(false));
		assertEquals(List.of("Epic Sword", "主手持有时："), tooltip(true));
	}

	@Test
	void altOff() {
		service.config().tooltipAlt = TranslatorConfig.TooltipAlt.OFF;
		assertEquals(List.of("Epic Sword", "主手持有时："), tooltip(false));
		assertEquals(List.of("Epic Sword", "主手持有时："), tooltip(true));
	}

	@Test
	void keptPartsMoveWithTheTranslation() {
		Object[] out = service.translateParts("wiki", new String[] {"Dropped by ", "Kaul", " in ", "Sealed Remorse", "."},
				new boolean[] {false, true, false, true, false}, true);
		assertArrayEquals(new Object[] {3, "中的", 1, "掉落。"}, out);
	}

	@Test
	void numbersMatchTheStringFiles() {
		Object[] out = service.translateParts("wiki", new String[] {"+12.5% Attack Speed"}, new boolean[] {false}, true);
		assertArrayEquals(new Object[] {"+", "12.5", "% 攻击速度"}, out);
	}

	@Test
	void missesAndNothingToTranslate() {
		assertNull(service.translateParts("wiki", new String[] {"Never seen before"}, new boolean[] {false}, true));
		assertNull(service.translateParts("wiki", new String[] {"Kaul", " 12 "}, new boolean[] {true, false}, true));
		service.config().surfaces.put("wiki", false);
		try {
			assertNull(service.translateParts("wiki", new String[] {"When in Main Hand:"}, new boolean[] {false}, true));
		} finally {
			service.config().surfaces.remove("wiki");
		}
		assertArrayEquals(new Object[] {"主手持有时："},
				service.translateParts("wiki", new String[] {"When in Main Hand:"}, new boolean[] {false}, true));
	}
}
