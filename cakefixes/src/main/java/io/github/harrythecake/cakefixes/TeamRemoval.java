package io.github.harrythecake.cakefixes;

import java.util.HashSet;
import java.util.Set;

/**
 * Fix "teamRemovalFix". The server sometimes tells the client to remove a player (or entity) from a team the
 * client does not have it on (Monumenta does this in dungeons, mostly for team "red"). Vanilla 1.20.4's
 * Scoreboard.removeScoreHolderFromTeam then throws, the packet handler builds a full CrashReport for it (OSHI
 * hardware queries over WMI: 100-500 ms on the render thread), logs it, and the rest of that packet's names are
 * never processed. Skipping just that one name is what vanilla would do if it didn't throw.
 * Plain Java (the scoreboard team is passed as Object) so the tests can run it.
 */
public final class TeamRemoval {
	private static final Set<String> loggedTeams = new HashSet<>();
	private static long skipped;

	private TeamRemoval() {}

	/** True = let vanilla remove it. False = skip: the holder is on another team or none, so vanilla would throw. */
	public static boolean shouldRemove(boolean fixOn, Object currentTeam, Object targetTeam, String targetTeamName) {
		if (!fixOn || currentTeam == targetTeam) {
			return true;
		}
		synchronized (TeamRemoval.class) {
			skipped++;
			if (loggedTeams.add(targetTeamName)) {
				// The team name only: holder names are player names (personal data) or entity UUIDs.
				CakeFixes.LOGGER.info("Skipped a removal from team '{}' for a player or entity not on it (vanilla"
						+ " would freeze to build a crash report). Later skips for this team are counted, not logged.",
						targetTeamName);
			}
		}
		return false;
	}

	public static synchronized long skipped() {
		return skipped;
	}
}
