package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.AddedElements;
import io.github.harrythecake.cakeui.edit.HudEdits;
import io.github.harrythecake.cakeui.edit.Placement;
import io.github.harrythecake.cakeui.edit.Placement.Box;
import io.github.harrythecake.cakeui.edit.PortEdit;
import io.github.harrythecake.cakeui.edit.StylePresets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
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
 * The HUD editor (ADR 0010, E1 + E2): drag HUD pieces (vanilla's, mods' pieces and the layout's own HUD
 * elements) to move them, scroll to resize, right-click for hide/show, opacity, position mode and reset.
 * Changes show on the live HUD at once; Done saves them (as the player's personal edits, or in dev mode
 * into the layout's hud/edits.json5), Cancel (or Esc twice) throws them away. Pieces another mod draws
 * itself are greyed out.
 */
public final class HudEditorScreen extends Screen {
	private static final double SNAP = 4;
	private static final int MENU_W = 140;
	private static final int ITEM_H = 12;

	@Nullable
	private final Screen parent;
	/** The working copy of the target being edited (personal or the layout's). */
	private Map<String, PortEdit> edits;
	private final Map<String, PortEdit>[] initial;
	private final long openedAt = System.currentTimeMillis();
	private final Deque<Map<String, PortEdit>> undo = new ArrayDeque<>();
	private final Deque<Map<String, PortEdit>> redo = new ArrayDeque<>();
	/** Movables' boxes with each undo/redo step (mods store those, not the edits). */
	private final Deque<Map<String, double[]>> undoBoxes = new ArrayDeque<>();
	private final Deque<Map<String, double[]>> redoBoxes = new ArrayDeque<>();
	/** Movables' boxes when the editor opened (Cancel puts them back; Done saves the ones that changed). */
	private final Map<String, double[]> movableStart;
	/** Dragging the bottom-right handle of a resizable movable. */
	private boolean resizing;
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
	/** The properties panel is open on top: leaving for it doesn't close the editor. */
	private boolean childOpen;

	public HudEditorScreen(@Nullable Screen parent) {
		super(Component.translatable("cakeui.editor.title"));
		this.parent = parent;
		HudEditor.start();
		this.edits = HudEditor.editingMap();
		this.initial = HudEditor.snapshot();
		var w = Minecraft.getInstance().getWindow();
		this.movableStart = movableBoxes(w.getGuiScaledWidth(), w.getGuiScaledHeight());
	}

	/** Opens the editor from anywhere (API: openHudEditor); false when not in a world. */
	public static boolean open(@Nullable Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return false;
		}
		mc.setScreen(new HudEditorScreen(parent));
		return true;
	}

	private static Map<String, double[]> movableBoxes(int w, int h) {
		Map<String, double[]> out = new java.util.LinkedHashMap<>();
		HudMovables.all().forEach((k, m) -> {
			double[] b = m.box(w, h);
			if (b != null) {
				out.put(k, b.clone());
			}
		});
		return out;
	}

	private static boolean isMovable(String name) {
		return name.startsWith(HudMovables.PREFIX);
	}

	/** The toolbar: a column on the left, half-way down (vanilla draws nothing there). */
	private static final int BAR_X = 4;
	private static final int BAR_W = 96;

	private int barTop() {
		return height / 2 - 50;
	}

	@Override
	protected void init() {
		childOpen = false;
		int y = barTop();
		addRenderableWidget(Button.builder(Component.translatable("cakeui.editor.done"), b -> close(true)).bounds(BAR_X, y, BAR_W, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("cakeui.editor.cancel"), b -> close(false)).bounds(BAR_X, y + 20, BAR_W, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("cakeui.editor.resetAll"), b -> {
			checkpoint();
			edits.clear();
		}).bounds(BAR_X, y + 40, BAR_W, 18).build());
		addRenderableWidget(CycleButton.onOffBuilder(previews)
				.create(BAR_X, y + 60, BAR_W, 18, Component.translatable("cakeui.editor.previews"), (b, v) -> previews = v));
		if (HudEditor.layoutTargetPossible()) {
			// dev mode: save into the layout (what every player of it sees) or as my own edits
			addRenderableWidget(CycleButton.<HudEditor.Target>builder(t -> Component.translatable("cakeui.editor.target." + t.name().toLowerCase(java.util.Locale.ROOT)))
					.withValues(HudEditor.Target.values())
					.withInitialValue(HudEditor.target())
					.displayOnlyValue()
					.withTooltip(t -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cakeui.editor.target.tooltip")))
					.create(BAR_X, y - 22, BAR_W, 18, Component.translatable("cakeui.editor.target"), (b, t) -> {
						HudEditor.setTarget(t);
						edits = HudEditor.editingMap();
						undo.clear();
						redo.clear();
						selected = null;
					}));
		}
	}

	// ---- pieces on screen ----

	/** The edit the HUD draws a piece with (the layout's and the player's together). */
	@Nullable
	private static PortEdit effective(String name) {
		return HudEditor.current().get(name);
	}

	private static boolean hidden(String name) {
		PortEdit e = effective(name);
		return e != null && Boolean.TRUE.equals(e.hide);
	}

	/** Another mod draws this vanilla piece itself (its CakeUI hook didn't run although it should be on screen). */
	private boolean otherMod(String name) {
		return System.currentTimeMillis() - openedAt > 700 && HudExpect.expected(name) && !HudBounds.hookedRecently(name);
	}

	private boolean visible(String name) {
		if (isMovable(name)) {
			var m = HudMovables.get(name);
			return m != null && m.box(width, height) != null;
		}
		PortEdit styled = effective(name);
		if (styled != null && styled.style != null && StylePresets.STYLES.containsKey(name)) {
			return false;
		}
		if (name.startsWith(HudEdits.ELEMENT)) {
			return HudBounds.box(name) != null; // the layout's elements: once they have been drawn
		}
		if (name.startsWith(HudEdits.ADDED)) {
			return true;
		}
		return previews || HudBounds.live(name) || hidden(name) || otherMod(name);
	}

	/** Where the piece is on screen now (its measured or default place, with the edits applied). */
	private Box screenBox(String name) {
		if (isMovable(name)) {
			var m = HudMovables.get(name);
			double[] b = m == null ? null : m.box(width, height);
			return b == null ? new Box(-1000, -1000, 0, 0) : new Box(b[0], b[1], b[2], b[3]);
		}
		Box inner = HudBounds.orDefault(name, width, height);
		PortEdit shown = effective(name);
		if (name.startsWith(HudEdits.ADDED) && HudBounds.box(name) == null && shown != null && shown.element != null) {
			double[] s = AddedElements.guessSize(shown.element); // not drawn yet
			inner = new Box(0, 0, s[0], s[1]);
		}
		return Placement.transform(effective(name), inner, width, height).apply(inner);
	}

	/** The smallest visible piece under the mouse (small pieces win over big ones they sit in). */
	@Nullable
	private String pick(double mx, double my) {
		String best = null;
		double bestArea = Double.MAX_VALUE;
		for (String name : HudEditor.pieces()) {
			if (!visible(name) || otherMod(name)) {
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
		if (isMovable(name)) {
			var m = HudMovables.get(name);
			return m == null ? Component.literal(name) : m.label();
		}
		if (StylePresets.portOf(name) != null) {
			String port = StylePresets.portOf(name);
			PortEdit pe = effective(port);
			return label(port).copy().append(" (").append(Component.translatable("cakeui.editor.style." + (pe == null || pe.style == null ? "vanilla" : pe.style))).append(")");
		}
		if (name.startsWith(HudEdits.ADDED)) {
			PortEdit e = effective(name);
			com.google.gson.JsonObject def = e == null ? null : e.element;
			String type = AddedElements.type(def);
			String detail = switch (type) {
				case "text" -> def.has("text") ? def.get("text").getAsString() : "";
				case "bar" -> def.has("value") ? def.get("value").getAsString() : "";
				case "image" -> def.has("src") ? def.get("src").getAsString() : "";
				default -> "";
			};
			if (detail.length() > 18) {
				detail = detail.substring(0, 17) + "…";
			}
			Component kind = Component.translatable("cakeui.editor.add." + (type.isEmpty() ? "text" : type));
			return detail.isEmpty() ? kind : kind.copy().append(": " + detail);
		}
		if (name.startsWith(HudEdits.ELEMENT)) {
			return Component.translatable("cakeui.editor.element", name.substring(HudEdits.ELEMENT.length()));
		}
		String key = "cakeui.hud.port." + name;
		return I18n.exists(key) ? Component.translatable(key) : Component.literal(name);
	}

	/** The target's edit of a piece, created on first change (starting from what is shown, so nothing jumps). */
	private PortEdit edit(String name) {
		return edits.computeIfAbsent(name, k -> new PortEdit());
	}

	// ---- undo ----

	private void checkpoint() {
		undo.push(HudEdits.copy(edits));
		undoBoxes.push(movableBoxes(width, height));
		if (undo.size() > 100) {
			undo.removeLast();
			undoBoxes.removeLast();
		}
		redo.clear();
		redoBoxes.clear();
	}

	private void restore(Deque<Map<String, PortEdit>> from, Deque<Map<String, PortEdit>> to) {
		if (from.isEmpty()) {
			return;
		}
		boolean isUndo = from == undo;
		Deque<Map<String, double[]>> boxesFrom = isUndo ? undoBoxes : redoBoxes;
		Deque<Map<String, double[]>> boxesTo = isUndo ? redoBoxes : undoBoxes;
		to.push(HudEdits.copy(edits));
		boxesTo.push(movableBoxes(width, height));
		Map<String, PortEdit> state = from.pop();
		edits.clear();
		edits.putAll(state);
		placeAll(boxesFrom.isEmpty() ? Map.of() : boxesFrom.pop());
	}

	/** Puts movables back to these boxes. */
	private void placeAll(Map<String, double[]> boxes) {
		Map<String, io.github.harrythecake.cakeui.api.Movable> all = HudMovables.all();
		boxes.forEach((k, b) -> {
			var m = all.get(k);
			if (m != null) {
				m.place(b[0], b[1], b[2], b[3], width, height);
			}
		});
	}

	private boolean dirty() {
		Map<String, PortEdit>[] now = HudEditor.snapshot();
		return !normalized(now[0]).equals(normalized(initial[0])) || !normalized(now[1]).equals(normalized(initial[1]));
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
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// no dim: the live HUD is the canvas
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
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
			boolean hidden = hidden(name);
			boolean live = HudBounds.live(name);
			if (otherMod(name)) {
				g.fill(x1, y1, x2, y2, 0x60404040);
				g.renderOutline(x1 - 1, y1 - 1, x2 - x1 + 2, y2 - y1 + 2, 0x80808080);
				Component text = label(name).copy().append(" ").append(Component.translatable("cakeui.editor.otherMod"));
				if (font.width(text) <= x2 - x1 + 40) {
					g.drawCenteredString(font, text, (x1 + x2) / 2, y1 - 10, 0xFFA0A0A0);
				}
				continue;
			}
			if (!live || hidden) {
				g.fill(x1, y1, x2, y2, hidden ? 0x30FF4040 : 0x30FFFFFF);
				Component text = hidden ? label(name).copy().append(" ").append(Component.translatable("cakeui.editor.hidden")) : label(name);
				if (y2 - y1 >= 10 && font.width(text) <= x2 - x1) {
					g.drawCenteredString(font, text, (x1 + x2) / 2, (y1 + y2) / 2 - 4, hidden ? 0xFFFF9090 : 0xFFD0D0D0);
				}
			}
			if (isMovable(name)) {
				var mv = HudMovables.get(name);
				if (mv != null && mv.resizable()) {
					g.fill(x2 - 4, y2 - 4, x2 + 1, y2 + 1, name.equals(hovered) || name.equals(selected) ? 0xFFFFD000 : 0xC0FFFFFF);
				}
			}
			int color = name.equals(selected) ? 0xFFFFD000 : name.equals(hovered) ? 0xFFFFFFFF : hidden ? 0xFFE05050 : 0x90FFFFFF;
			g.renderOutline(x1 - 1, y1 - 1, x2 - x1 + 2, y2 - y1 + 2, color);
		}
		String named = dragging != null ? dragging : hovered != null ? hovered : selected;
		if (named != null && visible(named)) {
			Box b = screenBox(named);
			PortEdit e = effective(named);
			Component text = label(named);
			if (e != null && e.scale != null) {
				text = text.copy().append(String.format(java.util.Locale.ROOT, "  ×%.2f", e.scale));
			}
			int ty = b.y() > 12 ? (int) b.y() - 11 : (int) b.bottom() + 3;
			int tw = font.width(text);
			int tx = (int) Math.max(2, Math.min(width - tw - 2, b.cx() - tw / 2.0));
			g.fill(tx - 2, ty - 1, tx + tw + 2, ty + 9, 0xC0000000);
			g.drawString(font, text, tx, ty, 0xFFFFD000);
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
			g.drawString(font, lines.get(i), BAR_X, helpY + i * 9, discard ? 0xFFFFD000 : 0xFFE0E0E0);
		}
		super.render(g, mouseX, mouseY, delta);
		if (menu != null) {
			int mx = (int) menuX;
			int my = (int) menuY;
			g.fill(mx, my, mx + MENU_W, my + menu.size() * ITEM_H + 2, 0xF0101010);
			g.renderOutline(mx, my, MENU_W, menu.size() * ITEM_H + 2, 0xFFFFD000);
			for (int i = 0; i < menu.size(); i++) {
				int iy = my + 1 + i * ITEM_H;
				if (mouseX >= mx && mouseX < mx + MENU_W && mouseY >= iy && mouseY < iy + ITEM_H) {
					g.fill(mx + 1, iy, mx + MENU_W - 1, iy + ITEM_H, 0xFF404040);
				}
				g.drawString(font, menu.get(i).label, mx + 5, iy + 2, 0xFFFFFFFF);
			}
		}
	}

	// ---- mouse ----

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (menu != null) {
			int i = (int) Math.floor((my - menuY - 1) / ITEM_H);
			if (mx >= menuX && mx < menuX + MENU_W && i >= 0 && i < menu.size()) {
				menu.get(i).action.run();
			}
			menu = null;
			return true;
		}
		if (super.mouseClicked(mx, my, button)) {
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
				var m = isMovable(hit) ? HudMovables.get(hit) : null;
				resizing = m != null && m.resizable() && mx >= dragStartBox.right() - 5 && my >= dragStartBox.bottom() - 5;
			}
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit != null) {
			selected = hit;
			openMenu(hit, mx, my);
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			openAddMenu(mx, my);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseDragged(mx, my, button, dx, dy);
		}
		if (resizing) {
			if (!dragMoved) {
				dragMoved = true;
				checkpoint();
			}
			var m = HudMovables.get(dragging);
			if (m != null) {
				m.place(dragStartBox.x(), dragStartBox.y(), Math.max(16, dragStartBox.w() + (mx - dragStartX)),
						Math.max(9, dragStartBox.h() + (my - dragStartY)), width, height);
			}
			return true;
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
		if (!hasAltDown()) {
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
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragging != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = null;
			resizing = false;
			guides = List.of();
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		String hit = pick(mx, my);
		if (hit == null || scrollY == 0) {
			return super.mouseScrolled(mx, my, scrollX, scrollY);
		}
		selected = hit;
		if (isMovable(hit)) {
			var m = HudMovables.get(hit);
			double[] b = m == null ? null : m.box(width, height);
			if (b != null && m.resizable()) {
				checkpoint();
				double f = scrollY > 0 ? 1.05 : 1 / 1.05;
				m.place(b[0], b[1], Math.max(16, b[2] * f), Math.max(9, b[3] * f), width, height);
			}
			return true;
		}
		checkpoint();
		PortEdit e = edit(hit);
		PortEdit shown = effective(hit);
		double s = Math.round(((shown == null ? 1 : shown.scaleOr1()) + Math.signum(scrollY) * 0.05) * 20) / 20.0;
		s = Math.max(0.25, Math.min(4, s));
		e.scale = s == 1 && e.mode == null ? null : s;
		return true;
	}

	/** Puts a piece's on-screen top-left at (left, top), keeping its position mode (anchored by default). */
	private void moveTo(String name, double left, double top) {
		if (isMovable(name)) {
			var m = HudMovables.get(name);
			double[] b = m == null ? null : m.box(width, height);
			if (b != null) {
				m.place(left, top, b[2], b[3], width, height);
			}
			return;
		}
		Box b = screenBox(name);
		PortEdit shown = effective(name);
		PortEdit e = edit(name);
		Placement.place(e, shown != null && shown.mode != null ? shown.mode : PortEdit.Mode.ANCHORED, left, top, b.w(), b.h(), width, height);
		if (e.scale == null && shown != null && shown.scale != null) {
			e.scale = shown.scale; // the size it was dropped at
		}
	}

	private void openMenu(String name, double mx, double my) {
		if (isMovable(name)) {
			return; // a mod's own piece: drag it (and its corner handle); nothing else here
		}
		if (name.equals("crosshair")) {
			openCrosshairMenu(mx, my);
			return;
		}
		String stylePort = StylePresets.STYLES.containsKey(name) ? name : StylePresets.portOf(name);
		if (stylePort != null) {
			openStyleMenu(name, stylePort, mx, my);
			return;
		}
		openPieceMenu(name, mx, my);
	}

	/** The crosshair: the usual items plus "Style: …" (vanilla, dot, cross, circle, image) and Edit… for a style. */
	private void openCrosshairMenu(double mx, double my) {
		openPieceMenu("crosshair", mx, my);
		PortEdit pe = effective("crosshair");
		String current = pe == null ? "vanilla" : io.github.harrythecake.cakeui.edit.CrosshairStyles.shape(pe.element);
		List<String> shapes = io.github.harrythecake.cakeui.edit.CrosshairStyles.SHAPES;
		String next = shapes.get((shapes.indexOf(current) + 1) % shapes.size());
		menu.add(0, new MenuItem(Component.translatable("cakeui.editor.menu.style", Component.translatable("cakeui.editor.crosshair." + current)), () -> {
			checkpoint();
			PortEdit c = edit("crosshair");
			c.element = next.equals("vanilla") ? null
					: io.github.harrythecake.cakeui.edit.CrosshairStyles.defaults(next, next.equals("image") ? HudElementScreen.firstImage() : null);
		}));
		PortEdit own = edits.get("crosshair");
		if (own != null && own.element != null) {
			menu.add(1, new MenuItem(Component.translatable("cakeui.editor.menu.edit"), () -> openPanel("crosshair", own)));
		}
		menuY = Math.min(my, height - menu.size() * ITEM_H - 4);
	}

	/** A port with style presets (or its preset element): the usual items plus "Style: …". */
	private void openStyleMenu(String name, String port, double mx, double my) {
		openPieceMenu(name, mx, my);
		PortEdit pe = effective(port);
		String current = pe == null || pe.style == null ? "vanilla" : pe.style;
		List<String> styles = StylePresets.STYLES.get(port);
		String next = styles.get((styles.indexOf(current) + 1) % styles.size());
		menu.add(0, new MenuItem(Component.translatable("cakeui.editor.menu.style", Component.translatable("cakeui.editor.style." + current)), () -> {
			checkpoint();
			applyStyle(port, next);
		}));
		menuY = Math.min(my, height - menu.size() * ITEM_H - 4);
	}

	/** Switches a port's look: a preset hides the vanilla piece and adds one element where it was. */
	private void applyStyle(String port, String style) {
		Box where = screenBox(port);
		PortEdit pe = edit(port);
		String key = StylePresets.key(port);
		PortEdit old = edits.remove(key);
		if (style.equals("vanilla")) {
			pe.style = null;
			pe.hide = null;
			selected = port;
			return;
		}
		pe.style = style;
		pe.hide = Boolean.TRUE;
		PortEdit el = new PortEdit();
		el.element = StylePresets.element(port, style);
		if (old != null && old.mode != null) {
			// keep where the player had put the previous preset
			el.mode = old.mode;
			el.ax = old.ax;
			el.ay = old.ay;
			el.x = old.x;
			el.y = old.y;
			el.scale = old.scale;
		} else {
			Placement.place(el, PortEdit.Mode.ANCHORED, where.x(), where.y(), Math.max(1, where.w()), Math.max(1, where.h()), width, height);
		}
		edits.put(key, el);
		selected = key;
	}

	private void openPieceMenu(String name, double mx, double my) {
		PortEdit e = effective(name);
		boolean hidden = e != null && Boolean.TRUE.equals(e.hide);
		float opacity = e == null || e.opacity == null ? 1 : e.opacity;
		PortEdit.Mode mode = e == null || e.mode == null ? PortEdit.Mode.ANCHORED : e.mode;
		List<MenuItem> items = new ArrayList<>();
		items.add(new MenuItem(Component.translatable(hidden ? "cakeui.editor.menu.show" : "cakeui.editor.menu.hide"), () -> {
			checkpoint();
			edit(name).hide = hidden ? Boolean.FALSE : Boolean.TRUE; // "show" also undoes a hide the layout made
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
		PortEdit own = edits.get(name);
		if (own != null && own.element != null) {
			// an element added in this target: edit its content, or delete it
			items.add(0, new MenuItem(Component.translatable("cakeui.editor.menu.edit"), () -> openPanel(name, own)));
			items.add(new MenuItem(Component.translatable("cakeui.editor.menu.delete"), () -> {
				checkpoint();
				String port = StylePresets.portOf(name);
				if (port != null) {
					applyStyle(port, "vanilla"); // a preset's element: back to the vanilla piece
				} else {
					edits.remove(name);
				}
				selected = null;
			}));
		} else {
			items.add(new MenuItem(Component.translatable(HudEditor.target() == HudEditor.Target.LAYOUT ? "cakeui.editor.menu.resetLayout" : "cakeui.editor.menu.reset"), () -> {
				checkpoint();
				edits.remove(name);
			}));
		}
		menu = items;
		menuX = Math.min(mx, width - MENU_W - 2);
		menuY = Math.min(my, height - items.size() * ITEM_H - 4);
	}

	/** Right-click on empty space: add a text, box, bar or image there. */
	private void openAddMenu(double mx, double my) {
		List<MenuItem> items = new ArrayList<>();
		for (String type : AddedElements.TYPES) {
			items.add(new MenuItem(Component.translatable("cakeui.editor.menu.add", Component.translatable("cakeui.editor.add." + type)), () -> {
				checkpoint();
				com.google.gson.JsonObject def = AddedElements.defaults(type, type.equals("image") ? HudElementScreen.firstImage() : null);
				String key = AddedElements.newKey(type, HudEditor.current());
				PortEdit e = new PortEdit();
				e.element = def;
				double[] size = AddedElements.guessSize(def);
				Placement.place(e, PortEdit.Mode.ANCHORED, mx, my, size[0], size[1], width, height);
				edits.put(key, e);
				selected = key;
				if (type.equals("text") || type.equals("image")) {
					openPanel(key, e);
				}
			}));
		}
		menu = items;
		menuX = Math.min(mx, width - MENU_W - 2);
		menuY = Math.min(my, height - items.size() * ITEM_H - 4);
	}

	/** Opens the properties panel of an added element (the editor stays open underneath). */
	void openPanel(String key, PortEdit e) {
		childOpen = true;
		minecraft.setScreen(new HudElementScreen(this, key, e));
	}

	/** The properties panel changed an element: an undo step (called before the change). */
	void beforeElementChange() {
		checkpoint();
	}

	// ---- keys ----

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
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
		if (hasControlDown() && key == GLFW.GLFW_KEY_Z) {
			restore(undo, redo);
			return true;
		}
		if (hasControlDown() && key == GLFW.GLFW_KEY_Y) {
			restore(redo, undo);
			return true;
		}
		if (selected != null) {
			int step = hasShiftDown() ? 10 : 1;
			int dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
			int dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
			if (dx != 0 || dy != 0) {
				checkpoint();
				Box b = screenBox(selected);
				moveTo(selected, b.x() + dx, b.y() + dy);
				return true;
			}
		}
		return super.keyPressed(key, scancode, modifiers);
	}

	// ---- closing ----

	/** Done (also for the self-test). */
	void done() {
		close(true);
	}

	private void close(boolean save) {
		finished = true;
		finishMovables(save);
		HudEditor.finish(save);
		minecraft.setScreen(parent);
	}

	/** Done: the movables that changed are saved by their mods. Cancel: all go back where they were. */
	private void finishMovables(boolean save) {
		Map<String, io.github.harrythecake.cakeui.api.Movable> all = HudMovables.all();
		for (Map.Entry<String, io.github.harrythecake.cakeui.api.Movable> e : all.entrySet()) {
			double[] start = movableStart.get(e.getKey());
			double[] now = e.getValue().box(width, height);
			try {
				if (save) {
					if (start == null || now == null || !java.util.Arrays.equals(start, now)) {
						e.getValue().save();
					}
				} else if (start != null) {
					e.getValue().place(start[0], start[1], start[2], start[3], width, height);
					e.getValue().cancel();
				}
			} catch (RuntimeException ex) {
				CakeUI.LOGGER.error("CakeUI: movable {} failed", e.getKey(), ex);
			}
		}
	}

	@Override
	public void onClose() {
		close(false);
	}

	@Override
	public void removed() {
		if (!finished && !childOpen) {
			finishMovables(false);
			finished = true;
			HudEditor.finish(false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
