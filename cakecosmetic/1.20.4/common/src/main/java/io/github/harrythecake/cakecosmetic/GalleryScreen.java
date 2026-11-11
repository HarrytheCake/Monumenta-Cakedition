package io.github.harrythecake.cakecosmetic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The gallery: every background as a thumbnail, for the main menu or the loading screens. One mode
 * shows the clicked picture; shuffle mode cycles through the ticked ones (none ticked = all
 * unlocked). Locked pictures are dimmed and name what unlocks them. Dev mode adds ids, the rule
 * editor, a new-player preview and "forget my unlocks".
 */
public final class GalleryScreen extends Screen {
	private static final int GAP = 6;
	private static final int GRID_Y = 76;
	private static final int YELLOW = 0xFFFFD000;
	/** The place last looked at, kept while the game runs. */
	private static int lastPlace;

	private final Screen parent;
	private int place;
	private final List<String> tiles = new ArrayList<>();
	private String focused;
	private double scroll;
	private int cols;
	private int tileW;
	private int tileH;
	private int gridX;
	private int gridW;
	private int gridBottom;
	private Button previewButton;
	private Button editButton;

	private GalleryScreen(Screen parent) {
		super(Component.translatable("cakecosmetic.gallery.title"));
		this.parent = parent;
		this.place = lastPlace;
	}

	public static GalleryScreen open(Screen parent) {
		Unlocks.reloadFiles();
		return new GalleryScreen(parent);
	}

	private Choices.Choice choice() {
		return place == 0 ? Unlocks.choices().mainMenu : Unlocks.choices().loading;
	}

	private boolean dev() {
		return Unlocks.settings().devMode;
	}

	@Override
	protected void init() {
		tiles.clear();
		tiles.add(Choices.VANILLA);
		for (Backgrounds.Background b : Backgrounds.list()) {
			tiles.add(b.id());
		}
		int cx = width / 2;
		addRenderableWidget(Button.builder(Component.translatable("cakecosmetic.gallery.place.menu"), b -> setPlace(0))
				.bounds(cx - 122, 18, 120, 20).build()).active = place != 0;
		addRenderableWidget(Button.builder(Component.translatable("cakecosmetic.gallery.place.loading"), b -> setPlace(1))
				.bounds(cx + 2, 18, 120, 20).build()).active = place != 1;
		addRenderableWidget(Button.builder(modeLabel(), b -> {
			Choices.Choice c = choice();
			c.mode = c.mode == Choices.Mode.ONE ? Choices.Mode.SHUFFLE : Choices.Mode.ONE;
			Unlocks.saveChoices();
			b.setMessage(modeLabel());
		}).bounds(cx - 110, 40, 220, 20).build());

		int bottom = height - 26;
		previewButton = addRenderableWidget(Button.builder(Component.translatable("cakecosmetic.gallery.preview"), b -> preview(focused))
				.bounds(cx - 102, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 2, bottom, 100, 20).build());
		int gridEnd = bottom - 6;
		if (dev()) {
			int y = bottom - 24;
			editButton = addRenderableWidget(Button.builder(Component.translatable("cakecosmetic.dev.editRule"), b -> {
				Backgrounds.Background bg = Backgrounds.get(focused == null ? "" : focused);
				if (bg != null) {
					minecraft.setScreen(new RuleEditorScreen(this, bg));
				}
			}).bounds(cx - 200, y, 130, 20).build());
			addRenderableWidget(Button.builder(newPlayerLabel(), b -> {
				Unlocks.setPreviewNewPlayer(!Unlocks.previewNewPlayer());
				b.setMessage(newPlayerLabel());
			}).bounds(cx - 66, y, 132, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("cakecosmetic.dev.forget"), b -> minecraft.setScreen(new ConfirmScreen(yes -> {
				if (yes) {
					Unlocks.forget();
				}
				minecraft.setScreen(this);
			}, Component.translatable("cakecosmetic.dev.forget"), Component.translatable("cakecosmetic.dev.forget.confirm")))).bounds(cx + 70, y, 130, 20).build());
			gridEnd = y - 6;
		}
		gridX = 20;
		gridW = width - 40;
		gridBottom = gridEnd;
		// at least about two rows visible: small GUIs get more, smaller tiles
		int maxTileH = Math.max(30, (gridBottom - GRID_Y - GAP) / 2);
		int maxTileW = maxTileH * 16 / 9;
		cols = Math.max(2, Math.min(8, Math.max(gridW / 130, (gridW + GAP + maxTileW + GAP - 1) / (maxTileW + GAP))));
		tileW = (gridW - (cols - 1) * GAP) / cols;
		tileH = tileW * 9 / 16;
		scroll = Math.min(scroll, maxScroll());
	}

	private void setPlace(int p) {
		place = p;
		lastPlace = p;
		rebuildWidgets();
	}

	private Component modeLabel() {
		return Component.translatable(choice().mode == Choices.Mode.ONE ? "cakecosmetic.gallery.mode.one" : "cakecosmetic.gallery.mode.shuffle");
	}

	private Component newPlayerLabel() {
		return Component.translatable("cakecosmetic.dev.newPlayer", Component.translatable(Unlocks.previewNewPlayer() ? "options.on" : "options.off"));
	}

	private int rows() {
		return (tiles.size() + cols - 1) / cols;
	}

	private double maxScroll() {
		return Math.max(0, rows() * (tileH + GAP) - GAP - (gridBottom - GRID_Y));
	}

	private int tileX(int i) {
		return gridX + (i % cols) * (tileW + GAP);
	}

	private int tileY(int i) {
		return GRID_Y + (i / cols) * (tileH + GAP) - (int) scroll;
	}

	private String tileAt(double mx, double my) {
		if (my < GRID_Y || my >= gridBottom) {
			return null;
		}
		for (int i = 0; i < tiles.size(); i++) {
			int x = tileX(i);
			int y = tileY(i);
			if (mx >= x && mx < x + tileW && my >= y && my < y + tileH) {
				return tiles.get(i);
			}
		}
		return null;
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		previewButton.active = focused != null && canPreview(focused);
		if (editButton != null) {
			editButton.active = focused != null && !Choices.VANILLA.equals(focused);
		}
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredString(font, title, width / 2, 6, 0xFFFFFFFF);
		drawCount(ctx);
		ctx.drawCenteredString(font, hint(), width / 2, 64, 0xFFAAAAAA);

		ctx.enableScissor(0, GRID_Y, width, gridBottom);
		String hovered = tileAt(mouseX, mouseY);
		for (int i = 0; i < tiles.size(); i++) {
			int y = tileY(i);
			if (y + tileH < GRID_Y || y > gridBottom) {
				continue;
			}
			drawTile(ctx, tiles.get(i), tileX(i), y, tiles.get(i).equals(hovered));
		}
		ctx.disableScissor();
		if (tiles.size() == 1) {
			ctx.drawCenteredString(font, Component.translatable("cakecosmetic.gallery.none"), width / 2, GRID_Y + tileH + 12, 0xFFAAAAAA);
		}
		if (hovered != null) {
			ctx.renderComponentTooltip(font, tooltip(hovered), mouseX, mouseY);
		}
	}

	private void drawCount(GuiGraphics ctx) {
		Component t;
		if (Unlocks.settings().mode == Settings.Mode.ALL) {
			t = Component.translatable("cakecosmetic.gallery.allMode");
		} else {
			int open = 0;
			for (Backgrounds.Background b : Backgrounds.list()) {
				if (Unlocks.pickable(b.id())) {
					open++;
				}
			}
			t = Component.translatable("cakecosmetic.gallery.count", open, Backgrounds.list().size());
		}
		ctx.drawString(font, t, width - 8 - font.width(t), 6, 0xFFAAAAAA, true);
		if (dev()) {
			ctx.drawString(font, Component.translatable("cakecosmetic.dev.on").withStyle(ChatFormatting.AQUA), 8, 6, 0xFFFFFFFF, true);
		}
	}

	private Component hint() {
		Choices.Choice c = choice();
		if (c.mode == Choices.Mode.ONE) {
			return Component.translatable("cakecosmetic.gallery.hint.one");
		}
		long ticked = c.shuffle.stream().filter(Unlocks::pickable).filter(id -> tiles.contains(id)).count();
		return ticked == 0 ? Component.translatable("cakecosmetic.gallery.hint.shuffleAll")
				: Component.translatable("cakecosmetic.gallery.hint.shuffle", ticked);
	}

	private void drawTile(GuiGraphics ctx, String id, int x, int y, boolean hovered) {
		Choices.Choice c = choice();
		boolean vanilla = Choices.VANILLA.equals(id);
		Backgrounds.Background b = vanilla ? null : Backgrounds.get(id);
		if (vanilla) {
			ctx.fillGradient(x, y, x + tileW, y + tileH, 0xFF3A3A3A, 0xFF141414);
			Component label = Component.translatable(place == 0 ? "cakecosmetic.gallery.vanilla.menu" : "cakecosmetic.gallery.vanilla.loading");
			String l = font.plainSubstrByWidth(label.getString(), tileW - 4);
			ctx.drawCenteredString(font, l, x + tileW / 2, y + tileH / 2 - 4, 0xFFFFFFFF);
		} else {
			BackgroundTextures.Tex t = b == null ? null : BackgroundTextures.thumb(b);
			if (t != null) {
				BackgroundTextures.drawCover(ctx, t, x, y, tileW, tileH, 1);
			} else {
				ctx.fill(x, y, x + tileW, y + tileH, 0xFF202020);
				ctx.drawCenteredString(font, "…", x + tileW / 2, y + tileH / 2 - 4, 0xFF888888);
			}
			boolean locked = !Unlocks.pickable(id);
			if (locked) {
				ctx.fill(x, y, x + tileW, y + tileH, 0xB4000000);
				ctx.drawCenteredString(font, Component.translatable("cakecosmetic.gallery.locked"), x + tileW / 2, y + tileH / 2 - 8, 0xFFFFAA00);
			}
			String name = b == null ? id : b.title();
			ctx.fill(x, y + tileH - 12, x + tileW, y + tileH, 0x90000000);
			ctx.drawString(font, font.plainSubstrByWidth(name, tileW - 6), x + 3, y + tileH - 10, locked ? 0xFF888888 : 0xFFFFFFFF, false);
			if (dev()) {
				String shown = font.plainSubstrByWidth(id.substring(id.indexOf(':') + 1), tileW - 4);
				ctx.fill(x, y, x + font.width(shown) + 4, y + 10, 0xA0000000);
				ctx.drawString(font, shown, x + 2, y + 1, 0xFF55FFFF, false);
			}
		}
		if (c.mode == Choices.Mode.ONE && id.equals(c.one)) {
			ctx.renderOutline(x - 1, y - 1, tileW + 2, tileH + 2, YELLOW);
			ctx.renderOutline(x - 2, y - 2, tileW + 4, tileH + 4, YELLOW);
		}
		if (c.mode == Choices.Mode.SHUFFLE && c.shuffle.contains(id)) {
			int bx = x + tileW - 13;
			ctx.fill(bx, y + 2, bx + 11, y + 13, YELLOW);
			ctx.drawString(font, "✔", bx + 2, y + 4, 0xFF000000, false);
		}
		if (id.equals(focused)) {
			ctx.renderOutline(x, y, tileW, tileH, 0xFF55FFFF);
		} else if (hovered) {
			ctx.renderOutline(x, y, tileW, tileH, 0xFFFFFFFF);
		}
	}

	private List<Component> tooltip(String id) {
		List<Component> lines = new ArrayList<>();
		if (Choices.VANILLA.equals(id)) {
			lines.add(Component.translatable(place == 0 ? "cakecosmetic.gallery.vanilla.menu" : "cakecosmetic.gallery.vanilla.loading"));
			return lines;
		}
		Backgrounds.Background b = Backgrounds.get(id);
		lines.add(Component.literal(b == null ? id : b.title()));
		if (b != null && !b.meta().artist().isEmpty()) {
			lines.add(Component.translatable("cakecosmetic.gallery.by", b.meta().artist()).withStyle(ChatFormatting.GRAY));
		}
		Rules.Rule rule = Unlocks.rules().get(id);
		if (!Unlocks.pickable(id) && rule != null) {
			if (rule.secret()) {
				lines.add(Component.translatable("cakecosmetic.gallery.unlock", "???").withStyle(ChatFormatting.GOLD));
			} else {
				for (Rules.Need n : rule.needs()) {
					lines.add(Component.translatable("cakecosmetic.gallery.unlock", n.title()).withStyle(ChatFormatting.GOLD));
				}
			}
		} else if (rule != null && Unlocks.unlockedByRule(id)) {
			lines.add(Component.translatable("cakecosmetic.gallery.unlocked").withStyle(ChatFormatting.GREEN));
		}
		if (dev()) {
			lines.add(Component.literal(id).withStyle(ChatFormatting.AQUA));
			lines.add(rule == null ? Component.translatable("cakecosmetic.dev.free").withStyle(ChatFormatting.AQUA)
					: Component.translatable("cakecosmetic.dev.needs", rule.needs().size()).withStyle(ChatFormatting.AQUA));
		}
		if (canPreview(id)) {
			lines.add(Component.translatable("cakecosmetic.gallery.rightClick").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private boolean canPreview(String id) {
		return !Choices.VANILLA.equals(id) && Backgrounds.get(id) != null && (Unlocks.pickable(id) || dev());
	}

	private void preview(String id) {
		Backgrounds.Background b = id == null ? null : Backgrounds.get(id);
		if (b != null && canPreview(id)) {
			minecraft.setScreen(new PreviewScreen(this, b));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		String id = tileAt(mouseX, mouseY);
		if (id == null) {
			return false;
		}
		if (button == 1) {
			preview(id);
			return true;
		}
		if (button != 0) {
			return false;
		}
		focused = id;
		if (!Unlocks.pickable(id)) {
			return true;
		}
		Choices.Choice c = choice();
		if (c.mode == Choices.Mode.ONE) {
			c.one = id;
		} else if (!c.shuffle.remove(id)) {
			c.shuffle.add(id);
		}
		Unlocks.saveChoices();
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - verticalAmount * (tileH + GAP) / 2.0));
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
