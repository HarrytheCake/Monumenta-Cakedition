package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.Problems;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Dev mode: the layout's problems in the top-left corner of every screen and the HUD. */
final class Overlay {
	private static final int MAX_LINES = 10;

	private Overlay() {
	}

	static void draw(GuiGraphicsExtractor ctx) {
		if (!Ui.devMode()) {
			return;
		}
		List<Problems.Problem> problems = Ui.problems();
		if (problems.isEmpty()) {
			return;
		}
		Font tr = Minecraft.getInstance().font;
		int maxWidth = ctx.guiWidth() - 8;
		ctx.nextStratum();
		ctx.pose().pushMatrix();
		int y = 4;
		Component header = Component.translatable(Ui.keptPrevious() ? "cakeui.overlay.kept" : "cakeui.overlay.header", Ui.problemsLayoutName(), problems.size());
		y = line(ctx, tr, header.getString(), y, 0xFFFFFF55, maxWidth);
		for (int i = 0; i < Math.min(MAX_LINES, problems.size()); i++) {
			y = line(ctx, tr, problems.get(i).toString(), y, 0xFFFF7070, maxWidth);
		}
		if (problems.size() > MAX_LINES) {
			line(ctx, tr, Component.translatable("cakeui.overlay.more", problems.size() - MAX_LINES).getString(), y, 0xFFAAAAAA, maxWidth);
		}
		ctx.pose().popMatrix();
	}

	private static int line(GuiGraphicsExtractor ctx, Font tr, String text, int y, int color, int maxWidth) {
		String shown = tr.plainSubstrByWidth(text, maxWidth);
		ctx.fill(2, y - 1, 6 + tr.width(shown), y + 9, 0xC0000000);
		ctx.text(tr, shown, 4, y, color, false);
		return y + 10;
	}
}
