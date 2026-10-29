package io.github.harrythecake.cakechat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The find bar in the open chat (Ctrl+F): a text field and two toggles (match case, regex) above the input line, at the
 * right. While it's open and has something to look for, every window shows only the messages it finds. Closing the
 * chat ends the search; the text stays for the next Ctrl+F.
 */
public final class FindBar {
	private static final int HEIGHT = 12;
	private static final int FIELD_WIDTH = 120;
	private static final int BUTTON_WIDTH = 18;

	private final MinecraftClient client;
	private final ChatWindows windows;
	private final Find find;
	private boolean open;
	/** Messages in the history the search finds (any tab). */
	private int found;

	@Nullable
	private TextFieldWidget field;
	@Nullable
	private ButtonWidget caseButton;
	@Nullable
	private ButtonWidget regexButton;

	FindBar(MinecraftClient client, ChatWindows windows) {
		this.client = client;
		this.windows = windows;
		ChatConfig.FindOptions options = CakeChat.config().find;
		this.find = new Find(options.regex, options.matchCase);
	}

	Find find() {
		return find;
	}

	/** Whether the windows are filtered now. */
	boolean searching() {
		return open && find.active();
	}

	/** The chat screen was (re)built: new widgets at the screen's current size. */
	public void init(Screen screen, Consumer<ClickableWidget> add) {
		int y = screen.height - 14 - HEIGHT - 2;
		int right = screen.width - 2;
		regexButton = ButtonWidget.builder(Text.empty(), b -> flip(false, screen))
				.dimensions(right - BUTTON_WIDTH, y, BUTTON_WIDTH, HEIGHT)
				.tooltip(Tooltip.of(Text.translatable("cakechat.find.regex.desc")))
				.build();
		caseButton = ButtonWidget.builder(Text.empty(), b -> flip(true, screen))
				.dimensions(right - 2 * BUTTON_WIDTH - 1, y, BUTTON_WIDTH, HEIGHT)
				.tooltip(Tooltip.of(Text.translatable("cakechat.find.case.desc")))
				.build();
		String text = find.query();
		field = new TextFieldWidget(client.textRenderer, right - 2 * BUTTON_WIDTH - 2 - FIELD_WIDTH, y, FIELD_WIDTH, HEIGHT,
				Text.translatable("cakechat.find.hint"));
		field.setMaxLength(256);
		field.setPlaceholder(Text.translatable("cakechat.find.hint").formatted(Formatting.DARK_GRAY));
		field.setText(text);
		field.setChangedListener(s -> update());
		add.accept(field);
		add.accept(caseButton);
		add.accept(regexButton);
		show(screen, open);
	}

	/** Ctrl+F outside the field: open the bar (if needed) and type in it. */
	public void open(Screen screen) {
		show(screen, true);
	}

	/** Esc in the field. */
	public void close(Screen screen) {
		show(screen, false);
	}

	private void show(Screen screen, boolean show) {
		boolean was = searching();
		open = show;
		if (field != null) {
			field.visible = show;
			caseButton.visible = show;
			regexButton.visible = show;
			if (show) {
				screen.setFocused(field);
			}
		}
		updateButtons();
		if (was != searching()) {
			changed();
		}
	}

	/** The chat closed: no more filtering; the text is kept. */
	public void chatClosed() {
		boolean was = searching();
		open = false;
		field = null;
		caseButton = null;
		regexButton = null;
		if (was) {
			changed();
		}
	}

	public boolean fieldFocused(Screen screen) {
		return open && field != null && screen.getFocused() == field;
	}

	private void flip(boolean matchCase, Screen screen) {
		ChatConfig.FindOptions options = CakeChat.config().find;
		if (matchCase) {
			options.matchCase = !options.matchCase;
		} else {
			options.regex = !options.regex;
		}
		CakeChat.saveConfigFile();
		update();
		if (field != null) {
			screen.setFocused(field);
		}
	}

	private void update() {
		ChatConfig.FindOptions options = CakeChat.config().find;
		String text = field == null ? find.query() : field.getText();
		boolean was = searching();
		if (find.set(text, options.regex, options.matchCase) && (was || searching())) {
			changed();
		}
		updateButtons();
	}

	private void updateButtons() {
		if (field == null) {
			return;
		}
		ChatConfig.FindOptions options = CakeChat.config().find;
		caseButton.setMessage(Text.literal("Aa").formatted(options.matchCase ? Formatting.WHITE : Formatting.DARK_GRAY));
		regexButton.setMessage(Text.literal(".*").formatted(options.regex ? Formatting.WHITE : Formatting.DARK_GRAY));
		field.setEditableColor(find.invalid() ? 0xFF5555 : 0xE0E0E0);
	}

	/** The search changed: count again and redraw every window. */
	private void changed() {
		found = 0;
		if (searching()) {
			for (ChatEntry entry : CakeChat.store().entries()) {
				if (find.matches(entry.content.getString())) {
					found++;
				}
			}
		}
		windows.invalidate();
	}

	void added(ChatEntry entry) {
		if (searching() && find.matches(entry.content.getString())) {
			found++;
		}
	}

	void removed(ChatEntry entry) {
		if (searching() && find.matches(entry.content.getString())) {
			found--;
		}
	}

	/** The count (or the regex error) left of the field. */
	public void render(DrawContext context) {
		if (!open || field == null) {
			return;
		}
		Text label;
		if (find.invalid()) {
			label = Text.translatable("cakechat.find.invalid").formatted(Formatting.RED);
		} else if (find.active()) {
			label = Text.translatable("cakechat.find.count", found).formatted(found > 0 ? Formatting.GRAY : Formatting.RED);
		} else {
			return;
		}
		int width = client.textRenderer.getWidth(label);
		int x = field.getX() - width - 4;
		int y = field.getY();
		context.fill(x - 2, y, field.getX() - 1, y + HEIGHT, client.options.getTextBackgroundColor(Integer.MIN_VALUE));
		context.drawTextWithShadow(client.textRenderer, label, x, y + 2, 0xFFFFFF);
	}
}
