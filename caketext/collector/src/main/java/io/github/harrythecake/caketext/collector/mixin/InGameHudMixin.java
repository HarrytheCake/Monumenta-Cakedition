package io.github.harrythecake.caketext.collector.mixin;

import io.github.harrythecake.caketext.collector.CollectorMod;
import io.github.harrythecake.caketext.core.context.Surfaces;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Titles and subtitles. Priority 900 so this runs before the translator replaces the text. */
@Mixin(value = InGameHud.class, priority = 900)
public abstract class InGameHudMixin {
	@Inject(method = "setTitle", at = @At("HEAD"))
	private void caketext$collectTitle(Text title, CallbackInfo ci) {
		CollectorMod.collect(Surfaces.TITLE, title, null);
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"))
	private void caketext$collectSubtitle(Text subtitle, CallbackInfo ci) {
		CollectorMod.collect(Surfaces.SUBTITLE, subtitle, null);
	}
}
