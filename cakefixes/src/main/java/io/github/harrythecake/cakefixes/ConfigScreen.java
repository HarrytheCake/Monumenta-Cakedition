package io.github.harrythecake.cakefixes;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * One on/off button per fix (tooltip explains it) and what it did this session, then the options that change what
 * you see (off by default). Done and Escape both save.
 */
final class ConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 260;
	private static final int ROW_HEIGHT = 44;
	private static final int TOP = 44;

	/** One row per fix: the switch name (also the lang key part), how to read/write it, and its session count. */
	private record Fix(String name, Predicate<Config> get, BiConsumer<Config, Boolean> set, LongSupplier count) {}

	private static final List<Fix> FIXES = List.of(
			new Fix("teamRemovalFix", c -> c.teamRemovalFix, (c, on) -> c.teamRemovalFix = on,
					TeamRemoval::skipped),
			new Fix("appleSkinBlendQueryFix", c -> c.appleSkinBlendQueryFix,
					(c, on) -> c.appleSkinBlendQueryFix = on, BlendQuery::answered));

	private final Screen parent;
	private final Config config = CakeFixes.config().copy();

	ConfigScreen(Screen parent) {
		super(Text.translatable("cakefixes.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (width - BUTTON_WIDTH) / 2;
		for (int i = 0; i < FIXES.size(); i++) {
			Fix fix = FIXES.get(i);
			addDrawableChild(CyclingButtonWidget.onOffBuilder(fix.get().test(config))
					.tooltip(on -> Tooltip.of(Text.translatable("cakefixes.fix." + fix.name() + ".tooltip")))
					.build(x, TOP + i * ROW_HEIGHT, BUTTON_WIDTH, 20,
							Text.translatable("cakefixes.fix." + fix.name()),
							(button, on) -> fix.set().accept(config, on)));
		}
		addDrawableChild(bannerDistanceButton(x, TOP + FIXES.size() * ROW_HEIGHT));
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions((width - 200) / 2, height - 28, 200, 20).build());
	}

	/** Off / 16 / 24 / 32 / 48 blocks; a hand-edited value outside the list is kept as an extra choice. */
	private CyclingButtonWidget<Integer> bannerDistanceButton(int x, int y) {
		List<Integer> choices = new ArrayList<>(BannerDistance.CHOICES);
		int current = Math.max(0, config.bannerDrawDistance);
		if (!choices.contains(current)) {
			choices.add(current);
			choices.sort(null);
		}
		return CyclingButtonWidget.<Integer>builder(d -> d == 0 ? ScreenTexts.OFF
						: Text.translatable("cakefixes.fix.bannerDrawDistance.value", d))
				.values(choices)
				.initially(current)
				.tooltip(d -> Tooltip.of(Text.translatable("cakefixes.fix.bannerDrawDistance.tooltip")))
				.build(x, y, BUTTON_WIDTH, 20, Text.translatable("cakefixes.fix.bannerDrawDistance"),
						(button, d) -> config.bannerDrawDistance = d);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
		for (int i = 0; i < FIXES.size(); i++) {
			Fix fix = FIXES.get(i);
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("cakefixes.fix." + fix.name() + ".count", fix.count().getAsLong()),
					width / 2, TOP + i * ROW_HEIGHT + 25, 0xA0A0A0);
		}
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("cakefixes.fix.bannerDrawDistance.count", BannerDistance.hidden()),
				width / 2, TOP + FIXES.size() * ROW_HEIGHT + 25, 0xA0A0A0);
	}

	@Override
	public void close() {
		Config.saveAndApply(config);
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
