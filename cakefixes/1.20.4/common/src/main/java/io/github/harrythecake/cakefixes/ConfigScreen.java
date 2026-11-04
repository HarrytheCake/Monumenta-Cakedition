package io.github.harrythecake.cakefixes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * One on/off button per fix (tooltip explains it) and what it did this session, then the options that change what
 * you see (off by default). Done and Escape both save.
 */
public final class ConfigScreen extends Screen {
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

	public ConfigScreen(Screen parent) {
		super(Component.translatable("cakefixes.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (width - BUTTON_WIDTH) / 2;
		for (int i = 0; i < FIXES.size(); i++) {
			Fix fix = FIXES.get(i);
			addRenderableWidget(CycleButton.onOffBuilder(fix.get().test(config))
					.withTooltip(on -> Tooltip.create(Component.translatable("cakefixes.fix." + fix.name() + ".tooltip")))
					.create(x, TOP + i * ROW_HEIGHT, BUTTON_WIDTH, 20,
							Component.translatable("cakefixes.fix." + fix.name()),
							(button, on) -> fix.set().accept(config, on)));
		}
		addRenderableWidget(bannerDistanceButton(x, TOP + FIXES.size() * ROW_HEIGHT));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds((width - 200) / 2, height - 28, 200, 20).build());
	}

	/** Off / 16 / 24 / 32 / 48 blocks; a hand-edited value outside the list is kept as an extra choice. */
	private CycleButton<Integer> bannerDistanceButton(int x, int y) {
		List<Integer> choices = new ArrayList<>(BannerDistance.CHOICES);
		int current = Math.max(0, config.bannerDrawDistance);
		if (!choices.contains(current)) {
			choices.add(current);
			choices.sort(null);
		}
		return CycleButton.<Integer>builder(d -> d == 0 ? CommonComponents.OPTION_OFF
						: Component.translatable("cakefixes.fix.bannerDrawDistance.value", d))
				.withValues(choices)
				.withInitialValue(current)
				.withTooltip(d -> Tooltip.create(Component.translatable("cakefixes.fix.bannerDrawDistance.tooltip")))
				.create(x, y, BUTTON_WIDTH, 20, Component.translatable("cakefixes.fix.bannerDrawDistance"),
						(button, d) -> config.bannerDrawDistance = d);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
		for (int i = 0; i < FIXES.size(); i++) {
			Fix fix = FIXES.get(i);
			context.drawCenteredString(font,
					Component.translatable("cakefixes.fix." + fix.name() + ".count", fix.count().getAsLong()),
					width / 2, TOP + i * ROW_HEIGHT + 25, 0xA0A0A0);
		}
		context.drawCenteredString(font,
				Component.translatable("cakefixes.fix.bannerDrawDistance.count", BannerDistance.hidden()),
				width / 2, TOP + FIXES.size() * ROW_HEIGHT + 25, 0xA0A0A0);
	}

	@Override
	public void onClose() {
		Config.saveAndApply(config);
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}
}
