package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.Problems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/** Dev mode: the layout's problems in the top-left corner of every screen and the HUD. */
final class Overlay {
	private static final int MAX_LINES = 10;

	private Overlay() {
	}

	static void draw(DrawContext ctx) {
		if (!Ui.devMode()) {
			return;
		}
		List<Problems.Problem> problems = Ui.problems();
		if (problems.isEmpty()) {
			return;
		}
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		int maxWidth = ctx.getScaledWindowWidth() - 8;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 500);
		int y = 4;
		Text header = Text.translatable(Ui.keptPrevious() ? "cakeui.overlay.kept" : "cakeui.overlay.header", Ui.problemsLayoutName(), problems.size());
		y = line(ctx, tr, header.getString(), y, 0xFFFFFF55, maxWidth);
		for (int i = 0; i < Math.min(MAX_LINES, problems.size()); i++) {
			y = line(ctx, tr, problems.get(i).toString(), y, 0xFFFF7070, maxWidth);
		}
		if (problems.size() > MAX_LINES) {
			line(ctx, tr, Text.translatable("cakeui.overlay.more", problems.size() - MAX_LINES).getString(), y, 0xFFAAAAAA, maxWidth);
		}
		ctx.getMatrices().pop();
	}

	private static int line(DrawContext ctx, TextRenderer tr, String text, int y, int color, int maxWidth) {
		String shown = tr.trimToWidth(text, maxWidth);
		ctx.fill(2, y - 1, 6 + tr.getWidth(shown), y + 9, 0xC0000000);
		ctx.drawText(tr, shown, 4, y, color, false);
		return y + 10;
	}
}
