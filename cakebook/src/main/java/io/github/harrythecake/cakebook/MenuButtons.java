package io.github.harrythecake.cakebook;

import io.github.harrythecake.cakebook.mixin.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/** The small book buttons: next to "Back to Game" in the pause menu, and to the right of the inventory. */
final class MenuButtons {
	private MenuButtons() {}

	/** A 20×20 button showing a book item. */
	static final class BookButton extends ButtonWidget {
		private static final ItemStack ICON = new ItemStack(Items.BOOK);

		BookButton(int x, int y, PressAction onPress) {
			super(x, y, 20, 20, Text.translatable("cakebook.title"), onPress, DEFAULT_NARRATION_SUPPLIER);
			setTooltip(Tooltip.of(Text.translatable("cakebook.button.hover")));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			super.renderWidget(context, mouseX, mouseY, delta);
			context.drawItem(ICON, getX() + 2, getY() + 2);
		}

		@Override
		public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
			// the icon is the label
		}
	}

	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof GameMenuScreen menu && menu.shouldShowMenu() && CakeBook.config().pauseMenuButton) {
				addToPauseMenu(client, screen);
			} else if (screen instanceof InventoryScreen && CakeBook.config().inventoryButton) {
				addToInventory(client, screen);
			}
		});
	}

	private static void addToPauseMenu(MinecraftClient client, Screen screen) {
		int x = 4;
		int y = 4;
		for (ClickableWidget widget : Screens.getButtons(screen)) {
			if (widget.getMessage().getContent() instanceof TranslatableTextContent t && t.getKey().equals("menu.returnToGame")) {
				x = widget.getX() - 24;
				y = widget.getY();
				break;
			}
		}
		Screens.getButtons(screen).add(new BookButton(x, y, b -> client.setScreen(new BookScreen(screen, null, null))));
	}

	private static void addToInventory(MinecraftClient client, Screen screen) {
		HandledScreenAccessor inventory = (HandledScreenAccessor) screen;
		BookButton button = new BookButton(0, 0, b -> client.setScreen(new BookScreen(screen, null, null)));
		Runnable place = () -> button.setPosition(inventory.cakebook$x() + inventory.cakebook$backgroundWidth() + 2,
				inventory.cakebook$y());
		place.run();
		Screens.getButtons(screen).add(button);
		// the inventory moves sideways when the recipe book opens or closes
		ScreenEvents.beforeRender(screen).register((s, context, mouseX, mouseY, delta) -> place.run());
	}
}
