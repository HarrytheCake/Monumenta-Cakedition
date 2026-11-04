package io.github.harrythecake.cakefixes;

import io.github.harrythecake.cakefixes.platform.Platform;
import io.github.harrythecake.cakefixes.platform.Services;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/**
 * Reads GlStateManager.BLEND.capState.state, Minecraft's own record of whether GL_BLEND is on. Both inner classes
 * are private, so the fields are found by name: Mojang's names on NeoForge, intermediary names mapped by Fabric's
 * resolver on Fabric (works in the dev environment and in a normal install). If anything is missing, {@link #SUPPLIER} is null and callers fall
 * back to the real GL query.
 */
final class TrackedBlend {
	private static final String GL_STATE_MANAGER = "com.mojang.blaze3d.platform.GlStateManager";
	private static final String BLEND_FUNC_STATE = GL_STATE_MANAGER + "$class_1017";
	private static final String BLEND_FUNC_STATE_MOJANG = GL_STATE_MANAGER + "$BlendState";
	private static final String CAPABILITY_TRACKER = GL_STATE_MANAGER + "$class_1018";
	private static final String CAPABILITY_TRACKER_MOJANG = GL_STATE_MANAGER + "$BooleanState";

	/** Null when the tracked state couldn't be found. */
	static final BooleanSupplier SUPPLIER = find();

	private TrackedBlend() {}

	private static BooleanSupplier find() {
		try {
			Platform r = Services.PLATFORM;
			String capState = r.fieldName(BLEND_FUNC_STATE, "field_5045", "mode",
					"L" + CAPABILITY_TRACKER.replace('.', '/') + ";");
			String state = r.fieldName(CAPABILITY_TRACKER, "field_5051", "enabled", "Z");

			Class<?> glStateManager = Class.forName(GL_STATE_MANAGER);
			Class<?> blendFuncState = Class.forName(r.className(BLEND_FUNC_STATE, BLEND_FUNC_STATE_MOJANG));
			Class<?> capabilityTracker = Class.forName(r.className(CAPABILITY_TRACKER, CAPABILITY_TRACKER_MOJANG));

			Object blend = field(glStateManager, "BLEND").get(null);
			Object tracker = field(blendFuncState, capState).get(blend);
			Field stateField = field(capabilityTracker, state);
			// Bound to the one tracker object; the JIT inlines this like a plain field read.
			MethodHandle getter = MethodHandles.lookup().unreflectGetter(stateField).bindTo(tracker)
					.asType(MethodType.methodType(boolean.class));
			return () -> {
				try {
					return (boolean) getter.invokeExact();
				} catch (Throwable t) {
					throw new IllegalStateException(t);
				}
			};
		} catch (Throwable t) {
			CakeFixes.LOGGER.warn("appleSkinBlendQueryFix: Minecraft's blend state wasn't found, so AppleSkin keeps"
					+ " its own query: {}", t.toString());
			return null;
		}
	}

	private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
		Field f = owner.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}
}
