package io.github.harrythecake.cakebook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import io.github.harrythecake.cakebook.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Where the player stands, for "Insert location" in a note: UMM's area and shard name when UMM is installed (the
 * same reflection as CakeText's UMM module: {@code Locations.getShortShard()} and
 * {@code UnofficialMonumentaModClient.locations.getLocation(x, z, shard)}), then the block position.
 */
final class Location {
	private static final String UMM = "unofficial-monumenta-mod";
	private static final String LOCATIONS = "ch.njol.unofficialmonumentamod.features.locations.Locations";
	private static final String CLIENT = "ch.njol.unofficialmonumentamod.UnofficialMonumentaModClient";

	private static MethodHandle shortShard;
	private static MethodHandle instance;
	private static MethodHandle getLocation;
	private static boolean looked;

	private Location() {}

	/** e.g. "Sierhaven · ring · 123 64 -45", or null outside a world. */
	static @Nullable String describe(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		String shard = shard();
		if (shard != null) {
			String area = area(shard, player.getX(), player.getZ());
			if (area != null) {
				parts.add(area);
			}
			parts.add(shard);
		} else if (client.level != null) {
			parts.add(client.level.dimension().location().toString());
		}
		parts.add(player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ());
		return String.join(" · ", parts);
	}

	/** The Markdown line a note gets: a bold label in the player's language and the place. */
	static @Nullable String markdown(Minecraft client) {
		String place = describe(client);
		return place == null ? null : "**" + Component.translatable("cakebook.note.location").getString() + "** " + place;
	}

	private static void lookUp() {
		if (looked) {
			return;
		}
		looked = true;
		if (!Services.PLATFORM.isModLoaded(UMM)) {
			return;
		}
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Class<?> locations = Class.forName(LOCATIONS);
			shortShard = lookup.findStatic(locations, "getShortShard", MethodType.methodType(String.class));
			instance = lookup.findStaticGetter(Class.forName(CLIENT), "locations", locations);
			getLocation = lookup.findVirtual(locations, "getLocation",
					MethodType.methodType(String.class, double.class, double.class, String.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			CakeBook.LOGGER.warn("UMM's location API not found (changed in a newer UMM?): {}", e.toString());
			shortShard = null;
		}
	}

	private static @Nullable String shard() {
		lookUp();
		if (shortShard == null) {
			return null;
		}
		try {
			String shard = (String) shortShard.invoke();
			return shard == null || shard.equals("unknown") ? null : shard;
		} catch (Throwable t) {
			return null;
		}
	}

	private static @Nullable String area(String shard, double x, double z) {
		try {
			Object locations = instance.invoke();
			String area = locations == null ? null : (String) getLocation.invoke(locations, x, z, shard);
			// UMM answers "unknown", or the shard's own name, when no named area covers the spot
			return area == null || area.equalsIgnoreCase("unknown") || area.equalsIgnoreCase(shard) ? null : area;
		} catch (Throwable t) {
			return null;
		}
	}
}
