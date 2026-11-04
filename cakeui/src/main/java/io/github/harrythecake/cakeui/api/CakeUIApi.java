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
 * What other mods can add to CakeUI: game values, element types and button actions. Register from
 * a {@link CakeUIPlugin} (before the first layout loads); anything registered later is used from
 * the next reload ({@link #reload()}).
 */
public final class CakeUIApi {
	/** Changes when the API changes in a way plugins must know about. */
	public static final int VERSION = 1;
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
		return this;
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
