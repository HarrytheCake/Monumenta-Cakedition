package io.github.harrythecake.cakeui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.api.CakeUIPlugin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.UnaryOperator;

/**
 * CakeUI's client side, the same for both loaders: the loader's entry class calls {@link #start},
 * runs the plugins through {@link #runPlugin}, calls {@link #afterPlugins}, registers the two keys
 * and forwards its screen, HUD and tick events to the hooks below.
 */
public final class CakeUIClient {
	/** 26.1: key categories are registered ids (lang key key.category.cakeui.main). */
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("cakeui", "main"));
	public static final KeyMapping RELOAD_KEY = new KeyMapping("key.cakeui.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
	public static final KeyMapping BUTTON_IDS_KEY = new KeyMapping("key.cakeui.buttonIds", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
	/** The loader's mod list for the "modmenu" action (Mod Menu on Fabric, NeoForge's own list), or null. */
	@Nullable
	public static UnaryOperator<Screen> modsScreen;

	private CakeUIClient() {
	}

	/** First: CakeUI's own game values. */
	public static void start() {
		McValues.register();
		CakeUIApi.setReloader(Ui::reload);
	}

	/** A mod's plugin registers its values, elements and actions before the first layout is read. */
	public static void runPlugin(String owner, CakeUIPlugin plugin) {
		try {
			plugin.register(CakeUIApi.get());
		} catch (RuntimeException | LinkageError e) {
			CakeUI.LOGGER.error("CakeUI: the plugin of {} failed", owner, e);
		}
	}

	/** After every plugin: the first layout load (render thread). */
	public static void afterPlugins() {
		Ui.reload();
		SelfTest.init();
	}

	// ---- hooks the loaders call ----

	/** End of every client tick. */
	public static void endClientTick(Minecraft client) {
		SelfTest.tick(client);
		while (RELOAD_KEY.consumeClick()) {
			Ui.reload();
		}
		while (BUTTON_IDS_KEY.consumeClick()) {
			Ui.config().showButtonIds = !Ui.config().showButtonIds;
		}
		Ui.tick();
		WindowPart.tick(client);
	}

	/** After the whole HUD: dev mode's problem list. */
	public static void afterHud(GuiGraphicsExtractor context) {
		Overlay.draw(context);
	}

	/** After a screen's init (and every resize). */
	public static void afterScreenInit(Screen screen) {
		SelfTest.screenInit(screen);
		ScreenPorts ports = ScreenPorts.of(screen);
		if (ports != null) {
			ports.afterInit(screen);
		}
	}

	/** Before a screen draws. The loading screens (one has no render method of its own) start their frame here. */
	public static void beforeScreenRender(Screen screen, GuiGraphicsExtractor context, int mouseX, int mouseY) {
		if (ScreenPorts.of(screen) == ScreenPorts.LOADING) {
			LoadingPart.beginFrame(screen, mouseX, mouseY);
		}
	}

	/** After a screen drew (tooltips included). */
	public static void afterScreenRender(Screen screen, GuiGraphicsExtractor context, int mouseX, int mouseY) {
		if (ScreenPorts.of(screen) == ScreenPorts.LOADING) {
			ScreenPorts.LOADING.drawElements(screen, context, "front", 1);
			ScreenPorts.drawButtonIds(screen, context);
		}
		Overlay.draw(context);
		SelfTest.afterScreenRender(screen, context);
	}

	/** A key pressed while a screen is open: the reload and button-id keys work there too. */
	public static void screenKeyPressed(net.minecraft.client.input.KeyEvent key) {
		if (RELOAD_KEY.matches(key)) {
			Ui.reload();
		} else if (BUTTON_IDS_KEY.matches(key)) {
			Ui.config().showButtonIds = !Ui.config().showButtonIds;
		}
	}

	/** A mouse click on a screen: true when a CakeUI button took it (then the screen must not see it). */
	public static boolean screenMouseClicked(Screen screen, int button) {
		ScreenPorts ports = ScreenPorts.of(screen);
		return ports != null && ports.click(screen, button);
	}

	public static void screenMouseReleased(Screen screen) {
		if (ScreenPorts.of(screen) != null && Ui.renderer() != null) {
			Ui.renderer().mouseReleased();
		}
	}

	static Minecraft client() {
		return Minecraft.getInstance();
	}
}
