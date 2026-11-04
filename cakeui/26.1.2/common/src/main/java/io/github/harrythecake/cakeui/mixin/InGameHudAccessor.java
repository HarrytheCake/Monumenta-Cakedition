package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The action bar and title texts, for {hud.actionbar}, {hud.title}, {hud.subtitle}. */
@Mixin(Gui.class)
public interface InGameHudAccessor {
	@Accessor("overlayMessageString")
	Component cakeui$overlayMessage();

	@Accessor("overlayMessageTime")
	int cakeui$overlayRemaining();

	@Accessor("title")
	Component cakeui$title();

	@Accessor("subtitle")
	Component cakeui$subtitle();

	@Accessor("titleTime")
	int cakeui$titleRemainTicks();
}
