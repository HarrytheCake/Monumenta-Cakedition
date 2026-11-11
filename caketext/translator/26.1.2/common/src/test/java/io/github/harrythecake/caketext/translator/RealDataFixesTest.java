package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.harrythecake.caketext.core.template.TextTemplate;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Problems found in the first in-game test on Monumenta (2026-10-27), with the real lines. */
class RealDataFixesTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void legacyColourCodesAreStylesNotNumbers() {
		TextTemplate city = TextTemplate.of(Component.literal("§6City Guide"), null);
		assertEquals("City Guide", city.template());
		List<String> colours = new ArrayList<>();
		city.render("城市指南").visit((style, s) -> {
			colours.add(style.getColor() + ":" + s);
			return Optional.empty();
		}, Style.EMPTY);
		assertEquals(List.of("gold:城市指南"), colours);

		// gold + bold is one style, so no marker
		assertEquals("Repair Station", TextTemplate.of(Component.literal("§6§lRepair Station"), null).template());
		assertEquals("{c1}Quest Guide {c2}(Capital)", TextTemplate.of(Component.literal("§6Quest Guide §7(Capital)"), null).template());
		assertEquals("§x stays §", TextTemplate.of(Component.literal("§x stays §"), null).original());
	}

	@Test
	void itemLinksAreKept() {
		Component link = Component.literal("[").withStyle(ChatFormatting.WHITE)
				.append(Component.literal("Astral Staff of Sloth").withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE)
						.withHoverEvent(new HoverEvent.ShowItem(
								new net.minecraft.world.item.ItemStackTemplate(Items.STICK)))))
				.append(Component.literal("] anyone?").withStyle(ChatFormatting.WHITE));
		TextTemplate t = TextTemplate.of(link, null);
		assertEquals("{c1}[{c2}Astral Staff of Sloth{c1}] anyone?", t.template());
		assertEquals(Set.of(2), t.itemStyles());
		List<String> asked = new ArrayList<>();
		String dst = MachineTranslator.translate(t.template(), t.itemStyles(), s -> {
			asked.add(s);
			return s.replace("anyone?", "有人要吗？");
		});
		assertEquals("{c1}[{c2}Astral Staff of Sloth{c1}] 有人要吗？", dst);
		assertTrue(asked.stream().noneMatch(s -> s.contains("Astral")), asked.toString());
	}

	@Test
	void llmAsidesAreDropped() {
		assertEquals("懒惰之杖", ServerEngine.dropAsides("Astral Staff of Sloth", "“懒惰之杖”（Astral Staff of Sloth）"));
		assertEquals("模糊的功绩评价体系", ServerEngine.dropAsides("Murky Band of Merit",
				"“模糊的功绩评价体系”（或译为“难以理解的功绩评定标准”）"));
		assertEquals("点击以查看能力触发条件。", ServerEngine.dropAsides("Click to view ability triggers.",
				"点击以查看能力触发条件（ability triggers）。"));
		assertEquals("贝拉维", ServerEngine.dropAsides("Bhairavi", "贝拉维（Bhairavi）"));
		// brackets the source has stay
		assertEquals("任务指南（首都）", ServerEngine.dropAsides("Quest Guide (Capital)", "任务指南（首都）"));
		assertEquals("“任务”", ServerEngine.dropAsides("\"Quest\"", "“任务”"));
	}
}
