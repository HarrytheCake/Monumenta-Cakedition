package io.github.harrythecake.cakebook;

import io.github.harrythecake.cakebook.mixin.HandledScreenAccessor;
import io.github.harrythecake.cakebook.mixin.RecipeBookScreenAccessor;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** The small book buttons: next to "Back to Game" in the pause menu, and to the left of the inventory. */
public final class MenuButtons {
	private MenuButtons() {}

	/** A 20×20 button showing a book item. */
	static final class BookButton extends Button {
		private static final ItemStack ICON = new ItemStack(Items.BOOK);

		BookButton(int x, int y, OnPress onPress) {
			super(x, y, 20, 20, Component.translatable("cakebook.title"), onPress, DEFAULT_NARRATION);
			setTooltip(Tooltip.create(Component.translatable("cakebook.button.hover")));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
			// the button's background, then the icon as the label
			extractDefaultSprite(context);
			context.item(ICON, getX() + 2, getY() + 2);
		}
	}

	/**
	 * Adds the book button to a screen that has just been set up (the loader's "after init" event).
	 *
	 * @param add puts a widget on the screen (drawn, clickable, focusable)
	 * @return what to run before each frame of that screen (the inventory button follows the recipe book), or null
	 */
	public static @Nullable Runnable afterInit(Screen screen, Consumer<AbstractWidget> add) {
		Minecraft client = Minecraft.getInstance();
		if (screen instanceof PauseScreen menu && menu.showsPauseMenu() && CakeBook.config().pauseMenuButton) {
			addToPauseMenu(client, screen, add);
		} else if (screen instanceof InventoryScreen && CakeBook.config().inventoryButton) {
			return addToInventory(client, screen, add);
		}
		return null;
	}

	private static void addToPauseMenu(Minecraft client, Screen screen, Consumer<AbstractWidget> add) {
		int x = 4;
		int y = 4;
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget.getMessage().getContents() instanceof TranslatableContents t
					&& t.getKey().equals("menu.returnToGame")) {
				x = widget.getX() - 24;
				y = widget.getY();
				break;
			}
		}
		add.accept(new BookButton(x, y, b -> client.setScreen(new BookScreen(screen, null, null))));
	}

	/** Width of the open recipe book, and how far vanilla shifts it left of the centre (RecipeBookComponent). */
	private static final int RECIPE_BOOK_WIDTH = 147;
	private static final int RECIPE_BOOK_SHIFT = 86;

	private static Runnable addToInventory(Minecraft client, Screen screen, Consumer<AbstractWidget> add) {
		HandledScreenAccessor inventory = (HandledScreenAccessor) screen;
		InventoryScreen inv = (InventoryScreen) screen;
		BookButton button = new BookButton(0, 0, b -> client.setScreen(new BookScreen(screen, null, null)));
		// left of the inventory (the right side is where vanilla lists status effects); left of the recipe book while it is open
		Runnable place = () -> {
			int left = inventory.cakebook$x();
			if (((RecipeBookScreenAccessor) inv).cakebook$recipeBook().isVisible()) {
				left = Math.min(left, (screen.width - RECIPE_BOOK_WIDTH) / 2 - RECIPE_BOOK_SHIFT);
			}
			button.setPosition(Math.max(2, left - 22), inventory.cakebook$y());
		};
		place.run();
		add.accept(button);
		// the inventory moves sideways when the recipe book opens or closes
		return place;
	}
}
