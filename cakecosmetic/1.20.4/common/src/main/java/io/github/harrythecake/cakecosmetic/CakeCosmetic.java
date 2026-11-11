package io.github.harrythecake.cakecosmetic;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * The mod's start and its hooks, called by each loader's entry (fabric: CakeCosmeticFabric, neoforge:
 * CakeCosmeticNeoForge): files, the gallery key, the main menu's gallery button and the connection
 * events. The background list is the Backgrounds reload listener; drawing goes through CakeUI
 * (CakeCosmeticPlugin).
 */
public final class CakeCosmetic {
	public static final String MOD_ID = CakeCosmeticCore.MOD_ID;
	public static final Logger LOG = CakeCosmeticCore.LOG;
	private static KeyMapping galleryKey;

	private CakeCosmetic() {
	}

	/** At client start, before any resource reload. */
	public static void init() {
		Unlocks.load();
		SelfTest.init();
	}

	/** The gallery key (unbound by default), made once; the loader registers it. */
	public static KeyMapping galleryKey() {
		if (galleryKey == null) {
			galleryKey = new KeyMapping("key.cakecosmetic.gallery", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.cakecosmetic");
		}
		return galleryKey;
	}

	/** A new Backgrounds reload listener for the loader to register. */
	public static Backgrounds backgrounds() {
		return new Backgrounds();
	}

	/** After each client tick. */
	public static void endClientTick(Minecraft client) {
		while (galleryKey().consumeClick()) {
			client.setScreen(GalleryScreen.open(client.screen));
		}
		SelfTest.tick(client);
	}

	/** After a screen's init: the gallery button, a 20x20 icon in the main menu's top-right corner. */
	public static void afterScreenInit(Screen screen, int width, Consumer<AbstractWidget> addButton) {
		if (screen instanceof TitleScreen) {
			addButton.accept(new GalleryButton(width - 24, 4, screen));
		}
		SelfTest.afterScreenInit(screen);
	}

	/** Only while the self-test runs: the loaders hook screen rendering just then. */
	public static boolean selfTestRunning() {
		return SelfTest.running();
	}

	/** After a screen rendered (self-test only). */
	public static void afterScreenRender(Screen screen, GuiGraphics ctx) {
		SelfTest.afterScreenRender(screen, ctx);
	}

	/** When the connection to a server or world ends (any thread: the work moves to the render thread). */
	public static void onDisconnect(Minecraft client) {
		client.execute(() -> {
			Unlocks.onDisconnect();
			MenuBackground.prepare();
		});
	}
}
