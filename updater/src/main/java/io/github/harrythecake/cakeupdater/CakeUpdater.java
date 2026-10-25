package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CakeUpdater implements ClientModInitializer {
	public static final String MOD_ID = "cakeupdater";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	static final String DEFAULT_PACK_URL =
			"https://raw.githubusercontent.com/HarrytheCake/Monumenta-Cakedition/packwiz/pack/pack.toml";

	private static String packUrl;
	private static volatile UpdateCheck.Result latest;
	private static String notifiedHash;
	private static boolean updateOnClose;

	@Override
	public void onInitializeClient() {
		packUrl = readPackUrl();

		// Check on every join, so an update released mid-session still shows up.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				UpdateCheck.runAsync(packUrl, gameDir()).thenAccept(result -> {
					if (result != null) {
						latest = result;
						client.send(() -> announce(client, result));
					}
				}));

		// Client-side command: the click events below run it, and it never reaches the server.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("cakeupdate")
						.executes(ctx -> showOptions(ctx.getSource()))
						.then(literal("onclose").executes(ctx -> scheduleOnClose(ctx.getSource())))
						.then(literal("now").executes(ctx -> updateNow(ctx.getSource())))
						.then(literal("cancel").executes(ctx -> cancel(ctx.getSource())))));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (updateOnClose) {
				try {
					HelperLauncher.launch(gameDir(), packUrl);
				} catch (Exception e) {
					LOGGER.error("Could not start the updater", e);
				}
			}
		});
	}

	private static void announce(MinecraftClient client, UpdateCheck.Result result) {
		if (!result.available() || updateOnClose || result.remoteHash().equals(notifiedHash) || client.player == null) {
			return;
		}
		notifiedHash = result.remoteHash();
		String versions = result.localVersion() == null
				? result.remoteVersion()
				: result.localVersion() + " -> " + result.remoteVersion();
		client.player.sendMessage(prefix()
				.append(Text.literal("Modpack update available (" + versions + ") ").formatted(Formatting.YELLOW))
				.append(button("[Click to update]", "/cakeupdate", "Show update options", Formatting.GREEN)), false);
	}

	private static int showOptions(FabricClientCommandSource source) {
		UpdateCheck.Result result = latest;
		if (result == null || !result.available()) {
			source.sendFeedback(prefix().append(Text.literal("The modpack is up to date.").formatted(Formatting.GRAY)));
			return 1;
		}
		source.sendFeedback(prefix().append(Text.literal("How do you want to update to " + result.remoteVersion() + "?")
				.formatted(Formatting.YELLOW)));
		source.sendFeedback(Text.literal("  ").append(button("[Update when I close the game myself]", "/cakeupdate onclose",
				"Keep playing. The updater opens after you quit Minecraft.", Formatting.GREEN)));
		source.sendFeedback(Text.literal("  ").append(button("[Update now (closes the game)]", "/cakeupdate now",
				"Quit Minecraft now and start the updater.", Formatting.RED)));
		return 1;
	}

	private static int scheduleOnClose(FabricClientCommandSource source) {
		updateOnClose = true;
		source.sendFeedback(prefix()
				.append(Text.literal("The update will start when you close Minecraft. ").formatted(Formatting.GREEN))
				.append(button("[Cancel]", "/cakeupdate cancel", "Don't update when the game closes", Formatting.GRAY)));
		return 1;
	}

	private static int cancel(FabricClientCommandSource source) {
		updateOnClose = false;
		source.sendFeedback(prefix().append(Text.literal("Update on close cancelled.").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int updateNow(FabricClientCommandSource source) {
		updateOnClose = true;
		source.sendFeedback(prefix().append(Text.literal("Closing Minecraft to update...").formatted(Formatting.YELLOW)));
		MinecraftClient client = source.getClient();
		// Queue it: disconnecting from inside the command handler would tear down the connection mid-packet.
		client.send(() -> {
			if (client.world != null) {
				boolean singleplayer = client.isInSingleplayer();
				client.world.disconnect();
				if (singleplayer) {
					client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
				} else {
					client.disconnect();
				}
			}
			client.scheduleStop();
		});
		return 1;
	}

	private static MutableText prefix() {
		return Text.literal("[Cakedition] ").formatted(Formatting.GOLD);
	}

	private static MutableText button(String label, String command, String hover, Formatting color) {
		return Text.literal(label).styled(style -> style
				.withColor(color)
				.withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
	}

	static Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	/** config/cakeupdater/config.json may override the pack URL: {"packUrl": "https://.../pack.toml"} */
	private static String readPackUrl() {
		Path config = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json");
		try {
			if (Files.exists(config)) {
				JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
				if (json.has("packUrl")) {
					return json.get("packUrl").getAsString();
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Ignoring unreadable {}: {}", config, e.toString());
		}
		return DEFAULT_PACK_URL;
	}
}
