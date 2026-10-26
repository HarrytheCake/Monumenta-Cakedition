package io.github.harrythecake.caketext.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import io.github.harrythecake.caketext.core.template.PlayerNames;
import io.github.harrythecake.caketext.core.template.TextTemplate;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

class TextTemplateTest {
	/** Colour and text of each run, adjacent runs of the same style joined (they look the same in game). */
	private static List<String> styledRuns(Text text) {
		List<String> out = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		text.visit((style, s) -> {
			int last = out.size() - 1;
			if (last >= 0 && styles.get(last).equals(style)) {
				out.set(last, out.get(last) + s);
			} else {
				styles.add(style);
				out.add(style.getColor() + ":" + s);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	@Test
	void numbersBecomePlaceholders() {
		TextTemplate t = TextTemplate.of(Text.literal("Total Damage: 1,250 (x2.5)"), null);
		assertEquals("Total Damage: {~} (x{~})", t.template());
		assertEquals(List.of("1,250", "2.5"), t.numbers());
		assertEquals("Total Damage: 1,250 (x2.5)", t.render(t.template()).getString());
	}

	@Test
	void singleStyleHasNoMarkers() {
		Text text = Text.literal("Hello ").formatted(Formatting.GOLD).append(Text.literal("world"));
		TextTemplate t = TextTemplate.of(text, null);
		assertEquals("Hello world", t.template());
		assertEquals(styledRuns(text), styledRuns(t.render(t.template())));
	}

	@Test
	void stylesBecomeMarkersAndRenderBack() {
		Text text = Text.literal("[Quest] ").formatted(Formatting.GREEN)
				.append(Text.literal("Talk to Aledar").formatted(Formatting.WHITE))
				.append(Text.literal(" now").formatted(Formatting.GREEN));
		TextTemplate t = TextTemplate.of(text, null);
		assertEquals("{c1}[Quest] {c2}Talk to Aledar{c1} now", t.template());
		assertEquals(styledRuns(text), styledRuns(t.render(t.template())));
		// a translation reorders the runs; each keeps its colour
		Text zh = t.render("{c1}[任务] {c1}现在{c2}去和 Aledar 说话");
		assertEquals(List.of("green:[任务] 现在", "white:去和 Aledar 说话"), styledRuns(zh));
	}

	@Test
	void playerNamesAreRemoved() {
		PlayerNames.update(List.of("Harry_BC", "Steve", "a b", "x"));
		Pattern names = PlayerNames.pattern();
		TextTemplate t = TextTemplate.of(Text.literal("Harry_BC gave Steve 3 coins. Steven waves."), names);
		assertEquals("{n} gave {n} {~} coins. Steven waves.", t.template());
		assertEquals(List.of("Harry_BC", "Steve"), t.names());
		assertEquals("Steve 收到 Harry_BC 的 3 枚硬币", t.render("{n2} 收到 {n1} 的 {~} 枚硬币").getString());
	}

	@Test
	void bracesAreEscaped() {
		TextTemplate t = TextTemplate.of(Text.literal("Use {c1} and {~} literally: 5"), null);
		// the 1 in a literal "{c1}" is still a number
		assertEquals("Use {{c{~}}} and {{~}} literally: {~}", t.template());
		assertEquals("Use {c1} and {~} literally: 5", t.render(t.template()).getString());
	}

	@Test
	void wordsCheck() {
		assertFalse(TextTemplate.of(Text.literal("12 / 40"), null).hasWords());
		assertFalse(TextTemplate.of(Text.literal(" "), null).hasWords());
		assertTrue(TextTemplate.of(Text.literal("Lv. 5"), null).hasWords());
	}
}
