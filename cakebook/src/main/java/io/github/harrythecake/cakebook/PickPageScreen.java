package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Picks a book page or note to link to from a note: every page by book, or search results while typing. */
final class PickPageScreen extends Screen {
	private final Screen parent;
	private final BiConsumer<Links.Target, String> onPick;
	private String query = "";
	private TextFieldWidget search;
	private RowList list;

	/** {@code onPick} gets the target and its title; the parent screen is shown again either way. */
	PickPageScreen(Screen parent, BiConsumer<Links.Target, String> onPick) {
		super(Text.translatable("cakebook.pick.title"));
		this.parent = parent;
		this.onPick = onPick;
	}

	@Override
	protected void init() {
		int listWidth = Math.min(320, width - 40);
		int left = (width - listWidth) / 2;
		search = new TextFieldWidget(textRenderer, left, 22, listWidth, 18, Text.translatable("cakebook.search"));
		search.setPlaceholder(Text.translatable("cakebook.search").formatted(Formatting.DARK_GRAY));
		search.setText(query);
		search.setChangedListener(text -> {
			query = text;
			list.resetScroll();
			refresh();
		});
		addDrawableChild(search);
		list = new RowList(textRenderer);
		list.bounds(left, 46, listWidth, height - 46 - 32);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
				.dimensions(width / 2 - 50, height - 26, 100, 20).build());
		setInitialFocus(search);
		refresh();
	}

	private void refresh() {
		String lang = client.getLanguageManager().getLanguage();
		List<RowList.Row> rows = new ArrayList<>();
		List<Notes.Note> notes = CakeBook.notes().list();
		if (!query.isBlank()) {
			for (Search.Hit hit : Search.find(CakeBook.library(), notes, lang, query, 100)) {
				rows.add(RowList.Row.page(Text.literal(hit.title()), 0, () -> pick(hit.target(), hit.title()), false));
			}
		} else {
			for (Book book : CakeBook.library().books()) {
				String bookTitle = book.title(lang);
				rows.add(RowList.Row.page(Text.literal(bookTitle).formatted(Formatting.BOLD), 0,
						() -> pick(Links.Target.page(book.id, Book.HOME), bookTitle), false));
				for (Book.TocEntry entry : book.toc(lang)) {
					if (entry.path() != null) {
						rows.add(RowList.Row.page(Text.literal(entry.title()), entry.depth() + 1,
								() -> pick(Links.Target.page(book.id, entry.path()), entry.title()), false));
					} else {
						rows.add(new RowList.Row(Text.literal(entry.title()).formatted(Formatting.GRAY), entry.depth() + 1,
								null, null, null, null, false, null));
					}
				}
			}
			if (!notes.isEmpty()) {
				rows.add(new RowList.Row(Text.translatable("cakebook.notes").formatted(Formatting.BOLD), 0, null, null,
						null, null, false, null));
				for (Notes.Note note : notes) {
					rows.add(RowList.Row.page(Text.literal(note.title()), 1,
							() -> pick(new Links.Target(Links.Kind.NOTE, null, note.id()), note.title()), false));
				}
			}
		}
		if (rows.isEmpty()) {
			rows.add(new RowList.Row(Text.translatable("cakebook.search.none").formatted(Formatting.GRAY), 0, null,
					null, null, null, false, null));
		}
		list.set(rows);
	}

	private void pick(Links.Target target, String title) {
		onPick.accept(target, title);
		client.setScreen(parent);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return super.mouseClicked(mouseX, mouseY, button) || button == 0 && list.click(mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		list.scroll(verticalAmount);
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 8, 0xFFFFFF);
		list.render(context, mouseX, mouseY);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
