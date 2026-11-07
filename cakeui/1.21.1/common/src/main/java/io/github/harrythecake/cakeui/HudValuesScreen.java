package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.GameValues;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Every game value an element can use (CakeUI's and mods'), with its current reading, live: type to
 * filter, scroll, click a name to put it into the field of the properties panel the player came from.
 */
public final class HudValuesScreen extends Screen {
	private static final int ROW_H = 11;

	private final Screen parent;
	private final Consumer<String> pick;
	private EditBox filter;
	private List<String> shown = List.of();
	private int scroll;

	HudValuesScreen(Screen parent, Consumer<String> pick) {
		super(Component.translatable("cakeui.values.title"));
		this.parent = parent;
		this.pick = pick;
	}

	private int top() {
		return 44;
	}

	private int rows() {
		return Math.max(1, (height - top() - 34) / ROW_H);
	}

	private int left() {
		return width / 2 - 150;
	}

	@Override
	protected void init() {
		filter = new EditBox(font, left(), 22, 300, 16, Component.translatable("cakeui.values.filter"));
		filter.setHint(Component.translatable("cakeui.values.filter"));
		filter.setResponder(s -> refresh());
		addRenderableWidget(filter);
		setInitialFocus(filter);
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
		refresh();
	}

	private void refresh() {
		String q = filter.getValue().trim().toLowerCase(Locale.ROOT);
		shown = GameValues.names().stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(q)).sorted().toList();
		scroll = Math.max(0, Math.min(scroll, shown.size() - rows()));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(width / 2 - 160, 2, width / 2 + 160, height - 2, 0xD0101010);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawString(font, title, left(), 8, 0xFFFFD000);
		int y = top();
		for (int i = scroll; i < shown.size() && i < scroll + rows(); i++) {
			String name = shown.get(i);
			boolean hover = mouseX >= left() && mouseX < left() + 300 && mouseY >= y && mouseY < y + ROW_H;
			if (hover) {
				g.fill(left() - 2, y - 1, left() + 302, y + ROW_H - 1, 0xFF404040);
			}
			g.drawString(font, name, left(), y + 1, 0xFFFFE080);
			String value = GameValues.preview(name);
			value = font.plainSubstrByWidth(value, 120);
			g.drawString(font, value, left() + 300 - font.width(value), y + 1, 0xFFB0B0B0);
			y += ROW_H;
		}
		if (shown.size() > rows()) {
			g.drawString(font, (scroll + 1) + "–" + Math.min(shown.size(), scroll + rows()) + " / " + shown.size(), left(), height - 40, 0xFF808080);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		int i = scroll + (int) Math.floor((my - top()) / ROW_H);
		if (mx >= left() && mx < left() + 300 && my >= top() && i >= scroll && i < shown.size() && i < scroll + rows()) {
			pick.accept(shown.get(i));
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(shown.size() - rows(), scroll - (int) Math.signum(scrollY) * 3));
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
