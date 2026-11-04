package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.cakeui.PluginPorts;
import io.github.harrythecake.cakeui.ScreenPorts;
import io.github.harrythecake.cakeui.TitleMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The main menu's ports. Every hook first asks TitleMenu whether the active layout mentions the
 * piece; with no layout each one just calls vanilla.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Shadow
	private long fadeInStart;
	@Shadow
	private boolean fading;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	private float cakeui$seconds() {
		if (!fading || fadeInStart == 0L) {
			return fading ? 0 : 2;
		}
		return (Util.getMillis() - fadeInStart) / 1000.0F;
	}

	/** How far the menu has faded in (vanilla's g). */
	private float cakeui$fade() {
		return fading ? Mth.clamp(cakeui$seconds() - 1.0F, 0.0F, 1.0F) : 1.0F;
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (TitleMenu.part() != null) {
			TitleMenu.beginFrame(this, mouseX, mouseY, cakeui$fade());
		}
	}

	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/SplashManager;getSplash()Lnet/minecraft/client/gui/components/SplashRenderer;"))
	private SplashRenderer cakeui$splash(SplashManager supplier, Operation<SplashRenderer> original) {
		if (TitleMenu.port("splash") == null) {
			return original.call(supplier);
		}
		return TitleMenu.pickSplash(supplier);
	}

	/** Since 1.20.5 the panorama and its overlay are one call (renderPanorama); a layout's background elements follow it. */
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
	private void cakeui$panorama(TitleScreen self, GuiGraphics context, float delta, Operation<Void> original) {
		float alpha = Mth.clamp(cakeui$seconds(), 0, 1);
		boolean mentioned = TitleMenu.port("background") != null;
		if (PluginPorts.fill(PluginPorts.MAIN_MENU_BACKGROUND, mentioned, context, this, alpha)) {
			return;
		}
		if (!mentioned) {
			original.call(self, context, delta);
			return;
		}
		if (TitleMenu.vanillaBackground()) {
			original.call(self, context, delta);
		}
		context.setColor(1, 1, 1, 1);
		TitleMenu.drawBackground(context, alpha);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
	private void cakeui$logo(LogoRenderer logo, GuiGraphics context, int screenWidth, float alpha, Operation<Void> original) {
		if (TitleMenu.port("logo") == null) {
			original.call(logo, context, screenWidth, alpha);
			return;
		}
		float a = alpha * TitleMenu.portOpacity("logo");
		TitleMenu.vanillaPiece("logo", context, screenWidth / 2.0, 52, () -> original.call(logo, context, screenWidth, a));
		TitleMenu.drawPortElements("logo", context, alpha);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"))
	private void cakeui$splashDraw(SplashRenderer splash, GuiGraphics context, int screenWidth, Font textRenderer, int alpha,
			Operation<Void> original) {
		if (TitleMenu.port("splash") == null) {
			original.call(splash, context, screenWidth, textRenderer, alpha);
			return;
		}
		float fade = ((alpha >>> 24) & 255) / 255.0F;
		if (!TitleMenu.drawSplash(context, fade)) {
			int a = Mth.ceil(fade * TitleMenu.portOpacity("splash") * 255.0F) << 24;
			TitleMenu.vanillaPiece("splash", context, screenWidth / 2.0 + 123, 69, () -> original.call(splash, context, screenWidth, textRenderer, a));
			TitleMenu.drawPortElements("splash", context, fade);
		}
	}

	// require = 0: NeoForge draws its branding lines (version included) in lambdas, so there the port does nothing
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"), require = 0)
	private int cakeui$version(GuiGraphics context, Font textRenderer, String text, int x, int y, int color, Operation<Integer> original) {
		if (TitleMenu.port("version") == null) {
			return original.call(context, textRenderer, text, x, y, color);
		}
		String shown = TitleMenu.versionText(text);
		int shownColor = TitleMenu.PORTS.color("version", color);
		int[] out = {0};
		TitleMenu.vanillaPiece("version", context, x, y, () -> out[0] = original.call(context, textRenderer, shown, x, y, shownColor));
		TitleMenu.drawPortElements("version", context, ((color >>> 24) & 255) / 255.0F);
		return out[0];
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void cakeui$back(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		TitleMenu.drawElements(this, context, "back", cakeui$fade());
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$front(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$fade() > 0) {
			TitleMenu.drawElements(this, context, "front", cakeui$fade());
		}
		ScreenPorts.drawButtonIds(this, context);
	}
}
