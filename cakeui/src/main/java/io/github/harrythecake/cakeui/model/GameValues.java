package io.github.harrythecake.cakeui.model;

import io.github.harrythecake.cakeui.expr.Expr;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Read-only values every expression can use (`player.health`, `mouse.x`, …). CakeUI registers the
 * vanilla ones; other mods add theirs through the API. Names use dots: `<mod or area>.<value>`.
 */
public final class GameValues {
	private static final Map<String, Supplier<Object>> VALUES = new ConcurrentHashMap<>();

	private GameValues() {
	}

	/** Registers or replaces a value. The supplier runs on the render thread, often every frame: keep it cheap. */
	public static void register(String name, Supplier<Object> supplier) {
		VALUES.put(name, supplier);
	}

	public static Expr resolve(String name) {
		Supplier<Object> supplier = VALUES.get(name);
		if (supplier == null) {
			return null;
		}
		return env -> {
			try {
				Object v = supplier.get();
				if (v instanceof Integer i) {
					return (double) i;
				}
				if (v instanceof Float f) {
					return (double) f;
				}
				if (v instanceof Long l) {
					return (double) l;
				}
				return v == null ? "" : v;
			} catch (RuntimeException e) {
				return 0.0;
			}
		};
	}

	public static Set<String> names() {
		return Collections.unmodifiableSet(new TreeMap<>(VALUES).keySet());
	}

	public static boolean has(String name) {
		return VALUES.containsKey(name);
	}
}
