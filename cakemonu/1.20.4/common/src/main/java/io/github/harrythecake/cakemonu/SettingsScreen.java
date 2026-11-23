package io.github.harrythecake.cakemonu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * CakeMonu's settings (Mod Menu, {@code /cakemonu}): one row per ability with an "Own place" switch. A switched-on
 * icon leaves UMM's bar and sits next to it; "Edit HUD…" opens CakeUI's HUD editor to move and resize it.
 * Abilities of another class that still have a place are listed too, so they can go back into the bar.
 */
public final class SettingsScreen extends Screen {
	private static final int ROW = 24;
	private static final int TOP = 64;

	@Nullable
	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private int scroll;
	private List<FormattedCharSequence> help = List.of();

	private record Row(String id, Component label, boolean current) {
	}

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("cakemonu.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		clearWidgets();
		collectRows();
		help = font.split(message(), Math.min(width - 40, 360));
		int visible = visibleRows();
		scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
		boolean ready = CakeMonu.source().state() == AbilitySource.State.READY;
		int buttonX = width / 2 + 40;
		for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
			Row row = rows.get(scroll + i);
			CycleButton<Boolean> b = CycleButton.onOffBuilder(CakeMonu.layout().has(row.id))
					.create(buttonX, TOP + i * ROW, 110, 20, Component.translatable("cakemonu.settings.ownPlace"),
							(button, on) -> CakeMonu.setPlaced(row.id, on, width, height));
			b.active = ready;
			addRenderableWidget(b);
		}

		int bw = 120;
		int y = height - 28;
		Button edit = Button.builder(Component.translatable("cakemonu.settings.editHud"), btn -> CakeUIBridge.openEditor(this))
				.bounds(width / 2 - bw - bw / 2 - 6, y, bw, 20).build();
		edit.active = ready && CakeUIBridge.editor && !CakeMonu.layout().isEmpty() && Minecraft.getInstance().level != null;
		edit.setTooltip(Tooltip.create(Component.translatable(!CakeUIBridge.editor ? "cakemonu.settings.editHud.noCakeUI"
				: Minecraft.getInstance().level == null ? "cakemonu.settings.editHud.noWorld" : "cakemonu.settings.editHud.tip")));
		addRenderableWidget(edit);
		Button back = Button.builder(Component.translatable("cakemonu.settings.allBack"), btn -> {
			CakeMonu.clearAll();
			init();
		}).bounds(width / 2 - bw / 2, y, bw, 20).build();
		back.active = !CakeMonu.layout().isEmpty();
		addRenderableWidget(back);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
				.bounds(width / 2 + bw / 2 + 6, y, bw, 20).build());
	}

	private Component message() {
		AbilitySource s = CakeMonu.source();
		return switch (s.state()) {
			case NO_UMM -> Component.translatable("cakemonu.settings.noUmm").withStyle(ChatFormatting.GOLD);
			case UNSUPPORTED -> Component.translatable("cakemonu.settings.unsupported", s.version()).withStyle(ChatFormatting.GOLD);
			case READY -> s.current().isEmpty() ? Component.translatable("cakemonu.settings.none").withStyle(ChatFormatting.GRAY)
					: !s.barEnabled() ? Component.translatable("cakemonu.settings.barOff").withStyle(ChatFormatting.GOLD)
					: Component.translatable("cakemonu.settings.help");
		};
	}

	private void collectRows() {
		rows.clear();
		Map<String, Row> byId = new LinkedHashMap<>();
		for (AbilitySource.Ability a : CakeMonu.source().current()) {
			byId.put(a.id(), new Row(a.id(), Component.literal(a.name()).append(Component.literal("  " + a.className()).withStyle(ChatFormatting.DARK_GRAY)), true));
		}
		for (String id : CakeMonu.layout().all().keySet()) {
			byId.putIfAbsent(id, new Row(id, Component.literal(id).append(Component.literal("  ")).append(
					Component.translatable("cakemonu.settings.otherClass").withStyle(ChatFormatting.DARK_GRAY)), false));
		}
		rows.addAll(byId.values());
	}

	private int visibleRows() {
		return Math.max(1, (height - 40 - TOP) / ROW);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, rows.size() - visibleRows());
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		if (next != scroll) {
			scroll = next;
			init();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		int y = 28;
		for (FormattedCharSequence line : help) {
			g.drawCenteredString(font, line, width / 2, y, 0xFFFFFF);
			y += 10;
		}
		int visible = visibleRows();
		for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
			Row row = rows.get(scroll + i);
			int ry = TOP + i * ROW + 6;
			int x = Math.max(10, width / 2 - 170);
			int maxW = width / 2 + 40 - 8 - x; // up to the switch
			FormattedCharSequence label = font.width(row.label) <= maxW ? row.label.getVisualOrderText()
					: Language.getInstance().getVisualOrder(font.substrByWidth(row.label, maxW - font.width("…")));
			g.drawString(font, label, x, ry, row.current ? 0xFFFFFF : 0xA0A0A0);
		}
		if (rows.size() > visible) {
			g.drawCenteredString(font, Component.literal((scroll + 1) + "-" + Math.min(rows.size(), scroll + visible) + " / " + rows.size())
					.withStyle(ChatFormatting.DARK_GRAY), width / 2, height - 40, 0xFFFFFF);
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}
}
