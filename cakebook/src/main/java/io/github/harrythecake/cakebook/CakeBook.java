package io.github.harrythecake.cakebook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CakeBook implements ClientModInitializer {
	public static final String MOD_ID = "cakebook";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Config config = new Config();
	private static volatile Library library = Library.EMPTY;
	private static Notes notes;
	private static KeyBinding openKey;

	static Config config() {
		return config;
	}

	static Library library() {
		return library;
	}

	static Notes notes() {
		return notes;
	}

	static KeyBinding openKey() {
		return openKey;
	}

	private static Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public void onInitializeClient() {
		config = Config.load();
		notes = new Notes(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("notes"));

		// Books are read on every resource reload (start, F3+T, resource pack or language change) and on /cakebook reload.
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return new Identifier(MOD_ID, "books");
					}

					@Override
					public void reload(ResourceManager manager) {
						loadBooks(manager);
					}
				});

		// B by default (free in Monumenta Cakedition). The buttons and /cakebook always work.
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cakebook.open", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_B, "key.categories.cakebook"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new BookScreen(null, null, null));
				}
			}
		});

		MenuButtons.register();

		// Client-side commands: they never reach the server.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal(MOD_ID)
						.executes(ctx -> show(null, null))
						.then(literal("open").then(argument("page", StringArgumentType.greedyString())
								.suggests((ctx, builder) -> suggestPages(builder))
								.executes(ctx -> openPage(ctx.getSource(), StringArgumentType.getString(ctx, "page")))))
						.then(literal("search").then(argument("text", StringArgumentType.greedyString())
								.executes(ctx -> show(null, StringArgumentType.getString(ctx, "text")))))
						.then(literal("note").then(argument("title", StringArgumentType.greedyString())
								.executes(ctx -> newNote(ctx.getSource(), StringArgumentType.getString(ctx, "title")))))
						.then(literal("reload").executes(ctx -> reload(ctx.getSource())))
						.then(literal("folder").executes(ctx -> openFolder(ctx.getSource())))));
	}

	private static void loadBooks(ResourceManager manager) {
		Images.clear();
		library = BookLoader.load(manager, BookLoader.folder(gameDir()));
	}

	/** Opens the book after the chat screen that ran the command has closed. */
	private static int show(@Nullable Links.Target target, @Nullable String query) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.send(() -> client.setScreen(new BookScreen(null, target, query)));
		return 1;
	}

	/** {@code cakedition/mods/sodium}, {@code book:…}, {@code note:…} or a web link. */
	private static int openPage(FabricClientCommandSource source, String page) {
		String link = page.strip();
		if (!link.contains(":")) {
			link = "book:" + link;
		}
		Links.Target target = Links.resolve(null, "", link);
		if (target == null || target.kind() == Links.Kind.URL) {
			source.sendError(Text.translatable("cakebook.command.bad_page", page));
			return 0;
		}
		return show(target, null);
	}

	private static CompletableFuture<Suggestions> suggestPages(SuggestionsBuilder builder) {
		String typed = builder.getRemaining();
		for (Book book : library.books()) {
			for (String path : book.paths()) {
				String id = path.equals(Book.HOME) ? book.id : book.id + "/" + path;
				if (id.startsWith(typed)) {
					builder.suggest(id);
				}
			}
		}
		for (Notes.Note note : notes.list()) {
			if (("note:" + note.id()).startsWith(typed)) {
				builder.suggest("note:" + note.id());
			}
		}
		return builder.buildFuture();
	}

	private static int newNote(FabricClientCommandSource source, String title) {
		try {
			Notes.Note note = notes.create(title);
			MinecraftClient client = MinecraftClient.getInstance();
			client.send(() -> {
				BookScreen book = new BookScreen(null, new Links.Target(Links.Kind.NOTE, null, note.id()), null);
				client.setScreen(new NoteEditScreen(book, note));
			});
			return 1;
		} catch (IOException e) {
			LOGGER.error("Could not create a note", e);
			source.sendError(Text.translatable("cakebook.note.save_failed", e.getMessage()));
			return 0;
		}
	}

	private static int reload(FabricClientCommandSource source) {
		loadBooks(source.getClient().getResourceManager());
		source.sendFeedback(Text.translatable("cakebook.command.reloaded", library.books().size()));
		return 1;
	}

	private static int openFolder(FabricClientCommandSource source) {
		Path folder = BookLoader.folder(gameDir());
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			source.sendError(Text.translatable("cakebook.command.folder_failed", e.getMessage()));
			return 0;
		}
		Util.getOperatingSystem().open(folder.toFile());
		return 1;
	}
}
