package io.github.harrythecake.cakecosmetic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceType;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint: files, the background list (resource reloads), the gallery key, the main
 * menu's gallery button and the connection events. Drawing goes through CakeUI (CakeCosmeticPlugin).
 */
public final class CakeCosmetic implements ClientModInitializer {
	public static final String MOD_ID = "cakecosmetic";
	public static final Logger LOG = LoggerFactory.getLogger("CakeCosmetic");
	private static KeyBinding galleryKey;

	@Override
	public void onInitializeClient() {
		Unlocks.load();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new Backgrounds());
		galleryKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.cakecosmetic.gallery", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.cakecosmetic"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (galleryKey.wasPressed()) {
				client.setScreen(GalleryScreen.open(client.currentScreen));
			}
		});
		// the gallery button: a 20x20 icon in the main menu's top-right corner
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				Screens.getButtons(screen).add(new GalleryButton(width - 24, 4, screen));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			Unlocks.onDisconnect();
			MenuBackground.prepare();
		}));
		SelfTest.init();
	}

	public static KeyBinding galleryKey() {
		return galleryKey;
	}
}
