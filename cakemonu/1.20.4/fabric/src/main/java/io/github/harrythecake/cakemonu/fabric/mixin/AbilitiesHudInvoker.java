package io.github.harrythecake.cakemonu.fabric.mixin;

import ch.njol.unofficialmonumentamod.hud.AbilitiesHud;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** UMM's own drawing of its ability bar, which CakeMonu calls once per placed icon (with only that icon visible). */
@Mixin(value = AbilitiesHud.class, remap = false)
public interface AbilitiesHudInvoker {
	@Invoker("render")
	void cakemonu$render(GuiGraphics graphics, float tickDelta);
}
