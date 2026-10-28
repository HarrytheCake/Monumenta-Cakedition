package io.github.harrythecake.cakebook;

import java.io.IOException;

import io.github.harrythecake.cakebook.mixin.EditBoxWidgetAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CursorMovement;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Writes or edits a note. Escape and Save both keep the text; only Cancel throws changes away. "Insert link" opens
 * the page picker and puts a Markdown link to the chosen page at the cursor; "Insert location" puts where the player
 * stands there.
 */
final class NoteEditScreen extends Screen {
	private final BookScreen parent;
	private final @Nullable Notes.Note note;
	private String title;
	private String body;
	private int cursor = -1;
	private @Nullable String pendingInsert;

	private TextFieldWidget titleField;
	private EditBoxWidget bodyField;

	/** {@code note} null = a new note, created on the first save. */
	NoteEditScreen(BookScreen parent, @Nullable Notes.Note note) {
		super(Text.translatable(note == null ? "cakebook.note.new" : "cakebook.note.edit"));
		this.parent = parent;
		this.note = note;
		this.title = note == null ? "" : note.title();
		this.body = note == null ? "" : note.body();
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(420, width - 40);
		int left = (width - fieldWidth) / 2;

		titleField = new TextFieldWidget(textRenderer, left, 22, fieldWidth, 18, Text.translatable("cakebook.note.title"));
		titleField.setPlaceholder(Text.translatable("cakebook.note.title").formatted(Formatting.DARK_GRAY));
		titleField.setMaxLength(80);
		titleField.setText(title);
		titleField.setChangedListener(text -> title = text);
		addDrawableChild(titleField);

		bodyField = new EditBoxWidget(textRenderer, left, 46, fieldWidth, height - 46 - 56,
				Text.translatable("cakebook.note.placeholder"), Text.translatable("cakebook.note.body"));
		bodyField.setMaxLength(100_000);
		bodyField.setText(body);
		bodyField.setChangeListener(text -> body = text);
		addDrawableChild(bodyField);
		if (cursor >= 0) {
			EditBox box = ((EditBoxWidgetAccessor) bodyField).cakebook$editBox();
			box.moveCursor(CursorMovement.ABSOLUTE, Math.min(cursor, body.length()));
			if (pendingInsert != null) {
				box.replaceSelection(pendingInsert);
				pendingInsert = null;
			}
			cursor = -1;
		}

		int row = height - 50;
		int half = (fieldWidth - 4) / 2;
		ButtonWidget location = addDrawableChild(ButtonWidget.builder(Text.translatable("cakebook.note.insert_location"),
				b -> insert(Location.markdown(client))).dimensions(left, row, half, 20)
				.tooltip(Tooltip.of(Text.translatable("cakebook.note.insert_location.hover"))).build());
		location.active = client.player != null;
		addDrawableChild(ButtonWidget.builder(Text.translatable("cakebook.note.insert_link"), b -> pickLink())
				.dimensions(left + half + 4, row, half, 20)
				.tooltip(Tooltip.of(Text.translatable("cakebook.note.insert_link.hover"))).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("cakebook.note.save"), b -> close())
				.dimensions(left, row + 24, half, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> client.setScreen(parent))
				.dimensions(left + half + 4, row + 24, half, 20).build());

		setInitialFocus(note == null ? titleField : bodyField);
	}

	private EditBox editBox() {
		return ((EditBoxWidgetAccessor) bodyField).cakebook$editBox();
	}

	private void insert(@Nullable String text) {
		if (text == null) {
			return;
		}
		EditBox box = editBox();
		int at = box.getCursor();
		boolean lineStart = at == 0 || body.charAt(at - 1) == '\n';
		box.replaceSelection((lineStart ? "" : "\n") + text + "\n");
		setFocused(bodyField);
	}

	private void pickLink() {
		cursor = editBox().getCursor();
		client.setScreen(new PickPageScreen(this, (target, pageTitle) ->
				pendingInsert = "[" + pageTitle.replace("]", "\\]") + "](" + target.toLink() + ")"));
	}

	/** Saves (a new note only if something was written) and goes back to the book. */
	@Override
	public void close() {
		String cleanTitle = title.strip();
		if (note == null && cleanTitle.isEmpty() && body.isBlank()) {
			client.setScreen(parent);
			return;
		}
		if (cleanTitle.isEmpty()) {
			String firstLine = body.strip().lines().findFirst().orElse("").replaceAll("^[#>*\\-\\s]+", "");
			cleanTitle = firstLine.isEmpty() ? Text.translatable("cakebook.note.untitled").getString()
					: firstLine.substring(0, Math.min(firstLine.length(), 40));
		}
		try {
			Notes notes = CakeBook.notes();
			Notes.Note target = note != null ? note : notes.create(cleanTitle);
			parent.noteSaved(notes.save(target.withText(cleanTitle, body)));
		} catch (IOException e) {
			CakeBook.LOGGER.error("Could not save the note", e);
			if (client.player != null) {
				client.player.sendMessage(Text.translatable("cakebook.note.save_failed", e.getMessage())
						.formatted(Formatting.RED), false);
			}
		}
		client.setScreen(parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 8, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
