package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.Problems;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Dev mode: the layout's problems in the top-left corner of every screen and the HUD. */
final class Overlay {
	private static final int MAX_LINES = 10;

	private Overlay() {
	}

	static void draw(GuiGraphics ctx) {
		if (!Ui.devMode()) {
			return;
		}
		List<Problems.Problem> problems = Ui.problems();
		java.util.Map<String, List<Problems.Problem>> mods = ContainerPart.modProblems();
		if (problems.isEmpty() && mods.isEmpty()) {
			return;
		}
		Font tr = Minecraft.getInstance().font;
		int maxWidth = ctx.guiWidth() - 8;
		ctx.pose().pushPose();
		ctx.pose().translate(0, 0, 500);
		int y = 4;
		if (!problems.isEmpty()) {
			Component header = Component.translatable(Ui.keptPrevious() ? "cakeui.overlay.kept" : "cakeui.overlay.header", Ui.problemsLayoutName(), problems.size());
			y = block(ctx, tr, header, problems, y, maxWidth);
		}
		// a mod's default container layouts (assets/<modid>/cakeui/): for that mod's developers
		for (java.util.Map.Entry<String, List<Problems.Problem>> e : mods.entrySet()) {
			y = block(ctx, tr, Component.translatable("cakeui.overlay.mod", e.getKey(), e.getValue().size()), e.getValue(), y, maxWidth);
		}
		ctx.pose().popPose();
	}

	private static int block(GuiGraphics ctx, Font tr, Component header, List<Problems.Problem> problems, int y, int maxWidth) {
		y = line(ctx, tr, header.getString(), y, 0xFFFFFF55, maxWidth);
		for (int i = 0; i < Math.min(MAX_LINES, problems.size()); i++) {
			y = line(ctx, tr, problems.get(i).toString(), y, 0xFFFF7070, maxWidth);
		}
		if (problems.size() > MAX_LINES) {
			y = line(ctx, tr, Component.translatable("cakeui.overlay.more", problems.size() - MAX_LINES).getString(), y, 0xFFAAAAAA, maxWidth);
		}
		return y;
	}

	private static int line(GuiGraphics ctx, Font tr, String text, int y, int color, int maxWidth) {
		String shown = tr.plainSubstrByWidth(text, maxWidth);
		ctx.fill(2, y - 1, 6 + tr.width(shown), y + 9, 0xC0000000);
		ctx.drawString(tr, shown, 4, y, color, false);
		return y + 10;
	}
}
