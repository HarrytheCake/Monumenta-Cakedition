package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The action bar and title texts, for {hud.actionbar}, {hud.title}, {hud.subtitle}. */
@Mixin(InGameHud.class)
public interface InGameHudAccessor {
	@Accessor("overlayMessage")
	Text cakeui$overlayMessage();

	@Accessor("overlayRemaining")
	int cakeui$overlayRemaining();

	@Accessor("title")
	Text cakeui$title();

	@Accessor("subtitle")
	Text cakeui$subtitle();

	@Accessor("titleRemainTicks")
	int cakeui$titleRemainTicks();
}
