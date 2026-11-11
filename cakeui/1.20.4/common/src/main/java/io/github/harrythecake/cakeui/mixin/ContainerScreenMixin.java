package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ContainerPart;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Container screens (`container/<namespace>/<path>/`): the panel's size and place, the panel itself (renderBg),
 * slot skins, the two title lines and the front layer. Every hook asks ContainerPart, which answers "vanilla"
 * for a screen neither the layout nor a mod lays out; then each hook calls vanilla unchanged. The slot highlight
 * differs between loaders (NeoForge has its own method), so it is in each loader's mixins.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {
	protected ContainerScreenMixin(Component title) {
		super(title);
	}

	private AbstractContainerScreen<?> cakeui$self() {
		return (AbstractContainerScreen<?>) (Object) this;
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void cakeui$size(CallbackInfo ci) {
		ContainerPart.beforeInit(cakeui$self());
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void cakeui$place(CallbackInfo ci) {
		ContainerPart.afterBaseInit(cakeui$self());
	}

	@WrapOperation(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
	private void cakeui$panel(AbstractContainerScreen<?> self, GuiGraphics context, float delta, int mouseX, int mouseY, Operation<Void> original) {
		ContainerPart.drawPanel(self, context, () -> original.call(self, context, delta, mouseX, mouseY));
	}

	@Inject(method = "renderSlot", at = @At("HEAD"))
	private void cakeui$slotSkin(GuiGraphics context, Slot slot, CallbackInfo ci) {
		ContainerPart.drawSlotSkin(cakeui$self(), context, slot);
	}

	@WrapOperation(method = "renderLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I", ordinal = 0))
	private int cakeui$title(GuiGraphics context, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
		return ContainerPart.label(cakeui$self(), "title", context, font, text, x, y, color, shadow, (t, c) -> original.call(context, font, t, x, y, c, shadow));
	}

	@WrapOperation(method = "renderLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I", ordinal = 1))
	private int cakeui$inventoryTitle(GuiGraphics context, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
		return ContainerPart.label(cakeui$self(), "inventoryTitle", context, font, text, x, y, color, shadow, (t, c) -> original.call(context, font, t, x, y, c, shadow));
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V", shift = At.Shift.AFTER))
	private void cakeui$front(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ContainerPart.drawFront(cakeui$self(), context);
	}
}
