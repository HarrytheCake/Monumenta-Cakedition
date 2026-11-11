package io.github.harrythecake.cakeui.model;

import io.github.harrythecake.cakeui.expr.Expr;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Read-only values every expression can use (`player.health`, `mouse.x`, …). CakeUI registers the
 * vanilla ones; other mods add theirs through the API. Names use dots: `<mod or area>.<value>`.
 */
public final class GameValues {
	private static final Map<String, Supplier<Object>> VALUES = new ConcurrentHashMap<>();
	/** Numbered families like `menu.data.<n>`: prefix (with its dot) → reader of entry n. */
	private static final Map<String, java.util.function.IntFunction<Object>> FAMILIES = new ConcurrentHashMap<>();
	/** The highest number a family entry may have (`menu.data.999`). */
	public static final int FAMILY_MAX = 999;

	private GameValues() {
	}

	/** Registers or replaces a value. The supplier runs on the render thread, often every frame: keep it cheap. */
	public static void register(String name, Supplier<Object> supplier) {
		VALUES.put(name, supplier);
	}

	/**
	 * A numbered family of values, `prefix` + a number from 0 to {@link #FAMILY_MAX} (`menu.data.` → `menu.data.0`,
	 * `menu.data.1`, …). The reader gets the number; it runs on the render thread like any supplier.
	 */
	public static void registerFamily(String prefix, java.util.function.IntFunction<Object> reader) {
		if (!prefix.endsWith(".")) {
			throw new IllegalArgumentException("a family prefix ends with a dot: " + prefix);
		}
		FAMILIES.put(prefix, reader);
	}

	/** The supplier of a name: a plain value, or an entry of a numbered family; null when unknown. */
	private static Supplier<Object> supplier(String name) {
		Supplier<Object> s = VALUES.get(name);
		if (s != null || FAMILIES.isEmpty()) {
			return s;
		}
		int dot = name.lastIndexOf('.');
		if (dot <= 0 || dot == name.length() - 1) {
			return null;
		}
		java.util.function.IntFunction<Object> family = FAMILIES.get(name.substring(0, dot + 1));
		String number = name.substring(dot + 1);
		if (family == null || number.length() > 3 || !number.chars().allMatch(Character::isDigit)) {
			return null;
		}
		int n = Integer.parseInt(number);
		return () -> family.apply(n);
	}

	public static Expr resolve(String name) {
		Supplier<Object> supplier = supplier(name);
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

	/** A value's current reading as text, for showing it (the HUD editor's value list); "" when unknown or failing. */
	public static String preview(String name) {
		Supplier<Object> supplier = supplier(name);
		if (supplier == null) {
			return "";
		}
		try {
			Object v = supplier.get();
			if (v instanceof Double d) {
				return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : String.format(java.util.Locale.ROOT, "%.3f", d);
			}
			if (v instanceof Float f) {
				return String.format(java.util.Locale.ROOT, "%.3f", f);
			}
			return v == null ? "" : String.valueOf(v);
		} catch (RuntimeException e) {
			return "";
		}
	}

	/** Every value's name; a numbered family is listed by its first entry (`menu.data.0`). */
	public static Set<String> names() {
		java.util.TreeSet<String> all = new java.util.TreeSet<>(VALUES.keySet());
		for (String prefix : FAMILIES.keySet()) {
			all.add(prefix + "0");
		}
		return Collections.unmodifiableSet(all);
	}

	public static boolean has(String name) {
		return supplier(name) != null;
	}
}
