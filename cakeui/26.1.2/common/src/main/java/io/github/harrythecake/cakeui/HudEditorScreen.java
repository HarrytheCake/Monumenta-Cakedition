package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.HudEdits;
import io.github.harrythecake.cakeui.edit.Placement;
import io.github.harrythecake.cakeui.edit.Placement.Box;
import io.github.harrythecake.cakeui.edit.PortEdit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The HUD editor (ADR 0010, E1): drag HUD pieces to move them, scroll to resize, right-click for
 * hide/show, opacity, position mode and reset. Changes show on the live HUD at once; Done saves them
 * as the player's personal edits for the active layout, Cancel (or Esc twice) throws them away.
 */
public final class HudEditorScreen extends Screen {
	private static final double SNAP = 4;
	private static final int MENU_W = 140;
	private static final int ITEM_H = 12;

	@Nullable
	private final Screen parent;
	private final Map<String, PortEdit> edits;
	private final Map<String, PortEdit> initial;
	private final Deque<Map<String, PortEdit>> undo = new ArrayDeque<>();
	private final Deque<Map<String, PortEdit>> redo = new ArrayDeque<>();
	private boolean previews = true;

	@Nullable
	private String selected;
	@Nullable
	private String dragging;
	private double dragStartX;
	private double dragStartY;
	private Box dragStartBox;
	private boolean dragMoved;
	private List<Placement.Guide> guides = List.of();

	private record MenuItem(Component label, Runnable action) {
	}

	@Nullable
	private List<MenuItem> menu;
	private double menuX;
	private double menuY;
	private long escAt;
	private boolean finished;

	public HudEditorScreen(@Nullable Screen parent) {
		super(Component.translatable("cakeui.editor.title"));
		this.parent = parent;
		this.edits = HudEditor.start();
		this.initial = HudEdits.copy(edits);
	}

	/** The toolbar: a column on the left, half-way down (vanilla draws nothing there). */
	private static final int BAR_X = 4;
	private static final int BAR_W = 96;

	private int barTop() {
		return height / 2 - 50;
	}

	@Override
	protected void init() {
		int y = barTop();
		addRenderableWidget(Button.builder(Component.translatable("cakeui.editor.done"), b -> close(true)).bounds(BAR_X, y, BAR_W, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("cakeui.editor.cancel"), b -> close(false)).bounds(BAR_X, y + 20, BAR_W, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("cakeui.editor.resetAll"), b -> {
			checkpoint();
			edits.clear();
		}).bounds(BAR_X, y + 40, BAR_W, 18).build());
		addRenderableWidget(CycleButton.onOffBuilder(previews)
				.create(BAR_X, y + 60, BAR_W, 18, Component.translatable("cakeui.editor.previews"), (b, v) -> previews = v));
	}

	// ---- pieces on screen ----

	private boolean visible(String name) {
		PortEdit e = edits.get(name);
		return previews || HudBounds.live(name) || (e != null && Boolean.TRUE.equals(e.hide));
	}

	/** Where the piece is on screen now (its measured or default place, with the edits applied). */
	private Box screenBox(String name) {
		Box inner = HudBounds.orDefault(name, width, height);
		return Placement.transform(edits.get(name), inner, width, height).apply(inner);
	}

	/** The smallest visible piece under the mouse (small pieces win over big ones they sit in). */
	@Nullable
	private String pick(double mx, double my) {
		String best = null;
		double bestArea = Double.MAX_VALUE;
		for (String name : HudEditor.pieces()) {
			if (!visible(name)) {
				continue;
			}
			Box b = screenBox(name);
			Box hit = new Box(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2);
			if (hit.contains(mx, my) && b.w() * b.h() < bestArea) {
				best = name;
				bestArea = b.w() * b.h();
			}
		}
		return best;
	}

	private static Component label(String name) {
		String key = "cakeui.hud.port." + name;
		return I18n.exists(key) ? Component.translatable(key) : Component.literal(name);
	}

	private PortEdit edit(String name) {
		return edits.computeIfAbsent(name, k -> new PortEdit());
	}

	// ---- undo ----

	private void checkpoint() {
		undo.push(HudEdits.copy(edits));
		if (undo.size() > 100) {
			undo.removeLast();
		}
		redo.clear();
	}

	private void restore(Deque<Map<String, PortEdit>> from, Deque<Map<String, PortEdit>> to) {
		if (from.isEmpty()) {
			return;
		}
		to.push(HudEdits.copy(edits));
		Map<String, PortEdit> state = from.pop();
		edits.clear();
		edits.putAll(state);
	}

	private boolean dirty() {
		return !normalized(edits).equals(normalized(initial));
	}

	private static Map<String, PortEdit> normalized(Map<String, PortEdit> m) {
		Map<String, PortEdit> out = new java.util.HashMap<>();
		m.forEach((k, v) -> {
			if (!v.isEmpty()) {
				out.put(k, v);
			}
		});
		return out;
	}

	// ---- drawing ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// no dim: the live HUD is the canvas
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		String hovered = menu == null && dragging == null ? pick(mouseX, mouseY) : null;
		for (String name : HudEditor.pieces()) {
			if (!visible(name)) {
				continue;
			}
			Box b = screenBox(name);
			int x1 = (int) Math.floor(b.x());
			int y1 = (int) Math.floor(b.y());
			int x2 = (int) Math.ceil(b.right());
			int y2 = (int) Math.ceil(b.bottom());
			PortEdit e = edits.get(name);
			boolean hidden = e != null && Boolean.TRUE.equals(e.hide);
			boolean live = HudBounds.live(name);
			if (!live || hidden) {
				g.fill(x1, y1, x2, y2, hidden ? 0x30FF4040 : 0x30FFFFFF);
				Component text = hidden ? label(name).copy().append(" ").append(Component.translatable("cakeui.editor.hidden")) : label(name);
				if (y2 - y1 >= 10 && font.width(text) <= x2 - x1) {
					g.centeredText(font, text, (x1 + x2) / 2, (y1 + y2) / 2 - 4, hidden ? 0xFFFF9090 : 0xFFD0D0D0);
				}
			}
			int color = name.equals(selected) ? 0xFFFFD000 : name.equals(hovered) ? 0xFFFFFFFF : hidden ? 0xFFE05050 : 0x90FFFFFF;
			g.outline(x1 - 1, y1 - 1, x2 - x1 + 2, y2 - y1 + 2, color);
		}
		String named = dragging != null ? dragging : hovered != null ? hovered : selected;
		if (named != null && visible(named)) {
			Box b = screenBox(named);
			PortEdit e = edits.get(named);
			Component text = label(named);
			if (e != null && e.scale != null) {
				text = text.copy().append(String.format(java.util.Locale.ROOT, "  ×%.2f", e.scale));
			}
			int ty = b.y() > 12 ? (int) b.y() - 11 : (int) b.bottom() + 3;
			int tw = font.width(text);
			int tx = (int) Math.max(2, Math.min(width - tw - 2, b.cx() - tw / 2.0));
			g.fill(tx - 2, ty - 1, tx + tw + 2, ty + 9, 0xC0000000);
			g.text(font, text, tx, ty, 0xFFFFD000);
		}
		for (Placement.Guide gd : guides) {
			if (gd.x1() == gd.x2()) {
				g.fill((int) gd.x1(), 0, (int) gd.x1() + 1, height, 0xC040C0FF);
			} else {
				g.fill(0, (int) gd.y1(), width, (int) gd.y1() + 1, 0xC040C0FF);
			}
		}
		boolean discard = escAt != 0 && System.currentTimeMillis() - escAt < 3000;
		Component help = Component.translatable(discard ? "cakeui.editor.discard" : "cakeui.editor.help");
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(help, BAR_W);
		int helpY = barTop() + 82;
		g.fill(BAR_X - 2, helpY - 2, BAR_X + BAR_W + 2, helpY + lines.size() * 9 + 1, 0xA0000000);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), BAR_X, helpY + i * 9, discard ? 0xFFFFD000 : 0xFFE0E0E0);
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (menu != null) {
			int mx = (int) menuX;
			int my = (int) menuY;
			g.fill(mx, my, mx + MENU_W, my + menu.size() * ITEM_H + 2, 0xF0101010);
			g.outline(mx, my, MENU_W, menu.size() * ITEM_H + 2, 0xFFFFD000);
			for (int i = 0; i < menu.size(); i++) {
				int iy = my + 1 + i * ITEM_H;
				if (mouseX >= mx && mouseX < mx + MENU_W && mouseY >= iy && mouseY < iy + ITEM_H) {
					g.fill(mx + 1, iy, mx + MENU_W - 1, iy + ITEM_H, 0xFF404040);
				}
				g.text(font, menu.get(i).label, mx + 5, iy + 2, 0xFFFFFFFF);
			}
		}
	}

	// ---- mouse ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		if (menu != null) {
			int i = (int) Math.floor((my - menuY - 1) / ITEM_H);
			if (mx >= menuX && mx < menuX + MENU_W && i >= 0 && i < menu.size()) {
				menu.get(i).action.run();
			}
			menu = null;
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		String hit = pick(mx, my);
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			selected = hit;
			if (hit != null) {
				dragging = hit;
				dragStartX = mx;
				dragStartY = my;
				dragStartBox = screenBox(hit);
				dragMoved = false;
			}
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit != null) {
			selected = hit;
			openMenu(hit, mx, my);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		double mx = event.x();
		double my = event.y();
		if (dragging == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseDragged(event, dx, dy);
		}
		double left = dragStartBox.x() + (mx - dragStartX);
		double top = dragStartBox.y() + (my - dragStartY);
		if (!dragMoved) {
			if (Math.abs(mx - dragStartX) < 1 && Math.abs(my - dragStartY) < 1) {
				return true;
			}
			dragMoved = true;
			checkpoint();
		}
		guides = List.of();
		if (!minecraft.hasAltDown()) {
			List<Box> others = new ArrayList<>();
			for (String name : HudEditor.pieces()) {
				if (!name.equals(dragging) && visible(name)) {
					others.add(screenBox(name));
				}
			}
			Placement.Snapped s = Placement.snap(left, top, dragStartBox.w(), dragStartBox.h(), others, width, height, SNAP);
			left = s.left();
			top = s.top();
			guides = s.guides();
		}
		moveTo(dragging, left, top);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = null;
			guides = List.of();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		String hit = pick(mx, my);
		if (hit == null || scrollY == 0) {
			return super.mouseScrolled(mx, my, scrollX, scrollY);
		}
		selected = hit;
		checkpoint();
		PortEdit e = edit(hit);
		double s = Math.round((e.scaleOr1() + Math.signum(scrollY) * 0.05) * 20) / 20.0;
		s = Math.max(0.25, Math.min(4, s));
		e.scale = s == 1 && e.mode == null ? null : s;
		return true;
	}

	/** Puts a piece's on-screen top-left at (left, top), keeping its position mode (anchored by default). */
	private void moveTo(String name, double left, double top) {
		PortEdit e = edit(name);
		Box b = screenBox(name);
		Placement.place(e, e.mode == null ? PortEdit.Mode.ANCHORED : e.mode, left, top, b.w(), b.h(), width, height);
	}

	private void openMenu(String name, double mx, double my) {
		PortEdit e = edits.get(name);
		boolean hidden = e != null && Boolean.TRUE.equals(e.hide);
		float opacity = e == null || e.opacity == null ? 1 : e.opacity;
		PortEdit.Mode mode = e == null || e.mode == null ? PortEdit.Mode.ANCHORED : e.mode;
		List<MenuItem> items = new ArrayList<>();
		items.add(new MenuItem(Component.translatable(hidden ? "cakeui.editor.menu.show" : "cakeui.editor.menu.hide"), () -> {
			checkpoint();
			edit(name).hide = hidden ? null : Boolean.TRUE;
		}));
		items.add(new MenuItem(Component.translatable("cakeui.editor.menu.opacity", Math.round(opacity * 100)), () -> {
			checkpoint();
			float next = opacity > 0.8f ? 0.75f : opacity > 0.6f ? 0.5f : opacity > 0.3f ? 0.25f : 1f;
			edit(name).opacity = next == 1f ? null : next;
		}));
		items.add(new MenuItem(Component.translatable("cakeui.editor.menu.mode",
				Component.translatable("cakeui.editor.mode." + mode.id())), () -> {
			checkpoint();
			PortEdit.Mode next = PortEdit.Mode.values()[(mode.ordinal() + 1) % PortEdit.Mode.values().length];
			Box b = screenBox(name);
			Placement.place(edit(name), next, b.x(), b.y(), b.w(), b.h(), width, height);
		}));
		items.add(new MenuItem(Component.translatable("cakeui.editor.menu.reset"), () -> {
			checkpoint();
			edits.remove(name);
		}));
		menu = items;
		menuX = Math.min(mx, width - MENU_W - 2);
		menuY = Math.min(my, height - items.size() * ITEM_H - 4);
	}

	// ---- keys ----

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (menu != null) {
				menu = null;
			} else if (dirty() && System.currentTimeMillis() - escAt > 3000) {
				escAt = System.currentTimeMillis();
			} else {
				close(false);
			}
			return true;
		}
		if (minecraft.hasControlDown() && key == GLFW.GLFW_KEY_Z) {
			restore(undo, redo);
			return true;
		}
		if (minecraft.hasControlDown() && key == GLFW.GLFW_KEY_Y) {
			restore(redo, undo);
			return true;
		}
		if (selected != null) {
			int step = minecraft.hasShiftDown() ? 10 : 1;
			int dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
			int dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
			if (dx != 0 || dy != 0) {
				checkpoint();
				Box b = screenBox(selected);
				moveTo(selected, b.x() + dx, b.y() + dy);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	// ---- closing ----

	private void close(boolean save) {
		finished = true;
		HudEditor.finish(save);
		minecraft.setScreen(parent);
	}

	@Override
	public void onClose() {
		close(false);
	}

	@Override
	public void removed() {
		if (!finished) {
			finished = true;
			HudEditor.finish(false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
