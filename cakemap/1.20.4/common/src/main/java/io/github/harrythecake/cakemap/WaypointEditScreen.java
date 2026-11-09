package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointColors;
import io.github.harrythecake.cakemap.core.WaypointKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A new waypoint (J) or an existing one: name, position, kind (its icon), colour, bookmark, which map shows it. */
public final class WaypointEditScreen extends Screen {
	private static final int SWATCH_W = 20;
	private static final int SWATCH_GAP = 4;

	private final Screen parent;
	private final Waypoint original;
	private final Waypoint w;
	private final boolean isNew;
	private EditBox name;
	private EditBox x;
	private EditBox y;
	private EditBox z;
	private Component error;

	public WaypointEditScreen(@Nullable Screen parent, Waypoint waypoint, boolean isNew) {
		super(Component.translatable(isNew ? "cakemap.waypoint.new" : "cakemap.waypoint.edit"));
		this.parent = parent;
		this.original = waypoint;
		this.w = waypoint.copy();
		this.isNew = isNew;
	}

	private int top() {
		return height / 2 - 82;
	}

	private int swatchX(int i) {
		int total = WaypointColors.PALETTE.length * (SWATCH_W + SWATCH_GAP) - SWATCH_GAP;
		return width / 2 - total / 2 + i * (SWATCH_W + SWATCH_GAP);
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int top = top();
		name = addRenderableWidget(new EditBox(font, cx - 100, top + 12, 200, 18, Component.translatable("cakemap.waypoint.name")));
		name.setMaxLength(64);
		name.setValue(w.name);
		x = addRenderableWidget(new EditBox(font, cx - 100, top + 46, 62, 18, Component.literal("x")));
		y = addRenderableWidget(new EditBox(font, cx - 31, top + 46, 62, 18, Component.literal("y")));
		z = addRenderableWidget(new EditBox(font, cx + 38, top + 46, 62, 18, Component.literal("z")));
		x.setValue(String.valueOf(w.x));
		y.setValue(String.valueOf(w.y));
		z.setValue(String.valueOf(w.z));
		addRenderableWidget(Button.builder(kindLabel(), b -> {
			WaypointKind[] all = WaypointKind.values();
			w.kind = all[(w.kind().ordinal() + 1) % all.length].id();
			b.setMessage(kindLabel());
		}).bounds(cx - 100, top + 72, 200, 20).build());
		// colour swatches (drawn over these buttons in render)
		for (int i = 0; i < WaypointColors.PALETTE.length; i++) {
			int c = WaypointColors.PALETTE[i];
			addRenderableWidget(Button.builder(Component.empty(), b -> w.color = c).bounds(swatchX(i), top + 108, SWATCH_W, 14).build());
		}
		addRenderableWidget(Button.builder(bookmarkLabel(), b -> {
			w.bookmarked = !w.bookmarked;
			b.setMessage(bookmarkLabel());
		}).bounds(cx - 100, top + 128, 98, 20).build());
		addRenderableWidget(Button.builder(mapsLabel(w.maps), b -> {
			w.maps = nextMaps(w.maps);
			b.setMessage(mapsLabel(w.maps));
		}).bounds(cx + 2, top + 128, 98, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).bounds(cx - 100, top + 156, 98, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(cx + 2, top + 156, 98, 20).build());
		setInitialFocus(name);
	}

	/** both → static → dynamic → both. */
	static String nextMaps(String m) {
		return switch (m == null ? Waypoint.BOTH : m) {
			case Waypoint.BOTH -> Waypoint.STATIC;
			case Waypoint.STATIC -> Waypoint.DYNAMIC;
			default -> Waypoint.BOTH;
		};
	}

	static Component mapsLabel(String m) {
		return Component.translatable("cakemap.waypoint.maps", Component.translatable("cakemap.waypoint.maps." + (m == null ? Waypoint.BOTH : m)));
	}

	private Component kindLabel() {
		return Component.translatable("cakemap.waypoint.kind", Component.translatable(w.kind().translationKey()));
	}

	private Component bookmarkLabel() {
		return Component.translatable("cakemap.waypoint.bookmarked", Component.translatable(w.bookmarked ? "options.on" : "options.off"));
	}

	private void save() {
		try {
			w.x = Integer.parseInt(x.getValue().trim());
			w.y = Integer.parseInt(y.getValue().trim());
			w.z = Integer.parseInt(z.getValue().trim());
		} catch (NumberFormatException e) {
			error = Component.translatable("cakemap.waypoint.bad_number");
			return;
		}
		w.name = name.getValue().trim();
		MapSession s = CakeMap.session();
		if (s == null) {
			onClose();
			return;
		}
		if (!isNew) {
			s.waypoints.remove(original);
		}
		s.waypoints.add(w);
		s.waypoints.setBookmarked(w, w.bookmarked);
		onClose();
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int cx = width / 2;
		int top = top();
		ctx.drawCenteredString(font, title, cx, top - 14, 0xFFFFFFFF);
		ctx.drawString(font, Component.translatable("cakemap.waypoint.name"), cx - 100, top + 2, 0xFFA0A0A0);
		ctx.drawString(font, Component.literal("X / Y / Z"), cx - 100, top + 36, 0xFFA0A0A0);
		ctx.drawString(font, Component.translatable("cakemap.waypoint.color"), cx - 100, top + 97, 0xFFA0A0A0);
		Icons.draw(ctx, w, cx - 116, top + 82, 16, 1F);
		for (int i = 0; i < WaypointColors.PALETTE.length; i++) {
			int c = WaypointColors.PALETTE[i];
			int sx = swatchX(i);
			int sy = top + 108;
			ctx.fill(sx + 2, sy + 2, sx + SWATCH_W - 2, sy + 12, 0xFF000000 | WaypointColors.opaque(c));
			if (c == w.color) {
				ctx.renderOutline(sx - 1, sy - 1, SWATCH_W + 2, 16, 0xFFFFFFFF);
			}
		}
		if (error != null) {
			ctx.drawCenteredString(font, error, cx, top + 182, 0xFFFF7070);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
