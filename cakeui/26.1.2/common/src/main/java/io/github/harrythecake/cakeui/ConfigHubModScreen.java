package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.configmap.ConfigDoc;
import io.github.harrythecake.cakeui.configmap.ConfigMap;
import io.github.harrythecake.cakeui.configmap.ConfigReloads;
import io.github.harrythecake.cakeui.configmap.HubModel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One mod's options on the config hub: a control per option (toggle, choice, slider, text, colour), a reset button
 * beside each, a gold mark on options the pack sets, and Reset this mod / Save / Cancel. Save writes the mod's file
 * (value by value) and asks the mod to reload it; without a reload hook it says the change applies after a restart.
 */
public final class ConfigHubModScreen extends Screen {
	private static final int ROW = 24;
	private final Screen parent;
	private final HubModel model;
	private final HubModel.Mod mod;
	/** Section titles (String) and options, in order. */
	private final List<Object> rows = new ArrayList<>();
	private int scroll;
	private Component message;
	private int messageColor = 0xA0A0A0;

	public ConfigHubModScreen(Screen parent, HubModel model, HubModel.Mod mod) {
		super(ConfigHubScreen.text(mod.map.title));
		this.parent = parent;
		this.model = model;
		this.mod = mod;
		for (ConfigMap.Section s : mod.map.sections) {
			if (!s.title().isEmpty()) {
				rows.add(s.title());
			}
			rows.addAll(s.options());
		}
	}

	private int visibleRows() {
		return Math.max(1, (height - 36 - 52) / ROW);
	}

	private int left() {
		return width / 2 - 160;
	}

	@Override
	protected void init() {
		scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
		int controlX = left() + 166;
		for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows()); i++) {
			if (!(rows.get(i) instanceof ConfigMap.Option o)) {
				continue;
			}
			int y = 36 + (i - scroll) * ROW;
			addRenderableWidget(control(o, controlX, y, 110));
			Object reset = model.resetValue(mod, o);
			Button r = addRenderableWidget(Button.builder(Component.translatable("cakeui.hub.reset"), b -> {
				model.reset(mod, o);
				rebuildWidgets();
			}).bounds(controlX + 114, y, 40, 20).build());
			r.active = reset != null && !ConfigDoc.same(reset, model.value(mod, o));
			if (reset != null) {
				r.setTooltip(Tooltip.create(Component.translatable(model.packDefault(mod, o) != null
						? "cakeui.hub.resetPack" : "cakeui.hub.resetDefault", display(o, reset))));
			}
		}
		int bw = 100, bx = width / 2 - 154;
		addRenderableWidget(Button.builder(Component.translatable("cakeui.hub.resetMod"), b -> {
			model.resetMod(mod);
			rebuildWidgets();
		}).bounds(bx, height - 27, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakeui.hub.save"), b -> save()).bounds(bx + bw + 4, height - 27, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(bx + 2 * (bw + 4), height - 27, bw, 20).build());
	}

	/** The control for one option, showing the current (or unsaved) value. */
	private net.minecraft.client.gui.components.AbstractWidget control(ConfigMap.Option o, int x, int y, int w) {
		Object v = model.value(mod, o);
		switch (o.kind) {
			case BOOL:
				return CycleButton.onOffBuilder(Boolean.TRUE.equals(v)).displayOnlyValue()
						.create(x, y, w, 20, ConfigHubScreen.text(o.label), (b, value) -> changed(o, value));
			case ENUM: {
				List<Object> values = new ArrayList<>();
				o.choices.forEach(c -> values.add(c.value()));
				Object initial = values.stream().filter(c -> ConfigDoc.same(c, v)).findFirst().orElse(values.get(0));
				return CycleButton.<Object>builder(c -> ConfigHubScreen.text(choiceLabel(o, c)), initial).withValues(values)
						.displayOnlyValue()
						.create(x, y, w, 20, ConfigHubScreen.text(o.label), (b, value) -> changed(o, value));
			}
			case INT:
			case FLOAT:
				if (o.min != null && o.max != null && o.max > o.min) {
					return new Slider(o, x, y, w, v instanceof Number n ? n.doubleValue() : o.min);
				}
				return textBox(o, x, y, w, v == null ? "" : display(o, v).getString());
			case COLOUR:
				return textBox(o, x, y, w, v == null ? "" : display(o, v).getString());
			default:
				return textBox(o, x, y, w, v == null ? "" : v.toString());
		}
	}

	private EditBox textBox(ConfigMap.Option o, int x, int y, int w, String text) {
		EditBox box = new EditBox(font, x + 1, y + 1, w - 2, 18, ConfigHubScreen.text(o.label));
		box.setMaxLength(256);
		box.setValue(text);
		box.setResponder(s -> {
			Object typed = parse(o, s);
			boolean ok = typed != null && model.set(mod, o, typed);
			box.setTextColor(ok || s.isEmpty() ? 0xE0E0E0 : 0xFF6060);
		});
		return box;
	}

	/** Text typed into a box as the option's type, or null. */
	private static Object parse(ConfigMap.Option o, String s) {
		String t = s.trim();
		if (t.isEmpty()) {
			return null;
		}
		return switch (o.kind) {
			case STRING -> s;
			case COLOUR -> o.read(t.startsWith("#") ? t : "#" + t);
			default -> o.read(t);
		};
	}

	private void changed(ConfigMap.Option o, Object typed) {
		model.set(mod, o, typed);
		message = null;
	}

	private static String choiceLabel(ConfigMap.Option o, Object value) {
		return o.choices.stream().filter(c -> ConfigDoc.same(c.value(), value)).map(ConfigMap.Choice::label).findFirst()
				.orElse(String.valueOf(value));
	}

	/** A typed value as the player reads it (a choice's label, a colour as #RRGGBB, numbers without a stray ".0"). */
	static Component display(ConfigMap.Option o, Object v) {
		return switch (o.kind) {
			case ENUM -> ConfigHubScreen.text(choiceLabel(o, v));
			case BOOL -> Boolean.TRUE.equals(v) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
			case COLOUR -> Component.literal(o.alpha ? String.format("#%08X", ((Number) v).longValue())
					: String.format("#%06X", ((Number) v).longValue() & 0xFFFFFF));
			case INT -> Component.literal(String.valueOf(((Number) v).longValue()));
			case FLOAT -> Component.literal(trim(((Number) v).doubleValue()));
			default -> Component.literal(String.valueOf(v));
		};
	}

	private static String trim(double d) {
		String s = String.format(java.util.Locale.ROOT, "%.3f", d);
		return s.replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	void save() {
		List<HubModel.Saved> results = model.save();
		if (results.isEmpty()) {
			message = Component.translatable("cakeui.hub.nothing");
			messageColor = 0xA0A0A0;
			return;
		}
		HubModel.Saved r = results.stream().filter(s -> s.mod() == mod).findFirst().orElse(results.get(0));
		if (!r.ok()) {
			message = Component.translatable("cakeui.hub.failed", r.problem());
			messageColor = 0xFF6060;
			return;
		}
		boolean reloaded = ConfigReloads.reload(mod.modId);
		message = reloaded ? Component.translatable("cakeui.hub.reloaded", ConfigHubScreen.text(mod.map.title))
				: Component.translatable("cakeui.hub.restart");
		messageColor = reloaded ? 0x80FF80 : 0xFFD060;
		rebuildWidgets();
	}

	/** The last save's result, for the self-test. */
	Component message() {
		return message;
	}

	HubModel.Mod mod() {
		return mod;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, rows.size() - visibleRows());
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		if (next != scroll) {
			scroll = next;
			rebuildWidgets();
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
		int x = left();
		for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows()); i++) {
			int y = 36 + (i - scroll) * ROW;
			if (rows.get(i) instanceof String section) {
				context.text(font, ConfigHubScreen.text(section).copy().withStyle(ChatFormatting.UNDERLINE), x, y + 6, 0xFFFFFFFF);
				continue;
			}
			ConfigMap.Option o = (ConfigMap.Option) rows.get(i);
			Object pack = model.packDefault(mod, o);
			Component label = ConfigHubScreen.text(o.label);
			if (pack != null) {
				label = label.copy().append(Component.literal(" ◆").withStyle(ChatFormatting.GOLD));
			}
			if (mod.dirty() && model.value(mod, o) != null && !ConfigDoc.same(model.value(mod, o), mod.file.read(o))) {
				label = Component.literal("* ").withStyle(ChatFormatting.YELLOW).append(label);
			}
			context.text(font, font.plainSubstrByWidth(label.getString(), 160).equals(label.getString()) ? label
					: Component.literal(font.plainSubstrByWidth(label.getString(), 154) + "…"), x, y + 6, 0xFFE0E0E0);
			if (o.kind == ConfigMap.Kind.COLOUR && model.value(mod, o) instanceof Number c) {
				int sx = x + 166 + 110 - 20;
				context.fill(sx, y + 2, sx + 16, y + 18, 0xFF000000);
				context.fill(sx + 1, y + 3, sx + 15, y + 17, 0xFF000000 | (int) (c.longValue() & 0xFFFFFF));
			}
			if (mouseX >= x && mouseX < x + 160 && mouseY >= y && mouseY < y + 20) {
				List<Component> tip = new ArrayList<>();
				tip.add(ConfigHubScreen.text(o.label));
				if (pack != null) {
					tip.add(Component.translatable("cakeui.hub.packDefault", display(o, pack)).withStyle(ChatFormatting.GOLD));
				}
				context.setTooltipForNextFrame(tip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
			}
		}
		scrollbar(context, x + 166 + 154 + 4, 36, visibleRows() * ROW - 4, scroll, visibleRows(), rows.size());
		if (message != null) {
			context.centeredText(font, message, width / 2, height - 42, 0xFF000000 | messageColor);
		} else if (mod.dirty()) {
			context.centeredText(font, Component.translatable("cakeui.hub.unsaved"), width / 2, height - 42, 0xFFFFD060);
		}
	}

	/** A thin scrollbar when there are more rows than fit (track and thumb). */
	static void scrollbar(GuiGraphicsExtractor context, int x, int y, int h, int scroll, int visible, int total) {
		if (total <= visible) {
			return;
		}
		context.fill(x, y, x + 3, y + h, 0x60000000);
		int thumb = Math.max(8, h * visible / total);
		int top = y + (h - thumb) * scroll / Math.max(1, total - visible);
		context.fill(x, top, x + 3, top + thumb, 0xFFC0C0C0);
	}

	@Override
	public void onClose() {
		model.discard();
		minecraft.setScreen(parent);
	}

	/** A slider between the option's min and max, snapped to its step (int: whole numbers). */
	private final class Slider extends AbstractSliderButton {
		private final ConfigMap.Option o;

		Slider(ConfigMap.Option o, int x, int y, int w, double current) {
			super(x, y, w, 20, Component.empty(), (Math.max(o.min, Math.min(o.max, current)) - o.min) / (o.max - o.min));
			this.o = o;
			updateMessage();
		}

		private Object typed() {
			double v = o.min + value * (o.max - o.min);
			double step = o.step != null && o.step > 0 ? o.step : o.kind == ConfigMap.Kind.INT ? 1 : 0;
			if (step > 0) {
				v = o.min + Math.round((v - o.min) / step) * step;
			}
			v = Math.max(o.min, Math.min(o.max, v));
			return o.kind == ConfigMap.Kind.INT ? (Object) Math.round(v) : (Object) (Math.round(v * 1e6) / 1e6);
		}

		@Override
		protected void updateMessage() {
			setMessage(display(o, typed()));
		}

		@Override
		protected void applyValue() {
			changed(o, typed());
		}
	}
}
