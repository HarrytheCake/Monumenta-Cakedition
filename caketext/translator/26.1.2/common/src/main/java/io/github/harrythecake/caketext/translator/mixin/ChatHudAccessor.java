package io.github.harrythecake.caketext.translator.mixin;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a model translation replace a chat line that was first shown in English. */
@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
	@Accessor("allMessages")
	List<GuiMessage> caketext$messages();

	/** Rebuilds the wrapped visible lines from {@code messages}. */
	@Invoker("refreshTrimmedMessages")
	void caketext$refresh();
}
