package io.github.harrythecake.cakemap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** CakeMap's short messages ("waypoint reached" …), under the minimap, never in the action bar. */
public final class HudNotice {
	private HudNotice() {
	}

	public static void draw(GuiGraphicsExtractor ctx, int w, int h) {
		Component t = CakeMap.currentNotice();
		Minecraft mc = Minecraft.getInstance();
		if (t == null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		int[] b = Minimap.box(w, h);
		int y = b[1] + b[2] + (CakeMap.settings().coordinates ? 14 : 4);
		int tw = mc.font.width(t);
		int x = Math.max(2, Math.min(w - tw - 2, b[0] + b[2] / 2 - tw / 2));
		if (!CakeMap.settings().minimap) {
			x = w / 2 - tw / 2;
			y = 4;
		}
		ctx.fill(x - 2, y - 2, x + tw + 2, y + 10, 0x90000000);
		ctx.text(mc.font, t, x, y, 0xFFFFFFFF);
	}
}
