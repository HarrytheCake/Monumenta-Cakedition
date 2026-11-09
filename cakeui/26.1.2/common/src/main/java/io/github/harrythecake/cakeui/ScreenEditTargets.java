package io.github.harrythecake.cakeui;

import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Screens the UI editor can show on their own (E6): a loading screen to edit its text (vanilla's "saving" message). */
final class ScreenEditTargets {
	private ScreenEditTargets() {
	}

	static Screen loadingPreview() {
		return new GenericMessageScreen(Component.translatable("menu.savingLevel"));
	}
}
