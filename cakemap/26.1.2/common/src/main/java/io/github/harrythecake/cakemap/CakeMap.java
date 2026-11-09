package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointKind;
import io.github.harrythecake.cakemap.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CakeMap's client entrypoint: settings, the session of the current server, keys (as Xaero's in the
 * pack: M world map, U waypoints, J new waypoint, Y settings), the chunk scanner, chat coordinates and the
 * client-only /cakemap command (never sent to the server).
 */
public final class CakeMap {
	public static final String MOD_ID = CakeMapCore.MOD_ID;
	public static final Logger LOG = CakeMapCore.LOG;
	public static final Map<String, KeyMapping> KEYS = new LinkedHashMap<>();
	/** Key categories are registered ids since 1.21.9 (lang key.category.cakemap.main). */
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(CakeMapCore.MOD_ID, "main"));

	private static MapSettings settings = new MapSettings();
	private static MapSession session;
	private static String lastDimension = "";
	private static int secondTimer;
	private static int savedWaypointRevision;
	private static int waypointSaveTimer;
	private static Component notice;
	private static long noticeUntil;
	private static volatile boolean importing;

	/**
	 * Called once by the loader entry (Fabric's client initializer, NeoForge's mod constructor):
	 * settings and the key mappings (the loader registers {@link #KEYS}). The loader then hooks
	 * {@link #endClientTick}, {@link Scanner#queue}, {@link #endSession}, {@link InWorldWaypoints#capture},
	 * {@link ChatLinks#modify}, {@link CakeMapCommands#build} and {@link StaticMaps} to its events.
	 */
	public static void init() {
		CakeMapCore.renderThread = r -> Minecraft.getInstance().execute(r);
		settings = MapSettings.load(configFile());
		key("world_map", GLFW.GLFW_KEY_M);
		key("waypoints", GLFW.GLFW_KEY_U);
		key("new_waypoint", GLFW.GLFW_KEY_J); // Xaero's default is B, but the pack ships it on J: B opens CakeBook
		key("settings", GLFW.GLFW_KEY_Y);
		key("temporary_waypoint", GLFW.GLFW_KEY_KP_ADD);
		key("enlarge", GLFW.GLFW_KEY_Z);
		key("cave_mode", GLFW.GLFW_KEY_UNKNOWN);
		key("zoom_in", GLFW.GLFW_KEY_UNKNOWN);
		key("zoom_out", GLFW.GLFW_KEY_UNKNOWN);
		key("toggle_minimap", GLFW.GLFW_KEY_UNKNOWN);
		SelfTest.init();
	}

	private static void key(String name, int code) {
		KEYS.put(name, new KeyMapping("key.cakemap." + name, InputConstants.Type.KEYSYM, code, CATEGORY));
	}

	/** Every client tick (end), from the loader's event. */
	public static void endClientTick(Minecraft mc) {
		tick(mc);
		SelfTest.tick(mc);
	}

	// ---- shared state ----

	public static MapSettings settings() {
		return settings;
	}

	public static Path configFile() {
		return Services.PLATFORM.configDir().resolve("cakemap").resolve("config.json");
	}

	public static void saveSettings() {
		try {
			settings.fix().save(configFile());
		} catch (IOException e) {
			LOG.warn("CakeMap: can't save settings: {}", e.toString());
		}
	}

	/** The folder with every server's maps and waypoints: {@code <game>/cakemap}. */
	public static Path dataRoot() {
		return Services.PLATFORM.gameDir().resolve("cakemap");
	}

	public static MapSession session() {
		return session;
	}

	public static boolean importing() {
		return importing;
	}

	public static void setImporting(boolean b) {
		importing = b;
	}

	public static boolean enlarged() {
		KeyMapping k = KEYS.get("enlarge");
		return k != null && k.isDown() && Minecraft.getInstance().screen == null;
	}

	/** Runs on the render thread (now, when already there). */
	public static void onRender(Runnable r) {
		CakeMapCore.onRender(r);
	}

	/** A short message under the minimap for a few seconds. */
	public static void notice(Component text) {
		notice = text;
		noticeUntil = System.currentTimeMillis() + 4000;
	}

	public static Component currentNotice() {
		return System.currentTimeMillis() < noticeUntil ? notice : null;
	}

	public static void builtinWaypointsChanged() {
		if (session != null) {
			session.waypoints.setBuiltin(StaticMaps.builtin(session.server));
		}
	}

	// ---- per tick ----

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		String key = MapSession.currentServerKey(mc);
		if (session == null || !session.server.equals(key)) {
			endSession();
			session = new MapSession(key, dataRoot());
			session.waypoints.setBuiltin(StaticMaps.builtin(key));
			savedWaypointRevision = session.waypoints.revision();
			offerXaeroImport(mc);
		}
		String dim = mc.level.dimension().identifier().toString();
		if (!dim.equals(lastDimension) || mc.level != lastWorld) {
			lastDimension = dim;
			lastWorld = mc.level;
			Scanner.clear();
			CaveLayer.reset();
			QuestCompass.worldChanged();
			// chunks that loaded before the session existed
			int r = mc.options.getEffectiveRenderDistance();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					Scanner.queue(new net.minecraft.world.level.ChunkPos(mc.player.chunkPosition().x() + dx, mc.player.chunkPosition().z() + dz));
				}
			}
		}
		session.enter(dim);
		if (!importing) {
			Scanner.tick(mc, session);
		}
		CaveLayer.tick(mc);
		DeathWaypoints.tick(mc);
		keys(mc);
		if (++secondTimer >= 20) {
			secondTimer = 0;
			int rx = mc.player.getBlockX() >> 9;
			int rz = mc.player.getBlockZ() >> 9;
			if (!importing) {
				session.tick(rx, rz);
			}
			for (Waypoint w : session.waypoints.reach(dim, mc.player.getX(), mc.player.getZ())) {
				notice(Component.translatable("cakemap.waypoint.reached", w.name));
			}
		}
		if (session.waypoints.revision() != savedWaypointRevision && ++waypointSaveTimer >= 100) {
			waypointSaveTimer = 0;
			savedWaypointRevision = session.waypoints.revision();
			try {
				session.waypoints.save();
			} catch (RuntimeException e) {
				LOG.warn("CakeMap: {}", e.getMessage());
			}
		}
	}

	private static net.minecraft.client.multiplayer.ClientLevel lastWorld;

	private static void keys(Minecraft mc) {
		while (KEYS.get("world_map").consumeClick()) {
			mc.setScreen(new WorldMapScreen(null));
		}
		while (KEYS.get("waypoints").consumeClick()) {
			mc.setScreen(new WaypointListScreen(null));
		}
		while (KEYS.get("new_waypoint").consumeClick()) {
			Waypoint w = new Waypoint("", WaypointKind.PLAYER, session.dimension(), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
			mc.setScreen(new WaypointEditScreen(null, w, true));
		}
		while (KEYS.get("settings").consumeClick()) {
			mc.setScreen(new SettingsScreen(null));
		}
		while (KEYS.get("temporary_waypoint").consumeClick()) {
			Waypoint w = new Waypoint(Component.translatable("cakemap.kind.player_temporary").getString(), WaypointKind.PLAYER_TEMPORARY,
					session.dimension(), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
			session.waypoints.add(w);
			notice(Component.translatable("cakemap.waypoint.added", w.name));
		}
		while (KEYS.get("cave_mode").consumeClick()) {
			settings.cave = settings.cave == MapSettings.Cave.ON ? MapSettings.Cave.OFF : MapSettings.Cave.ON;
			saveSettings();
			notice(Component.translatable("cakemap.settings.cave." + settings.cave.name().toLowerCase(java.util.Locale.ROOT)));
		}
		while (KEYS.get("zoom_in").consumeClick()) {
			settings.zoom = MapSettings.nextZoom(settings.zoom, true);
			saveSettings();
		}
		while (KEYS.get("zoom_out").consumeClick()) {
			settings.zoom = MapSettings.nextZoom(settings.zoom, false);
			saveSettings();
		}
		while (KEYS.get("toggle_minimap").consumeClick()) {
			settings.minimap = !settings.minimap;
			saveSettings();
		}
	}

	public static void endSession() {
		if (session != null) {
			session.close();
			session = null;
		}
		lastDimension = "";
		lastWorld = null;
		Scanner.clear();
		CaveLayer.reset();
		MapTextures.clear("dyn/");
	}

	private static void offerXaeroImport(Minecraft mc) {
		if (settings.xaeroImportOffered || !XaeroImport.found(Services.PLATFORM.gameDir())) {
			return;
		}
		settings.xaeroImportOffered = true;
		saveSettings();
		Component yes = Component.translatable("cakemap.import.offer.button").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand("/cakemap import")));
		mc.gui.getChat().addClientSystemMessage(Component.translatable("cakemap.import.offer", yes));
	}

	/** /cakemap export: what the game holds is saved first; the next tick opens the session again. */
	static void closeSessionForExport() {
		if (session != null) {
			session.close();
			session = null;
		}
	}

	static boolean xaeroDataExists(Path root) {
		return Files.isDirectory(root.resolve("xaero"));
	}
}
