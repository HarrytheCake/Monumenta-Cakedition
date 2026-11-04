package io.github.harrythecake.cakeui.neoforge;

import io.github.harrythecake.cakeui.CakeUI;
import io.github.harrythecake.cakeui.CakeUIClient;
import io.github.harrythecake.cakeui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/**
 * NeoForge entry. Plugins come from META-INF/services (NeoForgePlugins), the HUD ports from
 * NeoForge's overlay events (NeoForgeHud), the settings from the mod list's Config button. The first
 * layout loads in client setup; NeoForge's own loading screen covers the first start, so the
 * game_launch part shows on resource reloads only.
 */
@Mod(CakeUI.MOD_ID)
public final class CakeUINeoForge {
	public CakeUINeoForge(IEventBus modBus) {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
		modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
			e.register(CakeUIClient.RELOAD_KEY);
			e.register(CakeUIClient.BUTTON_IDS_KEY);
		});
		modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(() -> {
			CakeUIClient.start();
			NeoForgePlugins.runAll();
			CakeUIClient.modsScreen = ModListScreen::new;
			CakeUIClient.afterPlugins();
		}));
		NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent.class, e -> {
			if (e.phase == TickEvent.Phase.END) {
				CakeUIClient.endClientTick(Minecraft.getInstance());
			}
		});
		NeoForgeHud.register();
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> CakeUIClient.afterScreenInit(e.getScreen()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Pre.class,
				e -> CakeUIClient.beforeScreenRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class,
				e -> CakeUIClient.afterScreenRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, e -> CakeUIClient.screenKeyPressed(e.getKeyCode(), e.getScanCode()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class, e -> {
			if (CakeUIClient.screenMouseClicked(e.getScreen(), e.getButton())) {
				e.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonReleased.Post.class, e -> CakeUIClient.screenMouseReleased(e.getScreen()));
	}
}
