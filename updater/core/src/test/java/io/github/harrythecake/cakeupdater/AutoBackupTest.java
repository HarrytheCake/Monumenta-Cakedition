package io.github.harrythecake.cakeupdater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A made-up game folder in a temp folder; made-up files only. */
class AutoBackupTest {
	@TempDir
	Path game;

	private static final String WAYPOINTS = "xaero/minimap/Multiplayer_example.org/dim%monumenta$valley/mw$default_1.txt";

	private void write(String rel, String text) throws IOException {
		Path file = game.resolve(rel);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private void gameFolder() throws IOException {
		write("servers.dat", "servers");
		write("options.txt", "fov:0.5\n");
		write("config/some-mod.json", "{}");
		write("config/yosbr/options.txt", "shipped default");
		write("config/spark/profile-1.sparkprofile", "profile");
		write("config/caketext/exports/crowdin/a.pot", "export");
		write(WAYPOINTS, "waypoint:Camp:C:1:2:3:0:false:0:gui.xaero_default:false:0:0:false\n");
		write("xaero/minimap/Multiplayer_example.org/config.txt", "minimap settings");
		write("xaero/world-map/Multiplayer_example.org/null/mw$default/0_0.zip", "map tile");
		write("cakemap/mp_example.org/waypoints.json", "[]");
		write("cakemap/mp_example.org/tiles/0_0.png", "tile");
	}

	@Test
	void holdsSettingsAndWaypointsNotMapsOrCaches() throws IOException {
		gameFolder();
		Set<String> files = new Backups(game).autoFiles().keySet();
		assertEquals(Set.of("servers.dat", "options.txt", "config/some-mod.json", WAYPOINTS, "cakemap/mp_example.org/waypoints.json"), files);
	}

	@Test
	void madeWhenSomethingChangedAndRestorable() throws IOException {
		gameFolder();
		Backups backups = new Backups(game);
		Backups.Backup first = backups.autoBackup();
		assertNotNull(first);
		assertEquals("auto", first.kind());
		assertTrue(first.hasServers());
		assertTrue(first.hasFiles());
		assertNull(backups.autoBackup(), "nothing changed: no new backup");

		write(WAYPOINTS, "");  // the waypoints are lost
		assertNotNull(backups.autoBackup(), "a change: a new one");

		// restoring the first one brings the waypoints back (after close, like other config restores)
		backups.scheduleConfigRestore(first.name());
		assertTrue(backups.applyPendingConfigRestore(5) > 0);
		assertEquals("waypoint:Camp:C:1:2:3:0:false:0:gui.xaero_default:false:0:0:false\n",
				Files.readString(game.resolve(WAYPOINTS)));
	}

	@Test
	void keepsItsOwnCountWithoutTouchingOtherBackups() throws IOException {
		gameFolder();
		Backups backups = new Backups(game);
		backups.setKeepAuto(2);
		String imported = backups.newImportBackup("Old");
		for (int i = 0; i < 4; i++) {
			write("config/some-mod.json", "{\"n\": " + i + "}");
			assertNotNull(backups.autoBackup());
		}
		List<Backups.Backup> all = backups.list();
		assertEquals(2, all.stream().filter(b -> b.kind().equals("auto")).count(), "the newest two auto backups");
		assertTrue(all.stream().anyMatch(b -> b.name().equals(imported)), "the import backup stays");
		backups.prune(1);
		assertEquals(2, backups.list().stream().filter(b -> b.kind().equals("auto")).count(), "keep for other kinds doesn't apply");
		Path newest = game.resolve(Backups.DIR).resolve(all.get(0).name());
		assertEquals("{\"n\": 3}", Files.readString(newest.resolve("config/some-mod.json")));
		assertFalse(Files.exists(newest.resolve("config/spark/profile-1.sparkprofile")));
	}

	@Test
	void namesSortByAgeWithTwoDigitSuffixes() {
		List<String> names = new java.util.ArrayList<>(List.of("2026-11-12_18-00-00_10", "2026-11-12_18-00-01",
				"2026-11-12_18-00-00", "2026-11-12_18-00-00_9", "2026-11-12_18-00-00_2"));
		names.sort(Backups.BY_AGE);
		assertEquals(List.of("2026-11-12_18-00-00", "2026-11-12_18-00-00_2", "2026-11-12_18-00-00_9",
				"2026-11-12_18-00-00_10", "2026-11-12_18-00-01"), names);
	}

	@Test
	void playTimeIsKeptAcrossStarts() throws IOException {
		Backups backups = new Backups(game);
		assertEquals(0, backups.savedPlayTicks());
		backups.savePlayTicks(12345);
		assertEquals(12345, new Backups(game).savedPlayTicks());
	}
}
