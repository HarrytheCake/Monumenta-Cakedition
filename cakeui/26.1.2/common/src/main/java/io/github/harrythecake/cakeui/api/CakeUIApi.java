package io.github.harrythecake.cakeui.api;

import io.github.harrythecake.cakeui.model.GameValues;
import io.github.harrythecake.cakeui.model.PropKinds;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * What other mods can add to CakeUI: game values, element types, button actions and port fillers. Register from
 * a {@link CakeUIPlugin} (before the first layout loads); anything registered later is used from
 * the next reload ({@link #reload()}).
 */
public final class CakeUIApi {
	/** Changes when the API changes in a way plugins must know about. 2: {@link #fillPort}, {@link #addHudPiece} (CakeUI 0.2.0). */
	public static final int VERSION = 3;
	private static final CakeUIApi INSTANCE = new CakeUIApi();
	private static final Pattern VALUE_NAME = Pattern.compile("[a-z0-9_]+(\\.[a-zA-Z0-9_]+)+");
	private static final Pattern NAMESPACED = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
	private static final Map<String, ElementType> ELEMENTS = new ConcurrentHashMap<>();
	private static final Map<String, ButtonAction> ACTIONS = new ConcurrentHashMap<>();
	private static Runnable reloader = () -> {
	};

	private CakeUIApi() {
	}

	public static CakeUIApi get() {
		return INSTANCE;
	}

	/**
	 * Lets CakeUI's config hub ask your mod to read its config file again right after the hub wrote it (API version 5;
	 * on 26.1.2, where the API version is still 3, the method exists too). Runs on the render thread. Without a reload
	 * hook the hub tells the player the change takes effect after a restart. A mod that doesn't depend on CakeUI can
	 * name a class instead: {@code "cakeui:reload": "com.example.MyConfig"} in fabric.mod.json's {@code custom} block
	 * (NeoForge: {@code [modproperties.<modid>]}), with {@code public static void cakeuiReloadConfig()}.
	 */
	public CakeUIApi registerConfigReload(String modId, Runnable reload) {
		Objects.requireNonNull(modId, "modId");
		Objects.requireNonNull(reload, "reload");
		io.github.harrythecake.cakeui.configmap.ConfigReloads.register(modId, reload);
		return this;
	}

	/**
	 * A read-only value for every expression, like "mymod.mana". The supplier runs on the render
	 * thread, often every frame: keep it cheap. Numbers, booleans and strings work; anything else is
	 * shown as text.
	 */
	public CakeUIApi registerValue(String name, Supplier<?> value) {
		if (!VALUE_NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("CakeUI value names look like 'mymod.value' (lowercase prefix, a dot): " + name);
		}
		Objects.requireNonNull(value, "value");
		GameValues.register(name, value::get);
		return this;
	}

	/** An element type, used as { "type": "mymod:gauge" }. */
	public CakeUIApi registerElement(String type, ElementType element) {
		if (!NAMESPACED.matcher(type).matches()) {
			throw new IllegalArgumentException("CakeUI element types need a namespace, like 'mymod:gauge': " + type);
		}
		Objects.requireNonNull(element, "element");
		Map<String, PropKinds.Kind> kinds = new LinkedHashMap<>();
		for (Map.Entry<String, PropKind> e : element.properties().entrySet()) {
			kinds.put(e.getKey(), PropKinds.Kind.valueOf(e.getValue().name()));
		}
		PropKinds.registerType(type, kinds);
		ELEMENTS.put(type, element);
		return this;
	}

	/** A button action, used as "action": "mymod:open_map" or { "mymod:open_map": "argument" }. */
	public CakeUIApi registerAction(String name, ButtonAction action) {
		if (!NAMESPACED.matcher(name).matches()) {
			throw new IllegalArgumentException("CakeUI actions need a namespace, like 'mymod:open_map': " + name);
		}
		ACTIONS.put(name, Objects.requireNonNull(action, "action"));
		io.github.harrythecake.cakeui.model.PluginNames.addAction(name);
		return this;
	}

	/**
	 * Draws a port in vanilla's place whenever the active layout doesn't mention that port, also
	 * with no layout at all (a layout that mentions it wins). Ports: "main_menu/background" (the
	 * panorama and its overlay), "loading_screen/background" (every loading screen's background).
	 * One filler per port: a second one throws IllegalStateException. {@code owner} is your mod id,
	 * for logs.
	 */
	public CakeUIApi fillPort(String port, String owner, PortFiller filler) {
		Objects.requireNonNull(owner, "owner");
		io.github.harrythecake.cakeui.PluginPorts.register(port, owner, Objects.requireNonNull(filler, "filler"));
		return this;
	}

	/**
	 * A HUD piece of your own, like a minimap. CakeUI draws it every HUD frame (not with F1) and a
	 * layout's "hud" part can name it as a port ({@code "ports": { "minimap": { "x": 10 } }}) to
	 * hide, move, scale or fade it and draw elements with it, just like vanilla's pieces. {@code name}
	 * is one word, not a vanilla port's; one piece per name (a second throws IllegalStateException).
	 * Register it from your plugin so layouts that name it load without a problem.
	 */
	public CakeUIApi addHudPiece(String name, String owner, HudPiece piece) {
		Objects.requireNonNull(owner, "owner");
		io.github.harrythecake.cakeui.PluginHud.register(name, owner, Objects.requireNonNull(piece, "piece"));
		return this;
	}

	/**
	 * Things your mod places itself on the HUD (API version 3), so CakeUI's HUD editor can move and maybe
	 * resize them while your mod keeps storing their place (see {@link Movable}). Asked again whenever the
	 * editor needs them; one source per mod.
	 */
	public CakeUIApi addMovables(String owner, MovableSource source) {
		io.github.harrythecake.cakeui.HudMovables.register(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(source, "source"));
		return this;
	}

	/**
	 * Opens CakeUI's HUD editor (API version 3), e.g. from an "Edit HUD" button of your own; Done or Cancel
	 * goes back to {@code parent} (may be null). False when it can't open now (not in a world).
	 */
	public boolean openHudEditor(@Nullable net.minecraft.client.gui.screens.Screen parent) {
		return io.github.harrythecake.cakeui.HudEditorScreen.open(parent);
	}

	/** Loads the active layout again (render thread), e.g. after registering something late. */
	public void reload() {
		reloader.run();
	}

	// ---- for CakeUI itself ----

	@Nullable
	public static ElementType element(String type) {
		return ELEMENTS.get(type);
	}

	@Nullable
	public static ButtonAction action(String name) {
		return ACTIONS.get(name);
	}

	public static Set<String> actionNames() {
		return new TreeSet<>(ACTIONS.keySet());
	}

	public static void setReloader(Runnable r) {
		reloader = r;
	}
}
