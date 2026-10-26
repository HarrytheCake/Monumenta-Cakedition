package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

/** A Crowdin .po dropped into translations/<lang>/ is used as is; item names and ignored items stay untouched. */
class CrowdinAndTooltipTest {
	private static final String LANG = "zz_crowdin";
	private static TranslationService service;

	@BeforeAll
	static void setUp() throws Exception {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		Path dir = CakeText.translations(LANG);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("tooltip.po"), """
				msgid ""
				msgstr ""
				"Content-Type: text/plain; charset=UTF-8\\n"
				"Language: zh_CN\\n"

				#. seen 4 times
				msgid "Epic Sword"
				msgstr "史诗之剑"

				msgid "When in Main Hand:"
				msgstr "主手持有时："

				msgctxt "surface=tooltip"
				msgid "Back"
				msgstr "返回"
				""", StandardCharsets.UTF_8);
		TranslatorConfig config = new TranslatorConfig();
		config.language = LANG;
		config.markers = TranslatorConfig.Markers.OFF;
		service = new TranslationService(config);
		service.reload();
	}

	@AfterAll
	static void cleanUp() throws Exception {
		try (Stream<Path> walk = Files.walk(CakeText.translations(LANG))) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	private static List<String> tooltip(ItemStack stack, String... lines) {
		List<Text> texts = new ArrayList<>();
		for (String l : lines) {
			texts.add(Text.literal(l));
		}
		service.translateTooltip(stack, texts);
		return texts.stream().map(Text::getString).toList();
	}

	@Test
	void poFromCrowdinIsRead() {
		assertEquals(3, service.dictionary().humanCount());
		assertEquals(1, service.dictionary().files());
	}

	@Test
	void itemNameStaysUnlessTurnedOn() {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		assertEquals(List.of("Epic Sword", "主手持有时："), tooltip(sword, "Epic Sword", "When in Main Hand:"));
		service.config().translateItemNames = true;
		assertEquals(List.of("史诗之剑", "主手持有时："), tooltip(sword, "Epic Sword", "When in Main Hand:"));
		service.config().translateItemNames = false;
	}

	@Test
	void ignoredItemsAreLeftAlone() {
		for (ItemStack stack : List.of(new ItemStack(Items.WRITTEN_BOOK), new ItemStack(Items.OAK_SIGN),
				new ItemStack(Items.SHULKER_BOX), new ItemStack(Items.LIME_SHULKER_BOX))) {
			assertEquals(List.of("Epic Sword", "When in Main Hand:"), tooltip(stack, "Epic Sword", "When in Main Hand:"),
					stack.getItem().toString());
		}
	}
}
