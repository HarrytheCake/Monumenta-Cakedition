package io.github.harrythecake.caketext.core.context;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Where a string was shown: which surface (chat, scoreboard, nameplate…), server, dimension, shard, named area
 * and block position. The same English can mean different things in different places ("Back" on a puzzle floor
 * vs. in a menu); scopes in a strings file match against this.
 */
public record GameContext(String surface, @Nullable String server, @Nullable String dimension,
		@Nullable String shard, @Nullable String area, @Nullable BlockPos pos) {

	/**
	 * Context right now. {@code at} is where the text is (a nameplate's entity); null = where the player is.
	 * Call on the client thread.
	 */
	public static GameContext of(String surface, @Nullable Vec3d at) {
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d pos = at != null ? at : client.player != null ? client.player.getPos() : null;
		String dimension = client.world != null ? client.world.getRegistryKey().getValue().toString() : null;
		return new GameContext(surface, server(client), dimension, ContextRegistry.get("shard", pos),
				ContextRegistry.get("area", pos), pos == null ? null : BlockPos.ofFloored(pos));
	}

	/** Host of the current server, lower case, without port; "singleplayer" in a local world; null in menus. */
	public static @Nullable String server(MinecraftClient client) {
		if (client.isInSingleplayer()) {
			return "singleplayer";
		}
		ServerInfo info = client.getCurrentServerEntry();
		if (info == null || info.address == null) {
			return null;
		}
		String address = info.address.trim().toLowerCase(Locale.ROOT);
		int colon = address.lastIndexOf(':');
		if (colon > 0 && address.indexOf(':') == colon) {
			address = address.substring(0, colon);
		}
		return address;
	}

	/** For strings files. {@code withPos} = false leaves the coordinates out. */
	public Map<String, Object> toJson(boolean withPos) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("surface", surface);
		putIf(map, "server", server);
		putIf(map, "dimension", dimension);
		putIf(map, "shard", shard);
		putIf(map, "area", area);
		if (withPos && pos != null) {
			map.put("pos", new int[] {pos.getX(), pos.getY(), pos.getZ()});
		}
		return map;
	}

	private static void putIf(Map<String, Object> map, String key, @Nullable String value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	/**
	 * Key for merging sightings: same surface, place and 16-block cell count as one context.
	 * Nameplates keep their exact block, since a floating text never moves.
	 */
	public String mergeKey(boolean exactPos) {
		String cell = pos == null ? "" : exactPos ? pos.toShortString()
				: (pos.getX() >> 4) + "," + (pos.getY() >> 4) + "," + (pos.getZ() >> 4);
		return surface + '|' + server + '|' + dimension + '|' + shard + '|' + area + '|' + cell;
	}

	/** Does every key of {@code scope} match? Unknown keys never match, so a typo can't widen a scope. */
	public boolean matches(Map<String, String> scope) {
		for (Map.Entry<String, String> e : scope.entrySet()) {
			String want = e.getValue();
			boolean ok = switch (e.getKey()) {
				case "surface" -> want.equalsIgnoreCase(surface);
				case "server" -> want.equalsIgnoreCase(server);
				case "dimension" -> want.equalsIgnoreCase(dimension);
				case "shard" -> want.equalsIgnoreCase(shard);
				case "area" -> want.equalsIgnoreCase(area);
				case "box" -> inBox(want);
				default -> false;
			};
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private boolean inBox(String box) {
		if (pos == null) {
			return false;
		}
		String[] p = box.trim().split("[\\s,]+");
		if (p.length != 6) {
			return false;
		}
		try {
			int[] v = new int[6];
			for (int i = 0; i < 6; i++) {
				v[i] = Integer.parseInt(p[i]);
			}
			return between(pos.getX(), v[0], v[3]) && between(pos.getY(), v[1], v[4]) && between(pos.getZ(), v[2], v[5]);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean between(int x, int a, int b) {
		return x >= Math.min(a, b) && x <= Math.max(a, b);
	}
}
