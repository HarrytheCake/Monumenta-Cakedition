package io.github.harrythecake.cakeupdater;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown while the system's folder window for "/cakeupdate import" is open. A screen frees the mouse (in play the game
 * hides and locks the cursor, so the player couldn't reach the folder window) and says where to look. It has no close
 * button and ignores Esc: closing it early would lock the cursor again while the folder window is still open.
 * {@link CakeUpdater} closes it when the folder window returns (chosen, cancelled or failed).
 */
final class ImportPickScreen extends Screen {
	private MultiLineLabel text = MultiLineLabel.EMPTY;

	ImportPickScreen() {
		super(Component.translatable("cakeupdater.import.picker.title"));
	}

	@Override
	protected void init() {
		text = MultiLineLabel.create(font, Component.translatable("cakeupdater.import.picker.waiting"), Math.min(width - 40, 360));
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
		text.renderCentered(ctx, width / 2, height / 2 - 30, 10, 0xB0B0B0);
	}

	/** The folder window has closed: leave this screen if the player is still on it. */
	static void finished(Minecraft client) {
		if (client.screen instanceof ImportPickScreen s) {
			s.onClose();
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
