package io.github.harrythecake.cakebook;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The book: a sidebar with every book's contents, the player's notes and one search box, and the open page on the
 * right. Back/forward history and the last page survive closing the screen (until the game quits).
 */
final class BookScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int CONTENT_MAX = 380;
	private static final int SEARCH_LIMIT = 60;

	private static final Deque<Links.Target> BACK = new ArrayDeque<>();
	private static final Deque<Links.Target> FORWARD = new ArrayDeque<>();
	/** Expanded books ("book") and folders ("book/folder") in the sidebar; notes use the key ":notes". */
	private static final Set<String> EXPANDED = new HashSet<>(Set.of(":notes"));
	private static @Nullable Links.Target last;
	private static double lastScroll;

	private final @Nullable Screen parent;
	private Links.Target current;
	private String query;
	private List<Notes.Note> notes = List.of();

	private RowList sidebar;
	private TextFieldWidget search;
	private ButtonWidget back;
	private ButtonWidget forward;
	private ButtonWidget edit;
	private ButtonWidget delete;

	private @Nullable Markdown.Doc doc;
	private @Nullable String docTitle;
	private @Nullable Book.Page page;
	private @Nullable PageLayout layout;
	private double scroll;
	private int contentLeft;
	private int contentTop;
	private int contentWidth;
	private int contentBottom;

	/** Opens {@code target}, or the last page shown, or the first book's home page. */
	BookScreen(@Nullable Screen parent, @Nullable Links.Target target, @Nullable String query) {
		super(Text.translatable("cakebook.title"));
		this.parent = parent;
		this.query = query == null ? "" : query;
		if (target != null) {
			if (last != null && !target.equals(last)) {
				BACK.push(last);
				FORWARD.clear();
			}
			current = target;
		} else if (last != null) {
			current = last;
			scroll = lastScroll;
		} else {
			current = home();
		}
	}

	private static Links.Target home() {
		Library library = CakeBook.library();
		Book first = library.book(CakeBook.config().firstBook);
		if (first == null && !library.books().isEmpty()) {
			first = library.books().get(0);
		}
		return Links.Target.page(first == null ? "" : first.id, Book.HOME);
	}

	private String lang() {
		return client.getLanguageManager().getLanguage();
	}

	@Override
	protected void init() {
		int sideWidth = Math.max(110, Math.min(170, width / 4));
		int bottom = height - 32;
		contentLeft = MARGIN + sideWidth + 12;
		contentTop = 12;
		contentBottom = bottom;
		contentWidth = Math.min(CONTENT_MAX, width - contentLeft - 14);

		search = new TextFieldWidget(textRenderer, MARGIN + 1, 9, sideWidth - 2, 16, Text.translatable("cakebook.search"));
		search.setPlaceholder(Text.translatable("cakebook.search").formatted(Formatting.DARK_GRAY));
		search.setMaxLength(100);
		search.setText(query);
		search.setChangedListener(text -> {
			query = text;
			sidebar.resetScroll();
			refreshSidebar();
		});
		addDrawableChild(search);

		sidebar = new RowList(textRenderer);
		sidebar.bounds(MARGIN, 30, sideWidth, bottom - 30);

		int y = height - 26;
		back = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> goBack())
				.dimensions(MARGIN, y, 20, 20).tooltip(Tooltip.of(Text.translatable("cakebook.back"))).build());
		forward = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> goForward())
				.dimensions(MARGIN + 22, y, 20, 20).tooltip(Tooltip.of(Text.translatable("cakebook.forward"))).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("cakebook.note.new"), b -> newNote())
				.dimensions(MARGIN + 48, y, 74, 20).build());
		edit = addDrawableChild(ButtonWidget.builder(Text.translatable("cakebook.note.edit"), b -> editNote())
				.dimensions(MARGIN + 124, y, 50, 20).build());
		delete = addDrawableChild(ButtonWidget.builder(Text.translatable("cakebook.note.delete"), b -> deleteNote())
				.dimensions(MARGIN + 176, y, 50, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
				.dimensions(width - MARGIN - 70, y, 70, 20).build());

		show(current, false);
	}

	// ---------------------------------------------------------------- navigation

	/** Shows a page or note, putting the current one on the back stack. Web links ask before opening. */
	void open(Links.Target target) {
		if (target.kind() == Links.Kind.URL) {
			ConfirmLinkScreen.open(this, target.path());
			return;
		}
		if (!target.equals(current)) {
			BACK.push(current);
			FORWARD.clear();
		}
		scroll = 0;
		show(target, true);
	}

	private void goBack() {
		if (!BACK.isEmpty()) {
			FORWARD.push(current);
			scroll = 0;
			show(BACK.pop(), true);
		}
	}

	private void goForward() {
		if (!FORWARD.isEmpty()) {
			BACK.push(current);
			scroll = 0;
			show(FORWARD.pop(), true);
		}
	}

	private void show(Links.Target target, boolean reveal) {
		current = target;
		notes = CakeBook.notes().list();
		page = null;
		doc = null;
		docTitle = null;
		if (target.kind() == Links.Kind.NOTE) {
			Notes.Note note = CakeBook.notes().get(target.path());
			if (note != null) {
				doc = Markdown.parse(note.body());
				docTitle = note.title();
			}
		} else {
			Book book = CakeBook.library().book(target.book());
			if (book != null) {
				EXPANDED.add(book.id);
				page = book.page(target.path(), lang());
				if (page != null) {
					doc = page.doc();
					String dir = target.path();
					for (int slash = dir.lastIndexOf('/'); slash > 0; slash = dir.lastIndexOf('/')) {
						dir = dir.substring(0, slash);
						EXPANDED.add(book.id + "/" + dir);
					}
				}
			}
		}
		if (doc == null) {
			doc = Markdown.parse(missingText(target));
		}
		relayout();
		refreshSidebar();
		if (reveal) {
			sidebar.revealSelected();
		}
		back.active = !BACK.isEmpty();
		forward.active = !FORWARD.isEmpty();
		boolean isNote = target.kind() == Links.Kind.NOTE && docTitle != null;
		edit.visible = isNote;
		delete.visible = isNote;
	}

	private String missingText(Links.Target target) {
		if (CakeBook.library().books().isEmpty() && target.kind() == Links.Kind.PAGE) {
			return "# " + Text.translatable("cakebook.empty.title").getString() + "\n\n"
					+ Text.translatable("cakebook.empty.text").getString();
		}
		return "# " + Text.translatable("cakebook.missing.title").getString() + "\n\n"
				+ Text.translatable("cakebook.missing.text").getString() + "\n\n`" + target.toLink() + "`";
	}

	private void relayout() {
		Book book = page == null ? null : CakeBook.library().book(page.book());
		String fromFile = page == null ? "" : page.file();
		layout = PageLayout.of(doc, docTitle, src -> {
			Links.Image image = Links.image(fromFile, src);
			if (image == null) {
				return null;
			}
			if (image.resource() != null) {
				return "res:" + image.resource();
			}
			return book == null ? null : book.file(image.bookFile());
		}, textRenderer, contentWidth);
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private double maxScroll() {
		return layout == null ? 0 : Math.max(0, layout.height - (contentBottom - contentTop) + 8);
	}

	// ---------------------------------------------------------------- sidebar

	private void refreshSidebar() {
		List<RowList.Row> rows = new ArrayList<>();
		String lang = lang();
		if (!query.isBlank()) {
			List<Search.Hit> hits = Search.find(CakeBook.library(), notes, lang, query, SEARCH_LIMIT);
			if (hits.isEmpty()) {
				rows.add(new RowList.Row(Text.translatable("cakebook.search.none").formatted(Formatting.GRAY), 0,
						null, null, null, null, false, null));
			}
			for (Search.Hit hit : hits) {
				Text where = hit.kind() == Search.Kind.NOTE ? Text.translatable("cakebook.notes")
						: Text.literal(bookTitle(hit.book(), lang));
				Text tooltip = Text.empty().append(Text.literal(hit.title()).formatted(Formatting.YELLOW))
						.append(Text.literal("  ")).append(where.copy().formatted(Formatting.GRAY))
						.append(Text.literal("\n" + hit.snippet()));
				Links.Target target = hit.target();
				rows.add(new RowList.Row(Text.literal(hit.title()), 0, null, () -> open(target), null, null,
						target.equals(current), tooltip));
			}
			sidebar.set(rows);
			return;
		}
		for (Book book : CakeBook.library().books()) {
			boolean open = EXPANDED.contains(book.id);
			Links.Target home = Links.Target.page(book.id, Book.HOME);
			rows.add(new RowList.Row(Text.literal(book.title(lang)).formatted(Formatting.BOLD), 0, icon(book.icon),
					() -> {
						EXPANDED.add(book.id);
						open(home);
					}, () -> toggle(book.id), open, home.equals(current), null));
			if (!open) {
				continue;
			}
			Set<String> hiddenUnder = new HashSet<>();
			for (Book.TocEntry entry : book.toc(lang)) {
				String folderKey = entry.folder() == null ? null : book.id + "/" + entry.folder();
				if (hiddenUnder.stream().anyMatch(h -> entry.path() != null && entry.path().startsWith(h + "/")
						|| entry.folder() != null && entry.folder().startsWith(h + "/"))) {
					continue;
				}
				Links.Target target = entry.path() == null ? null : Links.Target.page(book.id, entry.path());
				if (folderKey != null) {
					boolean folderOpen = EXPANDED.contains(folderKey);
					if (!folderOpen) {
						hiddenUnder.add(entry.folder());
					}
					rows.add(new RowList.Row(Text.literal(entry.title()), entry.depth() + 1, null,
							target == null ? () -> toggle(folderKey) : () -> {
								EXPANDED.add(folderKey);
								open(target);
							}, () -> toggle(folderKey), folderOpen, target != null && target.equals(current), null));
				} else {
					rows.add(RowList.Row.page(Text.literal(entry.title()), entry.depth() + 1, () -> open(target),
							target.equals(current)));
				}
			}
		}
		boolean notesOpen = EXPANDED.contains(":notes");
		rows.add(new RowList.Row(Text.translatable("cakebook.notes").formatted(Formatting.BOLD), 0,
				new ItemStack(Items.WRITABLE_BOOK), () -> toggle(":notes"), () -> toggle(":notes"), notesOpen, false, null));
		if (notesOpen) {
			if (notes.isEmpty()) {
				rows.add(new RowList.Row(Text.translatable("cakebook.notes.none").formatted(Formatting.GRAY), 1,
						null, null, null, null, false, null));
			}
			for (Notes.Note note : notes) {
				Links.Target target = new Links.Target(Links.Kind.NOTE, null, note.id());
				rows.add(RowList.Row.page(Text.literal(note.title()), 1, () -> open(target), target.equals(current)));
			}
		}
		sidebar.set(rows);
	}

	private void toggle(String key) {
		if (!EXPANDED.remove(key)) {
			EXPANDED.add(key);
		}
		refreshSidebar();
	}

	private static String bookTitle(@Nullable String id, String lang) {
		Book book = id == null ? null : CakeBook.library().book(id);
		return book == null ? String.valueOf(id) : book.title(lang);
	}

	private static ItemStack icon(@Nullable String id) {
		Identifier item = id == null ? null : Identifier.tryParse(id);
		if (item != null && Registries.ITEM.containsId(item)) {
			return new ItemStack(Registries.ITEM.get(item));
		}
		return new ItemStack(Items.BOOK);
	}

	// ---------------------------------------------------------------- notes

	private void newNote() {
		client.setScreen(new NoteEditScreen(this, null));
	}

	private void editNote() {
		Notes.Note note = CakeBook.notes().get(current.path());
		if (note != null) {
			client.setScreen(new NoteEditScreen(this, note));
		}
	}

	private void deleteNote() {
		Notes.Note note = CakeBook.notes().get(current.path());
		if (note == null) {
			return;
		}
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				try {
					CakeBook.notes().delete(note.id());
				} catch (IOException e) {
					CakeBook.LOGGER.error("Could not delete note {}", note.id(), e);
				}
				BACK.removeIf(t -> t.equals(current));
				FORWARD.removeIf(t -> t.equals(current));
				current = BACK.isEmpty() ? home() : BACK.pop();
				scroll = 0;
			}
			client.setScreen(this);
		}, Text.translatable("cakebook.note.delete.title"), Text.translatable("cakebook.note.delete.text", note.title())));
	}

	/** Called by the editor after saving: show that note. */
	void noteSaved(Notes.Note note) {
		Links.Target target = new Links.Target(Links.Kind.NOTE, null, note.id());
		EXPANDED.add(":notes");
		if (!target.equals(current)) {
			BACK.push(current);
			FORWARD.clear();
			scroll = 0;
		}
		current = target;
	}

	// ---------------------------------------------------------------- input and drawing

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
			goBack();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
			goForward();
			return true;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (sidebar.click(mouseX, mouseY)) {
				return true;
			}
			Style style = styleAt(mouseX, mouseY);
			if (style != null && style.getInsertion() != null && style.getInsertion().startsWith(PageLayout.LINK)) {
				String link = style.getInsertion().substring(PageLayout.LINK.length());
				boolean inNote = current.kind() == Links.Kind.NOTE;
				Links.Target target = Links.resolve(inNote ? null : current.book(), inNote ? "" : current.path(), link);
				if (target != null) {
					open(target);
				}
				return true;
			}
		}
		return false;
	}

	private @Nullable Style styleAt(double mouseX, double mouseY) {
		if (layout == null || mouseX < contentLeft || mouseX >= contentLeft + contentWidth
				|| mouseY < contentTop || mouseY >= contentBottom) {
			return null;
		}
		return layout.styleAt(mouseX - contentLeft, mouseY - contentTop + scroll);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (sidebar.contains(mouseX, mouseY)) {
			sidebar.scroll(verticalAmount);
		} else {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - verticalAmount * PageLayout.LINE * 3));
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
			setFocused(search);
			search.setFocused(true);
			return true;
		}
		if (!search.isFocused()) {
			int page = contentBottom - contentTop - 20;
			switch (keyCode) {
				case GLFW.GLFW_KEY_DOWN -> scroll += PageLayout.LINE * 2;
				case GLFW.GLFW_KEY_UP -> scroll -= PageLayout.LINE * 2;
				case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> scroll += page;
				case GLFW.GLFW_KEY_PAGE_UP -> scroll -= page;
				case GLFW.GLFW_KEY_HOME -> scroll = 0;
				case GLFW.GLFW_KEY_END -> scroll = maxScroll();
				case GLFW.GLFW_KEY_BACKSPACE -> goBack();
				default -> {
					if (CakeBook.openKey().matchesKey(keyCode, scanCode)) {
						close();
						return true;
					}
					return super.keyPressed(keyCode, scanCode, modifiers);
				}
			}
			scroll = Math.max(0, Math.min(maxScroll(), scroll));
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		sidebar.render(context, mouseX, mouseY);

		int boxLeft = contentLeft - 6;
		int boxRight = contentLeft + contentWidth + 6;
		context.fill(boxLeft, contentTop - 4, boxRight, contentBottom, 0x40000000);
		if (layout != null) {
			context.enableScissor(boxLeft, contentTop - 4, boxRight, contentBottom);
			layout.render(context, contentLeft, contentTop - (int) scroll);
			context.disableScissor();
			double max = maxScroll();
			if (max > 0) {
				int viewport = contentBottom - contentTop + 4;
				int barHeight = Math.max(16, viewport * viewport / (viewport + (int) max));
				int barTop = contentTop - 4 + (int) ((viewport - barHeight) * scroll / max);
				context.fill(boxRight - 2, barTop, boxRight, barTop + barHeight, 0x80FFFFFF);
			}
		}
		Style style = styleAt(mouseX, mouseY);
		if (style != null && style.getHoverEvent() != null) {
			context.drawHoverEvent(textRenderer, style, mouseX, mouseY);
		}
	}

	@Override
	public void close() {
		last = current;
		lastScroll = scroll;
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
