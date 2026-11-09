package io.github.harrythecake.cakeui.render;

import io.github.harrythecake.cakeui.model.InventorySlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The player's own items for {@code item} (slot:…) and {@code inventory} elements (E4): which stack a
 * slot holds, the count / durability / cooldown overlays one by one (vanilla draws all three or none),
 * and slot backgrounds. Display only.
 */
final class ItemDraw {
	/** The vanilla slot look: dark top-left edge, light bottom-right edge, grey inside (18 x 18). */
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_FILL = 0xFF8B8B8B;

	private ItemDraw() {
	}

	/** The stack in one of the player's slots ({@link InventorySlots#MAINHAND} = the held item); empty without a player. */
	static ItemStack stack(int slot) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return ItemStack.EMPTY;
		}
		if (slot == InventorySlots.MAINHAND) {
			return p.getMainHandItem();
		}
		return slot >= 0 && slot < p.getInventory().getContainerSize() ? p.getInventory().getItem(slot) : ItemStack.EMPTY;
	}

	/** An item with the chosen overlays at (x, y), 16 x 16. */
	static void draw(GuiGraphicsExtractor g, @Nullable ItemStack stack, int x, int y, boolean counts, boolean durability, boolean cooldown) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		g.item(stack, x, y);
		Font font = Minecraft.getInstance().font;
		if (counts && durability && cooldown) {
			g.itemDecorations(font, stack, x, y); // vanilla's own, all three
			return;
		}
		// 26.1: overlays as later render states, above the item
		g.nextStratum();
		if (durability && stack.isBarVisible()) {
			int w = stack.getBarWidth();
			int color = stack.getBarColor();
			g.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
			g.fill(x + 2, y + 13, x + 2 + w, y + 14, color | 0xFF000000);
		}
		if (counts && stack.getCount() != 1) {
			String s = String.valueOf(stack.getCount());
			g.text(font, s, x + 19 - 2 - font.width(s), y + 6 + 3, 0xFFFFFFFF, true);
		}
		LocalPlayer p = Minecraft.getInstance().player;
		if (cooldown && p != null) {
			float f = p.getCooldowns().getCooldownPercent(stack, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
			if (f > 0) {
				int top = y + (int) Math.floor(16 * (1 - f));
				g.fill(x, top, x + 16, top + (int) Math.ceil(16 * f), Integer.MAX_VALUE);
			}
		}
	}

	/** A slot's background at (x, y), 18 x 18 (the item goes at x + 1, y + 1). */
	static void slot(GuiGraphicsExtractor g, int x, int y, String style, int color) {
		switch (style) {
			case "vanilla" -> {
				g.fill(x, y, x + 18, y + 18, SLOT_FILL);
				g.fill(x, y, x + 17, y + 1, SLOT_DARK);
				g.fill(x, y, x + 1, y + 17, SLOT_DARK);
				g.fill(x + 1, y + 17, x + 18, y + 18, SLOT_LIGHT);
				g.fill(x + 17, y + 1, x + 18, y + 18, SLOT_LIGHT);
			}
			case "rect" -> g.fill(x, y, x + 18, y + 18, color);
			default -> {
			}
		}
	}
}
