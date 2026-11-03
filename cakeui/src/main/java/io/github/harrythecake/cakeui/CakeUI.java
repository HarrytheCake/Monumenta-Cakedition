package io.github.harrythecake.cakeui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CakeUI: layouts that redraw Minecraft's menus, loading screens, HUD and window. With no layout
 * chosen (the default) nothing is drawn or changed.
 */
public final class CakeUI implements ClientModInitializer {
	public static final String MOD_ID = "cakeui";
	public static final Logger LOGGER = LoggerFactory.getLogger("CakeUI");
	private static KeyBinding reloadKey;
	private static KeyBinding buttonIdsKey;

	@Override
	public void onInitializeClient() {
		McValues.register();
		reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cakeui.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.cakeui"));
		buttonIdsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cakeui.buttonIds", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.cakeui"));
		Ui.reload();
		SelfTest.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (reloadKey.wasPressed()) {
				Ui.reload();
			}
			while (buttonIdsKey.wasPressed()) {
				Ui.config().showButtonIds = !Ui.config().showButtonIds;
			}
			Ui.tick();
		});
		HudRenderCallback.EVENT.register((context, delta) -> Overlay.draw(context));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> Overlay.draw(context));
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, modifiers) -> {
				if (reloadKey.matchesKey(key, scancode)) {
					Ui.reload();
				} else if (buttonIdsKey.matchesKey(key, scancode)) {
					Ui.config().showButtonIds = !Ui.config().showButtonIds;
				}
			});
			ScreenPorts ports = ScreenPorts.of(screen);
			if (ports != null) {
				ports.afterInit(screen);
				ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> !ports.click(s, button));
				ScreenMouseEvents.afterMouseRelease(screen).register((s, mouseX, mouseY, button) -> {
					if (Ui.renderer() != null) {
						Ui.renderer().mouseReleased();
					}
				});
			}
		});
	}

	static MinecraftClient client() {
		return MinecraftClient.getInstance();
	}
}
