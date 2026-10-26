package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.github.harrythecake.caketext.core.template.TextTemplate;
import io.github.harrythecake.caketext.translator.mt.OpusMt;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

class MachineTranslatorTest {
	/** A fake model that records its inputs and answers from a table. */
	private static final class Fake implements Function<String, String> {
		final List<String> inputs = new ArrayList<>();
		final java.util.Map<String, String> answers;

		Fake(String... pairs) {
			answers = new java.util.HashMap<>();
			for (int i = 0; i < pairs.length; i += 2) {
				answers.put(pairs[i], pairs[i + 1]);
			}
		}

		@Override
		public String apply(String s) {
			inputs.add(s);
			return answers.getOrDefault(s, "?" + s);
		}
	}

	@Test
	void placeholdersBecomeStandInsAndComeBack() {
		Fake model = new Fake("Takes 7301 seconds to wind up", "启动需要7301秒");
		assertEquals("启动需要{~1}秒", MachineTranslator.translate("Takes {~} seconds to wind up", model));
		assertEquals(List.of("Takes 7301 seconds to wind up"), model.inputs);
	}

	@Test
	void reorderedPlaceholdersKeepTheirIndex() {
		Fake model = new Fake("7301 gave 7302 coins", "7302 收到了 7301 的硬币");
		// numbers and names are counted separately: the first number is {~1}, the first name {n1}
		assertEquals("{~1} 收到了 {n1} 的硬币", MachineTranslator.translate("{n} gave {~} coins", model));
	}

	@Test
	void styleRunsAreTranslatedSeparately() {
		Fake model = new Fake("[Quest]", "[任务]", "Talk to the guard", "和守卫谈谈");
		assertEquals("{c1}[任务] {c2}和守卫谈谈", MachineTranslator.translate("{c1}[Quest] {c2}Talk to the guard", model));
	}

	@Test
	void lostStandInFallsBackToChunks() {
		// the model drops the number: translate the text around the placeholder instead
		Fake model = new Fake("Level 7301 reached", "达到等级", "Level", "等级", "reached", "已达到");
		assertEquals("等级 {~1} 已达到", MachineTranslator.translate("Level {~} reached", model));
	}

	@Test
	void iconsAndNumbersOnlyArentSent() {
		Fake model = new Fake("Kill the boss", "击杀首领");
		assertEquals(" 击杀首领", MachineTranslator.translate(" Kill the boss", model));
		assertEquals(List.of("Kill the boss"), model.inputs);
		Fake none = new Fake();
		assertEquals("{c1}{~1}/{~2}", MachineTranslator.translate("{c1}{~}/{~}", none));
		assertTrue(none.inputs.isEmpty());
	}

	@Test
	void resultRendersWithOriginalValuesAndColours() {
		Text line = Text.literal("Speak with ").formatted(Formatting.GRAY)
				.append(Text.literal("Aledar").formatted(Formatting.GOLD))
				.append(Text.literal(" in 3 minutes").formatted(Formatting.GRAY));
		TextTemplate t = TextTemplate.of(line, null);
		assertEquals("{c1}Speak with {c2}Aledar{c1} in {~} minutes", t.template());
		Fake model = new Fake("Speak with", "去找", "Aledar", "Aledar", "in 7301 minutes", "在7301分钟内");
		String dst = MachineTranslator.translate(t.template(), model);
		assertEquals("{c1}去找 {c2}Aledar{c1} 在{~1}分钟内", dst);
		assertEquals("去找 Aledar 在3分钟内", t.render(dst).getString());
	}

	@Test
	void chinesePunctuation() {
		assertEquals("你好，世界！", ChineseText.tidy("你好,世界!"));
		assertEquals("右键打开菜单。", ChineseText.tidy("右键打开菜单 。"));
		assertEquals("如果我是你，我不会进去", ChineseText.tidy("如果我是你 我不会进去"));
		assertEquals("Damage: 1,250", ChineseText.tidy("Damage: 1,250"));
		assertEquals("总伤害：7301", ChineseText.tidy("总伤害:7301"));
	}

	/** With the real converted model (same system properties as the reference test). */
	@Test
	void realModelOnGameTemplates() throws Exception {
		String work = System.getProperty("caketext.workDir", "");
		Path dir = work.isEmpty() ? null : Path.of(work, "opus-mt-en-zh-int8");
		assumeTrue(dir != null && Files.exists(dir.resolve(OpusMt.MODEL_FILE)), "converted model not available");
		OpusMt mt = OpusMt.load(dir);
		Function<String, String> model = s -> mt.translate(s, ">>cmn_Hans<<", OpusMt.Quality.BEST);
		String[] templates = {
				"Takes {~} seconds to wind up when activated.",
				"{c1}[Quest] {c2}Speak with the guard at the gate of Sierhaven.",
				"{n} has completed the dungeon in {~} minutes!",
				"{c1}Right-Click {c2}to open up the recipe menu.",
				"You need {~} more Experience to reach level {~}.",
				" Click to track",
				"Total Damage: {~}"};
		for (String template : templates) {
			long t = System.nanoTime();
			String dst = MachineTranslator.translate(template, model);
			System.out.printf("%5d ms  %-62s -> %s%n", (System.nanoTime() - t) / 1_000_000, template, dst);
			assertNotNull(dst);
			int placeholders = TextTemplate.parse(template).stream().filter(x -> !(x instanceof TextTemplate.Token.Literal)).toList().size();
			int back = TextTemplate.parse(dst).stream().filter(x -> !(x instanceof TextTemplate.Token.Literal)).toList().size();
			assertEquals(placeholders, back, "placeholders kept in " + dst);
		}
	}
}
