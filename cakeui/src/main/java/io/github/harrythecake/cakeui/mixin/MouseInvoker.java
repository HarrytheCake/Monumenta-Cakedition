package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the self-test move the mouse as if the player did. */
@Mixin(Mouse.class)
public interface MouseInvoker {
	@Invoker("onCursorPos")
	void cakeui$onCursorPos(long window, double x, double y);
}
