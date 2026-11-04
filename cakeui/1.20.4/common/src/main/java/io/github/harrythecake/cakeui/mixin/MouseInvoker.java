package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the self-test move the mouse as if the player did. */
@Mixin(MouseHandler.class)
public interface MouseInvoker {
	@Invoker("onMove")
	void cakeui$onCursorPos(long window, double x, double y);
}
