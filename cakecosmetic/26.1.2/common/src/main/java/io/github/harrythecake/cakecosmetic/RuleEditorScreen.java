package io.github.harrythecake.cakecosmetic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Dev mode: a background's unlock rule. Lists every advancement the game knows on this connection
 * (title, id, earned), searchable; a click adds or removes it. Save writes rules.json5 at once.
 */
final class RuleEditorScreen extends Screen {
	private static final int ROW = 12;
	private static final int LIST_Y = 46;
	private final Screen parent;
	private final Backgrounds.Background background;
	/** The rule being edited: id → title, in the order added. */
	private final Map<String, String> needs = new LinkedHashMap<>();
	private boolean secret;
	private boolean showUntitled;
	private Map<String, Unlocks.AdvancementInfo> known = Map.of();
	private final List<Unlocks.AdvancementInfo> rows = new ArrayList<>();
	private EditBox search;
	private double scroll;
	private int listBottom;
	private Component error;

	RuleEditorScreen(Screen parent, Backgrounds.Background background) {
		super(Component.translatable("cakecosmetic.dev.rule.title", background.title()));
		this.parent = parent;
		this.background = background;
		Rules.Rule rule = Unlocks.rules().get(background.id());
		if (rule != null) {
			for (Rules.Need n : rule.needs()) {
				needs.put(n.id(), n.title());
			}
			secret = rule.secret();
		}
	}

	@Override
	protected void init() {
		known = Unlocks.knownAdvancements();
		int w = Math.min(320, width - 40);
		String text = search == null ? "" : search.getValue();
		search = addRenderableWidget(new EditBox(font, width / 2 - w / 2, 22, w, 18, Component.translatable("cakecosmetic.dev.rule.search")));
		search.setHint(Component.translatable("cakecosmetic.dev.rule.search"));
		search.setValue(text);
		search.setResponder(s -> filter());
		setInitialFocus(search);
		int y = height - 26;
		int bw = 76;
		int x = width / 2 - (bw * 5 + 4 * 4) / 2;
		addRenderableWidget(Button.builder(secretLabel(), b -> {
			secret = !secret;
			b.setMessage(secretLabel());
		}).bounds(x, y, bw, 20).build());
		addRenderableWidget(Button.builder(untitledLabel(), b -> {
			showUntitled = !showUntitled;
			b.setMessage(untitledLabel());
			filter();
		}).bounds(x + (bw + 4), y, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakecosmetic.dev.rule.clear"), b -> {
			needs.clear();
			filter();
		})
				.bounds(x + 2 * (bw + 4), y, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakecosmetic.dev.rule.save"), b -> save())
				.bounds(x + 3 * (bw + 4), y, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(x + 4 * (bw + 4), y, bw, 20).build());
		listBottom = y - 18;
		filter();
	}

	private Component secretLabel() {
		return Component.translatable("cakecosmetic.dev.rule.secret", Component.translatable(secret ? "options.on" : "options.off"));
	}

	private Component untitledLabel() {
		return Component.translatable("cakecosmetic.dev.rule.untitled", Component.translatable(showUntitled ? "options.on" : "options.off"));
	}

	private void filter() {
		String q = search.getValue().trim().toLowerCase(Locale.ROOT);
		rows.clear();
		// the rule's own advancements first, also ones this connection doesn't know
		for (Map.Entry<String, String> e : needs.entrySet()) {
			Unlocks.AdvancementInfo a = known.get(e.getKey());
			rows.add(a != null ? a : new Unlocks.AdvancementInfo(e.getKey(), e.getValue(), false, false));
		}
		for (Unlocks.AdvancementInfo a : known.values()) {
			if (needs.containsKey(a.id()) || (a.title().isEmpty() && !showUntitled)) {
				continue;
			}
			if (q.isEmpty() || a.id().toLowerCase(Locale.ROOT).contains(q) || a.title().toLowerCase(Locale.ROOT).contains(q)) {
				rows.add(a);
			}
		}
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private double maxScroll() {
		return Math.max(0, rows.size() * ROW - (listBottom - LIST_Y));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
		int left = width / 2 - Math.min(320, width - 40) / 2;
		int right = width - left;
		ctx.fill(left - 2, LIST_Y - 2, right + 2, listBottom + 2, 0x80000000);
		ctx.enableScissor(left - 2, LIST_Y, right + 2, listBottom);
		for (int i = 0; i < rows.size(); i++) {
			int y = LIST_Y + i * ROW - (int) scroll;
			if (y + ROW < LIST_Y || y > listBottom) {
				continue;
			}
			Unlocks.AdvancementInfo a = rows.get(i);
			boolean in = needs.containsKey(a.id());
			boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW && mouseY >= LIST_Y && mouseY < listBottom;
			if (hover) {
				ctx.fill(left - 2, y, right + 2, y + ROW, 0x30FFFFFF);
			}
			ctx.fill(left, y + 1, left + 9, y + 10, in ? 0xFFFFD000 : 0xFF404040);
			if (in) {
				ctx.text(font, "✔", left + 1, y + 2, 0xFF000000, false);
			}
			String title = a.title().isEmpty() ? "-" : a.title();
			int x = left + 13;
			ctx.text(font, title, x, y + 2, 0xFFFFFFFF, false);
			x += font.width(title) + 6;
			String tag = a.earned() ? " " + Component.translatable("cakecosmetic.dev.rule.earned").getString() : "";
			int tagW = font.width(tag);
			ctx.text(font, font.plainSubstrByWidth(a.id(), Math.max(0, right - x - tagW - 4)), x, y + 2, 0xFF808080, false);
			if (!tag.isEmpty()) {
				ctx.text(font, tag, right - tagW, y + 2, 0xFF55FF55, false);
			}
		}
		ctx.disableScissor();
		if (known.isEmpty()) {
			ctx.centeredText(font, Component.translatable("cakecosmetic.dev.rule.noAdvancements"), width / 2, listBottom - 14, 0xFFFFAA00);
		}
		Component summary = error != null ? error
				: needs.isEmpty() ? Component.translatable("cakecosmetic.dev.rule.free")
				: Component.translatable("cakecosmetic.dev.rule.needs", needs.size(), String.join(", ", needs.values()));
		ctx.centeredText(font, font.plainSubstrByWidth(summary.getString(), width - 20), width / 2, listBottom + 5,
				error != null ? 0xFFFF5555 : 0xFFAAAAAA);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int left = width / 2 - Math.min(320, width - 40) / 2;
		if (button != 0 || mouseY < LIST_Y || mouseY >= listBottom || mouseX < left || mouseX >= width - left) {
			return false;
		}
		int i = (int) ((mouseY - LIST_Y + scroll) / ROW);
		if (i < 0 || i >= rows.size()) {
			return false;
		}
		Unlocks.AdvancementInfo a = rows.get(i);
		if (needs.remove(a.id()) == null) {
			needs.put(a.id(), a.title().isEmpty() ? a.id() : a.title());
		}
		filter();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - verticalAmount * ROW * 3));
		return true;
	}

	private void save() {
		List<Rules.Need> list = new ArrayList<>();
		for (Map.Entry<String, String> e : needs.entrySet()) {
			list.add(new Rules.Need(e.getKey(), e.getValue()));
		}
		try {
			Unlocks.saveRule(background.id(), new Rules.Rule(list, secret));
			onClose();
		} catch (IOException e) {
			error = Component.translatable("cakecosmetic.dev.rule.saveFailed", e.getMessage());
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
