package io.github.harrythecake.cakeui.fabric;

import io.github.harrythecake.cakeui.CakeUIClient;
import io.github.harrythecake.cakeui.api.CakeUIPlugin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;

/**
 * Fabric entry ("client" entrypoint). Plugins are the "cakeui" entrypoints of other mods
 * (fabric.mod.json); the HUD ports are mixins into Gui.render (InGameHudMixin, cakeui-fabric.mixins.json).
 */
public final class CakeUIFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeUIClient.start();
		// mods' plugins register their values, elements and actions before the first layout is read
		for (EntrypointContainer<CakeUIPlugin> plugin : FabricLoader.getInstance().getEntrypointContainers("cakeui", CakeUIPlugin.class)) {
			String owner = plugin.getProvider().getMetadata().getId();
			try {
				CakeUIClient.runPlugin(owner, plugin.getEntrypoint());
			} catch (RuntimeException | LinkageError e) {
				io.github.harrythecake.cakeui.CakeUI.LOGGER.error("CakeUI: the plugin of {} failed", owner, e);
			}
		}
		KeyMappingHelper.registerKeyMapping(CakeUIClient.RELOAD_KEY);
		KeyMappingHelper.registerKeyMapping(CakeUIClient.BUTTON_IDS_KEY);
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			CakeUIClient.modsScreen = CakeUIFabric::modMenu;
		}
		CakeUIClient.afterPlugins();

		ClientTickEvents.END_CLIENT_TICK.register(CakeUIClient::endClientTick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("cakeui", "problems"), (context, delta) -> CakeUIClient.afterHud(context));
		FabricHud.register();
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			CakeUIClient.afterScreenInit(screen);
			ScreenEvents.beforeExtract(screen).register((s, context, mouseX, mouseY, delta) -> CakeUIClient.beforeScreenRender(s, context, mouseX, mouseY));
			ScreenEvents.afterExtract(screen).register((s, context, mouseX, mouseY, delta) -> CakeUIClient.afterScreenRender(s, context, mouseX, mouseY));
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> CakeUIClient.screenKeyPressed(key));
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !CakeUIClient.screenMouseClicked(s, click.button()));
			ScreenMouseEvents.afterMouseRelease(screen).register((s, click, consumed) -> {
				CakeUIClient.screenMouseReleased(s);
				return consumed;
			});
		});
	}

	/** Mod Menu's mod list (Mod Menu is optional: reflection). */
	private static Screen modMenu(Screen parent) {
		try {
			Class<?> mods = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			return (Screen) mods.getConstructor(Screen.class).newInstance(parent);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Mod Menu's screen could not be opened", e);
		}
	}
}
