package io.github.harrythecake.cakeconfighub.configmap;

import io.github.harrythecake.cakeconfighub.CakeConfighub;
import io.github.harrythecake.cakeconfighub.platform.Services;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks a mod to read its config file again after CakeConfighub wrote it (ADR 0011, 0013). Never sends a command: a client command
 * that isn't registered would go to the server as chat.
 *
 * <p>How a mod offers a reload, without needing CakeConfighub: (1)
 * {@code CakeConfighubApi.registerConfigReload(modId, runnable)} (a soft dependency); or (2) a {@code "cakeconfighub:reload": "<class name>"} entry in its metadata (Fabric: the
 * {@code custom} block of fabric.mod.json; NeoForge: {@code [modproperties.<modid>]} in its mods.toml) naming a class
 * with {@code public static void cakeconfighubReloadConfig()}.
 */
public final class ConfigReloads {
	/** The metadata key naming a mod's reload class. */
	public static final String KEY = "cakeconfighub:reload";
	public static final String METHOD = "cakeconfighubReloadConfig";
	private static final Map<String, Runnable> HOOKS = new ConcurrentHashMap<>();

	private ConfigReloads() {
	}

	public static void register(String modId, Runnable reload) {
		HOOKS.put(modId, reload);
	}

	/** Whether the mod has a reload hook (registered, or named in its metadata). */
	public static boolean has(String modId) {
		return HOOKS.containsKey(modId) || Services.PLATFORM.modMetadata(modId, KEY) != null;
	}

	/** Runs the mod's reload hook; false when it has none or it failed (then the change takes effect after a restart). */
	public static boolean reload(String modId) {
		try {
			Runnable hook = HOOKS.get(modId);
			if (hook != null) {
				hook.run();
				return true;
			}
			String className = Services.PLATFORM.modMetadata(modId, KEY);
			if (className == null) {
				return false;
			}
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			Class<?> c = Class.forName(className.trim(), true, loader != null ? loader : ConfigReloads.class.getClassLoader());
			Method m = c.getMethod(METHOD);
			if (!Modifier.isStatic(m.getModifiers())) {
				CakeConfighub.LOGGER.warn("CakeConfighub: {}'s {}.{}() isn't static; not called", modId, className, METHOD);
				return false;
			}
			m.invoke(null);
			return true;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			CakeConfighub.LOGGER.warn("CakeConfighub: reloading {}'s config failed: {}", modId, e.toString());
			return false;
		}
	}
}
