package io.github.harrythecake.cakebench;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** The Minecraft calls that differ between versions (this is the 1.20.4 copy; each version folder has its own). */
final class Compat {
	private Compat() {}

	static String renderer() {
		return GlUtil.getRenderer();
	}

	static String glVersion() {
		return GlUtil.getOpenGLVersion();
	}

	static boolean onRenderThread() {
		return RenderSystem.isOnRenderThread();
	}

	static String graphics(Minecraft client) {
		return client.options.graphicsMode().get().toString();
	}

	static String entityStatistics(Minecraft client) {
		return client.levelRenderer.getEntityStatistics();
	}

	static String dimension(ClientLevel level) {
		return level.dimension().location().toString();
	}

	static boolean minimized(Minecraft client) {
		return GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_ICONIFIED) != 0;
	}

	static boolean isOperator(LocalPlayer player) {
		return player.hasPermissions(2);
	}

	/** Joins {@code server}; a live run declines the server's resource pack, so its prompt can't block the join. */
	static void connect(Minecraft client, Screen parent, String server, boolean live) {
		ServerData info = new ServerData("CakeBench", server, ServerData.Type.OTHER);
		if (live) {
			info.setResourcePackStatus(ServerData.ServerPackStatus.DISABLED);
		}
		ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(server), info, false);
	}

	/** Runs {@code task} on the client thread later (never right away, even when called on that thread). */
	static void later(Minecraft client, Runnable task) {
		client.tell(task);
	}

	static ClickEvent openFile(String path) {
		return new ClickEvent(ClickEvent.Action.OPEN_FILE, path);
	}

	/** Chat for the player only (never sent). */
	static void tellPlayer(LocalPlayer player, Component text) {
		player.displayClientMessage(text, false);
	}

	/** Adds a line to the chat window (what a client command's feedback does). */
	static void chat(Minecraft client, Component text) {
		client.gui.getChat().addMessage(text);
		client.getNarrator().sayNow(text);
	}

	/** Saves a screenshot to screenshots/{@code name} (self-test). */
	static void screenshot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
				message -> CakeBench.LOG.info("CakeBench self-test screenshot: {}", message.getString()));
	}
}
