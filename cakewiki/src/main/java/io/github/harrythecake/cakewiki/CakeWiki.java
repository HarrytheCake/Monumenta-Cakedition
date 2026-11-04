package io.github.harrythecake.cakewiki;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CakeWiki implements ClientModInitializer {
	public static final String MOD_ID = "cakewiki";
	public static final Logger LOG = LoggerFactory.getLogger("CakeWiki");

	/** Opens the reader; over an item in a container screen, opens that item's page. */
	public static KeyBinding openKey;
	private static Reader reader;

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		reader = new Reader(version, FabricLoader.getInstance().getGameDir().resolve("cakewiki").resolve("cache"),
				FabricLoader.getInstance().getConfigDir().resolve("cakewiki"));

		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cakewiki.open", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_K, "key.categories.cakewiki"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				if (client.currentScreen == null) open(null);
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("wiki")
						.executes(ctx -> {
							later(() -> open(null));
							return 1;
						})
						.then(argument("page", StringArgumentType.greedyString())
								.suggests((ctx, builder) -> {
									String typed = builder.getRemaining();
									Target t = target(typed);
									String prefix = t.site() == null ? "" : t.site().id() + ":";
									Reader.Source site = t.site() != null ? t.site() : reader.source();
									if (t.site() == null && reader.sources.size() > 1) { // offer the other sites' prefixes
										for (Reader.Source s : reader.sources) {
											if (!typed.isEmpty() && s.id().startsWith(typed.toLowerCase(java.util.Locale.ROOT))) builder.suggest(s.id() + ":");
										}
									}
									return site.site().suggest(t.page(), 10)
											.handle((titles, error) -> {
												if (titles != null) titles.forEach(title -> builder.suggest(prefix + title));
												return builder.build();
											})
											// the chat box reads suggestions on the game thread
											.thenApplyAsync(s -> s, MinecraftClient.getInstance());
								})
								.executes(ctx -> {
									String page = StringArgumentType.getString(ctx, "page");
									later(() -> open(page));
									return 1;
								}))));
		DevShots.startIfAsked();
		LOG.info("CakeWiki loaded");
	}

	static Reader reader() {
		return reader;
	}

	/** Runs after the chat screen has closed, so the reader isn't closed with it. */
	private static void later(Runnable r) {
		MinecraftClient.getInstance().send(r);
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
	 * its main page), or where the player left off (the default site's main page the first time).
	 */
	public static void open(String page) {
		WikiScreen screen = new WikiScreen(reader, () -> MinecraftClient.getInstance().setScreen(null));
		MinecraftClient.getInstance().setScreen(screen);
		if (page != null) {
			Target t = target(page);
			if (t.site() == null) screen.navigate(page, null);
			else if (t.page().isEmpty()) screen.home(t.site());
			else screen.navigate(t.site(), t.page(), null);
		} else if (reader.last != null) {
			screen.reopen(reader.last);
		} else {
			screen.home(reader.sources.get(0));
		}
	}

	/**
	 * The open key over an item in a container. Called from {@code HandledScreenMixin} only when no text box
	 * took the key. A server container is closed properly first (so the server knows); the player's own
	 * inventory comes back when the reader closes.
	 */
	public static void lookupItem(HandledScreen<?> from, ItemStack stack) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		String name = Formatting.strip(stack.getName().getString()).trim();
		if (name.isEmpty()) return;
		Runnable onClose;
		if (from instanceof InventoryScreen) {
			onClose = () -> mc.setScreen(new InventoryScreen(mc.player));
		} else {
			mc.player.closeHandledScreen();
			onClose = () -> mc.setScreen(null);
		}
		WikiScreen screen = new WikiScreen(reader, onClose);
		mc.setScreen(screen);
		reader.use(reader.sources.get(0)); // items are looked up on the default site
		screen.lookup(name);
	}
}
