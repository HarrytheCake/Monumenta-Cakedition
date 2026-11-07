package io.github.harrythecake.cakechat;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The find bar in the open chat (Ctrl+F): a text field and two toggles (match case, regex) above the input line, at the
 * right. While it's open and has something to look for, every window shows only the messages it finds. Closing the
 * chat ends the search; the text stays for the next Ctrl+F.
 */
public final class FindBar {
	private static final int HEIGHT = 12;
	private static final int FIELD_WIDTH = 120;
	private static final int BUTTON_WIDTH = 18;

	private final Minecraft client;
	private final ChatWindows windows;
	private final Find find;
	private boolean open;
	/** Messages in the history the search finds (any tab). */
	private int found;

	@Nullable
	private EditBox field;
	@Nullable
	private Button caseButton;
	@Nullable
	private Button regexButton;

	FindBar(Minecraft client, ChatWindows windows) {
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

	/**
	 * The chat screen was (re)built: new widgets at the screen's current size, for the screen to add. (A list, not a
	 * method reference into the mixin: NeoForge 1.20.4's Mixin can't merge that lambda into ChatScreen.)
	 */
	public List<AbstractWidget> init(Screen screen) {
		int y = screen.height - 14 - HEIGHT - 2;
		int right = screen.width - 2;
		regexButton = Button.builder(Component.empty(), b -> flip(false, screen))
				.bounds(right - BUTTON_WIDTH, y, BUTTON_WIDTH, HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("cakechat.find.regex.desc")))
				.build();
		caseButton = Button.builder(Component.empty(), b -> flip(true, screen))
				.bounds(right - 2 * BUTTON_WIDTH - 1, y, BUTTON_WIDTH, HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("cakechat.find.case.desc")))
				.build();
		String text = find.query();
		field = new EditBox(client.font, right - 2 * BUTTON_WIDTH - 2 - FIELD_WIDTH, y, FIELD_WIDTH, HEIGHT,
				Component.translatable("cakechat.find.hint"));
		field.setMaxLength(256);
		field.setHint(Component.translatable("cakechat.find.hint").withStyle(ChatFormatting.DARK_GRAY));
		field.setValue(text);
		field.setResponder(s -> update());
		show(screen, open);
		return List.of(field, caseButton, regexButton);
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

	/** Types {@code text} into the open field (for the self-test). */
	void search(String text) {
		if (field != null) {
			field.setValue(text);
		}
	}

	/** Messages in the history the search finds. */
	int found() {
		return found;
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
		String text = field == null ? find.query() : field.getValue();
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
		caseButton.setMessage(Component.literal("Aa").withStyle(options.matchCase ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
		regexButton.setMessage(Component.literal(".*").withStyle(options.regex ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
		field.setTextColor(find.invalid() ? 0xFF5555 : 0xE0E0E0);
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
	public void render(GuiGraphics context) {
		if (!open || field == null) {
			return;
		}
		Component label;
		if (find.invalid()) {
			label = Component.translatable("cakechat.find.invalid").withStyle(ChatFormatting.RED);
		} else if (find.active()) {
			label = Component.translatable("cakechat.find.count", found).withStyle(found > 0 ? ChatFormatting.GRAY : ChatFormatting.RED);
		} else {
			return;
		}
		int width = client.font.width(label);
		int x = field.getX() - width - 4;
		int y = field.getY();
		context.fill(x - 2, y, field.getX() - 1, y + HEIGHT, client.options.getBackgroundColor(Integer.MIN_VALUE));
		context.drawString(client.font, label, x, y + 2, 0xFFFFFF);
	}
}
