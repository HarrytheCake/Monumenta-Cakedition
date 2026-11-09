package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointKind;
import io.github.harrythecake.cakemap.core.WaypointStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Dying adds a death waypoint where the player died (user, 2026-11-07): kept like the player's own
 * waypoints, the newest {@link WaypointStore#DEATHS_KEPT} per server, never tracked by itself.
 * Two signals, whichever comes first, once per life: the server's death packet (the death screen;
 * also sent with immediate respawn) and, as a fallback, the player being dead on a client tick.
 */
public final class DeathWaypoints {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT);
	/** False after a death is recorded, until the player is alive again (a new player entity: respawn or another world). */
	private static boolean armed = true;
	/**
	 * The player entity that died. The death packet can come a tick before the health packet, so the
	 * dying player may still show health on the next tick: only a new player entity re-arms.
	 */
	private static LocalPlayer dead;

	private DeathWaypoints() {
	}

	/** The death packet named this entity id (render thread). */
	public static void deathPacket(int entityId) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null && p.getId() == entityId) {
			died(Minecraft.getInstance());
		}
	}

	/** Every client tick with a world and player. */
	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p.isDeadOrDying()) {
			died(mc);
		} else if (!armed && p != dead && p.getHealth() > 0) {
			armed = true;
			dead = null;
		}
	}

	private static void died(Minecraft mc) {
		LocalPlayer p = mc.player;
		MapSession session = CakeMap.session();
		if (!armed || p == null || mc.level == null || session == null) {
			return;
		}
		armed = false;
		dead = p;
		if (!CakeMap.settings().deathWaypoints) {
			return;
		}
		String dim = mc.level.dimension().identifier().toString();
		String name = Component.translatable("cakemap.waypoint.death", LocalDateTime.now().format(TIME)).getString();
		Waypoint w = new Waypoint(name, WaypointKind.DEATH, dim, p.getBlockX(), p.getBlockY(), p.getBlockZ());
		w.source = "death";
		session.waypoints.addDeath(w, WaypointStore.DEATHS_KEPT);
		CakeMap.notice(Component.translatable("cakemap.waypoint.death.added", w.x, w.y, w.z));
	}
}
