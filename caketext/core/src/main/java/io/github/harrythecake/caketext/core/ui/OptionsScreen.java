package io.github.harrythecake.caketext.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * A settings screen laid out as a two-column grid of widgets, with optional status lines under the title.
 * Subclasses add widgets in {@link #build()} with {@link #add} / {@link #addWide}.
 */
public abstract class OptionsScreen extends Screen {
	protected static final int WIDGET_WIDTH = 150;
	protected static final int ROW = 24;

	private final @Nullable Screen parent;
	private final List<Supplier<Text>> statusLines = new ArrayList<>();
	private int column;
	private int y;

	protected OptionsScreen(Text title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** Adds the widgets (and status lines) of this screen. */
	protected abstract void build();

	/** Called when the screen closes, e.g. to save. */
	protected void onClose() {
	}

	@Override
	protected void init() {
		statusLines.clear();
		column = 0;
		build();
		y = 32 + statusLines.size() * 11 + (statusLines.isEmpty() ? 0 : 6);
		List<ClickableWidget> widgets = new ArrayList<>(pending);
		List<Boolean> wides = new ArrayList<>(pendingWide);
		pending.clear();
		pendingWide.clear();
		for (int i = 0; i < widgets.size(); i++) {
			ClickableWidget w = widgets.get(i);
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
			addDrawableChild(w);
		}
		if (column == 1) {
			nextRow();
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
				.dimensions(width / 2 - 100, Math.max(y + 8, height - 28), 200, 20).build());
	}

	private final List<ClickableWidget> pending = new ArrayList<>();
	private final List<Boolean> pendingWide = new ArrayList<>();

	private void nextRow() {
		column = 0;
		y += ROW;
	}

	protected <T extends ClickableWidget> T add(T widget) {
		pending.add(widget);
		pendingWide.add(false);
		return widget;
	}

	protected <T extends ClickableWidget> T addWide(T widget) {
		pending.add(widget);
		pendingWide.add(true);
		return widget;
	}

	/** A line of text under the title, re-read every frame. */
	protected void status(Supplier<Text> line) {
		statusLines.add(line);
	}

	protected static ButtonWidget button(Text label, ButtonWidget.PressAction action) {
		return ButtonWidget.builder(label, action).size(WIDGET_WIDTH, 20).build();
	}

	/** An on/off button: "Label: ON". */
	protected static CyclingButtonWidget<Boolean> toggle(Text label, boolean value, Consumer<Boolean> set) {
		return CyclingButtonWidget.onOffBuilder(value).build(0, 0, WIDGET_WIDTH, 20, label, (b, v) -> set.accept(v));
	}

	/** A button cycling through {@code values}: "Label: value". */
	protected static <T> CyclingButtonWidget<T> cycle(Text label, List<T> values, T value, Function<T, Text> names,
			Consumer<T> set) {
		return CyclingButtonWidget.builder(names).values(values).initially(value)
				.build(0, 0, WIDGET_WIDTH, 20, label, (b, v) -> set.accept(v));
	}

	/** A text box (use with {@link #addWide}); {@code set} gets every change. */
	protected TextFieldWidget textField(Text name, String value, Consumer<String> set) {
		TextFieldWidget field = new TextFieldWidget(textRenderer, 0, 0, WIDGET_WIDTH, 20, name);
		field.setMaxLength(512);
		field.setText(value);
		field.setChangedListener(set);
		return field;
	}

	/** A line of text in the grid, e.g. the name of the text box below it. */
	protected TextWidget label(Text text) {
		return new TextWidget(WIDGET_WIDTH, 20, text, textRenderer).alignLeft();
	}

	/** Adds a tooltip and returns the widget. */
	protected static <T extends ClickableWidget> T tip(T widget, Text tooltip) {
		widget.setTooltip(Tooltip.of(tooltip));
		return widget;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
		int lineY = 30;
		for (Supplier<Text> line : statusLines) {
			context.drawCenteredTextWithShadow(textRenderer, line.get(), width / 2, lineY, 0xA0A0A0);
			lineY += 11;
		}
	}

	@Override
	public void close() {
		onClose();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
