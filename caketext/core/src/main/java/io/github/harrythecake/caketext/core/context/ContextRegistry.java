package io.github.harrythecake.caketext.core.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.harrythecake.caketext.core.CakeText;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Extra context that modules know and vanilla doesn't, e.g. the Monumenta shard and the named area.
 * Built-in keys used by scopes: {@code shard}, {@code area}.
 */
public final class ContextRegistry {
	/** Gives a value for a position (where the text is), or null when it doesn't apply. */
	@FunctionalInterface
	public interface Provider {
		@Nullable String get(@Nullable Vec3d pos);
	}

	private static final Map<String, Provider> PROVIDERS = new ConcurrentHashMap<>();

	private ContextRegistry() {
	}

	public static void register(String key, Provider provider) {
		PROVIDERS.put(key, provider);
	}

	/** The value, or null. A provider that throws is removed, so a broken module can't break the game. */
	public static @Nullable String get(String key, @Nullable Vec3d pos) {
		Provider provider = PROVIDERS.get(key);
		if (provider == null) {
			return null;
		}
		try {
			String value = provider.get(pos);
			return value == null || value.isBlank() ? null : value;
		} catch (Throwable t) {
			PROVIDERS.remove(key, provider);
			CakeText.LOG.warn("Context provider '{}' failed and was disabled", key, t);
			return null;
		}
	}

	public static boolean has(String key) {
		return PROVIDERS.containsKey(key);
	}
}
