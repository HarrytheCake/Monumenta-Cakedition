package io.github.harrythecake.caketext.translator.mixin;

import java.util.List;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a model translation replace a chat line that was first shown in English. */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
	@Accessor("messages")
	List<ChatHudLine> caketext$messages();

	/** Rebuilds the wrapped visible lines from {@code messages}. */
	@Invoker("refresh")
	void caketext$refresh();
}
