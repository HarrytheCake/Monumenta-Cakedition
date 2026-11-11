package io.github.harrythecake.cakecosmetic.neoforge;

import io.github.harrythecake.cakecosmetic.Backgrounds;
import io.github.harrythecake.cakecosmetic.CakeCosmetic;
import io.github.harrythecake.cakecosmetic.GalleryScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entry: CakeCosmetic's hooks on NeoForge's events, as Fabric API's are on Fabric. The CakeUI plugin is
 * listed in META-INF/services (CakeUI's NeoForge part loads it); the mod list's Config button opens the gallery.
 */
@Mod(value = CakeCosmetic.MOD_ID, dist = Dist.CLIENT)
public final class CakeCosmeticNeoForge {
	public CakeCosmeticNeoForge(IEventBus modBus, ModContainer container) {
		CakeCosmetic.init();
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> GalleryScreen.open(parent));
		modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Backgrounds.ID, CakeCosmetic.backgrounds()));
		modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
			KeyMapping.Category category = new KeyMapping.Category(CakeCosmetic.KEY_CATEGORY);
			e.registerCategory(category);
			e.register(CakeCosmetic.createKey(category));
		});
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> CakeCosmetic.endClientTick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class,
				e -> CakeCosmetic.afterScreenInit(e.getScreen(), e.getScreen().width, e::addListener));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, e -> {
			if (CakeCosmetic.selfTestRunning()) {
				CakeCosmetic.afterScreenRender(e.getScreen(), e.getGuiGraphics());
			}
		});
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> CakeCosmetic.onDisconnect(Minecraft.getInstance()));
	}
}
