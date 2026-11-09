package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The world map (M): drag to move, scroll to zoom, left-click an icon for "Track this" and
 * "Nearest town", right-click for waypoints. Shows the shard's static map where it applies, else
 * what this game has seen; other dimensions with a map can be picked. Only the player's own marker.
 */
public final class WorldMapScreen extends Screen {
	private static double lastScale = 1;
	private static String lastDim;
	private static double lastX;
	private static double lastZ;

	private final Screen parent;
	private final MapGeometry.View view = new MapGeometry.View();
	private String dim;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private Menu menu;
	/** The waypoint under the cursor when the left button went down (a click, unless it turns into a drag). */
	private Waypoint pressed;
	private double pressedMoved;
	/** A waypoint the map just jumped to: drawn larger for a moment. */
	private Waypoint flash;
	private long flashUntil;
	private Component message;
	private long messageUntil;
	private Button modeButton;
	private Button dimButton;

	private record Item(Component label, Runnable action) {
	}

	private static final class Menu {
		double x;
		double y;
		/** Drawn as a column of buttons (the left-click popup) instead of a plain list. */
		boolean buttons;
		final List<Item> items = new ArrayList<>();

		int rowHeight() {
			return buttons ? 20 : 12;
		}
	}

	public WorldMapScreen(@Nullable Screen parent) {
		super(Component.translatable("cakemap.map.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		MapSession s = CakeMap.session();
		LocalPlayer p = Minecraft.getInstance().player;
		if (dim == null) {
			dim = s == null ? "" : s.dimension();
			view.scale = lastScale;
			if (dim.equals(lastDim)) {
				view.centerX = lastX;
				view.centerZ = lastZ;
			} else if (p != null) {
				view.centerX = p.getX();
				view.centerZ = p.getZ();
			}
		}
		view.screenX = width / 2.0;
		view.screenY = height / 2.0;
		// right: Done, Waypoints; left: map mode, Center; the dimension picker takes what is left
		int doneW = Math.min(80, Math.max(50, width / 8));
		int listW = Math.min(80, Math.max(60, width / 7));
		int modeW = Math.min(110, Math.max(60, width / 7));
		int centerW = Math.min(70, Math.max(50, width / 9));
		int right = width - 4 - doneW - 4 - listW - 4;
		int x = 4;
		modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
			MapSettings st = CakeMap.settings();
			st.mode = st.mode == MapSettings.Mode.STATIC ? MapSettings.Mode.DYNAMIC : MapSettings.Mode.STATIC;
			CakeMap.saveSettings();
			b.setMessage(modeLabel());
		}).bounds(x, 4, modeW, 20).build());
		x += modeW + 4;
		addRenderableWidget(Button.builder(Component.translatable("cakemap.map.center"), b -> center()).bounds(x, 4, centerW, 20).build());
		x += centerW + 4;
		dimButton = addRenderableWidget(Button.builder(Component.literal(dim), b -> nextDimension()).bounds(x, 4, Math.max(40, right - x), 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoints.title"), b -> minecraft.setScreen(new WaypointListScreen(this)))
				.bounds(width - 4 - doneW - 4 - listW, 4, listW, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width - 4 - doneW, 4, doneW, 20).build());
	}

	private Component modeLabel() {
		return Component.translatable("cakemap.settings.mode." + CakeMap.settings().mode.name().toLowerCase(java.util.Locale.ROOT));
	}

	private void nextDimension() {
		MapSession s = CakeMap.session();
		if (s == null) {
			return;
		}
		List<String> dims = s.dimensions();
		int i = dims.indexOf(dim);
		dim = dims.get((i + 1) % dims.size());
		dimButton.setMessage(Component.literal(dim));
		center();
	}

	private void center() {
		LocalPlayer p = Minecraft.getInstance().player;
		MapSession s = CakeMap.session();
		if (p != null && s != null && dim.equals(s.dimension())) {
			view.centerX = p.getX();
			view.centerZ = p.getZ();
		} else if (s != null) {
			// another dimension: the middle of what is known there
			List<long[]> known = s.store(dim).knownRegions();
			if (!known.isEmpty()) {
				view.centerX = known.get(0)[0] * RegionData.SIZE + 256;
				view.centerZ = known.get(0)[1] * RegionData.SIZE + 256;
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		MapSession s = CakeMap.session();
		ctx.fill(0, 0, width, height, 0xFF16181B);
		MapDraw.map(ctx, view, MapDraw.rect(0, 0, width, height), s, dim, 1F, false);
		if (s == null) {
			return;
		}
		Waypoint hovered = menu == null ? hoveredWaypoint(mouseX, mouseY) : null;
		float size = iconSize();
		boolean flashing = flash != null && System.currentTimeMillis() < flashUntil;
		for (Waypoint w : s.shownWaypoints(dim)) {
			if (w == hovered || (flashing && w.id.equals(flash.id))) {
				continue; // drawn last, on top
			}
			drawWaypoint(ctx, w, size);
		}
		if (flashing) {
			double pulse = 1 + 0.25 * Math.abs(Math.sin((flashUntil - System.currentTimeMillis()) / 160.0));
			drawWaypoint(ctx, flash, (float) (size * 1.3 * pulse));
		}
		if (hovered != null) {
			drawWaypoint(ctx, hovered, size + 3);
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && dim.equals(s.dimension())) {
			double[] p = view.toScreen(player.getX(), player.getZ());
			if (CakeMap.settings().marker == MapSettings.Marker.HEAD) {
				PlayerFaceRenderer.draw(ctx, player.getSkin(), (int) Math.round(p[0]) - 5, (int) Math.round(p[1]) - 5, 10);
			} else {
				Icons.arrow(ctx, p[0], p[1], 12, player.getYRot() + 180);
			}
		}
		ctx.fill(0, 0, width, 28, 0x80000000);
	}

	private float iconSize() {
		return (float) Math.max(9, Math.min(16, 8 + view.scale * 4));
	}

	private void drawWaypoint(GuiGraphics ctx, Waypoint w, float size) {
		double[] p = view.toScreen(w.x + 0.5, w.z + 0.5);
		if (p[0] < -16 || p[1] < -16 || p[0] > width + 16 || p[1] > height + 16) {
			return;
		}
		MapSession s = CakeMap.session();
		if (s != null && s.waypoints.isTracked(w)) {
			// a yellow ring around the tracked waypoint
			MapDraw.outline(ctx.pose().last().pose(), MapDraw.circle(p[0], p[1], size / 2.0 + 2.5, 24), 1.2, 0xFFE15A, 1F);
		}
		Icons.draw(ctx, w, p[0], p[1], size, 1F);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		MapTextures.frame();
		double[] w = view.toWorld(mouseX, mouseY);
		int bx = (int) Math.floor(w[0]);
		int bz = (int) Math.floor(w[1]);
		String y = heightAt(bx, bz);
		String coords = bx + ", " + (y == null ? "?" : y) + ", " + bz;
		ctx.fill(0, height - 14, font.width(coords) + 8, height, 0x80000000);
		ctx.drawString(font, coords, 4, height - 11, 0xFFFFFFFF);
		Component hint = Component.translatable("cakemap.map.hint");
		int hintW = font.width(hint);
		// next to the coordinates when it fits, else a line above them
		int hintY = font.width(coords) + 8 + 12 + hintW <= width ? height - 11 : height - 25;
		if (hintY != height - 11) {
			ctx.fill(width - hintW - 8, hintY - 3, width, hintY + 11, 0x80000000);
		}
		ctx.drawString(font, hint, width - hintW - 4, hintY, 0xFFB0B0B0);
		MapSession s = CakeMap.session();
		if (s != null && StaticMaps.shard(s.server, dim) != null && !s.showStatic(dim) && CakeMap.settings().mode == MapSettings.Mode.STATIC) {
			Component why = !s.revealed(dim) ? Component.translatable("cakemap.static.not_revealed") : Component.translatable("cakemap.static.mismatch");
			ctx.drawCenteredString(font, why, width / 2, 32, 0xFFFFD27F);
		}
		Waypoint hovered = menu == null ? hoveredWaypoint(mouseX, mouseY) : null;
		if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hovered.name.isEmpty() ? Component.translatable(hovered.kind().translationKey()).getString() : hovered.name));
			lines.add(Component.translatable(hovered.kind().translationKey()).withStyle(st -> st.withColor(0xA0A0A0)));
			lines.add(Component.literal(hovered.x + ", " + hovered.y + ", " + hovered.z).withStyle(st -> st.withColor(0xA0A0A0)));
			LocalPlayer p = Minecraft.getInstance().player;
			if (p != null && s != null && hovered.dimension.equals(s.dimension())) {
				lines.add(Component.translatable("cakemap.waypoint.distance", Math.round(Math.sqrt(hovered.distanceSq(p.getX(), p.getY(), p.getZ())))));
			}
			ctx.renderComponentTooltip(font, lines, mouseX, mouseY);
		}
		if (menu != null) {
			drawMenu(ctx, mouseX, mouseY);
		}
		if (message != null && System.currentTimeMillis() < messageUntil) {
			ctx.drawCenteredString(font, message, width / 2, 44, 0xFFFFD27F);
		}
	}

	private String heightAt(int x, int z) {
		MapSession s = CakeMap.session();
		if (s == null) {
			return null;
		}
		RegionData r = s.store(dim).peek(Math.floorDiv(x, RegionData.SIZE), Math.floorDiv(z, RegionData.SIZE));
		if (r == null) {
			return null;
		}
		int i = RegionData.index(Math.floorMod(x, RegionData.SIZE), Math.floorMod(z, RegionData.SIZE));
		return r.known(i) ? String.valueOf(r.height[i]) : null;
	}

	@Nullable
	private Waypoint hoveredWaypoint(double mx, double my) {
		MapSession s = CakeMap.session();
		if (s == null || my < 28) {
			return null;
		}
		Waypoint best = null;
		double reach = Math.max(7, iconSize() / 2 + 1);
		double bestD = reach * reach;
		List<Waypoint> shown = s.shownWaypoints(dim);
		// top layer first: where icons overlap, the one drawn on top wins
		for (int i = shown.size() - 1; i >= 0; i--) {
			Waypoint w = shown.get(i);
			double[] p = view.toScreen(w.x + 0.5, w.z + 0.5);
			double d = (p[0] - mx) * (p[0] - mx) + (p[1] - my) * (p[1] - my);
			if (d < bestD) {
				bestD = d;
				best = w;
			}
		}
		return best;
	}

	// ---- right-click menu ----

	private void openMenu(double mx, double my) {
		MapSession s = CakeMap.session();
		if (s == null) {
			return;
		}
		Menu m = new Menu();
		m.x = mx;
		m.y = my;
		Waypoint w = hoveredWaypoint(mx, my);
		double[] wp = view.toWorld(mx, my);
		int bx = (int) Math.floor(wp[0]);
		int bz = (int) Math.floor(wp[1]);
		String hy = heightAt(bx, bz);
		int by = hy == null ? (minecraft.player == null ? 64 : minecraft.player.getBlockY()) : Integer.parseInt(hy) + 1;
		if (w != null) {
			m.items.add(trackItem(s, w));
			m.items.add(new Item(Component.translatable(w.bookmarked ? "cakemap.waypoint.unbookmark" : "cakemap.waypoint.bookmark"), () -> s.waypoints.setBookmarked(w, !w.bookmarked)));
			if (!w.builtin) {
				m.items.add(new Item(Component.translatable("cakemap.waypoint.edit"), () -> minecraft.setScreen(new WaypointEditScreen(this, w, false))));
				m.items.add(new Item(Component.translatable("cakemap.waypoint.delete"), () -> s.waypoints.remove(w)));
			} else {
				m.items.add(new Item(Component.translatable("cakemap.waypoint.hide"), () -> s.waypoints.setHidden(w, true)));
			}
		}
		m.items.add(new Item(Component.translatable("cakemap.map.add_here"), () -> minecraft.setScreen(new WaypointEditScreen(this,
				new Waypoint("", WaypointKind.PLAYER, dim, bx, by, bz), true))));
		m.items.add(new Item(Component.translatable("cakemap.map.add_temporary_here"), () -> {
			Waypoint t = new Waypoint(Component.translatable("cakemap.kind.player_temporary").getString(), WaypointKind.PLAYER_TEMPORARY, dim, bx, by, bz);
			s.waypoints.add(t);
		}));
		menu = m;
	}

	private Item trackItem(MapSession s, Waypoint w) {
		boolean on = s.waypoints.isTracked(w);
		return new Item(Component.translatable(on ? "cakemap.waypoint.untrack" : "cakemap.waypoint.track"), () -> {
			s.waypoints.track(on ? null : w);
			CakeMap.notice(Component.translatable(on ? "cakemap.waypoint.untracked" : "cakemap.waypoint.tracked", label(w)));
		});
	}

	private static String label(Waypoint w) {
		return w.name.isEmpty() ? Component.translatable(w.kind().translationKey()).getString() : w.name;
	}

	/** Left click on an icon: two buttons next to it. */
	private void openIconMenu(Waypoint w) {
		MapSession s = CakeMap.session();
		if (s == null) {
			return;
		}
		double[] p = view.toScreen(w.x + 0.5, w.z + 0.5);
		Menu m = new Menu();
		m.buttons = true;
		m.x = p[0] + iconSize() / 2 + 4;
		m.y = p[1] - 10;
		m.items.add(trackItem(s, w));
		m.items.add(new Item(Component.translatable("cakemap.map.nearest_town"), () -> nearestTown(s, w)));
		menu = m;
	}

	/** Moves the map to the town nearest to {@code from} and puts the mouse cursor on it. */
	private void nearestTown(MapSession s, Waypoint from) {
		Waypoint town = s.waypoints.nearest(dim, WaypointKind.TOWN, from.x + 0.5, from.z + 0.5, from);
		if (town == null) {
			message = Component.translatable("cakemap.map.no_town");
			messageUntil = System.currentTimeMillis() + 3000;
			return;
		}
		view.centerX = town.x + 0.5;
		view.centerZ = town.z + 0.5;
		flash = town;
		flashUntil = System.currentTimeMillis() + 2000;
		com.mojang.blaze3d.platform.Window win = minecraft.getWindow();
		// vanilla's own cursor call (mods that move GLFW to another thread, like Ixeris, handle it)
		com.mojang.blaze3d.platform.InputConstants.grabOrReleaseMouse(win.getWindow(), org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL,
				view.screenX * win.getScreenWidth() / win.getGuiScaledWidth(), view.screenY * win.getScreenHeight() / win.getGuiScaledHeight());
	}

	private int[] menuBox() {
		int w = 0;
		for (Item i : menu.items) {
			w = Math.max(w, font.width(i.label));
		}
		w += menu.buttons ? 20 : 12;
		int h = menu.items.size() * menu.rowHeight() + 4;
		int x = (int) Math.min(menu.x, width - w - 2);
		int y = (int) Math.min(menu.y, height - h - 2);
		return new int[] {x, y, w, h};
	}

	private void drawMenu(GuiGraphics ctx, int mx, int my) {
		int[] b = menuBox();
		ctx.pose().pushPose();
		ctx.pose().translate(0, 0, 400);
		ctx.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0xF0101214);
		ctx.renderOutline(b[0], b[1], b[2], b[3], 0xFF5A6070);
		int rh = menu.rowHeight();
		for (int i = 0; i < menu.items.size(); i++) {
			int iy = b[1] + 2 + i * rh;
			boolean hover = mx >= b[0] && mx < b[0] + b[2] && my >= iy && my < iy + rh;
			if (menu.buttons) {
				ctx.fill(b[0] + 2, iy + 1, b[0] + b[2] - 2, iy + rh - 1, hover ? 0xFF3C4658 : 0xFF262B34);
				ctx.renderOutline(b[0] + 2, iy + 1, b[2] - 4, rh - 2, hover ? 0xFFFFFFFF : 0xFF6A7080);
				ctx.drawCenteredString(font, menu.items.get(i).label, b[0] + b[2] / 2, iy + (rh - 8) / 2,
						hover ? 0xFFFFFF7F : 0xFFFFFFFF);
				continue;
			}
			if (hover) {
				ctx.fill(b[0] + 1, iy, b[0] + b[2] - 1, iy + rh, 0xFF2E3440);
			}
			ctx.drawString(font, menu.items.get(i).label, b[0] + 6, iy + 2, hover ? 0xFFFFFF7F : 0xFFFFFFFF);
		}
		ctx.pose().popPose();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (menu != null) {
			int[] b = menuBox();
			Menu m = menu;
			menu = null;
			if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] + 2 && my < b[1] + b[3] - 2) {
				int i = (int) ((my - b[1] - 2) / m.rowHeight());
				if (i >= 0 && i < m.items.size()) {
					m.items.get(i).action.run();
				}
			}
			return true;
		}
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		if (button == 1) {
			openMenu(mx, my);
			return true;
		}
		if (button == 0 && my >= 28) {
			dragging = true;
			dragX = mx;
			dragY = my;
			pressed = hoveredWaypoint(mx, my);
			pressedMoved = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (button == 0 && dragging && pressed != null && pressedMoved < 4) {
			openIconMenu(pressed);
		}
		pressed = null;
		dragging = false;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging && button == 0) {
			pressedMoved += Math.abs(mx - dragX) + Math.abs(my - dragY);
			double[] a = view.toWorld(dragX, dragY);
			double[] b = view.toWorld(mx, my);
			view.centerX += a[0] - b[0];
			view.centerZ += a[1] - b[1];
			dragX = mx;
			dragY = my;
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		double[] before = view.toWorld(mx, my);
		double next = view.scale * Math.pow(2, vertical * 0.5);
		view.scale = Math.max(1 / 16.0, Math.min(16, next));
		double[] after = view.toWorld(mx, my);
		// keep the point under the cursor in place
		view.centerX += before[0] - after[0];
		view.centerZ += before[1] - after[1];
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (CakeMap.KEYS.get("world_map").matches(keyCode, scanCode) && menu == null) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		lastScale = view.scale;
		lastDim = dim;
		lastX = view.centerX;
		lastZ = view.centerZ;
		minecraft.setScreen(parent);
	}

	/** For the self-test. */
	MapGeometry.View view() {
		return view;
	}

	/** For the self-test: what a left click on {@code w} does. */
	void openIconMenuForTest(Waypoint w) {
		openIconMenu(w);
	}

	/** For the self-test: the popup's second button. */
	void nearestTownForTest(Waypoint w) {
		menu = null;
		MapSession s = CakeMap.session();
		if (s != null) {
			nearestTown(s, w);
		}
	}
}
