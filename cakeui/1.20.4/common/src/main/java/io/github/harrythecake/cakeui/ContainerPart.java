package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.mixin.ContainerMenuAccessor;
import io.github.harrythecake.cakeui.mixin.ContainerScreenAccessor;
import io.github.harrythecake.cakeui.mixin.SlotAccessor;
import io.github.harrythecake.cakeui.model.GameValues;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Loader;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Problems;
import io.github.harrythecake.cakeui.platform.Platform;
import io.github.harrythecake.cakeui.platform.Services;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Container screens (any AbstractContainerScreen but the player's inventory, which is the HUD's built-in
 * `inventory`): the `container` part, one screen per menu type id (`container/minecraft/furnace/`). A screen
 * comes from the active layout when it mentions the menu, else from a mod's default layout (assets/<modid>/cakeui/
 * in its jar, or a folder a plugin added), also with no layout chosen; else the screen stays vanilla and none of
 * this runs (ADR 0006).
 *
 * The screen stays the real vanilla or modded screen: its slots, clicks, dragging, tooltips, recipe book and JEI work
 * as before. CakeUI draws around and behind them: the background (dim), the panel (the screen's renderBg), slot
 * skins and highlights, the two title lines, and the layout's elements; it may resize and move the panel
 * (imageWidth/Height, leftPos/topPos) and move slots on the client.
 */
public final class ContainerPart {
	/** One mod's default layout and the renderer that draws it. */
	private record ModLayout(String owner, Path folder, Renderer renderer) {
	}

	private record Folder(String owner, Path folder) {
	}

	private static final List<ModLayout> MODS = new ArrayList<>();
	/** Which mod's layout draws each menu (when the active layout doesn't). */
	private static final Map<String, ModLayout> BY_MENU = new HashMap<>();
	/** Folders plugins added through the API. */
	private static final List<Folder> ADDED = new CopyOnWriteArrayList<>();
	/** Menu type id of each open menu ("minecraft:furnace"); null for menus without a type (the player's inventory, creative). */
	private static final Map<AbstractContainerMenu, String> IDS = new WeakHashMap<>();
	private static final Map<String, ScreenPorts> PORTS = new HashMap<>();
	/** Slots CakeUI moved: their own place {x, y}. */
	private static final Map<Slot, int[]> MOVED = new WeakHashMap<>();
	/** Container screens whose panel size a layout set: their own {imageWidth, imageHeight}. */
	private static final Map<AbstractContainerScreen<?>, int[]> OWN_SIZE = new WeakHashMap<>();
	/** The slot whose skin or highlight is being drawn ({slot.*} values). */
	@Nullable
	private static Slot currentSlot;
	/** The mouse this frame, GUI pixels (slot.hovered). */
	private static int mouseX;
	private static int mouseY;
	/** Boxes CakeUI drew around the panel last frame ({x, y, w, h}), for JEI's exclusion areas. */
	private static List<int[]> extraAreas = List.of();
	@Nullable
	private static AbstractContainerScreen<?> extraAreasScreen;

	private ContainerPart() {
	}

	// ---- layouts ----

	/** A folder laid out like a mod's assets/<modid>/cakeui/ (API: addContainerLayouts). Used from the next reload. */
	public static void addFolder(String owner, Path folder) {
		ADDED.add(new Folder(owner, folder));
	}

	/** Loads every mod's default layout again (with Ui.reload). Render thread. */
	static void reload() {
		restoreSlots();
		for (ModLayout m : MODS) {
			m.renderer().close();
		}
		MODS.clear();
		BY_MENU.clear();
		List<Folder> folders = new ArrayList<>();
		try {
			for (Platform.ModLayoutFolder f : Services.PLATFORM.modLayoutFolders()) {
				folders.add(new Folder(f.modId(), f.folder()));
			}
		} catch (RuntimeException e) {
			CakeUI.LOGGER.warn("CakeUI: could not list the mods' container layouts", e);
		}
		folders.addAll(ADDED);
		for (Folder f : folders) {
			Loader loader;
			try {
				loader = Loader.loadMod(f.folder(), f.owner());
			} catch (RuntimeException e) {
				CakeUI.LOGGER.warn("CakeUI: {}'s container layouts could not be read", f.owner(), e);
				continue;
			}
			Layout l = loader.layout();
			for (Problems.Problem p : l.problems.list()) {
				CakeUI.LOGGER.warn("CakeUI: {}'s container layouts: {}", f.owner(), p);
			}
			Node part = l.part(Layout.CONTAINER);
			if (loader.fatal || part == null || part.builtins.isEmpty()) {
				continue;
			}
			ModLayout m = new ModLayout(f.owner(), f.folder(), new Renderer(l));
			MODS.add(m);
			for (String id : part.builtins.keySet()) {
				ModLayout other = BY_MENU.get(id);
				String namespace = id.substring(0, id.indexOf(':'));
				if (other == null || (!other.owner().equals(namespace) && f.owner().equals(namespace))) {
					BY_MENU.put(id, m); // the mod that owns the menu's namespace wins
				}
				if (other != null) {
					CakeUI.LOGGER.warn("CakeUI: both {} and {} ship a layout for {}; {}'s is used", other.owner(), f.owner(), id, BY_MENU.get(id).owner());
				}
			}
			CakeUI.LOGGER.info("CakeUI: {} ships container layouts for {} ({} problem(s))", f.owner(), part.builtins.keySet(), l.problems.size());
		}
		PORTS.clear();
	}

	/** Problems of the mods' default layouts (dev mode's overlay lists them after the active layout's), by mod. */
	static Map<String, List<Problems.Problem>> modProblems() {
		Map<String, List<Problems.Problem>> out = new LinkedHashMap<>();
		for (ModLayout m : MODS) {
			if (!m.renderer().layout.problems.isEmpty()) {
				out.put(m.owner(), m.renderer().layout.problems.list());
			}
		}
		return out;
	}

	/** The menu ids each mod's default layout covers (for the settings screen and logs). */
	static Map<String, String> modMenus() {
		Map<String, String> out = new java.util.TreeMap<>();
		BY_MENU.forEach((id, m) -> out.put(id, m.owner()));
		return out;
	}

	@Nullable
	private static Node node(String id) {
		Renderer pack = Ui.renderer();
		Node n = pack == null ? null : pack.layout.container(id);
		if (n != null) {
			return n;
		}
		ModLayout m = BY_MENU.get(id);
		return m == null ? null : m.renderer().layout.container(id);
	}

	@Nullable
	private static Renderer renderer(String id) {
		Renderer pack = Ui.renderer();
		if (pack != null && pack.layout.container(id) != null) {
			return pack;
		}
		ModLayout m = BY_MENU.get(id);
		return m == null ? null : m.renderer();
	}

	/** A menu's type id ("minecraft:furnace", "pigtech:treadmill"), or null when it has none (the player's inventory, creative). */
	@Nullable
	public static String menuId(AbstractContainerMenu menu) {
		if (IDS.containsKey(menu)) {
			return IDS.get(menu);
		}
		String id = null;
		try {
			net.minecraft.world.inventory.MenuType<?> type = menu.getType();
			ResourceLocation key = BuiltInRegistries.MENU.getKey(type);
			id = key == null ? null : key.toString();
		} catch (RuntimeException e) {
			// a menu built without a type: getType() throws
		}
		IDS.put(menu, id);
		return id;
	}

	/** The ports of a container screen that the active layout or a mod's default lays out; null leaves the screen vanilla. */
	@Nullable
	static ScreenPorts ports(AbstractContainerScreen<?> screen) {
		if (screen instanceof InventoryScreen) {
			return null;
		}
		String id = menuId(screen.getMenu());
		if (id == null || node(id) == null) {
			return null;
		}
		return PORTS.computeIfAbsent(id, k -> new ScreenPorts(Layout.CONTAINER, () -> node(k), () -> renderer(k), ContainerPart::panel, false));
	}

	@Nullable
	private static ScreenPorts ports(Screen screen) {
		return screen instanceof AbstractContainerScreen<?> c ? ports(c) : null;
	}

	/** The panel's box {x, y, w, h} in GUI pixels: the frame of a container screen's buttons port and panel elements. */
	static int[] panel(Screen screen) {
		ContainerScreenAccessor a = (ContainerScreenAccessor) screen;
		return new int[] {a.cakeui$left(), a.cakeui$top(), a.cakeui$imageWidth(), a.cakeui$imageHeight()};
	}

	// ---- the panel's size and place (init) ----

	/** AbstractContainerScreen.init HEAD: the panel's size before vanilla centres it (leftPos/topPos follow, so JEI sees it). */
	public static void beforeInit(AbstractContainerScreen<?> screen) {
		ScreenPorts p = ports(screen);
		ContainerScreenAccessor a = (ContainerScreenAccessor) screen;
		int[] own = OWN_SIZE.get(screen);
		Node panel = p == null ? null : p.port("panel");
		if (panel == null || (!panel.has("width") && !panel.has("height"))) {
			if (own != null) { // a reload took the size away
				a.cakeui$setImageWidth(own[0]);
				a.cakeui$setImageHeight(own[1]);
				OWN_SIZE.remove(screen);
			}
			return;
		}
		if (own == null) {
			own = new int[] {a.cakeui$imageWidth(), a.cakeui$imageHeight()};
			OWN_SIZE.put(screen, own);
		}
		Renderer r = p.renderer();
		r.prepare(screen.width, screen.height);
		r.env.self = null;
		a.cakeui$setImageWidth(Math.max(1, (int) Math.round(r.length(panel, null, "width", Env.AXIS_X, screen.width, screen.height, own[0]))));
		a.cakeui$setImageHeight(Math.max(1, (int) Math.round(r.length(panel, null, "height", Env.AXIS_Y, screen.width, screen.height, own[1]))));
	}

	/** AbstractContainerScreen.init TAIL: the panel's x/y move it (and so every slot); the screen's own init goes on from there. */
	public static void afterBaseInit(AbstractContainerScreen<?> screen) {
		ScreenPorts p = ports(screen);
		Node panel = p == null ? null : p.port("panel");
		if (panel == null || (!panel.has("x") && !panel.has("y"))) {
			return;
		}
		Renderer r = p.renderer();
		r.prepare(screen.width, screen.height);
		r.env.self = null;
		ContainerScreenAccessor a = (ContainerScreenAccessor) screen;
		a.cakeui$setLeft(a.cakeui$left() + (int) Math.round(r.length(panel, null, "x", Env.AXIS_X, screen.width, screen.height, 0)));
		a.cakeui$setTop(a.cakeui$top() + (int) Math.round(r.length(panel, null, "y", Env.AXIS_Y, screen.width, screen.height, 0)));
	}

	/** After the whole init (and every resize): slots to their layout places. */
	static void afterInit(Screen screen) {
		ScreenPorts p = ports(screen);
		if (p != null) {
			placeSlots((AbstractContainerScreen<?>) screen, p);
		}
	}

	// ---- per frame ----

	/** Before the screen draws: the frame's mouse and time, slot places (they may follow values), buttons. */
	static void beginFrame(Screen screen, int mouseX, int mouseY) {
		ScreenPorts p = ports(screen);
		if (p == null) {
			return;
		}
		ContainerPart.mouseX = mouseX;
		ContainerPart.mouseY = mouseY;
		p.renderer().beginFrame(screen.width, screen.height, mouseX, mouseY, true);
		placeSlots((AbstractContainerScreen<?>) screen, p);
	}

	/** The panel (the screen's renderBg, with its progress arrows): vanilla's unless hidden, then the panel's back elements. */
	public static void drawPanel(AbstractContainerScreen<?> screen, GuiGraphics ctx, Runnable vanilla) {
		ScreenPorts p = ports(screen);
		if (p == null || p.port("panel") == null) {
			vanilla.run();
			return;
		}
		if (p.vanilla("panel")) {
			ctx.setColor(1, 1, 1, p.portOpacity("panel"));
			vanilla.run();
			ctx.setColor(1, 1, 1, 1);
		}
		Renderer r = p.renderer();
		r.recordBoxes = new ArrayList<>();
		drawPanelLayer(p, screen, ctx, "back");
		frameBoxes = r.recordBoxes;
		r.recordBoxes = null;
	}

	/** Boxes the panel's elements took this frame (back layer; the front layer adds its own). */
	private static List<double[]> frameBoxes = new ArrayList<>();

	private static void drawPanelLayer(ScreenPorts p, AbstractContainerScreen<?> screen, GuiGraphics ctx, String layer) {
		Node panel = p.port("panel");
		if (panel == null || panel.children.isEmpty()) {
			return;
		}
		Renderer r = p.renderer();
		List<Node> kids = new ArrayList<>();
		for (Node k : panel.children) {
			r.env.self = null;
			if (r.word(k, "layer", "back").equals(layer)) {
				kids.add(k);
			}
		}
		int[] b = panel(screen);
		r.drawElementsIn(ctx, panel, kids, null, b[0], b[1], b[2], b[3], 1);
	}

	/** renderSlot HEAD (the pose is at the panel's corner): the slot's skin behind its item. */
	public static void drawSlotSkin(AbstractContainerScreen<?> screen, GuiGraphics ctx, Slot slot) {
		ScreenPorts p = ports(screen);
		Node o = p == null ? null : pick(p, slot, "skin");
		if (o == null) {
			return;
		}
		drawInSlot(p, screen, ctx, slot, o.ports.get("skin"));
	}

	/**
	 * The hovered slot's highlight: false when the layout draws its own (drawn here), true to let vanilla draw
	 * its white square. The pose is at the panel's corner.
	 */
	public static boolean vanillaHighlight(AbstractContainerScreen<?> screen, GuiGraphics ctx, Slot slot) {
		ScreenPorts p = ports(screen);
		Node o = p == null ? null : pick(p, slot, "highlight");
		if (o == null) {
			return true;
		}
		drawInSlot(p, screen, ctx, slot, o.ports.get("highlight"));
		return false;
	}

	private static void drawInSlot(ScreenPorts p, AbstractContainerScreen<?> screen, GuiGraphics ctx, Slot slot, Node group) {
		if (group.children.isEmpty()) {
			return;
		}
		int[] b = panel(screen);
		ctx.pose().pushPose();
		ctx.pose().translate(-b[0], -b[1], 0);
		currentSlot = slot;
		try {
			p.renderer().drawElementsIn(ctx, group, group.children, slot, b[0] + slot.x - 1, b[1] + slot.y - 1, 18, 18, 1);
		} finally {
			currentSlot = null;
			ctx.pose().popPose();
		}
	}

	/** One of the title lines (renderLabels: "title", "inventoryTitle"), moved, restyled or hidden; the pose is at the panel's corner. */
	public static int label(AbstractContainerScreen<?> screen, String port, GuiGraphics ctx, Font font, Component text, int x, int y, int color,
			boolean shadow, LabelDraw original) {
		ScreenPorts p = ports(screen);
		if (p == null || p.port(port) == null) {
			return original.draw(text, color);
		}
		Component shown = p.port(port).has("text") ? Component.literal(p.text(port, text.getString())) : text;
		int shownColor = p.color(port, color);
		int[] out = {0};
		p.vanillaPiece(port, ctx, x, y, () -> out[0] = original.draw(shown, shownColor));
		return out[0];
	}

	/** Vanilla's drawString for a title line, with CakeUI's text and colour. */
	@FunctionalInterface
	public interface LabelDraw {
		int draw(Component text, int color);
	}

	/**
	 * After the slots and title lines, before the carried item and tooltips (the pose is at the panel's corner): the
	 * title ports' elements, the panel's front elements, the screen's front layer, then an element's tooltip.
	 */
	public static void drawFront(AbstractContainerScreen<?> screen, GuiGraphics ctx) {
		ScreenPorts p = ports(screen);
		if (p == null) {
			return;
		}
		int[] b = panel(screen);
		Renderer r = p.renderer();
		ctx.pose().pushPose();
		ctx.pose().translate(-b[0], -b[1], 0);
		r.recordBoxes = frameBoxes;
		drawPanelLayer(p, screen, ctx, "front");
		r.recordBoxes = null;
		p.drawPortElementsIn("title", ctx, b[0], b[1], b[2], b[3]);
		p.drawPortElementsIn("inventoryTitle", ctx, b[0], b[1], b[2], b[3]);
		p.drawElements(screen, ctx, "front", 1);
		ctx.pose().popPose();
		updateExtraAreas(screen, b);
		List<Component> tip = r.hoverTooltip();
		if (tip != null && !itemTooltip(screen)) {
			List<FormattedCharSequence> lines = new ArrayList<>();
			for (Component c : tip) {
				lines.add(c.getVisualOrderText());
			}
			screen.setTooltipForNextRenderPass(lines);
		}
	}

	/** Vanilla shows an item's tooltip now (a slot with an item under the mouse, nothing carried): it wins over an element's. */
	private static boolean itemTooltip(AbstractContainerScreen<?> screen) {
		Slot hovered = ((ContainerScreenAccessor) screen).cakeui$hoveredSlot();
		return hovered != null && hovered.hasItem() && screen.getMenu().getCarried().isEmpty();
	}

	// ---- slots ----

	/** The most specific entry of the slots port that sets {@code what} ("x", "y", or the "skin"/"highlight" groups) for this slot. */
	@Nullable
	private static Node pick(ScreenPorts p, Slot slot, String what) {
		Node port = p.port("slots");
		if (port == null) {
			return null;
		}
		boolean group = what.equals("skin") || what.equals("highlight");
		boolean player = slot.container instanceof Inventory;
		Node best = null;
		int bestSpecificity = -1;
		for (Node o : port.slots.values()) {
			if ((group ? o.ports.containsKey(what) : o.has(what)) && o.selector.specificity() > bestSpecificity && o.selector.matches(slot.index, player)) {
				best = o;
				bestSpecificity = o.selector.specificity();
			}
		}
		return best;
	}

	/** Moves the slots the layout places (x/y in the panel, GUI pixels) and puts back any it no longer places. */
	private static void placeSlots(AbstractContainerScreen<?> screen, ScreenPorts p) {
		Node port = p.port("slots");
		if (port == null && MOVED.isEmpty()) {
			return;
		}
		Renderer r = p.renderer();
		int[] b = panel(screen);
		for (Slot slot : screen.getMenu().slots) {
			Node ox = port == null ? null : pick(p, slot, "x");
			Node oy = port == null ? null : pick(p, slot, "y");
			int[] own = MOVED.get(slot);
			if (ox == null && oy == null) {
				if (own != null) {
					move(slot, own[0], own[1]);
					MOVED.remove(slot);
				}
				continue;
			}
			if (own == null) {
				own = new int[] {slot.x, slot.y};
				MOVED.put(slot, own);
			}
			r.env.self = null;
			int x = ox == null ? own[0] : (int) Math.round(r.length(ox, null, "x", Env.AXIS_X, b[2], b[3], own[0]));
			int y = oy == null ? own[1] : (int) Math.round(r.length(oy, null, "y", Env.AXIS_Y, b[2], b[3], own[1]));
			move(slot, x, y);
		}
	}

	private static void move(Slot slot, int x, int y) {
		if (slot.x != x) {
			((SlotAccessor) slot).cakeui$setX(x);
		}
		if (slot.y != y) {
			((SlotAccessor) slot).cakeui$setY(y);
		}
	}

	/** Every moved slot back to its own place (before a reload: the new layout places them again). */
	private static void restoreSlots() {
		for (Map.Entry<Slot, int[]> e : MOVED.entrySet()) {
			move(e.getKey(), e.getValue()[0], e.getValue()[1]);
		}
		MOVED.clear();
	}

	// ---- JEI: what CakeUI draws outside the panel ----

	private static void updateExtraAreas(AbstractContainerScreen<?> screen, int[] b) {
		List<int[]> out = new ArrayList<>();
		for (double[] box : frameBoxes) {
			int x1 = (int) Math.floor(box[0]);
			int y1 = (int) Math.floor(box[1]);
			int x2 = (int) Math.ceil(box[2]);
			int y2 = (int) Math.ceil(box[3]);
			if (x2 - x1 >= 1 && y2 - y1 >= 1 && (x1 < b[0] || y1 < b[1] || x2 > b[0] + b[2] || y2 > b[1] + b[3])) {
				addMerged(out, new int[] {x1, y1, x2, y2});
			}
		}
		for (Slot slot : screen.getMenu().slots) {
			int x1 = b[0] + slot.x - 1;
			int y1 = b[1] + slot.y - 1;
			if (slot.isActive() && (x1 < b[0] || y1 < b[1] || x1 + 18 > b[0] + b[2] || y1 + 18 > b[1] + b[3])) {
				addMerged(out, new int[] {x1, y1, x1 + 18, y1 + 18});
			}
		}
		for (int[] r : out) {
			r[2] -= r[0];
			r[3] -= r[1];
		}
		extraAreas = out;
		extraAreasScreen = screen;
		frameBoxes = new ArrayList<>();
	}

	/** Adds a box {x1, y1, x2, y2}, joined with every box it overlaps. */
	private static void addMerged(List<int[]> boxes, int[] box) {
		boolean merged = true;
		while (merged) {
			merged = false;
			for (int i = 0; i < boxes.size(); i++) {
				int[] o = boxes.get(i);
				if (box[0] < o[2] && o[0] < box[2] && box[1] < o[3] && o[1] < box[3]) {
					box = new int[] {Math.min(box[0], o[0]), Math.min(box[1], o[1]), Math.max(box[2], o[2]), Math.max(box[3], o[3])};
					boxes.remove(i);
					merged = true;
					break;
				}
			}
		}
		boxes.add(box);
	}

	/**
	 * What a laid-out container screen draws outside its panel, {x, y, width, height} in GUI pixels: the panel
	 * port's elements and moved slots. Empty for any other screen. JEI keeps its lists clear of these.
	 */
	public static List<int[]> extraAreas(AbstractContainerScreen<?> screen) {
		return screen == extraAreasScreen && ports(screen) != null ? extraAreas : List.of();
	}

	// ---- game values ----

	@Nullable
	static AbstractContainerScreen<?> openScreen() {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.screen instanceof AbstractContainerScreen<?> s ? s : null;
	}

	/** The menu of the open container screen, or null. */
	@Nullable
	public static AbstractContainerMenu openMenu() {
		AbstractContainerScreen<?> s = openScreen();
		return s == null ? null : s.getMenu();
	}

	private static void screenValue(String name, java.util.function.Function<AbstractContainerScreen<?>, Object> f, Object fallback) {
		GameValues.register(name, () -> {
			AbstractContainerScreen<?> s = openScreen();
			return s == null ? fallback : f.apply(s);
		});
	}

	private static void slotValue(String name, java.util.function.Function<Slot, Object> f, Object fallback) {
		GameValues.register(name, () -> currentSlot == null ? fallback : f.apply(currentSlot));
	}

	/** `menu.*` (the open container screen) and `slot.*` (inside a slot's skin or highlight). */
	static void registerValues() {
		screenValue("menu.open", s -> true, false);
		screenValue("menu.id", s -> {
			String id = menuId(s.getMenu());
			return id == null ? "" : id;
		}, "");
		screenValue("menu.title", s -> s.getTitle().getString(), "");
		screenValue("menu.slotCount", s -> s.getMenu().slots.size(), 0);
		screenValue("menu.dataCount", s -> ((ContainerMenuAccessor) s.getMenu()).cakeui$dataSlots().size(), 0);
		screenValue("menu.carried", s -> !s.getMenu().getCarried().isEmpty(), false);
		screenValue("menu.hoveredSlot", s -> {
			Slot h = ((ContainerScreenAccessor) s).cakeui$hoveredSlot();
			return h == null ? -1 : h.index;
		}, -1);
		screenValue("menu.left", s -> ((ContainerScreenAccessor) s).cakeui$left(), 0);
		screenValue("menu.top", s -> ((ContainerScreenAccessor) s).cakeui$top(), 0);
		screenValue("menu.width", s -> ((ContainerScreenAccessor) s).cakeui$imageWidth(), 0);
		screenValue("menu.height", s -> ((ContainerScreenAccessor) s).cakeui$imageHeight(), 0);
		GameValues.registerFamily("menu.data.", n -> {
			AbstractContainerMenu m = openMenu();
			if (m == null) {
				return 0;
			}
			List<DataSlot> data = ((ContainerMenuAccessor) m).cakeui$dataSlots();
			return n < data.size() ? data.get(n).get() : 0;
		});
		slotValue("slot.index", s -> s.index, -1);
		slotValue("slot.empty", s -> !s.hasItem(), true);
		slotValue("slot.count", s -> s.getItem().getCount(), 0);
		slotValue("slot.item", s -> s.hasItem() ? BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString() : "", "");
		slotValue("slot.player", s -> s.container instanceof Inventory, false);
		slotValue("slot.hovered", s -> {
			// vanilla's own test (isHovering); its hoveredSlot is only set after the slot's skin is drawn
			AbstractContainerScreen<?> screen = openScreen();
			if (screen == null) {
				return false;
			}
			int[] b = panel(screen);
			return mouseX >= b[0] + s.x - 1 && mouseX < b[0] + s.x + 17 && mouseY >= b[1] + s.y - 1 && mouseY < b[1] + s.y + 17;
		}, false);
	}

	/** A plugin's value read from the open menu when it is one of {@code menuClass} (API: registerMenuValue). */
	public static <M extends AbstractContainerMenu> Supplier<Object> menuValue(Class<M> menuClass, java.util.function.Function<? super M, ?> value, Object fallback) {
		return () -> {
			AbstractContainerMenu m = openMenu();
			if (!menuClass.isInstance(m)) {
				return fallback;
			}
			Object v = value.apply(menuClass.cast(m));
			return v == null ? fallback : v;
		};
	}

	// ---- dev mode ----

	/** Dev mode with button ids on: the menu's id above the panel and each slot's number, on every container screen. */
	static void drawIds(Screen screen, GuiGraphics ctx) {
		if (!Ui.devMode() || !Ui.config().showButtonIds || !(screen instanceof AbstractContainerScreen<?> c) || screen instanceof InventoryScreen) {
			return;
		}
		String id = menuId(c.getMenu());
		Font font = Minecraft.getInstance().font;
		int[] b = panel(screen);
		ctx.pose().pushPose();
		ctx.pose().translate(0, 0, 400);
		String head = (id == null ? "(no menu type)" : id) + (ports(c) != null ? "" : "  (vanilla)");
		ctx.fill(b[0] - 1, b[1] - 11, b[0] + font.width(head) + 1, b[1] - 2, 0xC0000000);
		ctx.drawString(font, head, b[0], b[1] - 10, 0xFFFFFF55, false);
		ctx.pose().scale(0.5F, 0.5F, 1);
		for (Slot slot : c.getMenu().slots) {
			if (slot.isActive()) {
				String n = String.valueOf(slot.index);
				int x = (b[0] + slot.x) * 2;
				int y = (b[1] + slot.y) * 2;
				ctx.fill(x - 1, y - 1, x + font.width(n) + 1, y + 8, 0xC0000000);
				ctx.drawString(font, n, x, y, 0xFF55FFFF, false);
			}
		}
		ctx.pose().popPose();
	}
}
