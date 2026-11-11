package io.github.harrythecake.caketext.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A settings screen laid out as a two-column grid of widgets, with optional status lines under the title.
 * Subclasses add widgets in {@link #build()} with {@link #add} / {@link #addWide}.
 */
public abstract class OptionsScreen extends Screen {
	protected static final int WIDGET_WIDTH = 150;
	protected static final int ROW = 24;

	private final @Nullable Screen parent;
	private final List<Supplier<Component>> statusLines = new ArrayList<>();
	private int column;
	private int y;

	protected OptionsScreen(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** Adds the widgets (and status lines) of this screen. */
	protected abstract void build();

	/** Called when the screen closes, e.g. to save. */
	protected void onClosing() {
	}

	@Override
	protected void init() {
		statusLines.clear();
		column = 0;
		build();
		y = 32 + statusLines.size() * 11 + (statusLines.isEmpty() ? 0 : 6);
		List<AbstractWidget> widgets = new ArrayList<>(pending);
		List<Boolean> wides = new ArrayList<>(pendingWide);
		pending.clear();
		pendingWide.clear();
		for (int i = 0; i < widgets.size(); i++) {
			AbstractWidget w = widgets.get(i);
			if (wides.get(i)) {
				if (column == 1) {
					nextRow();
				}
				w.setPosition(width / 2 - WIDGET_WIDTH - 5, y);
				w.setWidth(WIDGET_WIDTH * 2 + 10);
				nextRow();
			} else {
				w.setPosition(column == 0 ? width / 2 - WIDGET_WIDTH - 5 : width / 2 + 5, y);
				w.setWidth(WIDGET_WIDTH);
				if (column == 1) {
					nextRow();
				} else {
					column = 1;
				}
			}
			addRenderableWidget(w);
		}
		if (column == 1) {
			nextRow();
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, Math.max(y + 8, height - 28), 200, 20).build());
	}

	private final List<AbstractWidget> pending = new ArrayList<>();
	private final List<Boolean> pendingWide = new ArrayList<>();

	private void nextRow() {
		column = 0;
		y += ROW;
	}

	protected <T extends AbstractWidget> T add(T widget) {
		pending.add(widget);
		pendingWide.add(false);
		return widget;
	}

	protected <T extends AbstractWidget> T addWide(T widget) {
		pending.add(widget);
		pendingWide.add(true);
		return widget;
	}

	/** A line of text under the title, re-read every frame. */
	protected void status(Supplier<Component> line) {
		statusLines.add(line);
	}

	protected static Button button(Component label, Button.OnPress action) {
		return Button.builder(label, action).size(WIDGET_WIDTH, 20).build();
	}

	/** An on/off button: "Label: ON". */
	protected static CycleButton<Boolean> toggle(Component label, boolean value, Consumer<Boolean> set) {
		return CycleButton.onOffBuilder(value).create(0, 0, WIDGET_WIDTH, 20, label, (b, v) -> set.accept(v));
	}

	/** A button cycling through {@code values}: "Label: value". */
	protected static <T> CycleButton<T> cycle(Component label, List<T> values, T value, Function<T, Component> names,
			Consumer<T> set) {
		return CycleButton.builder(names).withValues(values).withInitialValue(value)
				.create(0, 0, WIDGET_WIDTH, 20, label, (b, v) -> set.accept(v));
	}

	/** A text box (use with {@link #addWide}); {@code set} gets every change. */
	protected EditBox textField(Component name, String value, Consumer<String> set) {
		EditBox field = new EditBox(font, 0, 0, WIDGET_WIDTH, 20, name);
		field.setMaxLength(512);
		field.setValue(value);
		field.setResponder(set);
		return field;
	}

	/** A line of text in the grid, e.g. the name of the text box below it. */
	protected StringWidget label(Component text) {
		return new StringWidget(WIDGET_WIDTH, 20, text, font).alignLeft();
	}

	/** Adds a tooltip and returns the widget. */
	protected static <T extends AbstractWidget> T tip(T widget, Component tooltip) {
		widget.setTooltip(Tooltip.create(tooltip));
		return widget;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		int lineY = 30;
		for (Supplier<Component> line : statusLines) {
			context.drawCenteredString(font, line.get(), width / 2, lineY, 0xA0A0A0);
			lineY += 11;
		}
	}

	@Override
	public void onClose() {
		onClosing();
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}
}
