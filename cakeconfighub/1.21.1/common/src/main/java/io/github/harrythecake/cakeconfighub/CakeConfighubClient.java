package io.github.harrythecake.cakeconfighub;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.harrythecake.cakeconfighub.configmap.ConfigHub;
import io.github.harrythecake.cakeconfighub.configmap.ConfigMaps;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The client side shared by both loaders: the hub's first screen, the "Open CakeConfighub" key (unbound by default),
 * {@code /cakeconfighub} (opens the hub next tick, after the chat screen closed) and {@code /cakeconfighub reload}.
 * The loader part registers the key and calls {@link #endClientTick} (Fabric: only with Fabric API installed).
 */
public final class CakeConfighubClient {
	/** Unbound by default (the pack sets none); in its own category. */
	public static final KeyMapping OPEN_KEY = new KeyMapping("key.cakeconfighub.open", InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN, "key.categories.cakeconfighub");
	private static boolean openNextTick;

	private CakeConfighubClient() {
	}

	/** The hub's first screen: the list of mods (Mod Menu's Configure, NeoForge's Config button, the key, the command). */
	public static Screen hubScreen(@Nullable Screen parent) {
		return new ConfigHubScreen(parent);
	}

	/** {@code /cakeconfighub}: the chat screen is still open while the command runs, so the hub opens on the next tick. */
	public static void openNextTick() {
		openNextTick = true;
	}

	/** {@code /cakeconfighub reload}: reads the config maps again (pack defaults stay a startup thing). */
	public static Component reloadMaps() {
		ConfigMaps.Loaded maps = ConfigHub.reloadMaps();
		return Component.translatable("cakeconfighub.command.reloaded", maps.maps().size(), maps.problems().size());
	}

	public static void endClientTick(Minecraft client) {
		if (openNextTick) {
			openNextTick = false;
			client.setScreen(hubScreen(client.screen));
		}
		while (OPEN_KEY.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(hubScreen(null));
			}
		}
		ConfigHubSelfTest.tick(client);
	}
}
