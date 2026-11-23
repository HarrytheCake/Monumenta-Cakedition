package io.github.harrythecake.cakemonu.fabric;

import ch.njol.unofficialmonumentamod.AbilityHandler;
import ch.njol.unofficialmonumentamod.UnofficialMonumentaModClient;
import ch.njol.unofficialmonumentamod.hud.AbilitiesHud;
import ch.njol.unofficialmonumentamod.options.Options;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.harrythecake.cakemonu.AbilitySource;
import io.github.harrythecake.cakemonu.CakeMonu;
import io.github.harrythecake.cakemonu.IconPlacement;
import io.github.harrythecake.cakemonu.fabric.mixin.AbilitiesHudInvoker;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Unofficial Monumenta Mod's ability bar as CakeMonu's {@link AbilitySource}, and the drawing of placed icons.
 * A placed icon is drawn by UMM's own bar code ({@link AbilitiesHudInvoker}) with only that ability visible,
 * moved and scaled to its place, so cooldowns, charges, flashes, duration bars and silence look exactly as in
 * UMM's bar. Only loaded when a supported UMM is installed ({@link UmmSupport}). Render thread.
 */
public final class UmmAbilities implements AbilitySource {
	/** While set, UMM's bar shows only this ability (CakeMonu is drawing it on its own). */
	private static String solo;
	/** Icons drawn in the last frame (self-test). */
	static int drawnLastFrame;
	private static boolean failed;

	@Override
	public State state() {
		return State.READY;
	}

	@Override
	public String version() {
		String v = UmmSupport.version();
		return v == null ? "" : v;
	}

	@Override
	public List<Ability> current() {
		List<Ability> list = new ArrayList<>();
		for (AbilityHandler.AbilityInfo i : snapshot()) {
			list.add(new Ability(i.getOrderId(), i.name == null ? "?" : i.name, i.className == null ? "" : i.className));
		}
		return list;
	}

	@Override
	public boolean barEnabled() {
		return options().abilitiesDisplay_enabled;
	}

	@Override
	public int iconSize() {
		return options().abilitiesDisplay_iconSize;
	}

	@Override
	public int iconGap() {
		return options().abilitiesDisplay_iconGap;
	}

	@Override
	public boolean horizontal() {
		return options().abilitiesDisplay_horizontal;
	}

	@Override
	public double[] barBox(int screenWidth, int screenHeight) {
		Object hud = AbilitiesHud.INSTANCE;
		if (hud == null || snapshot().isEmpty()) {
			return null;
		}
		try {
			if (getDimension == null) {
				getDimension = hud.getClass().getMethod("getDimension");
			}
			Rectangle r = (Rectangle) getDimension.invoke(hud);
			return r == null ? null : new double[] {r.x, r.y, r.width, r.height};
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * UMM's bar box comes from {@code HudElement.getDimension()}, which lives in UMM's bundled UI library: Loom
	 * doesn't put that nested jar on the compile classpath, so it's called by reflection.
	 */
	private static java.lang.reflect.Method getDimension;

	/** For the mixin: false = leave this ability out of UMM's bar; null = UMM decides. */
	public static Boolean visibility(String id, boolean forSpaceCalculation) {
		if (solo != null) {
			if (!solo.equals(id)) {
				return Boolean.FALSE;
			}
			return forSpaceCalculation ? Boolean.TRUE : null;
		}
		return CakeMonu.placed(id) ? Boolean.FALSE : null;
	}

	/** True when there are abilities and every one has its own place. */
	public static boolean allPlaced() {
		List<AbilityHandler.AbilityInfo> list = snapshot();
		if (list.isEmpty() || CakeMonu.layout().isEmpty()) {
			return false;
		}
		for (AbilityHandler.AbilityInfo i : list) {
			if (!CakeMonu.placed(i.getOrderId())) {
				return false;
			}
		}
		return true;
	}

	/** HUD callback: each placed icon of the current class, at its place, drawn by UMM's bar code. */
	static void drawPlaced(GuiGraphics g, float tickDelta) {
		drawnLastFrame = 0;
		if (failed || solo != null || CakeMonu.layout().isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Options o = options();
		Object hud = AbilitiesHud.INSTANCE;
		if (hud == null || !o.abilitiesDisplay_enabled || mc.options.hideGui) {
			return;
		}
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int ummSize = Math.max(1, o.abilitiesDisplay_iconSize);
		PoseStack pose = g.pose();
		for (AbilityHandler.AbilityInfo info : snapshot()) {
			String id = info.getOrderId();
			IconPlacement p = CakeMonu.layout().get(id);
			if (p == null) {
				continue;
			}
			double[] box = p.box(sw, sh, ummSize);
			float scale = (float) (box[2] / ummSize);
			pose.pushPose();
			pose.translate(box[0], box[1], 0);
			pose.scale(scale, scale, 1);
			solo = id;
			try {
				((AbilitiesHudInvoker) hud).cakemonu$render(g, tickDelta);
				drawnLastFrame++;
			} catch (RuntimeException | LinkageError e) {
				failed = true;
				CakeMonu.LOG.error("CakeMonu: drawing UMM's ability icon failed; placed icons are off until restart", e);
			} finally {
				solo = null;
				pose.popPose();
			}
		}
	}

	private static List<AbilityHandler.AbilityInfo> snapshot() {
		AbilityHandler h = UnofficialMonumentaModClient.abilityHandler;
		if (h == null) {
			return List.of();
		}
		synchronized (h) {
			return new ArrayList<>(h.abilityData);
		}
	}

	private static Options options() {
		return UnofficialMonumentaModClient.options;
	}
}
