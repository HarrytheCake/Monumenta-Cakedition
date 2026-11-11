package io.github.harrythecake.cakebook;

import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import io.github.harrythecake.cakebook.mixin.EditBoxWidgetAccessor;
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

	private EditBox titleField;
	private MultiLineEditBox bodyField;

	/** {@code note} null = a new note, created on the first save. */
	NoteEditScreen(BookScreen parent, @Nullable Notes.Note note) {
		super(Component.translatable(note == null ? "cakebook.note.new" : "cakebook.note.edit"));
		this.parent = parent;
		this.note = note;
		this.title = note == null ? "" : note.title();
		this.body = note == null ? "" : note.body();
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(420, width - 40);
		int left = (width - fieldWidth) / 2;

		titleField = new EditBox(font, left, 22, fieldWidth, 18, Component.translatable("cakebook.note.title"));
		titleField.setHint(Component.translatable("cakebook.note.title").withStyle(ChatFormatting.DARK_GRAY));
		titleField.setMaxLength(80);
		titleField.setValue(title);
		titleField.setResponder(text -> title = text);
		addRenderableWidget(titleField);

		bodyField = MultiLineEditBox.builder().setX(left).setY(46)
				.setPlaceholder(Component.translatable("cakebook.note.placeholder"))
				.build(font, fieldWidth, height - 46 - 56, Component.translatable("cakebook.note.body"));
		bodyField.setCharacterLimit(100_000);
		bodyField.setValue(body);
		bodyField.setValueListener(text -> body = text);
		addRenderableWidget(bodyField);
		if (cursor >= 0) {
			MultilineTextField box = ((EditBoxWidgetAccessor) bodyField).cakebook$editBox();
			box.seekCursor(Whence.ABSOLUTE, Math.min(cursor, body.length()));
			if (pendingInsert != null) {
				box.insertText(pendingInsert);
				pendingInsert = null;
			}
			cursor = -1;
		}

		int row = height - 50;
		int half = (fieldWidth - 4) / 2;
		Button location = addRenderableWidget(Button.builder(Component.translatable("cakebook.note.insert_location"),
				b -> insert(Location.markdown(minecraft))).bounds(left, row, half, 20)
				.tooltip(Tooltip.create(Component.translatable("cakebook.note.insert_location.hover"))).build());
		location.active = minecraft.player != null;
		addRenderableWidget(Button.builder(Component.translatable("cakebook.note.insert_link"), b -> pickLink())
				.bounds(left + half + 4, row, half, 20)
				.tooltip(Tooltip.create(Component.translatable("cakebook.note.insert_link.hover"))).build());
		addRenderableWidget(Button.builder(Component.translatable("cakebook.note.save"), b -> onClose())
				.bounds(left, row + 24, half, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> minecraft.setScreen(parent))
				.bounds(left + half + 4, row + 24, half, 20).build());

		setInitialFocus(note == null ? titleField : bodyField);
	}

	MultilineTextField editBox() {
		return ((EditBoxWidgetAccessor) bodyField).cakebook$editBox();
	}

	void insert(@Nullable String text) {
		if (text == null) {
			return;
		}
		MultilineTextField box = editBox();
		int at = box.cursor();
		boolean lineStart = at == 0 || body.charAt(at - 1) == '\n';
		box.insertText((lineStart ? "" : "\n") + text + "\n");
		setFocused(bodyField);
	}

	private void pickLink() {
		cursor = editBox().cursor();
		minecraft.setScreen(new PickPageScreen(this, (target, pageTitle) ->
				pendingInsert = "[" + pageTitle.replace("]", "\\]") + "](" + target.toLink() + ")"));
	}

	/** Saves (a new note only if something was written) and goes back to the book. */
	@Override
	public void onClose() {
		String cleanTitle = title.strip();
		if (note == null && cleanTitle.isEmpty() && body.isBlank()) {
			minecraft.setScreen(parent);
			return;
		}
		if (cleanTitle.isEmpty()) {
			String firstLine = body.strip().lines().findFirst().orElse("").replaceAll("^[#>*\\-\\s]+", "");
			cleanTitle = firstLine.isEmpty() ? Component.translatable("cakebook.note.untitled").getString()
					: firstLine.substring(0, Math.min(firstLine.length(), 40));
		}
		try {
			Notes notes = CakeBook.notes();
			Notes.Note target = note != null ? note : notes.create(cleanTitle);
			parent.noteSaved(notes.save(target.withText(cleanTitle, body)));
		} catch (IOException e) {
			CakeBook.LOGGER.error("Could not save the note", e);
			if (minecraft.player != null) {
				minecraft.player.sendSystemMessage(Component.translatable("cakebook.note.save_failed", e.getMessage())
						.withStyle(ChatFormatting.RED));
			}
		}
		minecraft.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, getTitle(), width / 2, 8, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
