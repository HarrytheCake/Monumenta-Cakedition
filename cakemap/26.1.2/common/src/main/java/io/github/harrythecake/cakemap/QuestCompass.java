package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointKind;
import io.github.harrythecake.cakemap.core.WorldKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The compass target as a temporary "quest objective" waypoint. Monumenta's quest compass points
 * the vanilla compass at the tracked quest's next place. The server also sends the world spawn on
 * every join and respawn, so targets in the first two seconds of a world are ignored. On
 * Monumenta only (any server in dev mode).
 */
public final class QuestCompass {
	public static final String SOURCE = "compass";
	private static long worldSince;
	private static BlockPos last;

	private QuestCompass() {
	}

	/** A new world or dimension: the next spawn position is the world's, not a quest's. */
	public static void worldChanged() {
		worldSince = System.currentTimeMillis();
		last = null;
	}

	public static void target(BlockPos pos) {
		MapSession session = CakeMap.session();
		Minecraft mc = Minecraft.getInstance();
		if (session == null || mc.player == null || !CakeMap.settings().questCompass) {
			return;
		}
		if (!session.server.equals(WorldKeys.MONUMENTA) && !CakeMap.settings().devMode) {
			return;
		}
		if (System.currentTimeMillis() - worldSince < 2000 || pos.equals(last)) {
			last = pos;
			return;
		}
		last = pos;
		double dx = pos.getX() + 0.5 - mc.player.getX();
		double dz = pos.getZ() + 0.5 - mc.player.getZ();
		if (dx * dx + dz * dz < 16) {
			return; // already there
		}
		Waypoint w = new Waypoint(Component.translatable("cakemap.waypoint.compass").getString(), WaypointKind.QUEST_OBJECTIVE, session.dimension(),
				pos.getX(), pos.getY(), pos.getZ());
		w.source = SOURCE;
		session.waypoints.replaceTemporary(SOURCE, w);
		CakeMap.notice(Component.translatable("cakemap.waypoint.compass.set", pos.getX(), pos.getY(), pos.getZ()));
	}
}
