package io.github.harrythecake.cakeui.render;

import com.mojang.realmsclient.RealmsMainScreen;
import io.github.harrythecake.cakeui.CakeUI;
import io.github.harrythecake.cakeui.api.ButtonAction;
import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.expr.Values;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.CakeUIClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * What CakeUI buttons can do. Everything stays on this computer: open a screen, connect to a
 * server, open a link after vanilla's confirmation, quit, or set a layout variable; on a container
 * screen, `menuButton` also sends vanilla's container-button click for that screen's menu. Never chat
 * or commands (ADR 0005).
 */
final class Actions {
	private Actions() {
	}

	static void run(Renderer renderer, Node node, NodeState st, Screen screen) {
		Node.Action a = node.action;
		Minecraft client = Minecraft.getInstance();
		try {
			switch (a.kind()) {
				case "singleplayer" -> client.setScreen(new SelectWorldScreen(screen));
				case "multiplayer" -> client.setScreen(client.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(screen) : new SafetyScreen(screen));
				case "realms" -> client.setScreen(new RealmsMainScreen(screen));
				case "options" -> client.setScreen(new OptionsScreen(screen, client.options));
				case "language" -> client.setScreen(new LanguageSelectScreen(screen, client.options, client.getLanguageManager()));
				case "accessibility" -> client.setScreen(new AccessibilityOptionsScreen(screen, client.options));
				case "modmenu" -> openModMenu(client, screen);
				case "quit" -> client.stop();
				case "back" -> {
					if (screen != null) {
						screen.onClose();
					}
				}
				case "connect" -> {
					String address = a.argument();
					if (ServerAddress.isValidAddress(address)) {
						ConnectScreen.startConnecting(screen, client, ServerAddress.parseString(address), new ServerData(address, address, ServerData.Type.OTHER), false);
					} else {
						renderer.layout.problems.add(node.file, node.line, "not a server address: " + address);
					}
				}
				case "url" -> ConfirmLinkScreen.confirmLinkNow(screen, a.argument());
				case "menuButton" -> menuButton(renderer, node, st, a, screen, client);
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
					ButtonAction custom = CakeUIApi.action(a.kind());
					if (custom != null) {
						custom.run(a.argument(), screen);
					}
				}
			}
		} catch (RuntimeException e) {
			CakeUI.LOGGER.warn("CakeUI: action {} of {} failed", a.kind(), node, e);
			renderer.layout.problems.add(node.file, node.line, "action '" + a.kind() + "' failed: " + e);
		}
	}

	/**
	 * A container screen's button: vanilla's container-button click (what an enchanting table's or stonecutter's
	 * buttons send) with the layout's number, for the screen's own menu. The server calls the menu's clickMenuButton
	 * and ignores numbers the menu doesn't know. Nothing is sent on any other screen.
	 */
	private static void menuButton(Renderer renderer, Node node, NodeState st, Node.Action a, Screen screen, Minecraft client) {
		if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> c) || client.gameMode == null || client.player == null
				|| client.player.containerMenu != c.getMenu()) {
			return;
		}
		renderer.env.self = st;
		double id = a.value() == null ? -1 : Values.num(a.value().eval(renderer.env));
		if (id < 0 || id != Math.rint(id) || id > Integer.MAX_VALUE) {
			renderer.layout.problems.add(node.file, node.line, "menuButton: " + Values.format(id) + " isn't a button number (0 or more)");
			return;
		}
		client.gameMode.handleInventoryButtonClick(c.getMenu().containerId, (int) id);
	}

	/** The loader's mod list: Mod Menu on Fabric (when installed), NeoForge's own list. */
	private static void openModMenu(Minecraft client, Screen parent) {
		var mods = CakeUIClient.modsScreen;
		if (mods != null) {
			client.setScreen(mods.apply(parent));
		}
	}
}
