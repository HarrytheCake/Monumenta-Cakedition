package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.api.CakeUIPlugin;
import io.github.harrythecake.cakeui.api.ElementContext;
import io.github.harrythecake.cakeui.api.ElementType;
import io.github.harrythecake.cakeui.api.HudPiece;
import io.github.harrythecake.cakeui.api.PropKind;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * CakeMap's hook into CakeUI: the "waypoints" (in-world markers) and "minimap" HUD pieces, which a
 * layout can hide, move, scale or fade like vanilla's; the {@code cakemap:minimap} element for a
 * minimap in a layout's own box; values and actions for layout buttons.
 */
public final class CakeMapPlugin implements CakeUIPlugin {
	/** CakeUI 3+: the minimap is moved and resized in CakeUI's HUD editor ("Move on HUD…" in the settings). */
	static boolean hudEditor;

	@Override
	public void register(CakeUIApi api) {
		try {
			api.addMovables(CakeMap.MOD_ID, () -> java.util.List.of(Minimap.MOVABLE));
			hudEditor = true;
		} catch (NoSuchMethodError e) {
			// CakeUI 0.2 (API 2): the minimap keeps its corner setting
		}
		api.addHudPiece("waypoints", CakeMap.MOD_ID, new HudPiece() {
			@Override
			public void draw(GuiGraphicsExtractor context, int w, int h, float delta) {
				InWorldWaypoints.draw(context, w, h, delta);
			}

			/** Markers in the world, all over the screen: nothing to move in CakeUI's HUD editor. */
			@Override
			public boolean editable() {
				return false;
			}
		});
		api.addHudPiece("minimap", CakeMap.MOD_ID, new HudPiece() {
			@Override
			public void draw(GuiGraphicsExtractor context, int w, int h, float delta) {
				Minimap.drawHud(context, w, h, delta);
				HudNotice.draw(context, w, h);
			}

			/** With CakeUI 3+ the editor moves the minimap through its movable below (CakeMap keeps its place). */
			@Override
			public boolean editable() {
				return false;
			}

			@Override
			public double pivotX(int w) {
				int[] b = Minimap.box(w, Minecraft.getInstance().getWindow().getGuiScaledHeight());
				return b[0] + b[2] / 2.0;
			}

			@Override
			public double pivotY(int h) {
				int[] b = Minimap.box(Minecraft.getInstance().getWindow().getGuiScaledWidth(), h);
				return b[1] + b[2] / 2.0;
			}
		});
		api.registerElement("cakemap:minimap", new ElementType() {
			@Override
			public Map<String, PropKind> properties() {
				return Map.of("shape", PropKind.WORD, "zoom", PropKind.NUMBER, "rotate", PropKind.BOOL, "coordinates", PropKind.BOOL,
						"waypoints", PropKind.BOOL);
			}

			@Override
			public double defaultWidth() {
				return 120;
			}

			@Override
			public double defaultHeight() {
				return 120;
			}

			@Override
			public void draw(ElementContext c) {
				MapSettings s = CakeMap.settings();
				String shape = c.text("shape", s.shape.name()).toUpperCase(Locale.ROOT);
				MapSettings.Shape sh = shape.startsWith("C") ? MapSettings.Shape.CIRCLE : MapSettings.Shape.SQUARE;
				Minimap.draw(c.drawContext(), 0, 0, c.width(), c.height(), sh, c.number("zoom", s.zoom), c.bool("rotate", s.rotate), c.opacity(),
						c.bool("coordinates", false), c.bool("waypoints", s.waypointsOnMinimap), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
			}
		});
		api.registerValue("cakemap.dimension", () -> CakeMap.session() == null ? "" : CakeMap.session().dimension());
		api.registerValue("cakemap.mode", () -> CakeMap.settings().mode.name().toLowerCase(Locale.ROOT));
		api.registerValue("cakemap.cave", CaveLayer::active);
		api.registerValue("cakemap.minimap", () -> CakeMap.settings().minimap);
		api.registerAction("cakemap:open_map", (arg, screen) -> Minecraft.getInstance().setScreen(new WorldMapScreen(screen)));
		api.registerAction("cakemap:waypoints", (arg, screen) -> Minecraft.getInstance().setScreen(new WaypointListScreen(screen)));
		api.registerAction("cakemap:settings", (arg, screen) -> Minecraft.getInstance().setScreen(new SettingsScreen(screen)));
	}
}
