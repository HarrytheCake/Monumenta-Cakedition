package io.github.harrythecake.cakebook;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
	private EditBox search;
	private Button back;
	private Button forward;
	private Button edit;
	private Button delete;

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
		super(Component.translatable("cakebook.title"));
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
		return minecraft.getLanguageManager().getSelected();
	}

	@Override
	protected void init() {
		int sideWidth = Math.max(110, Math.min(170, width / 4));
		int bottom = height - 32;
		contentLeft = MARGIN + sideWidth + 12;
		contentTop = 12;
		contentBottom = bottom;
		contentWidth = Math.min(CONTENT_MAX, width - contentLeft - 14);

		search = new EditBox(font, MARGIN + 1, 9, sideWidth - 2, 16, Component.translatable("cakebook.search"));
		search.setHint(Component.translatable("cakebook.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setMaxLength(100);
		search.setValue(query);
		search.setResponder(text -> {
			query = text;
			sidebar.resetScroll();
			refreshSidebar();
		});
		addRenderableWidget(search);

		sidebar = new RowList(font);
		sidebar.bounds(MARGIN, 30, sideWidth, bottom - 30);

		int y = height - 26;
		back = addRenderableWidget(Button.builder(Component.literal("<"), b -> goBack())
				.bounds(MARGIN, y, 20, 20).tooltip(Tooltip.create(Component.translatable("cakebook.back"))).build());
		forward = addRenderableWidget(Button.builder(Component.literal(">"), b -> goForward())
				.bounds(MARGIN + 22, y, 20, 20).tooltip(Tooltip.create(Component.translatable("cakebook.forward"))).build());
		addRenderableWidget(Button.builder(Component.translatable("cakebook.note.new"), b -> newNote())
				.bounds(MARGIN + 48, y, 74, 20).build());
		edit = addRenderableWidget(Button.builder(Component.translatable("cakebook.note.edit"), b -> editNote())
				.bounds(MARGIN + 124, y, 50, 20).build());
		delete = addRenderableWidget(Button.builder(Component.translatable("cakebook.note.delete"), b -> deleteNote())
				.bounds(MARGIN + 176, y, 50, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width - MARGIN - 70, y, 70, 20).build());

		show(current, false);
	}

	// ---------------------------------------------------------------- navigation

	/** Shows a page or note, putting the current one on the back stack. Web links ask before opening. */
	void open(Links.Target target) {
		if (target.kind() == Links.Kind.URL) {
			ConfirmLinkScreen.confirmLinkNow(this, target.path());
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
			return "# " + Component.translatable("cakebook.empty.title").getString() + "\n\n"
					+ Component.translatable("cakebook.empty.text").getString();
		}
		return "# " + Component.translatable("cakebook.missing.title").getString() + "\n\n"
				+ Component.translatable("cakebook.missing.text").getString() + "\n\n`" + target.toLink() + "`";
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
		}, font, contentWidth);
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
				rows.add(new RowList.Row(Component.translatable("cakebook.search.none").withStyle(ChatFormatting.GRAY), 0,
						null, null, null, null, false, null));
			}
			for (Search.Hit hit : hits) {
				Component where = hit.kind() == Search.Kind.NOTE ? Component.translatable("cakebook.notes")
						: Component.literal(bookTitle(hit.book(), lang));
				Component tooltip = Component.empty().append(Component.literal(hit.title()).withStyle(ChatFormatting.YELLOW))
						.append(Component.literal("  ")).append(where.copy().withStyle(ChatFormatting.GRAY))
						.append(Component.literal("\n" + hit.snippet()));
				Links.Target target = hit.target();
				rows.add(new RowList.Row(Component.literal(hit.title()), 0, null, () -> open(target), null, null,
						target.equals(current), tooltip));
			}
			sidebar.set(rows);
			return;
		}
		for (Book book : CakeBook.library().books()) {
			boolean open = EXPANDED.contains(book.id);
			Links.Target home = Links.Target.page(book.id, Book.HOME);
			rows.add(new RowList.Row(Component.literal(book.title(lang)).withStyle(ChatFormatting.BOLD), 0, icon(book.icon),
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
					rows.add(new RowList.Row(Component.literal(entry.title()), entry.depth() + 1, null,
							target == null ? () -> toggle(folderKey) : () -> {
								EXPANDED.add(folderKey);
								open(target);
							}, () -> toggle(folderKey), folderOpen, target != null && target.equals(current), null));
				} else {
					rows.add(RowList.Row.page(Component.literal(entry.title()), entry.depth() + 1, () -> open(target),
							target.equals(current)));
				}
			}
		}
		boolean notesOpen = EXPANDED.contains(":notes");
		rows.add(new RowList.Row(Component.translatable("cakebook.notes").withStyle(ChatFormatting.BOLD), 0,
				new ItemStack(Items.WRITABLE_BOOK), () -> toggle(":notes"), () -> toggle(":notes"), notesOpen, false, null));
		if (notesOpen) {
			if (notes.isEmpty()) {
				rows.add(new RowList.Row(Component.translatable("cakebook.notes.none").withStyle(ChatFormatting.GRAY), 1,
						null, null, null, null, false, null));
			}
			for (Notes.Note note : notes) {
				Links.Target target = new Links.Target(Links.Kind.NOTE, null, note.id());
				rows.add(RowList.Row.page(Component.literal(note.title()), 1, () -> open(target), target.equals(current)));
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
		if (item != null && BuiltInRegistries.ITEM.containsKey(item)) {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(item));
		}
		return new ItemStack(Items.BOOK);
	}

	// ---------------------------------------------------------------- notes

	private void newNote() {
		minecraft.setScreen(new NoteEditScreen(this, null));
	}

	private void editNote() {
		Notes.Note note = CakeBook.notes().get(current.path());
		if (note != null) {
			minecraft.setScreen(new NoteEditScreen(this, note));
		}
	}

	private void deleteNote() {
		Notes.Note note = CakeBook.notes().get(current.path());
		if (note == null) {
			return;
		}
		minecraft.setScreen(new ConfirmScreen(confirmed -> {
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
			minecraft.setScreen(this);
		}, Component.translatable("cakebook.note.delete.title"), Component.translatable("cakebook.note.delete.text", note.title())));
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
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
			goBack();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
			goForward();
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
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
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key();
		if (keyCode == GLFW.GLFW_KEY_F && event.hasControlDown()) {
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
					if (CakeBookClient.openKey() != null && CakeBookClient.openKey().matches(event)) {
						onClose();
						return true;
					}
					return super.keyPressed(event);
				}
			}
			scroll = Math.max(0, Math.min(maxScroll(), scroll));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
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
		// the page's hovers are all text (key names, web links); 26.1's GuiGraphicsExtractor.componentHoverEffect is private
		if (style != null && style.getHoverEvent() instanceof HoverEvent.ShowText(Component text)) {
			context.setTooltipForNextFrame(font, font.split(text, Math.max(width / 2, 200)), mouseX, mouseY);
		}
	}

	/** For the self-test log: what the page laid out to. */
	String summary() {
		return "page " + current.toLink() + ": " + (layout == null ? "no layout"
				: layout.lines.size() + " lines, " + layout.boxes.size() + " boxes, " + layout.pictures.size() + " pictures");
	}

	@Override
	public void onClose() {
		last = current;
		lastScroll = scroll;
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
