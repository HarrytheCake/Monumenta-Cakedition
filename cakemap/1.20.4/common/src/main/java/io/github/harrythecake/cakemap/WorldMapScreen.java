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
	/**
	 * Icons too close together under a left click (user, 2026-11-11): a picker on the left third of the window,
	 * one button per waypoint, nearest to the click first; a pick opens that icon's popup. Null = closed.
	 */
	@Nullable
	private List<Waypoint> crowd;
	private int crowdScroll;
	private static final int CROWD_ROW = 20;
	private static final int CROWD_TOP = 22;

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
		Waypoint hovered = crowd != null ? crowdRowAt(mouseX, mouseY) : menu == null ? hoveredWaypoint(mouseX, mouseY) : null;
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
			if (crowd != null) {
				// the picker's row under the mouse: its icon on the map, bigger and ringed
				double[] hp = view.toScreen(hovered.x + 0.5, hovered.z + 0.5);
				MapDraw.outline(ctx.pose().last().pose(), MapDraw.circle(hp[0], hp[1], (size + 6) / 2.0 + 2.5, 24), 1.5, 0xFFFFFF, 1F);
				drawWaypoint(ctx, hovered, size + 6);
			} else {
				drawWaypoint(ctx, hovered, size + 3);
			}
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
		if (hintY != height - 11 && crowd == null) {
			ctx.fill(width - hintW - 8, hintY - 3, width, hintY + 11, 0x80000000);
		}
		if (crowd == null) { // the picker covers the bottom left; its own title says what to do
			ctx.drawString(font, hint, width - hintW - 4, hintY, 0xFFB0B0B0);
		}
		MapSession s = CakeMap.session();
		if (s != null && StaticMaps.shard(s.server, dim) != null && !s.showStatic(dim) && CakeMap.settings().mode == MapSettings.Mode.STATIC) {
			Component why = !s.revealed(dim) ? Component.translatable("cakemap.static.not_revealed") : Component.translatable("cakemap.static.mismatch");
			ctx.drawCenteredString(font, why, width / 2, 32, 0xFFFFD27F);
		}
		Waypoint hovered = menu == null && crowd == null ? hoveredWaypoint(mouseX, mouseY) : null;
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
		if (crowd != null) {
			drawCrowd(ctx, mouseX, mouseY);
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

	/** A left click on an icon: its popup, or the picker when other icons overlap it. */
	private void clickIcon(Waypoint w) {
		List<Waypoint> near = crowdNear(w);
		if (near.size() < 2) {
			openIconMenu(w);
			return;
		}
		crowd = near;
		crowdScroll = 0;
		// keep the crowded spot visible: out from under the picker, into the middle of the free part
		double[] p = view.toScreen(w.x + 0.5, w.z + 0.5);
		int[] b = crowdBox();
		if (p[0] < b[2] + iconSize()) {
			double[] from = view.toWorld(p[0], p[1]);
			double[] to = view.toWorld(b[2] + (width - b[2]) / 2.0, p[1]);
			view.centerX += from[0] - to[0];
			view.centerZ += from[1] - to[1];
		}
	}

	/** The shown waypoints whose icons overlap {@code w}'s (centres closer than one icon): {@code w} first, then nearest. */
	private List<Waypoint> crowdNear(Waypoint w) {
		MapSession s = CakeMap.session();
		List<Waypoint> out = new ArrayList<>();
		if (s == null) {
			return out;
		}
		double[] c = view.toScreen(w.x + 0.5, w.z + 0.5);
		double r = iconSize();
		java.util.Map<Waypoint, Double> dist = new java.util.IdentityHashMap<>();
		for (Waypoint o : s.shownWaypoints(dim)) {
			double[] p = view.toScreen(o.x + 0.5, o.z + 0.5);
			double d = Math.hypot(p[0] - c[0], p[1] - c[1]);
			if (o == w || d < r) {
				out.add(o);
				dist.put(o, o == w ? -1 : d);
			}
		}
		out.sort(java.util.Comparator.comparingDouble(dist::get));
		return out;
	}

	/** {left, top, right, bottom} of the picker: the left third of the window under the toolbar. */
	private int[] crowdBox() {
		return new int[] {0, 28, Math.max(150, width / 3), height - 15};
	}

	private int crowdRows() {
		int[] b = crowdBox();
		return Math.max(1, (b[3] - 4 - (b[1] + CROWD_TOP)) / CROWD_ROW);
	}

	@Nullable
	private Waypoint crowdRowAt(double mx, double my) {
		if (crowd == null) {
			return null;
		}
		int[] b = crowdBox();
		int top = b[1] + CROWD_TOP;
		if (mx < b[0] + 4 || mx >= b[2] - 4 || my < top) {
			return null;
		}
		int i = (int) ((my - top) / CROWD_ROW);
		if (i >= crowdRows() || crowdScroll + i >= crowd.size()) {
			return null;
		}
		return crowd.get(crowdScroll + i);
	}

	private boolean inCrowdBox(double mx, double my) {
		int[] b = crowdBox();
		return crowd != null && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
	}

	private void drawCrowd(GuiGraphics ctx, int mx, int my) {
		int[] b = crowdBox();
		ctx.pose().pushPose();
		ctx.pose().translate(0, 0, 400);
		ctx.fill(b[0], b[1], b[2], b[3], 0xB0000000);
		ctx.renderOutline(b[0], b[1], b[2] - b[0], b[3] - b[1], 0xFFFFFFFF);
		ctx.drawString(font, font.plainSubstrByWidth(Component.translatable("cakemap.map.nearby", crowd.size()).getString(), b[2] - b[0] - 12),
				b[0] + 6, b[1] + 7, 0xFFFFFFFF);
		int rows = crowdRows();
		crowdScroll = Math.max(0, Math.min(crowdScroll, crowd.size() - rows));
		int top = b[1] + CROWD_TOP;
		LocalPlayer p = Minecraft.getInstance().player;
		MapSession s = CakeMap.session();
		Waypoint over = crowdRowAt(mx, my);
		for (int i = 0; i < rows && crowdScroll + i < crowd.size(); i++) {
			Waypoint w = crowd.get(crowdScroll + i);
			int x0 = b[0] + 4;
			int x1 = b[2] - (crowd.size() > rows ? 8 : 4);
			int y0 = top + i * CROWD_ROW;
			boolean hover = w == over;
			ctx.fill(x0, y0 + 1, x1, y0 + CROWD_ROW - 1, hover ? 0xFF3C4658 : 0xFF262B34);
			ctx.renderOutline(x0, y0 + 1, x1 - x0, CROWD_ROW - 2, hover ? 0xFFFFFFFF : 0xFF6A7080);
			Icons.draw(ctx, w, x0 + 11, y0 + CROWD_ROW / 2.0, 14, 1F);
			String dist = "";
			if (p != null && s != null && w.dimension.equals(s.dimension())) {
				dist = Math.round(Math.sqrt(w.distanceSq(p.getX(), p.getY(), p.getZ()))) + "m";
			}
			int textX = x0 + 22;
			int right = x1 - 6 - font.width(dist) - 6;
			String name = font.plainSubstrByWidth(label(w), Math.max(0, right - textX));
			int nameColor = hover ? 0xFFFFFF7F : (w.color != 0 ? 0xFF000000 | w.color : 0xFFFFFFFF);
			ctx.drawString(font, name, textX, y0 + 6, nameColor);
			int kindX = textX + font.width(name) + 6;
			String kind = Component.translatable(w.kind().translationKey()).getString();
			if (kindX + font.width(kind) <= right) { // the kind only when it fits whole
				ctx.drawString(font, kind, kindX, y0 + 6, 0xFF9A9A9A);
			}
			ctx.drawString(font, dist, x1 - 5 - font.width(dist), y0 + 6, 0xFFC8C8C8);
		}
		if (crowd.size() > rows) {
			// a thin scroll bar on the right
			int trackH = rows * CROWD_ROW;
			int barH = Math.max(8, trackH * rows / crowd.size());
			int barY = top + (trackH - barH) * crowdScroll / Math.max(1, crowd.size() - rows);
			ctx.fill(b[2] - 6, top, b[2] - 3, top + trackH, 0x40FFFFFF);
			ctx.fill(b[2] - 6, barY, b[2] - 3, barY + barH, 0xFFFFFFFF);
		}
		ctx.pose().popPose();
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
		if (crowd != null) {
			// a row picks that waypoint (its popup); elsewhere in the picker nothing; outside it closes
			if (inCrowdBox(mx, my)) {
				Waypoint w = button == 0 ? crowdRowAt(mx, my) : null;
				if (w != null) {
					crowd = null;
					openIconMenu(w);
				}
				return true;
			}
			crowd = null;
			return true;
		}
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
			clickIcon(pressed);
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
		if (inCrowdBox(mx, my)) {
			crowdScroll -= (int) Math.signum(vertical);
			return true;
		}
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
		if (crowd != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			crowd = null; // Esc closes the picker first, then the map
			return true;
		}
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

	/** For the self-test: a left click on the waypoint with the most icons over it; the picker's rows (0 = none). */
	int openCrowdForTest() {
		MapSession s = CakeMap.session();
		if (s == null) {
			return 0;
		}
		Waypoint best = null;
		int most = 0;
		for (Waypoint w : s.shownWaypoints(dim)) {
			int n = crowdNear(w).size();
			if (n > most) {
				most = n;
				best = w;
			}
		}
		if (best != null) {
			clickIcon(best);
		}
		return crowd == null ? 0 : crowd.size();
	}

	/** For the self-test: picks row i of the picker; true when that icon's popup opened. */
	boolean pickForTest(int i) {
		if (crowd == null || i >= crowd.size()) {
			return false;
		}
		Waypoint w = crowd.get(i);
		crowd = null;
		openIconMenu(w);
		return menu != null && menu.buttons;
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
