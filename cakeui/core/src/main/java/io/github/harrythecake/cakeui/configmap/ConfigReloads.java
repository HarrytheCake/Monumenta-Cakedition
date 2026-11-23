package io.github.harrythecake.cakeui.configmap;

import io.github.harrythecake.cakeui.CakeUI;
import io.github.harrythecake.cakeui.platform.Services;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks a mod to read its config file again after CakeUI wrote it (ADR 0011). Never sends a command: a client command
 * that isn't registered would go to the server as chat.
 *
 * <p>How a mod offers a reload, without needing CakeUI: (1) {@code CakeUIApi.registerConfigReload(modId, runnable)}
 * from a CakeUI plugin; or (2) a {@code "cakeui:reload": "<class name>"} entry in its metadata (Fabric: the
 * {@code custom} block of fabric.mod.json; NeoForge: {@code [modproperties.<modid>]} in its mods.toml) naming a class
 * with {@code public static void cakeuiReloadConfig()}.
 */
public final class ConfigReloads {
	/** The metadata key naming a mod's reload class. */
	public static final String KEY = "cakeui:reload";
	public static final String METHOD = "cakeuiReloadConfig";
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
				CakeUI.LOGGER.warn("CakeUI: {}'s {}.{}() isn't static; not called", modId, className, METHOD);
				return false;
			}
			m.invoke(null);
			return true;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			CakeUI.LOGGER.warn("CakeUI: reloading {}'s config failed: {}", modId, e.toString());
			return false;
		}
	}
}
