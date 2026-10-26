package io.github.harrythecake.caketext.core.module;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import io.github.harrythecake.caketext.core.context.ContextRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;

/**
 * Unofficial Monumenta Mod: the current shard ({@code valley}, {@code isles}…) and UMM's named areas
 * ({@code Sierhaven}, {@code Kaul Arena}…), read by reflection so UMM stays optional.
 *
 * <p>Uses {@code Locations.getShortShard()} and {@code UnofficialMonumentaModClient.locations.getLocation(x, z, shard)}
 * (UMM 1.10). If UMM changes them, the provider throws once and is disabled; nothing else breaks.
 */
public final class UmmModule implements CakeTextModule {
	private static final String MOD_ID = "unofficial-monumenta-mod";
	private static final String LOCATIONS = "ch.njol.unofficialmonumentamod.features.locations.Locations";
	private static final String CLIENT = "ch.njol.unofficialmonumentamod.UnofficialMonumentaModClient";

	@Override
	public String id() {
		return "umm";
	}

	@Override
	public boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	@Override
	public void registerContext() {
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Class<?> locations = Class.forName(LOCATIONS);
			MethodHandle shortShard = lookup.findStatic(locations, "getShortShard", MethodType.methodType(String.class));
			MethodHandle instance = lookup.findStaticGetter(Class.forName(CLIENT), "locations", locations);
			MethodHandle getLocation = lookup.findVirtual(locations, "getLocation",
					MethodType.methodType(String.class, double.class, double.class, String.class));

			ContextRegistry.register("shard", pos -> {
				try {
					String shard = (String) shortShard.invoke();
					return shard == null || shard.equals("unknown") ? null : shard;
				} catch (Throwable t) {
					throw new IllegalStateException(t);
				}
			});
			ContextRegistry.register("area", (Vec3d pos) -> {
				if (pos == null) {
					return null;
				}
				try {
					Object locs = instance.invoke();
					String shard = (String) shortShard.invoke();
					String area = locs == null || shard == null ? null
							: (String) getLocation.invoke(locs, pos.x, pos.z, shard);
					// UMM answers "unknown", or the shard's own name, when no named area covers the spot
					return area == null || area.equalsIgnoreCase("unknown") || area.equalsIgnoreCase(shard) ? null : area;
				} catch (Throwable t) {
					throw new IllegalStateException(t);
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("UMM API not found (changed in a newer UMM?)", e);
		}
	}
}
