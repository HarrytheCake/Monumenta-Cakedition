package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.6.0: updates from Modrinth first, then pack.toml at GitHub, then GitLab. Made-up packs only; no real service. */
class ModrinthUpdateTest {
	@TempDir
	Path tmp;

	@Test
	void cakeditionDefaultsOnlyForCakeditionsPack() {
		Config.Packwiz cakedition = new Config.Packwiz(); // a pre-1.6.0 config file: no modrinthProject, no fallbackUrls
		assertEquals("Yx4XUCFJ", cakedition.modrinth());
		assertEquals(List.of(Config.Packwiz.CAKEDITION_PACK_URL, Config.Packwiz.CAKEDITION_GITLAB_URL), cakedition.packUrls());

		Config.Packwiz other = new Config.Packwiz();
		other.packUrl = "https://example.org/pack.toml"; // another modpack using the mod
		assertEquals("", other.modrinth());
		assertEquals(List.of("https://example.org/pack.toml"), other.packUrls());

		Config.Packwiz off = new Config.Packwiz();
		off.modrinthProject = "";
		off.fallbackUrls = List.of(" ", Config.Packwiz.CAKEDITION_PACK_URL, "https://mirror.example/pack.toml");
		assertEquals("", off.modrinth());
		assertEquals(List.of(Config.Packwiz.CAKEDITION_PACK_URL, "https://mirror.example/pack.toml"), off.packUrls());
	}

	private static final String VERSIONS = """
			[{"id": "BETA0001", "version_number": "2.4.0-beta", "version_type": "beta",
			  "files": [{"primary": true, "filename": "P-2.4.0-beta.mrpack", "url": "https://cdn/b.mrpack", "hashes": {"sha512": "bb"}}]},
			 {"id": "UnoXiYGu", "version_number": "2.3.0", "version_type": "release",
			  "files": [{"primary": false, "filename": "notes.txt", "url": "https://cdn/n.txt", "hashes": {"sha512": "nn"}},
			            {"primary": true, "filename": "P-2.3.0.mrpack", "url": "https://cdn/p.mrpack", "hashes": {"sha512": "aa"}}]}]
			""";

	@Test
	void newestReleaseOnModrinthIsTheUpdate() {
		UpdateCheck.Result r = UpdateCheck.fromModrinth(JsonParser.parseString(VERSIONS), "2.2.3");
		assertTrue(r.available());
		assertEquals("2.3.0", r.remoteVersion());
		assertEquals(new UpdateCheck.Mrpack("UnoXiYGu", "https://cdn/p.mrpack", "aa"), r.mrpack());
		assertEquals("modrinth:UnoXiYGu", r.remoteHash());
		assertNull(r.packUrl());
		assertFalse(UpdateCheck.fromModrinth(JsonParser.parseString(VERSIONS), "2.3.0").available());
		// installed from GitHub before Modrinth listed it: not an update backwards
		assertFalse(UpdateCheck.fromModrinth(JsonParser.parseString(VERSIONS), "2.3.1").available());
		assertThrows(IllegalStateException.class, () -> UpdateCheck.fromModrinth(JsonParser.parseString("[]"), "2.3.0"));
	}

	@Test
	void modrinthQueryAsksForFabricAndTheGameVersion() {
		assertEquals("https://api.modrinth.com/v2/project/Yx4XUCFJ/version?loaders=%5B%22fabric%22%5D"
				+ "&game_versions=%5B%221.20.4%22%5D", UpdateCheck.modrinthUrl("Yx4XUCFJ", "1.20.4"));
	}

	@Test
	void helperArgumentsMustSurviveCmd() {
		assertTrue(HelperLauncher.safe("https://cdn.modrinth.com/data/Yx4XUCFJ/versions/UnoXiYGu/P-2.3.0.mrpack"));
		assertFalse(HelperLauncher.safe("https://x/a%20b.mrpack"));
		assertFalse(HelperLauncher.safe("https://x/?a=1&b=2"));
		assertFalse(HelperLauncher.safe(""));
	}

	// ---- rebuilding the packwiz pack from a .mrpack ----

	private static byte[] zip(Map<String, byte[]> entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream z = new ZipOutputStream(out)) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				z.putNextEntry(new ZipEntry(e.getKey()));
				z.write(e.getValue());
				z.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	private static String sha256(byte[] d) {
		return MrpackPack.hash("SHA-256", d);
	}

	@Test
	void rebuildsThePackAndDownloadsOnlyChangedLinkedFiles() throws Exception {
		byte[] art = b("big art v2");
		byte[] oldArt = b("big art v1");
		AtomicInteger hits = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			hits.incrementAndGet();
			byte[] body = ex.getRequestURI().getPath().endsWith("art2.zip") ? art : oldArt;
			ex.sendResponseHeaders(200, body.length);
			ex.getResponseBody().write(body);
			ex.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
			byte[] config = b("a = 1\n");
			byte[] servers = b("servers");
			Map<String, byte[]> entries = new LinkedHashMap<>();
			entries.put("modrinth.index.json", b("{}"));
			entries.put("overrides/config/x.json", config);
			entries.put("overrides/config/cakeupdater/defaults/servers.dat", servers);
			entries.put(MrpackPack.PACKWIZ + "pack.toml", b("name = \"P\"\n"));
			entries.put(MrpackPack.PACKWIZ + "index.toml", b("index"));
			entries.put(MrpackPack.PACKWIZ + "mods/a.pw.toml", b("meta"));
			entries.put(MrpackPack.TSV, b(String.join("\n",
					"config/x.json\t" + sha256(config) + "\tmrpack:overrides/config/x.json",
					"servers.dat\t" + sha256(servers) + "\tmrpack:overrides/config/cakeupdater/defaults/servers.dat",
					"resourcepacks/art1.zip\t" + sha256(oldArt) + "\turl:" + base + "art1.zip",
					"resourcepacks/art2.zip\t" + sha256(art) + "\turl:" + base + "art2.zip") + "\n"));
			Path game = tmp.resolve("game");
			Files.createDirectories(game.resolve("resourcepacks"));
			Files.write(game.resolve("resourcepacks/art1.zip"), oldArt); // already installed: copied, not downloaded

			Path pack = MrpackPack.rebuild(zip(entries), game, tmp.resolve("work"), "test");
			Path work = pack.getParent();
			assertEquals("name = \"P\"\n", Files.readString(pack));
			assertEquals("meta", Files.readString(work.resolve("mods/a.pw.toml")));
			assertArrayEquals(config, Files.readAllBytes(work.resolve("config/x.json")));
			assertArrayEquals(servers, Files.readAllBytes(work.resolve("servers.dat")));
			assertArrayEquals(oldArt, Files.readAllBytes(work.resolve("resourcepacks/art1.zip")));
			assertArrayEquals(art, Files.readAllBytes(work.resolve("resourcepacks/art2.zip")));
			assertFalse(Files.exists(work.resolve("files.tsv")));
			assertEquals(1, hits.get(), "only the changed linked file is downloaded");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void refusesBadHashesUnsafePathsAndOldPackFiles() throws Exception {
		Path game = tmp.resolve("game");
		Map<String, byte[]> bad = new LinkedHashMap<>();
		bad.put(MrpackPack.PACKWIZ + "pack.toml", b("p"));
		bad.put("overrides/config/x.json", b("tampered"));
		bad.put(MrpackPack.TSV, b("config/x.json\t" + sha256(b("original")) + "\tmrpack:overrides/config/x.json\n"));
		assertThrows(IOException.class, () -> MrpackPack.rebuild(zip(bad), game, tmp.resolve("w1"), "test"));

		Map<String, byte[]> escape = new LinkedHashMap<>();
		escape.put(MrpackPack.PACKWIZ + "pack.toml", b("p"));
		escape.put("overrides/x", b("x"));
		escape.put(MrpackPack.TSV, b("../outside.txt\t" + sha256(b("x")) + "\tmrpack:overrides/x\n"));
		assertThrows(IOException.class, () -> MrpackPack.rebuild(zip(escape), game, tmp.resolve("w2"), "test"));
		assertFalse(Files.exists(tmp.resolve("outside.txt")));

		Map<String, byte[]> old = Map.of("modrinth.index.json", b("{}"), "overrides/config/x.json", b("x"));
		assertThrows(IOException.class, () -> MrpackPack.rebuild(zip(old), game, tmp.resolve("w3"), "test"));

		assertThrows(IOException.class, () -> MrpackPack.inside(tmp, "C:/Windows/x"));
		assertThrows(IOException.class, () -> MrpackPack.inside(tmp, "/etc/x"));
		assertEquals(tmp.toAbsolutePath().normalize().resolve("a/b"), MrpackPack.inside(tmp, "a/b"));
	}

	@Test
	void wrongMrpackDownloadIsRefused() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			ex.sendResponseHeaders(200, 3);
			ex.getResponseBody().write(b("abc"));
			ex.close();
		});
		server.start();
		try {
			String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/p.mrpack";
			IOException e = assertThrows(IOException.class,
					() -> MrpackPack.download(url, "00", tmp.resolve("g"), tmp.resolve("w"), "test"));
			assertTrue(e.getMessage().contains("sha512"));
		} finally {
			server.stop(0);
		}
	}
}
