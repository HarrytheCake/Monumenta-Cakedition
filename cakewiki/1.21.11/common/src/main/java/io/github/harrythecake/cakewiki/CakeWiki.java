package io.github.harrythecake.cakewiki;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.harrythecake.cakewiki.net.SiteConfig;
import io.github.harrythecake.cakewiki.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * The mod, shared by both loaders: the loader entry ({@code fabric/CakeWikiFabric}, {@code neoforge/CakeWikiNeoForge})
 * calls {@link #init()}, registers {@link #openKey} and {@link #command()}, and calls {@link #onClientTick} at the end
 * of every client tick.
 */
public final class CakeWiki {
	public static final String MOD_ID = "cakewiki";
	public static final Logger LOG = Log.LOG;

	/** The Controls category (1.21.9+: a registered id; its name is lang key {@code key.category.cakewiki.main}). */
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	/** Opens the reader; over an item in a container screen, opens that item's page. */
	public static final KeyMapping openKey = new KeyMapping("key.cakewiki.open", InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_Y, CATEGORY); // Y since 0.2.0 (K before; the Cakedition pack keeps K in its options.txt)
	/** Runs on the next client tick ({@link #later}). */
	private static final java.util.List<Runnable> nextTick = new java.util.ArrayList<>();
	private static Reader reader;

	private CakeWiki() {}

	public static void init() {
		String version = Services.PLATFORM.modVersion(MOD_ID);
		reader = new Reader(version, Services.PLATFORM.gameDir().resolve("cakewiki").resolve("cache"),
				Services.PLATFORM.configDir().resolve("cakewiki"), CakeWiki::serverHost);
		DevShots.startIfAsked();
		McefTest.startIfAsked();
		LOG.info("CakeWiki loaded");
	}

	/** End of every client tick: the open key in the world, and the dev screenshot tour. */
	public static void onClientTick(Minecraft client) {
		if (!nextTick.isEmpty()) {
			java.util.List<Runnable> run = java.util.List.copyOf(nextTick);
			nextTick.clear();
			run.forEach(Runnable::run);
		}
		while (openKey.consumeClick()) {
			if (client.screen == null) open(null);
		}
		DevShots.tick(client);
		McefTest.tick(client);
	}

	/** {@code /wiki [page]} with live suggestions, for the loader's client-command dispatcher (source type {@code S}). */
	public static <S> LiteralArgumentBuilder<S> command() {
		return LiteralArgumentBuilder.<S>literal("wiki")
				.executes(ctx -> {
					later(() -> open(null));
					return 1;
				})
				.then(RequiredArgumentBuilder.<S, String>argument("page", StringArgumentType.greedyString())
						.suggests((ctx, builder) -> {
							String typed = builder.getRemaining();
							Target t = target(typed);
							String prefix = t.site() == null ? "" : t.site().id() + ":";
							Reader.Source site = t.site() != null ? t.site() : reader.source();
							java.util.List<Reader.Source> offered = reader.offered();
							if (t.site() == null && offered.size() > 1) { // offer the other sites' prefixes (those offered here)
								for (Reader.Source s : offered) {
									if (!typed.isEmpty() && s.id().startsWith(typed.toLowerCase(java.util.Locale.ROOT))) builder.suggest(s.id() + ":");
								}
							}
							return site.site().suggest(t.page(), 10)
									.handle((titles, error) -> {
										if (titles != null) titles.forEach(title -> builder.suggest(prefix + title));
										return builder.build();
									})
									// the chat box reads suggestions on the game thread
									.thenApplyAsync(s -> s, Minecraft.getInstance());
						})
						.executes(ctx -> {
							String page = StringArgumentType.getString(ctx, "page");
							later(() -> open(page));
							return 1;
						}));
	}

	static Reader reader() {
		return reader;
	}

	/** The host of the server the player is on (from the server list entry, without port), or null in single-player / menus. */
	static String serverHost() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.isLocalServer() || mc.getCurrentServer() == null) return null;
		return SiteConfig.host(mc.getCurrentServer().ip);
	}

	/**
	 * Runs after the chat screen has closed, so the reader isn't closed with it. 1.21.11 has no {@code tell}, and
	 * {@code execute}/{@code submit} run at once on the game thread, so this waits for the next client tick.
	 */
	private static void later(Runnable r) {
		Minecraft.getInstance().execute(() -> nextTick.add(r));
	}

	/** {@code /wiki <site id>:<page>}: the site named before the first colon, when the site list has it; else the current site. */
	record Target(Reader.Source site, String page) {}

	static Target target(String typed) {
		int colon = typed.indexOf(':');
		if (colon > 0) {
			Reader.Source s = reader.source(typed.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT));
			if (s != null) return new Target(s, typed.substring(colon + 1).trim());
		}
		return new Target(null, typed);
	}

	/**
	 * Opens the reader at {@code page} (of the current site, or {@code <site id>:<page>}; just {@code <site id>:} is
	 * its main page), or where the player left off on a site offered here (else the default site's main page).
	 */
	public static void open(String page) {
		WikiScreen screen = new WikiScreen(reader, () -> Minecraft.getInstance().setScreen(null));
		Minecraft.getInstance().setScreen(screen);
		if (page != null) {
			Target t = target(page);
			if (t.site() == null) screen.navigate(page, null);
			else if (t.page().isEmpty()) screen.home(t.site());
			else screen.navigate(t.site(), t.page(), null);
		} else {
			Reader.Visit resume = reader.resume();
			if (resume != null) screen.reopen(resume);
			else screen.home(reader.home());
		}
	}

	/**
	 * The open key over an item in a container. Called from {@code HandledScreenMixin} only when no text box
	 * took the key. A server container is closed properly first (so the server knows); the player's own
	 * inventory comes back when the reader closes.
	 */
	public static void lookupItem(AbstractContainerScreen<?> from, ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String name = ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
		if (name.isEmpty()) return;
		Runnable onClose;
		if (from instanceof InventoryScreen) {
			onClose = () -> mc.setScreen(new InventoryScreen(mc.player));
		} else {
			mc.player.closeContainer();
			onClose = () -> mc.setScreen(null);
		}
		WikiScreen screen = new WikiScreen(reader, onClose);
		mc.setScreen(screen);
		reader.use(reader.home()); // items are looked up on the default site of this server
		screen.lookup(name);
	}
}
