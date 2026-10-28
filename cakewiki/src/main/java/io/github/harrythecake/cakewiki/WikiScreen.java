package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.Block;
import io.github.harrythecake.cakewiki.doc.Document;
import io.github.harrythecake.cakewiki.doc.Image;
import io.github.harrythecake.cakewiki.doc.Inline;
import io.github.harrythecake.cakewiki.doc.Link;
import io.github.harrythecake.cakewiki.layout.Laid;
import io.github.harrythecake.cakewiki.layout.Layout;
import io.github.harrythecake.cakewiki.layout.Metrics;
import io.github.harrythecake.cakewiki.layout.ViewState;
import io.github.harrythecake.cakewiki.net.Pages;
import io.github.harrythecake.cakewiki.net.WikiException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/** The reader: a top bar (back, forward, search, refresh, browser) over a scrolling page. */
public final class WikiScreen extends Screen {
	private static final int BAR = 28, MARGIN = 6, PAD = 4, MAX_WIDTH = 560, SCROLL_STEP = 24, SUGGESTIONS = 8;

	private enum Phase { LOADING, READY, ERROR }

	private final Reader reader;
	private final Runnable onClose;

	private Phase phase = Phase.LOADING;
	private Text status = Text.empty();
	private String title;
	private Document doc;
	private boolean stale;
	private ViewState view = new ViewState();
	private Laid laid;
	private int laidWidth = -1, laidImages = -1;
	private int scroll;
	private int request;
	private Runnable retry;
	private String pendingAnchor;
	private int pendingScroll;

	private TextFieldWidget search;
	private String searchText = "";
	private ButtonWidget backButton, forwardButton, bookmarkButton, bookmarksButton, refreshButton, browserButton, retryButton;
	private boolean bookmarksOpen;
	private int bookmarksScroll;
	private List<String> suggestions = List.of();
	private String suggestedFor = "";
	private long typedAt;
	private boolean draggingBar;

	WikiScreen(Reader reader, Runnable onClose) {
		super(Text.translatable("cakewiki.title"));
		this.reader = reader;
		this.onClose = onClose;
	}

	// ---- opening pages ----------------------------------------------------------------------------------

	/** Opens a page, adding the current one to the back history. */
	void navigate(String page, String anchor) {
		if (title != null && phase == Phase.READY) {
			reader.back.push(new Reader.Visit(title, scroll));
			reader.forward.clear();
		}
		load(page, anchor, 0, false);
	}

	/** Shows the page the reader was closed on, where it was left, without touching the history. */
	void reopen(Reader.Visit visit) {
		load(visit.title(), null, visit.scroll(), false);
	}

	/** Finds the page for an item's name and opens it. */
	void lookup(String name) {
		int id = ++request;
		showStatus(Phase.LOADING, Text.translatable("cakewiki.finding", name));
		retry = () -> lookup(name);
		reader.client.resolve(name).whenComplete((found, error) -> client().execute(() -> {
			if (id != request) return;
			if (error != null) showError(name, error);
			else if (found.isPresent()) navigate(found.get(), null);
			else showStatus(Phase.ERROR, Text.translatable("cakewiki.notfound", name));
		}));
	}

	/** Shows {@code page} (from the cache when fresh, unless {@code force}) and scrolls to the anchor or {@code restoreScroll}. */
	private void load(String page, String anchor, int restoreScroll, boolean force) {
		int id = ++request;
		showStatus(Phase.LOADING, Text.translatable("cakewiki.loading", page));
		retry = () -> load(page, anchor, restoreScroll, force);
		(force ? reader.pages.refresh(page) : reader.pages.load(page))
				.thenApplyAsync(loaded -> new Converted(loaded, reader.converter.convert(loaded.page().displayTitle(), loaded.page().html())),
						Util.getMainWorkerExecutor())
				.whenComplete((converted, error) -> client().execute(() -> {
					if (id != request) return; // the player went somewhere else meanwhile
					if (error == null) {
						show(converted, anchor, restoreScroll);
						return;
					}
					Throwable cause = cause(error);
					if (cause instanceof WikiException w && w.code().equals("missingtitle")) {
						// not an exact title: let the wiki's search find the page
						reader.client.resolve(page).whenComplete((found, e2) -> client().execute(() -> {
							if (id != request) return;
							Optional<String> other = found != null ? found.filter(t -> !t.equals(page)) : Optional.empty();
							if (other.isPresent()) load(other.get(), anchor, 0, false);
							else showStatus(Phase.ERROR, Text.translatable("cakewiki.notfound", page));
						}));
					} else {
						showError(page, cause);
					}
				}));
	}

	private record Converted(Pages.Loaded loaded, Document doc) {}

	private boolean openAllOnLoad;

	boolean loading() {
		return phase == Phase.LOADING;
	}

	/** {@link DevShots}: what is on screen, for its log. */
	String devState() {
		return phase + " \"" + title + "\" scroll=" + scroll + "/" + maxScroll() + " picturesLoading=" + reader.images.pending();
	}

	/** {@link DevShots}: open every top-level collapsible of the next page shown. */
	void devOpenAll() {
		openAllOnLoad = true;
	}

	private boolean markOnLoad;

	/** {@link DevShots}: bookmark the next page shown and open the bookmarks list. */
	void devMark() {
		markOnLoad = true;
	}

	private void show(Converted c, String anchor, int restoreScroll) {
		title = c.loaded().page().title();
		doc = c.doc();
		stale = c.loaded().stale();
		view = new ViewState();
		if (openAllOnLoad) {
			for (Block b : doc.blocks()) if (b instanceof Block.Collapsible col && !view.isOpen(col)) view.toggle(col);
			openAllOnLoad = false;
		}
		if (markOnLoad) {
			markOnLoad = false;
			phase = Phase.READY;
			if (!reader.bookmarks.contains(title)) toggleBookmark();
			bookmarksOpen = true;
		}
		laid = null;
		phase = Phase.READY;
		pendingAnchor = anchor;
		pendingScroll = restoreScroll;
		reader.last = new Reader.Visit(title, restoreScroll);
		updateButtons();
	}

	private void showStatus(Phase p, Text message) {
		phase = p;
		status = message;
		updateButtons();
	}

	private void showError(String what, Throwable error) {
		CakeWiki.LOG.info("Couldn't load wiki page {}: {}", what, error.toString());
		showStatus(Phase.ERROR, Text.translatable("cakewiki.error", what, error.getMessage() != null ? error.getMessage() : error.toString()));
	}

	private static Throwable cause(Throwable t) {
		return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
	}

	private MinecraftClient client() {
		return MinecraftClient.getInstance();
	}

	// ---- widgets ----------------------------------------------------------------------------------------

	@Override
	protected void init() {
		int left = contentLeft(), right = contentRight();
		backButton = addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> goBack())
				.dimensions(left, MARGIN, 20, 20).tooltip(Tooltip.of(Text.translatable("cakewiki.back"))).build());
		forwardButton = addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> goForward())
				.dimensions(left + 22, MARGIN, 20, 20).tooltip(Tooltip.of(Text.translatable("cakewiki.forward"))).build());
		bookmarkButton = addDrawableChild(ButtonWidget.builder(Text.literal("☆"), b -> toggleBookmark())
				.dimensions(right - 86, MARGIN, 20, 20).build());
		bookmarksButton = addDrawableChild(ButtonWidget.builder(Text.literal("☰"), b -> {
					bookmarksOpen = !bookmarksOpen;
					bookmarksScroll = 0;
				})
				.dimensions(right - 64, MARGIN, 20, 20).tooltip(Tooltip.of(Text.translatable("cakewiki.bookmarks"))).build());
		refreshButton = addDrawableChild(ButtonWidget.builder(Text.literal("⟳"), b -> refresh())
				.dimensions(right - 42, MARGIN, 20, 20).tooltip(Tooltip.of(Text.translatable("cakewiki.refresh"))).build());
		browserButton = addDrawableChild(ButtonWidget.builder(Text.literal("↗"), b -> openInBrowser(reader.client.pageUrl(title)))
				.dimensions(right - 20, MARGIN, 20, 20).tooltip(Tooltip.of(Text.translatable("cakewiki.browser"))).build());
		search = new TextFieldWidget(textRenderer, left + 46, MARGIN + 1, right - 90 - (left + 46), 18, Text.translatable("cakewiki.search"));
		search.setPlaceholder(Text.translatable("cakewiki.search").formatted(Formatting.GRAY));
		search.setMaxLength(200);
		search.setText(searchText);
		search.setChangedListener(s -> {
			searchText = s;
			typedAt = Util.getMeasuringTimeMs();
		});
		addDrawableChild(search);
		retryButton = addDrawableChild(ButtonWidget.builder(Text.translatable("cakewiki.retry"), b -> { if (retry != null) retry.run(); })
				.dimensions(width / 2 - 40, BAR + 50, 80, 20).build());
		laid = null; // width may have changed
		updateButtons();
	}

	private void updateButtons() {
		if (backButton == null) return;
		backButton.active = !reader.back.isEmpty();
		forwardButton.active = !reader.forward.isEmpty();
		refreshButton.active = title != null && phase != Phase.LOADING;
		browserButton.active = title != null;
		retryButton.visible = phase == Phase.ERROR && retry != null;
		boolean marked = title != null && reader.bookmarks.contains(title);
		bookmarkButton.active = title != null && phase == Phase.READY;
		bookmarkButton.setMessage(marked ? Text.literal("★").formatted(Formatting.GOLD) : Text.literal("☆"));
		bookmarkButton.setTooltip(Tooltip.of(Text.translatable(marked ? "cakewiki.bookmark.remove" : "cakewiki.bookmark.add")));
	}

	// ---- bookmarks --------------------------------------------------------------------------------------

	private void toggleBookmark() {
		if (title == null || phase != Phase.READY) return;
		try {
			reader.bookmarks.toggle(title);
		} catch (java.io.IOException e) {
			CakeWiki.LOG.warn("Couldn't save bookmarks: {}", e.toString());
		}
		updateButtons();
	}

	private static final int ROW = 12, REMOVE_W = 12;

	private int bookmarksPanelWidth() {
		return Math.min(220, contentWidth());
	}

	private int bookmarksPanelX() {
		return bookmarksButton.getX() + bookmarksButton.getWidth() - bookmarksPanelWidth();
	}

	private int bookmarksPanelY() {
		return bookmarksButton.getY() + bookmarksButton.getHeight() + 1;
	}

	/** Rows the panel shows at once (at least 1, for the "no bookmarks" line). */
	private int bookmarkRows() {
		int fit = Math.max(1, (height - bookmarksPanelY() - MARGIN) / ROW);
		return Math.max(1, Math.min(reader.bookmarks.all().size(), fit));
	}

	/** -2 outside the open panel, -1 inside but on no bookmark, else the bookmark's index. */
	private int bookmarkAt(double mx, double my) {
		if (!bookmarksOpen) return -2;
		int x = bookmarksPanelX(), y = bookmarksPanelY();
		if (mx < x || mx >= x + bookmarksPanelWidth() || my < y || my >= y + bookmarkRows() * ROW) return -2;
		int i = bookmarksScroll + (int) ((my - y) / ROW);
		return i < reader.bookmarks.all().size() ? i : -1;
	}

	private void drawBookmarks(DrawContext ctx, int mx, int my) {
		if (!bookmarksOpen) return;
		List<Bookmarks.Bookmark> all = reader.bookmarks.all();
		int x = bookmarksPanelX(), y = bookmarksPanelY(), w = bookmarksPanelWidth(), rows = bookmarkRows();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 300); // above the page
		ctx.fill(x - 1, y - 1, x + w + 1, y + rows * ROW + 1, 0xFF000000);
		if (all.isEmpty()) {
			ctx.fill(x, y, x + w, y + ROW, 0xFF202030);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(I18n.translate("cakewiki.bookmarks.empty"), w - 6), x + 3, y + 2, 0xFF9C9CAC, false);
		}
		int hover = bookmarkAt(mx, my);
		for (int r = 0; r < rows && bookmarksScroll + r < all.size(); r++) {
			int i = bookmarksScroll + r, ry = y + r * ROW;
			boolean onRemove = hover == i && mx >= x + w - REMOVE_W;
			ctx.fill(x, ry, x + w, ry + ROW, hover == i ? 0xFF3A3A60 : 0xFF202030);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(all.get(i).title(), w - REMOVE_W - 6), x + 3, ry + 2, 0xFFE0E0E0, false);
			ctx.drawText(textRenderer, "✕", x + w - REMOVE_W + 2, ry + 2, onRemove ? 0xFFFF7070 : 0xFF808090, false);
		}
		if (all.size() > rows) { // scroll position
			int h = rows * ROW, barH = Math.max(6, h * rows / all.size());
			int barY = y + (h - barH) * bookmarksScroll / Math.max(1, all.size() - rows);
			ctx.fill(x + w - 1, barY, x + w, barY + barH, 0xFF777777);
		}
		ctx.getMatrices().pop();
		if (hover >= 0) {
			ctx.drawTooltip(textRenderer, List.of(mx >= x + w - REMOVE_W ? Text.translatable("cakewiki.bookmarks.delete")
					: Text.literal(all.get(hover).title())), mx, my);
		}
	}

	/** A click while the panel is open: true if the panel used it. */
	private boolean clickBookmarks(double mx, double my, int button) {
		int i = bookmarkAt(mx, my);
		if (i == -2) {
			if (!bookmarksButton.isMouseOver(mx, my)) bookmarksOpen = false; // a click elsewhere closes it
			return false;
		}
		if (i >= 0 && button == 0) {
			String page = reader.bookmarks.all().get(i).title();
			if (mx >= bookmarksPanelX() + bookmarksPanelWidth() - REMOVE_W) {
				try {
					reader.bookmarks.remove(page);
				} catch (java.io.IOException e) {
					CakeWiki.LOG.warn("Couldn't save bookmarks: {}", e.toString());
				}
				bookmarksScroll = Math.max(0, Math.min(bookmarksScroll, reader.bookmarks.all().size() - bookmarkRows()));
				updateButtons();
			} else {
				bookmarksOpen = false;
				navigate(page, null);
			}
		}
		return true;
	}

	private void goBack() {
		if (reader.back.isEmpty()) return;
		if (title != null) reader.forward.push(new Reader.Visit(title, scroll));
		Reader.Visit v = reader.back.pop();
		load(v.title(), null, v.scroll(), false);
	}

	private void goForward() {
		if (reader.forward.isEmpty()) return;
		if (title != null) reader.back.push(new Reader.Visit(title, scroll));
		Reader.Visit v = reader.forward.pop();
		load(v.title(), null, v.scroll(), false);
	}

	private void refresh() {
		if (title != null) load(title, null, scroll, true);
	}

	private void submitSearch() {
		String q = searchText.trim();
		if (q.isEmpty()) return;
		suggestions = List.of();
		search.setFocused(false);
		setFocused(null);
		String exact = null;
		if (suggestedFor.equals(q)) for (String s : suggestions) if (s.equalsIgnoreCase(q)) exact = s;
		if (exact != null) navigate(exact, null);
		else lookup(q);
	}

	@Override
	public void tick() {
		// ask for suggestions once typing pauses
		String q = searchText.trim();
		if (search != null && search.isFocused() && !q.equals(suggestedFor) && Util.getMeasuringTimeMs() - typedAt > 250) {
			suggestedFor = q;
			if (q.isEmpty()) {
				suggestions = List.of();
			} else {
				reader.client.suggest(q, SUGGESTIONS).whenComplete((list, e) -> client().execute(() -> {
					if (q.equals(suggestedFor)) suggestions = list != null ? list : List.of();
				}));
			}
		}
	}

	// ---- geometry ---------------------------------------------------------------------------------------

	private int contentWidth() {
		return Math.min(width - 2 * MARGIN - 8, MAX_WIDTH);
	}

	private int contentLeft() {
		return (width - contentWidth()) / 2;
	}

	private int contentRight() {
		return contentLeft() + contentWidth();
	}

	private int contentTop() {
		return BAR + 2;
	}

	private int contentBottom() {
		return height - MARGIN;
	}

	private int viewHeight() {
		return contentBottom() - contentTop() - 2 * PAD;
	}

	private int maxScroll() {
		return laid == null ? 0 : Math.max(0, laid.height() - viewHeight());
	}

	private boolean inContent(double mx, double my) {
		return mx >= contentLeft() && mx < contentRight() && my >= contentTop() && my < contentBottom();
	}

	// ---- layout -----------------------------------------------------------------------------------------

	private final Metrics metrics = new Metrics() {
		@Override
		public int width(String text, Inline.Style style) {
			return style.bold() ? textRenderer.getWidth(Text.literal(text).formatted(Formatting.BOLD)) : textRenderer.getWidth(text);
		}

		@Override
		public int lineHeight() {
			return textRenderer.fontHeight + 1;
		}

		@Override
		public int[] naturalSize(Image image) {
			return reader.images.naturalSize(image);
		}

		@Override
		public String label(String key) {
			return I18n.translate(key);
		}
	};

	private void ensureLayout() {
		if (doc == null) return;
		int w = contentWidth() - 2 * PAD;
		boolean imagesChanged = laid != null && laid.unknownSizes() && laidImages != reader.images.version();
		if (laid != null && laidWidth == w && !imagesChanged) return;
		laidImages = reader.images.version();
		laid = Layout.page(header(), doc, w, metrics, view);
		laidWidth = w;
		if (pendingAnchor != null) {
			Integer y = laid.anchors().get(pendingAnchor);
			if (y == null) y = laid.anchors().get(pendingAnchor.replace(' ', '_'));
			if (y != null) scroll = y;
			pendingAnchor = null;
		} else if (pendingScroll >= 0) {
			scroll = pendingScroll;
			pendingScroll = -1;
		}
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	/** Title, source and licence (CC BY-SA asks for both), and a note when showing a saved copy. */
	private List<Block> header() {
		List<Block> h = new ArrayList<>();
		h.add(new Block.Heading(1, List.of(new Inline.Text(doc.title(), Inline.Style.PLAIN)), null));
		Inline.Style muted = Inline.Style.PLAIN.withColor(Layout.MUTED & 0xFFFFFF);
		h.add(new Block.Paragraph(List.of(
				new Inline.Text(I18n.translate("cakewiki.attribution.wiki"), muted.withLink(new Link.External(reader.client.pageUrl(title)))),
				new Inline.Text(" · ", muted),
				new Inline.Text(I18n.translate("cakewiki.attribution.license"), muted.withLink(new Link.External(Reader.LICENSE_URL))))));
		if (stale) h.add(new Block.Paragraph(List.of(new Inline.Text(I18n.translate("cakewiki.stale"), Inline.Style.PLAIN.withColor(0xFFB347)))));
		return h;
	}

	// ---- drawing ----------------------------------------------------------------------------------------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta); // background, then the bar's widgets
		int left = contentLeft(), right = contentRight(), top = contentTop(), bottom = contentBottom();
		ctx.fill(left - 1, top - 1, right + 1, bottom + 1, Layout.BORDER);
		ctx.fill(left, top, right, bottom, Layout.PAGE_BG);

		Link hovered = null;
		if (phase == Phase.READY) {
			ensureLayout();
			int ox = left + PAD, oy = top + PAD - scroll;
			if (inContent(mouseX, mouseY) && !overSuggestions(mouseX, mouseY) && bookmarkAt(mouseX, mouseY) == -2) {
				hovered = laid.linkAt(mouseX - ox, mouseY - oy);
			}
			ctx.enableScissor(left, top, right, bottom);
			drawPage(ctx, ox, oy, top - oy, bottom - oy, hovered);
			ctx.disableScissor();
			drawScrollbar(ctx);
		} else {
			List<OrderedText> lines = textRenderer.wrapLines(status, contentWidth() - 20);
			int y = top + 24;
			for (OrderedText line : lines) {
				ctx.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, phase == Phase.ERROR ? 0xFFFF8080 : 0xFFCCCCCC);
				y += 11;
			}
			retryButton.setY(y + 8);
		}
		retryButton.render(ctx, mouseX, mouseY, delta); // drawn again so it sits above the page area

		drawSuggestions(ctx, mouseX, mouseY);
		if (hovered != null) ctx.drawTooltip(textRenderer, tooltip(hovered), mouseX, mouseY);
		drawBookmarks(ctx, mouseX, mouseY);
	}

	private void drawPage(DrawContext ctx, int ox, int oy, int visibleTop, int visibleBottom, Link hovered) {
		for (Laid.Item it : laid.items()) {
			if (it.y() + it.h() < visibleTop || it.y() > visibleBottom) continue;
			int x = ox + it.x(), y = oy + it.y();
			if (it instanceof Laid.Fill f) {
				ctx.fill(x, y, x + f.w(), y + f.h(), f.argb());
			} else if (it instanceof Laid.Run r) {
				ctx.drawText(textRenderer, Text.literal(r.text()).setStyle(mcStyle(r.style())), x, y, r.color(), false);
				if (hovered != null && hovered.equals(r.style().link())) ctx.fill(x, y + 9, x + r.w(), y + 10, r.color());
			} else if (it instanceof Laid.Pic p) {
				reader.images.draw(ctx, p.image(), x, y, p.w(), p.h());
			}
		}
	}

	private static Style mcStyle(Inline.Style s) {
		return Style.EMPTY.withBold(s.bold()).withItalic(s.italic()).withUnderline(s.underline()).withStrikethrough(s.strikethrough());
	}

	private void drawScrollbar(DrawContext ctx) {
		int max = maxScroll();
		if (max <= 0) return;
		int x = contentRight() + 2, top = contentTop(), h = contentBottom() - top;
		int barH = Math.max(16, h * h / (h + max));
		int barY = top + (int) ((long) (h - barH) * scroll / max);
		ctx.fill(x, top, x + 4, top + h, 0x40000000);
		ctx.fill(x, barY, x + 4, barY + barH, draggingBar ? 0xFFAAAAAA : 0xFF777777);
	}

	private List<Text> tooltip(Link link) {
		if (link instanceof Link.Page p) {
			String t = p.title().isEmpty() ? title : p.title();
			return List.of(Text.literal(p.anchor() != null ? t + " § " + p.anchor().replace('_', ' ') : t));
		}
		if (link instanceof Link.External e) return List.of(Text.literal(e.url()).formatted(Formatting.GRAY));
		return List.of(Text.translatable("cakewiki.missing").formatted(Formatting.RED));
	}

	// ---- suggestions dropdown ---------------------------------------------------------------------------

	private boolean suggestionsVisible() {
		return search != null && search.isFocused() && !suggestions.isEmpty();
	}

	private int suggestionAt(double mx, double my) {
		if (!suggestionsVisible()) return -1;
		int x = search.getX(), y = search.getY() + search.getHeight() + 1;
		if (mx < x || mx >= x + search.getWidth() || my < y) return -1;
		int i = (int) ((my - y) / 12);
		return i < suggestions.size() ? i : -1;
	}

	private boolean overSuggestions(double mx, double my) {
		return suggestionAt(mx, my) >= 0;
	}

	private void drawSuggestions(DrawContext ctx, int mx, int my) {
		if (!suggestionsVisible()) return;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 300); // above the page
		int x = search.getX(), y = search.getY() + search.getHeight() + 1, w = search.getWidth();
		ctx.fill(x - 1, y - 1, x + w + 1, y + suggestions.size() * 12 + 1, 0xFF000000);
		int hover = suggestionAt(mx, my);
		for (int i = 0; i < suggestions.size(); i++) {
			ctx.fill(x, y + i * 12, x + w, y + i * 12 + 12, i == hover ? 0xFF3A3A60 : 0xFF202030);
			String s = textRenderer.trimToWidth(suggestions.get(i), w - 6);
			ctx.drawText(textRenderer, s, x + 3, y + i * 12 + 2, 0xFFE0E0E0, false);
		}
		ctx.getMatrices().pop();
	}

	// ---- input ------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (bookmarksOpen && clickBookmarks(mx, my, button)) return true;
		int s = suggestionAt(mx, my);
		if (s >= 0 && button == 0) {
			String page = suggestions.get(s);
			suggestions = List.of();
			search.setFocused(false);
			setFocused(null);
			navigate(page, null);
			return true;
		}
		if (button == 3) { goBack(); return true; } // mouse side buttons
		if (button == 4) { goForward(); return true; }
		if (super.mouseClicked(mx, my, button)) return true;
		if (search.isFocused() && !search.isMouseOver(mx, my)) {
			search.setFocused(false);
			setFocused(null);
		}
		if (phase != Phase.READY || laid == null) return false;
		int barX = contentRight() + 2;
		if (button == 0 && maxScroll() > 0 && mx >= barX && mx < barX + 4 && my >= contentTop() && my < contentBottom()) {
			draggingBar = true;
			scrollToBar(my);
			return true;
		}
		if (button != 0 || !inContent(mx, my)) return false;
		double px = mx - contentLeft() - PAD, py = my - contentTop() - PAD + scroll;
		Link link = laid.linkAt(px, py);
		if (link != null) {
			follow(link);
			return true;
		}
		Laid.Control control = laid.controlAt(px, py);
		if (control != null) {
			view.apply(control);
			laid = null; // lay out again, same scroll
			pendingScroll = scroll;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingBar) {
			scrollToBar(my);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		draggingBar = false;
		return super.mouseReleased(mx, my, button);
	}

	private void scrollToBar(double my) {
		int top = contentTop(), h = contentBottom() - top;
		scroll = (int) Math.max(0, Math.min(maxScroll(), (my - top) / h * (laid.height())));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (bookmarkAt(mx, my) != -2) {
			int max = Math.max(0, reader.bookmarks.all().size() - bookmarkRows());
			bookmarksScroll = Math.max(0, Math.min(max, bookmarksScroll - (int) Math.signum(vertical)));
			return true;
		}
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * SCROLL_STEP)));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (search.isFocused()) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				submitSearch();
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) { // first Esc leaves the search box
				search.setFocused(false);
				setFocused(null);
				return true;
			}
			return super.keyPressed(key, scan, modifiers);
		}
		if (bookmarksOpen && key == GLFW.GLFW_KEY_ESCAPE) { // first Esc closes the bookmarks list
			bookmarksOpen = false;
			return true;
		}
		if (key == GLFW.GLFW_KEY_D && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
			toggleBookmark();
			return true;
		}
		if (CakeWiki.openKey.matchesKey(key, scan)) {
			close();
			return true;
		}
		int page = Math.max(SCROLL_STEP, viewHeight() - 20);
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> goBack();
			case GLFW.GLFW_KEY_UP -> scroll -= SCROLL_STEP;
			case GLFW.GLFW_KEY_DOWN -> scroll += SCROLL_STEP;
			case GLFW.GLFW_KEY_PAGE_UP -> scroll -= page;
			case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> scroll += page;
			case GLFW.GLFW_KEY_HOME -> scroll = 0;
			case GLFW.GLFW_KEY_END -> scroll = maxScroll();
			default -> {
				if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
					setFocused(search);
					search.setFocused(true);
					return true;
				}
				return super.keyPressed(key, scan, modifiers);
			}
		}
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
		return true;
	}

	private void follow(Link link) {
		if (link instanceof Link.Page p) {
			if (p.title().isEmpty() || p.title().equals(title)) {
				Integer y = p.anchor() != null && laid != null ? laid.anchors().get(p.anchor()) : null;
				if (y != null) scroll = Math.min(y, maxScroll());
			} else if (p.title().startsWith("Special:") || p.title().startsWith("Category:")) {
				openInBrowser(reader.client.pageUrl(p.title())); // the parse API doesn't list category members
			} else {
				navigate(p.title(), p.anchor());
			}
		} else if (link instanceof Link.External e) {
			openInBrowser(e.url());
		}
		// Link.Missing: nothing to open; the tooltip says why
	}

	private void openInBrowser(String url) {
		MinecraftClient mc = client();
		mc.setScreen(new ConfirmLinkScreen(open -> {
			if (open) Util.getOperatingSystem().open(url);
			mc.setScreen(this);
		}, url, true));
	}

	@Override
	public void close() {
		if (title != null) reader.last = new Reader.Visit(title, scroll);
		onClose.run();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
