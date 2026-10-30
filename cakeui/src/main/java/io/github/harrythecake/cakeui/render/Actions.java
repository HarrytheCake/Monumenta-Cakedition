package io.github.harrythecake.cakeui.render;

import io.github.harrythecake.cakeui.CakeUI;
import io.github.harrythecake.cakeui.expr.Values;
import io.github.harrythecake.cakeui.model.Node;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;

/**
 * What CakeUI buttons can do. Everything stays on this computer: open a screen, connect to a
 * server, open a link after vanilla's confirmation, quit, or set a layout variable. Never chat or
 * commands (ADR 0005).
 */
final class Actions {
	private Actions() {
	}

	static void run(Renderer renderer, Node node, NodeState st, Screen screen) {
		Node.Action a = node.action;
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			switch (a.kind()) {
				case "singleplayer" -> client.setScreen(new SelectWorldScreen(screen));
				case "multiplayer" -> client.setScreen(client.options.skipMultiplayerWarning ? new MultiplayerScreen(screen) : new MultiplayerWarningScreen(screen));
				case "realms" -> client.setScreen(new RealmsMainScreen(screen));
				case "options" -> client.setScreen(new OptionsScreen(screen, client.options));
				case "language" -> client.setScreen(new LanguageOptionsScreen(screen, client.options, client.getLanguageManager()));
				case "accessibility" -> client.setScreen(new AccessibilityOptionsScreen(screen, client.options));
				case "modmenu" -> openModMenu(client, screen);
				case "quit" -> client.scheduleStop();
				case "back" -> {
					if (screen != null) {
						screen.close();
					}
				}
				case "connect" -> {
					String address = a.argument();
					if (ServerAddress.isValid(address)) {
						ConnectScreen.connect(screen, client, ServerAddress.parse(address), new ServerInfo(address, address, ServerInfo.ServerType.OTHER), false);
					} else {
						renderer.layout.problems.add(node.file, node.line, "not a server address: " + address);
					}
				}
				case "url" -> ConfirmLinkScreen.open(screen, a.argument());
				case "set" -> {
					renderer.env.self = st;
					for (Node.Assign set : a.assigns()) {
						if (set.toggle()) {
							set.target().set(!Values.bool(set.target().get(renderer.env)));
						} else {
							set.target().set(set.value().eval(renderer.env));
						}
					}
				}
				default -> {
				}
			}
		} catch (RuntimeException e) {
			CakeUI.LOGGER.warn("CakeUI: action {} of {} failed", a.kind(), node, e);
			renderer.layout.problems.add(node.file, node.line, "action '" + a.kind() + "' failed: " + e);
		}
	}

	private static void openModMenu(MinecraftClient client, Screen parent) throws RuntimeException {
		if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
			return;
		}
		try {
			Class<?> mods = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			client.setScreen((Screen) mods.getConstructor(Screen.class).newInstance(parent));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Mod Menu's screen could not be opened", e);
		}
	}
}
