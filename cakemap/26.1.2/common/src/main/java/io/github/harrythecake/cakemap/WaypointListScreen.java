package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Waypoints (U): this dimension's or all, nearest first. Select a row, then edit, delete,
 * bookmark or hide it; "Kinds" hides whole kinds (all built-in POIs, say).
 */
public final class WaypointListScreen extends Screen {
	private static final int ROW = 16;
	private static boolean allDimensions;

	private final Screen parent;
	private List<Waypoint> rows = new ArrayList<>();
	private int scroll;
	private Waypoint selected;
	private int seenRevision = -1;
	private Button edit;
	private Button delete;
	private Button bookmark;
	private Button hide;
	private Button track;

	public WaypointListScreen(@Nullable Screen parent) {
		super(Component.translatable("cakemap.waypoints.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int bw = Math.min(72, (width - 16 - 4 * 7) / 8);
		int y = height - 26;
		int x = width / 2 - (bw * 8 + 4 * 7) / 2;
		addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoints.new"), b -> {
			LocalPlayer p = Minecraft.getInstance().player;
			MapSession s = CakeMap.session();
			if (p != null && s != null) {
				minecraft.setScreen(new WaypointEditScreen(this, new Waypoint("", WaypointKind.PLAYER, s.dimension(), p.getBlockX(), p.getBlockY(), p.getBlockZ()), true));
			}
		}).bounds(x, y, bw, 20).build());
		edit = addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoint.edit"), b -> {
			if (selected != null) {
				minecraft.setScreen(new WaypointEditScreen(this, selected, false));
			}
		}).bounds(x + (bw + 4), y, bw, 20).build());
		delete = addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoint.delete"), b -> {
			if (selected != null && CakeMap.session() != null) {
				CakeMap.session().waypoints.remove(selected);
				selected = null;
			}
		}).bounds(x + (bw + 4) * 2, y, bw, 20).build());
		bookmark = addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoint.bookmark"), b -> {
			if (selected != null && CakeMap.session() != null) {
				CakeMap.session().waypoints.setBookmarked(selected, !selected.bookmarked);
			}
		}).bounds(x + (bw + 4) * 3, y, bw, 20).build());
		hide = addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoint.hide"), b -> {
			if (selected != null && CakeMap.session() != null) {
				CakeMap.session().waypoints.setHidden(selected, !selected.hidden);
			}
		}).bounds(x + (bw + 4) * 4, y, bw, 20).build());
		track = addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoint.track"), b -> {
			MapSession s = CakeMap.session();
			if (selected != null && s != null) {
				s.waypoints.track(s.waypoints.isTracked(selected) ? null : selected);
			}
		}).bounds(x + (bw + 4) * 5, y, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoints.kinds"), b -> minecraft.setScreen(new KindsScreen(this)))
				.bounds(x + (bw + 4) * 6, y, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + (bw + 4) * 7, y, bw, 20).build());
		addRenderableWidget(Button.builder(scopeLabel(), b -> {
			allDimensions = !allDimensions;
			b.setMessage(scopeLabel());
			seenRevision = -1;
		}).bounds(width - 154, 6, 150, 20).build());
		seenRevision = -1;
	}

	private static MapSession s0() {
		return CakeMap.session();
	}

	private Component scopeLabel() {
		return Component.translatable(allDimensions ? "cakemap.waypoints.scope.all" : "cakemap.waypoints.scope.here");
	}

	private void refresh() {
		MapSession s = CakeMap.session();
		if (s == null) {
			rows = new ArrayList<>();
			return;
		}
		if (s.waypoints.revision() == seenRevision) {
			return;
		}
		seenRevision = s.waypoints.revision();
		LocalPlayer p = Minecraft.getInstance().player;
		List<Waypoint> list = new ArrayList<>();
		for (Waypoint w : s.waypoints.all()) {
			if (allDimensions || w.dimension.equals(s.dimension())) {
				list.add(w);
			}
		}
		if (p != null) {
			list.sort(Comparator.comparing((Waypoint w) -> !w.dimension.equals(s.dimension()))
					.thenComparingDouble(w -> w.distanceSq(p.getX(), p.getY(), p.getZ())));
		}
		rows = list;
		if (selected != null && !rows.contains(selected)) {
			selected = null;
		}
	}

	private int listTop() {
		return 32;
	}

	private int listBottom() {
		return height - 32;
	}

	private int visibleRows() {
		return Math.max(1, (listBottom() - listTop()) / ROW);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		refresh();
		boolean sel = selected != null;
		edit.active = sel && !selected.builtin;
		delete.active = sel && !selected.builtin;
		bookmark.active = sel;
		hide.active = sel;
		track.active = sel;
		if (sel) {
			track.setMessage(Component.translatable(s0() != null && s0().waypoints.isTracked(selected) ? "cakemap.waypoint.untrack" : "cakemap.waypoint.track"));
			bookmark.setMessage(Component.translatable(selected.bookmarked ? "cakemap.waypoint.unbookmark" : "cakemap.waypoint.bookmark"));
			hide.setMessage(Component.translatable(selected.hidden ? "cakemap.waypoint.show" : "cakemap.waypoint.hide"));
		}
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.text(font, title, 8, 12, 0xFFFFFFFF);
		int left = Math.max(8, width / 2 - 220);
		int right = Math.min(width - 8, width / 2 + 220);
		ctx.fill(left, listTop(), right, listBottom(), 0x80000000);
		MapSession s = CakeMap.session();
		LocalPlayer p = Minecraft.getInstance().player;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
		if (rows.isEmpty()) {
			ctx.centeredText(font, Component.translatable("cakemap.waypoints.empty"), width / 2, listTop() + 20, 0xFFA0A0A0);
		}
		for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
			Waypoint w = rows.get(scroll + i);
			int y = listTop() + i * ROW;
			boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW;
			if (w == selected) {
				ctx.fill(left, y, right, y + ROW, 0xFF34404F);
			} else if (hover) {
				ctx.fill(left, y, right, y + ROW, 0x40FFFFFF);
			}
			Icons.draw(ctx, w, left + 10, y + ROW / 2.0, 12, w.hidden ? 0.4F : 1F);
			String name = w.name.isEmpty() ? Component.translatable(w.kind().translationKey()).getString() : w.name;
			int colour = w.hidden ? 0xFF808080 : (w.color != 0 ? 0xFF000000 | w.color : 0xFFFFFFFF);
			ctx.text(font, font.plainSubstrByWidth(name, 150), left + 20, y + 4, colour);
			String kind = Component.translatable(w.kind().translationKey()).getString() + (w.builtin ? " ·" : "");
			ctx.text(font, font.plainSubstrByWidth(kind, 100), left + 176, y + 4, 0xFFA0A0A0);
			String where = w.x + ", " + w.y + ", " + w.z;
			if (s != null && p != null && w.dimension.equals(s.dimension())) {
				where += "  " + Math.round(Math.sqrt(w.distanceSq(p.getX(), p.getY(), p.getZ()))) + "m";
			} else {
				where += "  " + w.dimension;
			}
			ctx.text(font, where, right - font.width(where) - 6, y + 4, 0xFFC8C8C8);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int left = Math.max(8, width / 2 - 220);
		int right = Math.min(width - 8, width / 2 + 220);
		if (mx >= left && mx < right && my >= listTop() && my < listBottom()) {
			int i = scroll + (int) ((my - listTop()) / ROW);
			if (i >= 0 && i < rows.size()) {
				Waypoint w = rows.get(i);
				if (w == selected && button == 0 && !w.builtin) {
					minecraft.setScreen(new WaypointEditScreen(this, w, false));
				}
				selected = w;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (int) Math.signum(v) * 3;
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Which kinds are shown on the maps and in the world. */
	static final class KindsScreen extends Screen {
		private final Screen parent;

		KindsScreen(Screen parent) {
			super(Component.translatable("cakemap.waypoints.kinds"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			WaypointKind[] kinds = WaypointKind.values();
			int y = 40;
			for (int i = 0; i < kinds.length; i++) {
				WaypointKind k = kinds[i];
				int x = width / 2 - 155 + (i % 2) * 160;
				addRenderableWidget(Button.builder(label(k), b -> {
					MapSession s = CakeMap.session();
					if (s != null) {
						s.waypoints.setKindHidden(k, !s.waypoints.kindHidden(k));
						b.setMessage(label(k));
					}
				}).bounds(x, y + (i / 2) * 24, 150, 20).build());
			}
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 75, height - 28, 150, 20).build());
		}

		private static Component label(WaypointKind k) {
			MapSession s = CakeMap.session();
			boolean shown = s == null || !s.waypoints.kindHidden(k);
			return Component.translatable(k.translationKey()).append(": ").append(Component.translatable(shown ? "options.on" : "options.off"));
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
			super.extractRenderState(ctx, mouseX, mouseY, delta);
			ctx.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
			WaypointKind[] kinds = WaypointKind.values();
			for (int i = 0; i < kinds.length; i++) {
				int x = width / 2 - 155 + (i % 2) * 160;
				Waypoint dummy = new Waypoint("", kinds[i], "", 0, 0, 0);
				Icons.draw(ctx, dummy, x - 10, 40 + (i / 2) * 24 + 10, 14, 1F);
			}
		}

		@Override
		public void onClose() {
			minecraft.setScreen(parent);
		}
	}
}
