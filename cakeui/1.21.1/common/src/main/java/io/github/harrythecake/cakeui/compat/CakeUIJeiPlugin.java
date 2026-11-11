package io.github.harrythecake.cakeui.compat;

import io.github.harrythecake.cakeui.ContainerPart;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional JEI plugin (JEI finds it by its annotation on NeoForge and the "jei_mod_plugin" entrypoint on Fabric;
 * without JEI nothing loads this class). A container screen keeps its real panel box (leftPos, topPos, imageWidth,
 * imageHeight), so JEI's lists, recipe clicks and ghost dragging work as usual; this only tells JEI about what a
 * container layout draws outside that box (the panel's elements, moved slots), so its lists stay clear of it.
 */
@JeiPlugin
public final class CakeUIJeiPlugin implements IModPlugin {
	@Override
	public ResourceLocation getPluginUid() {
		return ResourceLocation.fromNamespaceAndPath("cakeui", "container_layouts");
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
				List<int[]> areas = ContainerPart.extraAreas(screen);
				if (areas.isEmpty()) {
					return List.of();
				}
				List<Rect2i> out = new ArrayList<>(areas.size());
				for (int[] a : areas) {
					out.add(new Rect2i(a[0], a[1], a[2], a[3]));
				}
				return out;
			}
		});
	}
}
