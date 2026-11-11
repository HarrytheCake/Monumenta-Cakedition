package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Picks a book page or note to link to from a note: every page by book, or search results while typing. */
final class PickPageScreen extends Screen {
	private final Screen parent;
	private final BiConsumer<Links.Target, String> onPick;
	private String query = "";
	private EditBox search;
	private RowList list;

	/** {@code onPick} gets the target and its title; the parent screen is shown again either way. */
	PickPageScreen(Screen parent, BiConsumer<Links.Target, String> onPick) {
		super(Component.translatable("cakebook.pick.title"));
		this.parent = parent;
		this.onPick = onPick;
	}

	@Override
	protected void init() {
		int listWidth = Math.min(320, width - 40);
		int left = (width - listWidth) / 2;
		search = new EditBox(font, left, 22, listWidth, 18, Component.translatable("cakebook.search"));
		search.setHint(Component.translatable("cakebook.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(text -> {
			query = text;
			list.resetScroll();
			refresh();
		});
		addRenderableWidget(search);
		list = new RowList(font);
		list.bounds(left, 46, listWidth, height - 46 - 32);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(width / 2 - 50, height - 26, 100, 20).build());
		setInitialFocus(search);
		refresh();
	}

	private void refresh() {
		String lang = minecraft.getLanguageManager().getSelected();
		List<RowList.Row> rows = new ArrayList<>();
		List<Notes.Note> notes = CakeBook.notes().list();
		if (!query.isBlank()) {
			for (Search.Hit hit : Search.find(CakeBook.library(), notes, lang, query, 100)) {
				rows.add(RowList.Row.page(Component.literal(hit.title()), 0, () -> pick(hit.target(), hit.title()), false));
			}
		} else {
			for (Book book : CakeBook.library().books()) {
				String bookTitle = book.title(lang);
				rows.add(RowList.Row.page(Component.literal(bookTitle).withStyle(ChatFormatting.BOLD), 0,
						() -> pick(Links.Target.page(book.id, Book.HOME), bookTitle), false));
				for (Book.TocEntry entry : book.toc(lang)) {
					if (entry.path() != null) {
						rows.add(RowList.Row.page(Component.literal(entry.title()), entry.depth() + 1,
								() -> pick(Links.Target.page(book.id, entry.path()), entry.title()), false));
					} else {
						rows.add(new RowList.Row(Component.literal(entry.title()).withStyle(ChatFormatting.GRAY), entry.depth() + 1,
								null, null, null, null, false, null));
					}
				}
			}
			if (!notes.isEmpty()) {
				rows.add(new RowList.Row(Component.translatable("cakebook.notes").withStyle(ChatFormatting.BOLD), 0, null, null,
						null, null, false, null));
				for (Notes.Note note : notes) {
					rows.add(RowList.Row.page(Component.literal(note.title()), 1,
							() -> pick(new Links.Target(Links.Kind.NOTE, null, note.id()), note.title()), false));
				}
			}
		}
		if (rows.isEmpty()) {
			rows.add(new RowList.Row(Component.translatable("cakebook.search.none").withStyle(ChatFormatting.GRAY), 0, null,
					null, null, null, false, null));
		}
		list.set(rows);
	}

	private void pick(Links.Target target, String title) {
		onPick.accept(target, title);
		minecraft.setScreen(parent);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event, doubleClick) || event.button() == 0 && list.click(event.x(), event.y());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		list.scroll(verticalAmount);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, getTitle(), width / 2, 8, 0xFFFFFFFF);
		list.render(context, mouseX, mouseY);
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
