package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.api.CakeUIPlugin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
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
		CakeUIApi.setReloader(Ui::reload);
		// mods' plugins register their values, elements and actions before the first layout is read
		for (EntrypointContainer<CakeUIPlugin> plugin : FabricLoader.getInstance().getEntrypointContainers("cakeui", CakeUIPlugin.class)) {
			try {
				plugin.getEntrypoint().register(CakeUIApi.get());
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("CakeUI: the plugin of {} failed", plugin.getProvider().getMetadata().getId(), e);
			}
		}
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
			WindowPart.tick(client);
		});
		HudRenderCallback.EVENT.register((context, delta) -> Overlay.draw(context));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ScreenPorts ports = ScreenPorts.of(screen);
			if (ports == ScreenPorts.LOADING) {
				// several vanilla screens, one without a render method of its own: Fabric events instead of mixins
				ScreenEvents.beforeRender(screen).register((s, context, mouseX, mouseY, delta) -> LoadingPart.beginFrame(s, mouseX, mouseY));
				ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> {
					ScreenPorts.LOADING.drawElements(s, context, "front", 1);
					ScreenPorts.drawButtonIds(s, context);
				});
			}
			ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> Overlay.draw(context));
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, modifiers) -> {
				if (reloadKey.matchesKey(key, scancode)) {
					Ui.reload();
				} else if (buttonIdsKey.matchesKey(key, scancode)) {
					Ui.config().showButtonIds = !Ui.config().showButtonIds;
				}
			});
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
