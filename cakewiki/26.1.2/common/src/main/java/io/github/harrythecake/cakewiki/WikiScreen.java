package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.Block;
import io.github.harrythecake.cakewiki.doc.Document;
import io.github.harrythecake.cakewiki.doc.Image;
import io.github.harrythecake.cakewiki.doc.Inline;
import io.github.harrythecake.cakewiki.doc.Link;
import io.github.harrythecake.cakewiki.layout.Contents;
import io.github.harrythecake.cakewiki.layout.Laid;
import io.github.harrythecake.cakewiki.layout.Layout;
import io.github.harrythecake.cakewiki.layout.Metrics;
import io.github.harrythecake.cakewiki.layout.ViewState;
import io.github.harrythecake.cakewiki.net.NavItem;
import io.github.harrythecake.cakewiki.net.Pages;
import io.github.harrythecake.cakewiki.net.WikiException;
import io.github.harrythecake.cakewiki.translate.PageTranslator;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * The reader: a top bar (contents, back, forward, site, search, refresh, browser) over a scrolling page, with the
 * site's contents in a panel on the left when it has them.
 */
public final class WikiScreen extends Screen {
	private static final int BAR = 28, MARGIN = 6, PAD = 4, MAX_WIDTH = 560, SCROLL_STEP = 24, SUGGESTIONS = 8;

	private enum Phase { LOADING, READY, ERROR }

	private final Reader reader;
	private final Runnable onClose;

	private Phase phase = Phase.LOADING;
	private Component status = Component.empty();
	private String title;
	/** The site {@link #title} is on; set with it. */
	private Reader.Source shown;
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

	private EditBox search;
	private String searchText = "";
	private Button backButton, forwardButton, bookmarkButton, bookmarksButton, refreshButton, browserButton, retryButton;
	private Button translateButton, siteButton, contentsButton;
	/** The shown page's site contents (sidebar), or null when it has none. */
	private Contents contents;
	/** Scroll the contents panel to the shown page at the next drawing. */
	private boolean revealPending;
	private List<Component> contentsTooltip;
	/** This page's translation (CakeText), made at its first layout; null when not translating. */
	private PageTranslator words;
	private boolean laidTranslated;
	private long laidAt;
	private boolean bookmarksOpen;
	private int bookmarksScroll;
	private List<String> suggestions = List.of();
	private String suggestedFor = "";
	private long typedAt;
	private boolean draggingBar;

	WikiScreen(Reader reader, Runnable onClose) {
		super(Component.translatable("cakewiki.title"));
		this.reader = reader;
		this.onClose = onClose;
	}

	// ---- opening pages ----------------------------------------------------------------------------------

	/** Opens a page of the current site, adding the shown one to the back history. */
	void navigate(String page, String anchor) {
		navigate(reader.source(), page, anchor);
	}

	/** Opens a page of {@code site}, adding the shown one to the back history. */
	void navigate(Reader.Source site, String page, String anchor) {
		if (title != null && phase == Phase.READY) {
			reader.back.push(new Reader.Visit(shown.id(), title, scroll));
			reader.forward.clear();
		}
		load(site, page, anchor, 0, false);
	}

	/** Opens the main page of {@code site}. */
	void home(Reader.Source site) {
		navigate(site, site.info().mainPage(), null);
	}

	/** Shows the page the reader was closed on, where it was left, without touching the history. */
	void reopen(Reader.Visit visit) {
		Reader.Source site = reader.source(visit.site());
		if (site == null) home(reader.home()); // its site was taken out of the site list
		else load(site, visit.title(), null, visit.scroll(), false);
	}

	/** Finds the page for a name (an item's, the search box's) on the current site and opens it. */
	void lookup(String name) {
		Reader.Source site = reader.source();
		int id = ++request;
		showStatus(Phase.LOADING, Component.translatable("cakewiki.finding", name));
		retry = () -> lookup(name);
		site.site().resolve(name).whenComplete((found, error) -> client().execute(() -> {
			if (id != request) return;
			if (error != null) showError(name, error);
			else if (found.isPresent()) navigate(site, found.get(), null);
			else showStatus(Phase.ERROR, Component.translatable("cakewiki.notfound", name));
		}));
	}

	/**
	 * Shows {@code page} of {@code site} (from the cache when fresh, unless {@code force}) and scrolls to the anchor or
	 * {@code restoreScroll}. The reader is on that site from now on, even if the page fails.
	 */
	private void load(Reader.Source site, String page, String anchor, int restoreScroll, boolean force) {
		int id = ++request;
		reader.use(site);
		showStatus(Phase.LOADING, Component.translatable("cakewiki.loading", page));
		retry = () -> load(site, page, anchor, restoreScroll, force);
		(force ? site.pages().refresh(page) : site.pages().load(page))
				.thenApplyAsync(loaded -> new Converted(site, loaded, site.converter().convert(loaded.page().displayTitle(), loaded.page().html())),
						Util.backgroundExecutor())
				.whenComplete((converted, error) -> client().execute(() -> {
					if (id != request) return; // the player went somewhere else meanwhile
					if (error == null) {
						show(converted, anchor, restoreScroll);
						return;
					}
					Throwable cause = cause(error);
					if (cause instanceof WikiException w && w.code().equals("missingtitle")) {
						// not an exact title: let the site's search find the page
						site.site().resolve(page).whenComplete((found, e2) -> client().execute(() -> {
							if (id != request) return;
							Optional<String> other = found != null ? found.filter(t -> !t.equals(page)) : Optional.empty();
							if (other.isPresent()) load(site, other.get(), anchor, 0, false);
							else showStatus(Phase.ERROR, Component.translatable("cakewiki.notfound", page));
						}));
					} else {
						showError(page, cause);
					}
				}));
	}

	private record Converted(Reader.Source site, Pages.Loaded loaded, Document doc) {}

	private boolean openAllOnLoad;

	boolean loading() {
		return phase == Phase.LOADING;
	}

	/** {@link DevShots}: a page is shown (not loading, not an error). */
	boolean devReady() {
		return phase == Phase.READY && laid != null;
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
		shown = c.site();
		doc = c.doc();
		stale = c.loaded().stale();
		view = new ViewState();
		words = null;
		if (openAllOnLoad) {
			for (Block b : doc.blocks()) if (b instanceof Block.Collapsible col && !view.isOpen(col)) view.toggle(col);
			openAllOnLoad = false;
		}
		if (markOnLoad) {
			markOnLoad = false;
			phase = Phase.READY;
			if (!reader.bookmarks.contains(shown.id(), title)) toggleBookmark();
			bookmarksOpen = true;
		}
		laid = null;
		phase = Phase.READY;
		pendingAnchor = anchor;
		pendingScroll = restoreScroll;
		reader.remember(new Reader.Visit(shown.id(), title, restoreScroll));
		updateButtons();
		updateContents();
	}

	private void showStatus(Phase p, Component message) {
		phase = p;
		status = message;
		updateButtons();
	}

	private void showError(String what, Throwable error) {
		CakeWiki.LOG.info("Couldn't load wiki page {}: {}", what, error.toString());
		showStatus(Phase.ERROR, Component.translatable("cakewiki.error", what, error.getMessage() != null ? error.getMessage() : error.toString()));
	}

	private static Throwable cause(Throwable t) {
		return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
	}

	private Minecraft client() {
		return Minecraft.getInstance();
	}

	// ---- widgets ----------------------------------------------------------------------------------------

	@Override
	protected void init() {
		int left = barLeft(), right = contentRight();
		if (contents != null) { // only for pages whose site has contents
			contentsButton = addRenderableWidget(Button.builder(Component.literal("▤"), b -> {
						reader.contentsOpen = !reader.contentsOpen;
						revealPending = true;
						rebuildWidgets(); // the page gets narrower or wider
					})
					.bounds(left, MARGIN, 20, 20).tooltip(Tooltip.create(Component.translatable("cakewiki.contents"))).build());
			left += 22;
		}
		backButton = addRenderableWidget(Button.builder(Component.literal("◀"), b -> goBack())
				.bounds(left, MARGIN, 20, 20).tooltip(Tooltip.create(Component.translatable("cakewiki.back"))).build());
		forwardButton = addRenderableWidget(Button.builder(Component.literal("▶"), b -> goForward())
				.bounds(left + 22, MARGIN, 20, 20).tooltip(Tooltip.create(Component.translatable("cakewiki.forward"))).build());
		bookmarkButton = addRenderableWidget(Button.builder(Component.literal("☆"), b -> toggleBookmark())
				.bounds(right - 86, MARGIN, 20, 20).build());
		// only with CakeText Translator installed: translation on/off
		int searchRight = right - 90;
		if (reader.cakeText != null) {
			translateButton = addRenderableWidget(Button.builder(Component.literal("文"), b -> {
						reader.translate = !reader.translate;
						laid = null;
						updateButtons();
					})
					.bounds(right - 108, MARGIN, 20, 20).build());
			searchRight -= 22;
		}
		bookmarksButton = addRenderableWidget(Button.builder(Component.literal("☰"), b -> {
					bookmarksOpen = !bookmarksOpen;
					bookmarksScroll = 0;
				})
				.bounds(right - 64, MARGIN, 20, 20).tooltip(Tooltip.create(Component.translatable("cakewiki.bookmarks"))).build());
		refreshButton = addRenderableWidget(Button.builder(Component.literal("⟳"), b -> refresh())
				.bounds(right - 42, MARGIN, 20, 20).tooltip(Tooltip.create(Component.translatable("cakewiki.refresh"))).build());
		browserButton = addRenderableWidget(Button.builder(Component.literal("↗"), b -> openInBrowser(shown.site().pageUrl(title)))
				.bounds(right - 20, MARGIN, 20, 20).tooltip(Tooltip.create(Component.translatable("cakewiki.browser"))).build());
		// only with more than one site in config/cakewiki/sites.json: the next site's main page
		int searchLeft = left + 46;
		if (reader.offered().size() > 1) { // the sites offered on this server
			siteButton = addRenderableWidget(Button.builder(Component.literal("⇄"), b -> home(reader.next()))
					.bounds(left + 44, MARGIN, 20, 20).build());
			searchLeft += 22;
		}
		search = new EditBox(font, searchLeft, MARGIN + 1, searchRight - searchLeft, 18, Component.translatable("cakewiki.search"));
		search.setHint(Component.translatable("cakewiki.search").withStyle(ChatFormatting.GRAY));
		search.setMaxLength(200);
		search.setValue(searchText);
		search.setResponder(s -> {
			searchText = s;
			typedAt = Util.getMillis();
		});
		addRenderableWidget(search);
		retryButton = addRenderableWidget(Button.builder(Component.translatable("cakewiki.retry"), b -> { if (retry != null) retry.run(); })
				.bounds(width / 2 - 40, BAR + 50, 80, 20).build());
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
		boolean marked = title != null && reader.bookmarks.contains(shown.id(), title);
		bookmarkButton.active = title != null && phase == Phase.READY;
		bookmarkButton.setMessage(marked ? Component.literal("★").withStyle(ChatFormatting.GOLD) : Component.literal("☆"));
		bookmarkButton.setTooltip(Tooltip.create(Component.translatable(marked ? "cakewiki.bookmark.remove" : "cakewiki.bookmark.add")));
		if (contentsButton != null) {
			contentsButton.setMessage(reader.contentsOpen ? Component.literal("▤").withStyle(ChatFormatting.GOLD) : Component.literal("▤"));
		}
		if (siteButton != null) {
			siteButton.setTooltip(Tooltip.create(Component.translatable("cakewiki.site", reader.source().info().name(), reader.next().info().name())));
		}
		if (translateButton != null) {
			boolean on = translating();
			translateButton.setMessage(on ? Component.literal("文").withStyle(ChatFormatting.GOLD) : Component.literal("A"));
			translateButton.setTooltip(Tooltip.create(Component.translatable(on ? "cakewiki.translate.on"
					: reader.translate ? "cakewiki.translate.off_in_cakeText" : "cakewiki.translate.off")));
		}
	}

	/** Pages are shown translated: CakeText is there, on for wiki pages, and the player hasn't turned it off here. */
	private boolean translating() {
		return reader.cakeText != null && reader.translate && reader.cakeText.active();
	}

	// ---- bookmarks --------------------------------------------------------------------------------------

	private void toggleBookmark() {
		if (title == null || phase != Phase.READY) return;
		try {
			reader.bookmarks.toggle(shown.id(), title);
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
		return Math.max(1, Math.min(reader.bookmarks.all(reader.source().id()).size(), fit));
	}

	/** -2 outside the open panel, -1 inside but on no bookmark, else the bookmark's index. */
	private int bookmarkAt(double mx, double my) {
		if (!bookmarksOpen) return -2;
		int x = bookmarksPanelX(), y = bookmarksPanelY();
		if (mx < x || mx >= x + bookmarksPanelWidth() || my < y || my >= y + bookmarkRows() * ROW) return -2;
		int i = bookmarksScroll + (int) ((my - y) / ROW);
		return i < reader.bookmarks.all(reader.source().id()).size() ? i : -1;
	}

	private void drawBookmarks(GuiGraphicsExtractor ctx, int mx, int my) {
		if (!bookmarksOpen) return;
		List<Bookmarks.Bookmark> all = reader.bookmarks.all(reader.source().id());
		int x = bookmarksPanelX(), y = bookmarksPanelY(), w = bookmarksPanelWidth(), rows = bookmarkRows();
		ctx.nextStratum(); // above the page
		ctx.fill(x - 1, y - 1, x + w + 1, y + rows * ROW + 1, 0xFF000000);
		if (all.isEmpty()) {
			ctx.fill(x, y, x + w, y + ROW, 0xFF202030);
			ctx.text(font, font.plainSubstrByWidth(I18n.get("cakewiki.bookmarks.empty"), w - 6), x + 3, y + 2, 0xFF9C9CAC, false);
		}
		int hover = bookmarkAt(mx, my);
		for (int r = 0; r < rows && bookmarksScroll + r < all.size(); r++) {
			int i = bookmarksScroll + r, ry = y + r * ROW;
			boolean onRemove = hover == i && mx >= x + w - REMOVE_W;
			ctx.fill(x, ry, x + w, ry + ROW, hover == i ? 0xFF3A3A60 : 0xFF202030);
			ctx.text(font, font.plainSubstrByWidth(all.get(i).title(), w - REMOVE_W - 6), x + 3, ry + 2, 0xFFE0E0E0, false);
			ctx.text(font, "✕", x + w - REMOVE_W + 2, ry + 2, onRemove ? 0xFFFF7070 : 0xFF808090, false);
		}
		if (all.size() > rows) { // scroll position
			int h = rows * ROW, barH = Math.max(6, h * rows / all.size());
			int barY = y + (h - barH) * bookmarksScroll / Math.max(1, all.size() - rows);
			ctx.fill(x + w - 1, barY, x + w, barY + barH, 0xFF777777);
		}
		if (hover >= 0) {
			ctx.setComponentTooltipForNextFrame(font, List.of(mx >= x + w - REMOVE_W ? Component.translatable("cakewiki.bookmarks.delete")
					: Component.literal(all.get(hover).title())), mx, my);
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
			String page = reader.bookmarks.all(reader.source().id()).get(i).title();
			if (mx >= bookmarksPanelX() + bookmarksPanelWidth() - REMOVE_W) {
				try {
					reader.bookmarks.remove(reader.source().id(), page);
				} catch (java.io.IOException e) {
					CakeWiki.LOG.warn("Couldn't save bookmarks: {}", e.toString());
				}
				bookmarksScroll = Math.max(0, Math.min(bookmarksScroll, reader.bookmarks.all(reader.source().id()).size() - bookmarkRows()));
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
		if (title != null) reader.forward.push(new Reader.Visit(shown.id(), title, scroll));
		visit(reader.back.pop());
	}

	private void goForward() {
		if (reader.forward.isEmpty()) return;
		if (title != null) reader.back.push(new Reader.Visit(shown.id(), title, scroll));
		visit(reader.forward.pop());
	}

	/** A page from the history; one whose site was taken out of the site list opens the default site. */
	private void visit(Reader.Visit v) {
		Reader.Source site = reader.source(v.site());
		if (site != null) load(site, v.title(), null, v.scroll(), false);
		else home(reader.home());
	}

	private void refresh() {
		if (title != null) load(shown, title, null, scroll, true);
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
		if (search != null && search.isFocused() && !q.equals(suggestedFor) && Util.getMillis() - typedAt > 250) {
			suggestedFor = q;
			if (q.isEmpty()) {
				suggestions = List.of();
			} else {
				reader.source().site().suggest(q, SUGGESTIONS).whenComplete((list, e) -> client().execute(() -> {
					if (q.equals(suggestedFor)) suggestions = list != null ? list : List.of();
				}));
			}
		}
	}

	// ---- geometry ---------------------------------------------------------------------------------------

	private boolean contentsShown() {
		return contents != null && reader.contentsOpen;
	}

	private int panelWidth() {
		return Math.max(110, Math.min(180, width / 4));
	}

	private int contentWidth() {
		return Math.min(width - 2 * MARGIN - 8 - (contentsShown() ? panelWidth() + 8 : 0), MAX_WIDTH);
	}

	/** Centred; right of the contents panel when it is shown. */
	private int contentLeft() {
		int centred = (width - contentWidth()) / 2;
		return contentsShown() ? Math.max(centred, MARGIN + panelWidth() + 8) : centred;
	}

	/** The bar spans the panel and the page. */
	private int barLeft() {
		return contentsShown() ? MARGIN : contentLeft();
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
			return style.bold() ? font.width(Component.literal(text).withStyle(ChatFormatting.BOLD)) : font.width(text);
		}

		@Override
		public int lineHeight() {
			return font.lineHeight + 1;
		}

		@Override
		public int[] naturalSize(Image image) {
			return reader.images.naturalSize(image);
		}

		@Override
		public String label(String key) {
			return I18n.get(key);
		}
	};

	private void ensureLayout() {
		if (doc == null) return;
		int w = contentWidth() - 2 * PAD;
		boolean imagesChanged = laid != null && laid.unknownSizes() && laidImages != reader.images.version();
		boolean translating = translating();
		if (translating && words == null) words = new PageTranslator(reader.cakeText);
		// translations arriving: lay out again, at most twice a second
		long now = Util.getMillis();
		boolean translated = laid != null && translating && now - laidAt > 500 && words.refresh();
		if (laid != null && laidWidth == w && !imagesChanged && !translated && laidTranslated == translating) return;
		laidImages = reader.images.version();
		laid = Layout.page(header(), doc, w, metrics, view, translating ? words : UnaryOperator.identity());
		laidWidth = w;
		laidAt = now;
		if (laidTranslated != translating) {
			laidTranslated = translating;
			updateButtons();
		}
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

	/** Title, source and licence (CC BY-SA asks for both; from the site list), and a note when showing a saved copy. */
	private List<Block> header() {
		List<Block> h = new ArrayList<>();
		h.add(new Block.Heading(1, List.of(new Inline.Text(doc.title(), Inline.Style.PLAIN)), null));
		Inline.Style muted = Inline.Style.PLAIN.withColor(Layout.MUTED & 0xFFFFFF);
		var info = shown.info();
		List<Inline> source = new ArrayList<>();
		source.add(new Inline.Text(info.name() + " ↗", muted.withLink(new Link.External(shown.site().pageUrl(title)))));
		if (!info.license().isEmpty()) {
			source.add(new Inline.Text(" · ", muted));
			source.add(info.licenseUrl().isEmpty() ? new Inline.Text(info.license(), muted)
					: new Inline.Text(info.license() + " ↗", muted.withLink(new Link.External(info.licenseUrl()))));
		}
		h.add(new Block.Paragraph(source));
		if (stale) h.add(new Block.Paragraph(List.of(new Inline.Text(I18n.get("cakewiki.stale"), Inline.Style.PLAIN.withColor(0xFFB347)))));
		if (translating()) h.add(new Block.Paragraph(List.of(new Inline.Text(I18n.get("cakewiki.translated"), muted))));
		return h;
	}

	// ---- drawing ----------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta); // background, then the bar's widgets
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
			List<FormattedCharSequence> lines = font.split(status, contentWidth() - 20);
			int y = top + 24;
			for (FormattedCharSequence line : lines) {
				ctx.centeredText(font, line, width / 2, y, phase == Phase.ERROR ? 0xFFFF8080 : 0xFFCCCCCC);
				y += 11;
			}
			retryButton.setY(y + 8);
		}
		retryButton.extractRenderState(ctx, mouseX, mouseY, delta); // drawn again so it sits above the page area

		contentsTooltip = null;
		drawContents(ctx, mouseX, mouseY);
		drawSuggestions(ctx, mouseX, mouseY);
		if (hovered != null) ctx.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
		else if (contentsTooltip != null && !overSuggestions(mouseX, mouseY)) ctx.setComponentTooltipForNextFrame(font, contentsTooltip, mouseX, mouseY);
		drawBookmarks(ctx, mouseX, mouseY);
	}

	private void drawPage(GuiGraphicsExtractor ctx, int ox, int oy, int visibleTop, int visibleBottom, Link hovered) {
		for (Laid.Item it : laid.items()) {
			if (it.y() + it.h() < visibleTop || it.y() > visibleBottom) continue;
			int x = ox + it.x(), y = oy + it.y();
			if (it instanceof Laid.Fill f) {
				ctx.fill(x, y, x + f.w(), y + f.h(), f.argb());
			} else if (it instanceof Laid.Run r) {
				ctx.text(font, Component.literal(r.text()).setStyle(mcStyle(r.style())), x, y, r.color(), false);
				if (hovered != null && hovered.equals(r.style().link())) ctx.fill(x, y + 9, x + r.w(), y + 10, r.color());
			} else if (it instanceof Laid.Pic p) {
				reader.images.draw(ctx, p.image(), x, y, p.w(), p.h());
			}
		}
	}

	private static Style mcStyle(Inline.Style s) {
		return Style.EMPTY.withBold(s.bold()).withItalic(s.italic()).withUnderlined(s.underline()).withStrikethrough(s.strikethrough());
	}

	private void drawScrollbar(GuiGraphicsExtractor ctx) {
		int max = maxScroll();
		if (max <= 0) return;
		int x = contentRight() + 2, top = contentTop(), h = contentBottom() - top;
		int barH = Math.max(16, h * h / (h + max));
		int barY = top + (int) ((long) (h - barH) * scroll / max);
		ctx.fill(x, top, x + 4, top + h, 0x40000000);
		ctx.fill(x, barY, x + 4, barY + barH, draggingBar ? 0xFFAAAAAA : 0xFF777777);
	}

	private List<Component> tooltip(Link link) {
		if (link instanceof Link.Page p) {
			String t = p.title().isEmpty() ? title : p.title();
			return List.of(Component.literal(p.anchor() != null ? t + " § " + p.anchor().replace('_', ' ') : t));
		}
		if (link instanceof Link.External e) return List.of(Component.literal(e.url()).withStyle(ChatFormatting.GRAY));
		return List.of(Component.translatable("cakewiki.missing").withStyle(ChatFormatting.RED));
	}

	// ---- contents panel ---------------------------------------------------------------------------------

	private static final int NAV_ROW = 12;

	/** The contents of the shown page's site (kept in the reader, so open groups survive), opened on the way to the page. */
	private void updateContents() {
		List<NavItem> roots = shown == null ? List.of() : shown.site().navigation(title);
		boolean had = contents != null;
		if (roots.isEmpty()) {
			contents = null;
		} else {
			if (reader.contents == null || reader.contents.roots() != roots) {
				reader.contents = new Contents(roots);
				reader.contentsScroll = 0;
			}
			contents = reader.contents;
			contents.reveal(title);
			revealPending = true;
		}
		if (had != (contents != null)) rebuildWidgets(); // the ▤ button and the page's width change
	}

	private int navMaxScroll(int rows) {
		return Math.max(0, rows * NAV_ROW + 4 - (contentBottom() - contentTop()));
	}

	/** -2 outside the panel, -1 inside but on no row, else the row's index. */
	private int navRowAt(double mx, double my, int count) {
		if (!contentsShown()) return -2;
		if (mx < MARGIN || mx >= MARGIN + panelWidth() || my < contentTop() || my >= contentBottom()) return -2;
		int i = (int) Math.floor((my - contentTop() - 2 + reader.contentsScroll) / NAV_ROW);
		return i >= 0 && i < count ? i : -1;
	}

	private void drawContents(GuiGraphicsExtractor ctx, int mx, int my) {
		if (!contentsShown()) return;
		int x = MARGIN, top = contentTop(), bottom = contentBottom(), w = panelWidth();
		ctx.fill(x - 1, top - 1, x + w + 1, bottom + 1, Layout.BORDER);
		ctx.fill(x, top, x + w, bottom, Layout.PAGE_BG);
		List<Contents.Row> rows = contents.rows();
		int view = bottom - top - 4;
		if (revealPending) { // the shown page in view, a third of the way down
			revealPending = false;
			for (int i = 0; i < rows.size(); i++) {
				if (title == null || !title.equals(rows.get(i).item().key())) continue;
				int ry = i * NAV_ROW;
				if (ry < reader.contentsScroll || ry + NAV_ROW > reader.contentsScroll + view) reader.contentsScroll = ry - view / 3;
				break;
			}
		}
		int max = navMaxScroll(rows.size());
		reader.contentsScroll = Math.max(0, Math.min(max, reader.contentsScroll));
		int hover = navRowAt(mx, my, rows.size());
		ctx.enableScissor(x, top, x + w, bottom);
		for (int i = 0; i < rows.size(); i++) {
			int ry = top + 2 + i * NAV_ROW - reader.contentsScroll;
			if (ry + NAV_ROW < top || ry > bottom) continue;
			Contents.Row r = rows.get(i);
			NavItem it = r.item();
			boolean current = title != null && title.equals(it.key());
			boolean clickable = it.key() != null || it.collapsible();
			if (current) ctx.fill(x, ry, x + w, ry + NAV_ROW, 0xFF2C2C4A);
			else if (hover == i && clickable) ctx.fill(x, ry, x + w, ry + NAV_ROW, 0xFF3A3A60);
			int indent = 4 + r.depth() * 8, arrow = it.group() && it.collapsible() ? 12 : 0, avail = w - indent - arrow - 4;
			boolean heading = it.group() && !it.collapsible();
			int color = current ? 0xFFFFD27F : heading ? 0xFFE8C872 : it.key() != null || it.group() ? 0xFFE0E0E0 : 0xFF9C9CAC;
			Component label = heading ? Component.literal(it.text()).withStyle(ChatFormatting.BOLD) : Component.literal(it.text());
			boolean cut = font.width(label) > avail;
			String shownText = cut ? font.plainSubstrByWidth(it.text(), avail - font.width("…")) + "…" : it.text();
			ctx.text(font, heading ? Component.literal(shownText).withStyle(ChatFormatting.BOLD) : Component.literal(shownText),
					x + indent, ry + 2, color, false);
			if (arrow > 0) ctx.text(font, r.open() ? "▾" : "▸", x + w - 11, ry + 2, 0xFF9C9CAC, false);
			if (hover == i && cut) contentsTooltip = List.of(Component.literal(it.text()));
		}
		ctx.disableScissor();
		if (max > 0) { // scroll position
			int h = bottom - top, barH = Math.max(10, h * h / (h + max));
			int barY = top + (int) ((long) (h - barH) * reader.contentsScroll / max);
			ctx.fill(x + w - 2, barY, x + w, barY + barH, 0xFF777777);
		}
	}

	/** A click on the panel: a group opens or closes (its arrow, or the whole row when it is no page), a page opens. */
	private boolean clickContents(double mx, double my, int button) {
		if (!contentsShown()) return false;
		List<Contents.Row> rows = contents.rows();
		int i = navRowAt(mx, my, rows.size());
		if (i == -2) return false;
		if (i >= 0 && button == 0) {
			NavItem it = rows.get(i).item();
			boolean onArrow = mx >= MARGIN + panelWidth() - 14;
			if (it.group() && it.collapsible() && (it.key() == null || onArrow)) {
				contents.toggle(it);
			} else if (it.key() != null) {
				if (it.group() && !contents.isOpen(it)) contents.toggle(it);
				if (!it.key().equals(title)) navigate(shown, it.key(), null); // the panel's pages are on the shown page's site
			}
		}
		return true;
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

	private void drawSuggestions(GuiGraphicsExtractor ctx, int mx, int my) {
		if (!suggestionsVisible()) return;
		ctx.nextStratum(); // above the page
		int x = search.getX(), y = search.getY() + search.getHeight() + 1, w = search.getWidth();
		ctx.fill(x - 1, y - 1, x + w + 1, y + suggestions.size() * 12 + 1, 0xFF000000);
		int hover = suggestionAt(mx, my);
		for (int i = 0; i < suggestions.size(); i++) {
			ctx.fill(x, y + i * 12, x + w, y + i * 12 + 12, i == hover ? 0xFF3A3A60 : 0xFF202030);
			String s = font.plainSubstrByWidth(suggestions.get(i), w - 6);
			ctx.text(font, s, x + 3, y + i * 12 + 2, 0xFFE0E0E0, false);
		}
	}

	// ---- input ------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int button = event.button();
		if (bookmarksOpen && clickBookmarks(mx, my, button)) return true;
		int s = suggestionAt(mx, my);
		if (s < 0 && clickContents(mx, my, button)) return true;
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
		if (super.mouseClicked(event, doubleClick)) return true;
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
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingBar) {
			scrollToBar(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingBar = false;
		return super.mouseReleased(event);
	}

	private void scrollToBar(double my) {
		int top = contentTop(), h = contentBottom() - top;
		scroll = (int) Math.max(0, Math.min(maxScroll(), (my - top) / h * (laid.height())));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (bookmarkAt(mx, my) != -2) {
			int max = Math.max(0, reader.bookmarks.all(reader.source().id()).size() - bookmarkRows());
			bookmarksScroll = Math.max(0, Math.min(max, bookmarksScroll - (int) Math.signum(vertical)));
			return true;
		}
		if (contents != null && navRowAt(mx, my, 0) != -2) {
			int max = navMaxScroll(contents.rows().size());
			reader.contentsScroll = Math.max(0, Math.min(max, reader.contentsScroll - (int) (vertical * 3 * NAV_ROW)));
			return true;
		}
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * SCROLL_STEP)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key(), modifiers = event.modifiers();
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
			return super.keyPressed(event);
		}
		if (bookmarksOpen && key == GLFW.GLFW_KEY_ESCAPE) { // first Esc closes the bookmarks list
			bookmarksOpen = false;
			return true;
		}
		if (key == GLFW.GLFW_KEY_D && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
			toggleBookmark();
			return true;
		}
		if (CakeWiki.openKey.matches(event)) {
			onClose();
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
				return super.keyPressed(event);
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
			} else if (shown.site().browserOnly(p.title())) {
				openInBrowser(shown.site().pageUrl(p.title())); // e.g. MediaWiki's category lists
			} else {
				navigate(shown, p.title(), p.anchor()); // a page's links are to its own site
			}
		} else if (link instanceof Link.External e) {
			openInBrowser(e.url());
		}
		// Link.Missing: nothing to open; the tooltip says why
	}

	private void openInBrowser(String url) {
		Minecraft mc = client();
		mc.setScreen(new ConfirmLinkScreen(open -> {
			if (open) Util.getPlatform().openUri(url);
			mc.setScreen(this);
		}, url, true));
	}

	@Override
	public void onClose() {
		if (title != null) reader.remember(new Reader.Visit(shown.id(), title, scroll));
		onClose.run();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
