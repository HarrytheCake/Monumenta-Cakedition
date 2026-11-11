package io.github.harrythecake.cakebook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.harrythecake.cakebook.platform.Services;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The game side of CakeBook, shared by both loaders: the open key, what a tick does, the books' reload, the
 * {@code /cakebook} command tree. Each loader's entry class registers these with its own events.
 */
public final class CakeBookClient {
	/** The key category (26.1: an id; its name is the lang key key.category.cakebook.cakebook). */
	public static final Identifier KEY_CATEGORY = Identifier.fromNamespaceAndPath(CakeBook.MOD_ID, "cakebook");
	private static @Nullable KeyMapping openKey;

	/** Books are read on every resource reload (start, F3+T, resource pack or language change) and on /cakebook reload. */
	public static final ResourceManagerReloadListener RELOAD_LISTENER = CakeBookClient::loadBooks;

	private CakeBookClient() {}

	/** Reads the config and notes folder. Call once when the client starts. */
	public static void init() {
		CakeBook.init();
	}

	/**
	 * Makes the open key, B by default (free in Monumenta Cakedition); the buttons and /cakebook always work. The
	 * loader registers the category (Fabric: {@code KeyMapping.Category.register}, NeoForge: its event) and the key.
	 */
	public static KeyMapping createOpenKey(KeyMapping.Category category) {
		openKey = new KeyMapping("key.cakebook.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category);
		return openKey;
	}

	static @Nullable KeyMapping openKey() {
		return openKey;
	}

	/** The book, opened where the player left it; for Mod Menu / the NeoForge mods list. */
	public static Screen bookScreen(@Nullable Screen parent) {
		return new BookScreen(parent, null, null);
	}

	/** At the end of every client tick. */
	public static void tick(Minecraft client) {
		SelfTest.tick(client);
		while (openKey != null && openKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new BookScreen(null, null, null));
			}
		}
	}

	public static void loadBooks(ResourceManager manager) {
		Images.clear();
		CakeBook.library(BookLoader.load(manager, BookLoader.folder(gameDir())));
	}

	private static Path gameDir() {
		return Services.PLATFORM.gameDir();
	}

	/**
	 * The client-side {@code /cakebook} command for a loader's command source type: it never reaches the server.
	 *
	 * @param error    shows a red line in chat
	 * @param feedback shows a line in chat
	 */
	public static <S> LiteralArgumentBuilder<S> command(BiConsumer<S, Component> error, BiConsumer<S, Component> feedback) {
		return LiteralArgumentBuilder.<S>literal(CakeBook.MOD_ID)
				.executes(ctx -> show(null, null))
				.then(LiteralArgumentBuilder.<S>literal("open").then(RequiredArgumentBuilder.<S, String>argument("page", StringArgumentType.greedyString())
						.suggests((ctx, builder) -> suggestPages(builder))
						.executes(ctx -> openPage(ctx.getSource(), error, StringArgumentType.getString(ctx, "page")))))
				.then(LiteralArgumentBuilder.<S>literal("search").then(RequiredArgumentBuilder.<S, String>argument("text", StringArgumentType.greedyString())
						.executes(ctx -> show(null, StringArgumentType.getString(ctx, "text")))))
				.then(LiteralArgumentBuilder.<S>literal("note").then(RequiredArgumentBuilder.<S, String>argument("title", StringArgumentType.greedyString())
						.executes(ctx -> newNote(ctx.getSource(), error, StringArgumentType.getString(ctx, "title")))))
				.then(LiteralArgumentBuilder.<S>literal("reload").executes(ctx -> reload(ctx.getSource(), feedback)))
				.then(LiteralArgumentBuilder.<S>literal("folder").executes(ctx -> openFolder(ctx.getSource(), error)));
	}

	/** Opens the book after the chat screen that ran the command has closed. */
	private static int show(@Nullable Links.Target target, @Nullable String query) {
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> client.setScreen(new BookScreen(null, target, query)));
		return 1;
	}

	/** {@code cakedition/mods/sodium}, {@code book:…}, {@code note:…} or a web link. */
	private static <S> int openPage(S source, BiConsumer<S, Component> error, String page) {
		String link = page.strip();
		if (!link.contains(":")) {
			link = "book:" + link;
		}
		Links.Target target = Links.resolve(null, "", link);
		if (target == null || target.kind() == Links.Kind.URL) {
			error.accept(source, Component.translatable("cakebook.command.bad_page", page));
			return 0;
		}
		return show(target, null);
	}

	private static CompletableFuture<Suggestions> suggestPages(SuggestionsBuilder builder) {
		String typed = builder.getRemaining();
		for (Book book : CakeBook.library().books()) {
			for (String path : book.paths()) {
				String id = path.equals(Book.HOME) ? book.id : book.id + "/" + path;
				if (id.startsWith(typed)) {
					builder.suggest(id);
				}
			}
		}
		for (Notes.Note note : CakeBook.notes().list()) {
			if (("note:" + note.id()).startsWith(typed)) {
				builder.suggest("note:" + note.id());
			}
		}
		return builder.buildFuture();
	}

	private static <S> int newNote(S source, BiConsumer<S, Component> error, String title) {
		try {
			Notes.Note note = CakeBook.notes().create(title);
			Minecraft client = Minecraft.getInstance();
			client.schedule(() -> {
				BookScreen book = new BookScreen(null, new Links.Target(Links.Kind.NOTE, null, note.id()), null);
				client.setScreen(new NoteEditScreen(book, note));
			});
			return 1;
		} catch (IOException e) {
			CakeBook.LOGGER.error("Could not create a note", e);
			error.accept(source, Component.translatable("cakebook.note.save_failed", e.getMessage()));
			return 0;
		}
	}

	private static <S> int reload(S source, BiConsumer<S, Component> feedback) {
		loadBooks(Minecraft.getInstance().getResourceManager());
		feedback.accept(source, Component.translatable("cakebook.command.reloaded", CakeBook.library().books().size()));
		return 1;
	}

	private static <S> int openFolder(S source, BiConsumer<S, Component> error) {
		Path folder = BookLoader.folder(gameDir());
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			error.accept(source, Component.translatable("cakebook.command.folder_failed", e.getMessage()));
			return 0;
		}
		Util.getPlatform().openFile(folder.toFile());
		return 1;
	}
}
