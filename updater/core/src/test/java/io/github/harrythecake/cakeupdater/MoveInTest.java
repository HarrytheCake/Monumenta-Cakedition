package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An old install and this one in temp folders; made-up files only. */
class MoveInTest {
	@TempDir
	Path tmp;

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private Path oldInstall() throws IOException {
		Path old = tmp.resolve("OldPack").resolve(".minecraft");
		write(old.resolve("options.txt"), "version:3700\nkey_key.jump:key.keyboard.space\nfov:0.5\n");
		write(old.resolve("config/unofficial-monumenta-mod.json"), "{\"old\": true}");
		write(old.resolve("config/monumenta/lockedSlots.json"), "{\"lockedSlots\": [true]}");
		write(old.resolve("config/fma.json"), "{\"features\": {}}");
		write(old.resolve("XaeroWaypoints/Multiplayer_server.playmonumenta.com/mw$default_1.txt"), "waypoint:Home:H:1:2:3\n");
		write(old.resolve("XaeroWorldMap/Multiplayer_server.playmonumenta.com/null/mw$default/0_0.zip"), "map");
		write(old.resolve("config/chatplus/chatplus-v2.7.0.json"), """
				{"keyPeekChat": "key.keyboard.o", "chatWindows": [{"tabSettings": {"tabs": [
				  {"settings": [{"pattern": "(?s).*", "name": "All"}]},
				  {"settings": [{"pattern": ".*<g>.*", "name": "g"}]}]}}]}
				""");
		write(old.resolve("config/other-mod.json"), "{}");
		return old;
	}

	@Test
	void instanceFolderLeadsToItsGameFolder() throws IOException {
		Path old = oldInstall();
		assertEquals(old, MoveIn.gameFolder(old.getParent()));
		assertEquals(old, MoveIn.gameFolder(old));
		assertEquals("OldPack", MoveIn.label(old));
		assertEquals("OldPack", MoveIn.label(old.getParent()));
	}

	@Test
	void scanFindsWhatTheOldInstallHas() throws IOException {
		Path old = oldInstall();
		List<MoveIn.Found> found = MoveIn.scan(old, true);
		assertEquals(List.of(MoveIn.Kind.OPTIONS, MoveIn.Kind.UMM, MoveIn.Kind.LOCKED_SLOTS, MoveIn.Kind.FMA,
				MoveIn.Kind.XAERO, MoveIn.Kind.XAERO, MoveIn.Kind.CHATPLUS), found.stream().map(MoveIn.Found::kind).toList());
		assertEquals("config/chatplus/chatplus-v2.7.0.json", found.get(6).path());
		// without CakeChat, ChatPlus isn't offered
		assertFalse(MoveIn.scan(old, false).stream().anyMatch(f -> f.kind() == MoveIn.Kind.CHATPLUS));
		assertTrue(MoveIn.scan(tmp.resolve("empty"), true).isEmpty());
	}

	@Test
	void applyCopiesBacksUpAndPatchesCakeChat() throws IOException {
		Path old = oldInstall();
		Path game = tmp.resolve("Cakedition").resolve(".minecraft");
		write(game.resolve("options.txt"), "version:3700\nkey_key.jump:key.keyboard.j\n");
		write(game.resolve("config/unofficial-monumenta-mod.json"), "{\"ours\": true}");
		write(game.resolve("config/cakechat/config.json"), "{\"configVersion\": 3, \"history\": {\"keepOnDisconnect\": false}}");
		write(game.resolve("XaeroWaypoints/Multiplayer_server.playmonumenta.com/mw$default_1.txt"), "ours\n");
		Backups backups = new Backups(game);
		String backup = backups.newImportBackup(MoveIn.label(old));

		JsonObject chatplus = ChatPlusImport.read(old.resolve("config/chatplus/chatplus-v2.7.0.json")).orElseThrow();
		ChatPlusImport.Result r = ChatPlusImport.convert(chatplus, 0);
		List<String> copy = MoveIn.scan(old, true).stream()
				.filter(f -> f.kind() != MoveIn.Kind.CHATPLUS).map(MoveIn.Found::path).toList();
		MoveIn.stage(game, new MoveIn.Pending(old.toString(), backup, copy, r.patch(),
				Map.of(ChatPlusImport.PEEK_OPTION, r.peekKey())));
		assertNotNull(MoveIn.pending(game));

		int files = MoveIn.apply(game, backups, 5);
		// options, UMM, locked slots, fma, 1 waypoint file, 1 map file, CakeChat's config
		assertEquals(7, files);
		assertNull(MoveIn.pending(game));
		assertEquals(-1, MoveIn.apply(game, backups, 5), "applied once");

		String options = Files.readString(game.resolve("options.txt"));
		assertTrue(options.contains("key_key.jump:key.keyboard.space"), "the whole old options.txt");
		assertTrue(options.contains("key_key.cakechat.peek:key.keyboard.o"), "ChatPlus's peek key for CakeChat");
		assertEquals("{\"old\": true}", Files.readString(game.resolve("config/unofficial-monumenta-mod.json")));
		assertEquals("waypoint:Home:H:1:2:3\n", Files.readString(game.resolve("XaeroWaypoints/Multiplayer_server.playmonumenta.com/mw$default_1.txt")));
		assertTrue(Files.exists(game.resolve("XaeroWorldMap/Multiplayer_server.playmonumenta.com/null/mw$default/0_0.zip")));
		assertFalse(Files.exists(game.resolve("config/other-mod.json")), "only the listed things");
		assertFalse(Files.exists(game.resolve("config/chatplus")), "ChatPlus's file isn't copied, only converted");

		JsonObject cakechat = JsonParser.parseString(Files.readString(game.resolve("config/cakechat/config.json"))).getAsJsonObject();
		assertFalse(cakechat.getAsJsonObject("history").get("keepOnDisconnect").getAsBoolean());
		assertEquals(2, cakechat.getAsJsonArray("windows").get(0).getAsJsonObject().getAsJsonArray("tabs").size());

		// what was replaced is in the import backup, the originals (options.txt saved once, before the key was set)
		Path saved = game.resolve(Backups.DIR).resolve(backup);
		assertEquals("version:3700\nkey_key.jump:key.keyboard.j\n", Files.readString(saved.resolve("options.txt")));
		assertEquals("{\"ours\": true}", Files.readString(saved.resolve("config/unofficial-monumenta-mod.json")));
		assertEquals("ours\n", Files.readString(saved.resolve("XaeroWaypoints/Multiplayer_server.playmonumenta.com/mw$default_1.txt")));
		assertFalse(Files.exists(saved.resolve("config/fma.json")), "we had none, nothing to save");
		Backups.Backup info = backups.find(backup);
		assertEquals("import", info.kind());
		assertEquals("OldPack", info.from());
		assertTrue(info.hasFiles());

		MoveIn.Done done = MoveIn.takeDone(game);
		assertEquals(7, done.files());
		assertEquals(backup, done.backup());
		assertNull(MoveIn.takeDone(game), "announced once");

		// restoring the import backup puts our files back (options.txt and Xaero too, not only config/)
		backups.scheduleConfigRestore(backup);
		backups.applyPendingConfigRestore(5);
		assertEquals("version:3700\nkey_key.jump:key.keyboard.j\n", Files.readString(game.resolve("options.txt")));
		assertEquals("ours\n", Files.readString(game.resolve("XaeroWaypoints/Multiplayer_server.playmonumenta.com/mw$default_1.txt")));
	}

	@Test
	void nothingLeavesTheGameFolder() throws IOException {
		Path old = oldInstall();
		Path game = tmp.resolve("game");
		Files.createDirectories(game);
		Backups backups = new Backups(game);
		String backup = backups.newImportBackup("x");
		MoveIn.stage(game, new MoveIn.Pending(old.toString(), backup, List.of("../../escape.txt", "options.txt"), null, Map.of()));
		assertEquals(1, MoveIn.apply(game, backups, 5));
		assertFalse(Files.exists(tmp.resolve("escape.txt")));
	}

	@Test
	void setOptionsReplacesOrAppends() throws IOException {
		Path options = tmp.resolve("options.txt");
		write(options, "a:1\nkey_key.cakechat.hide:key.keyboard.unknown\n");
		MoveIn.setOptions(options, Map.of("key_key.cakechat.hide", "key.keyboard.h"));
		MoveIn.setOptions(options, Map.of("key_key.cakechat.peek", "key.keyboard.o"));
		assertEquals(List.of("a:1", "key_key.cakechat.hide:key.keyboard.h", "key_key.cakechat.peek:key.keyboard.o"),
				Files.readAllLines(options));
	}

	@Test
	void offerCanBeTurnedOff() throws IOException {
		Path game = tmp.resolve("game");
		assertFalse(MoveIn.offerOff(game));
		MoveIn.turnOfferOff(game);
		assertTrue(MoveIn.offerOff(game));
	}
}
