package io.github.harrythecake.caketext.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.harrythecake.caketext.core.strings.Exchanger;
import io.github.harrythecake.caketext.core.strings.PoFile;
import io.github.harrythecake.caketext.core.strings.StringEntry;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import io.github.harrythecake.caketext.core.template.ItemFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExchangerTest {
	@TempDir
	Path dir;

	private static StringsFile collected() {
		StringsFile file = new StringsFile();
		file.server = "server.playmonumenta.com";
		StringEntry quest = new StringEntry("{c1}[Quest] {c2}Talk to {~} \"guards\"", "");
		quest.count = 12L;
		quest.first = "2026-10-27T10:00:00Z";
		quest.last = "2026-10-27T11:00:00Z";
		Map<String, Object> ctx = new LinkedHashMap<>();
		ctx.put("surface", "chat");
		ctx.put("server", "server.playmonumenta.com");
		ctx.put("dimension", "minecraft:overworld");
		ctx.put("shard", "valley");
		ctx.put("area", "Sierhaven");
		ctx.put("pos", List.of(-712, 64, 103));
		quest.contexts = List.of(ctx);
		StringEntry back = new StringEntry("Back", "向后");
		back.scope = Map.of("surface", "nameplate", "area", "Excavation Site B");
		back.note = "Puzzle floor arrow";
		StringEntry lore = new StringEntry("Line one\nLine two", "");
		Map<String, Object> tip = new LinkedHashMap<>();
		tip.put("surface", "tooltip");
		lore.contexts = List.of(tip);
		file.entries.addAll(List.of(quest, back, lore));
		return file;
	}

	@Test
	void roundTripKeepsEverything() throws Exception {
		StringsFile file = collected();
		PoFile po = Exchanger.toPo(file, file.entries, false, true);
		String text = po.toString();
		assertTrue(text.contains("msgctxt \"area=Excavation Site B;surface=nameplate\""), text);
		assertTrue(text.contains("#. where: chat | server.playmonumenta.com | minecraft:overworld | valley | Sierhaven | -712 64 103"), text);
		assertTrue(text.contains("msgid \"{c1}[Quest] {c2}Talk to {~} \\\"guards\\\"\""), text);
		assertTrue(text.contains("msgid \"\"\n\"Line one\\n\"\n\"Line two\""), text);

		StringsFile back = Exchanger.fromPo(PoFile.parse(text));
		assertEquals(3, back.entries.size());
		StringEntry q = back.entries.get(0);
		assertEquals(file.entries.get(0).src, q.src);
		assertEquals(12L, q.count);
		assertEquals("2026-10-27T10:00:00Z", q.first);
		assertEquals("2026-10-27T11:00:00Z", q.last);
		assertEquals("valley", q.contexts.get(0).get("shard"));
		assertEquals(List.of(-712, 64, 103), q.contexts.get(0).get("pos"));
		StringEntry b = back.entries.get(1);
		assertEquals("向后", b.dst);
		assertEquals(Map.of("surface", "nameplate", "area", "Excavation Site B"), b.scope);
		assertEquals("Puzzle floor arrow", b.note);
		assertEquals("Line one\nLine two", back.entries.get(2).src);
		assertEquals("server.playmonumenta.com", back.server);
	}

	@Test
	void readsWhatCrowdinSends() throws Exception {
		String po = """
				# translator comment
				msgid ""
				msgstr ""
				"Content-Type: text/plain; charset=UTF-8\\n"
				"Language: zh_CN\\n"

				#. seen 3 times
				msgid "Welcome to the Valley!"
				msgstr "欢迎来到山谷！"

				#, fuzzy
				msgid "Total Damage: {~}"
				msgstr "损失总额：{~}"

				msgctxt "surface=nameplate"
				msgid "Back"
				msgstr ""
				"向"
				"后"
				msgid "Untranslated"
				msgstr ""

				#~ msgid "Old string"
				#~ msgstr "旧"
				""";
		StringsFile file = Exchanger.fromPo(PoFile.parse(po));
		assertEquals("zh_cn", file.lang);
		assertEquals(4, file.entries.size());
		assertEquals("欢迎来到山谷！", file.entries.get(0).dst);
		assertEquals(3L, file.entries.get(0).count);
		// fuzzy = not reviewed: not used
		assertFalse(file.entries.get(1).translated());
		assertEquals("向后", file.entries.get(2).dst);
		assertEquals(Map.of("surface", "nameplate"), file.entries.get(2).scope);
		assertFalse(file.entries.get(3).translated());
	}

	@Test
	void crowdinLayout() throws Exception {
		List<Path> written = Exchanger.writeCrowdin(collected(), dir, false);
		assertTrue(Files.exists(dir.resolve("en_us/chat.pot")));
		assertTrue(Files.exists(dir.resolve("en_us/nameplate.pot")));
		assertTrue(Files.exists(dir.resolve("en_us/tooltip.pot")));
		assertTrue(Files.exists(dir.resolve("crowdin.yml")));
		assertEquals(4, written.size());
		PoFile chat = PoFile.read(dir.resolve("en_us/chat.pot"));
		assertEquals("", chat.entries.get(0).str, "sources carry no translations");
		assertNull(chat.header.get("Language"));
		assertTrue(chat.entries.get(0).comments.stream().anyMatch(c -> c.endsWith("| -")), "positions left out");
		String yml = Files.readString(dir.resolve("crowdin.yml"), StandardCharsets.UTF_8);
		assertTrue(yml.contains("source: /en_us/*.pot"), yml);
		assertTrue(yml.contains("translation: /translations/%locale_with_underscore%/%file_name%.po"), yml);
		assertTrue(yml.contains("zh-CN: zh_cn"), yml);
		// crowdin.yml is the user's once written
		Files.writeString(dir.resolve("crowdin.yml"), "mine");
		Exchanger.writeCrowdin(collected(), dir, false);
		assertEquals("mine", Files.readString(dir.resolve("crowdin.yml")));
	}

	@Test
	void converterGoesBothWays() throws Exception {
		Path json = dir.resolve("strings.json");
		collected().write(json, false);
		List<Path> pos = Exchanger.convert(List.of(json), dir.resolve("out"));
		// one entry is translated, so these are .po files, not sources
		assertTrue(pos.stream().allMatch(p -> p.toString().endsWith(".po")), pos.toString());
		Path nameplate = dir.resolve("out/strings/nameplate.po");
		List<Path> back = Exchanger.convert(List.of(nameplate), dir.resolve("back"));
		StringsFile file = StringsFile.read(back.get(0));
		assertEquals("向后", file.entries.get(0).dst);
		assertEquals(Map.of("surface", "nameplate", "area", "Excavation Site B"), file.entries.get(0).scope);
	}

	@Test
	void itemFilter() {
		ItemFilter f = ItemFilter.of(ItemFilter.DEFAULT);
		for (String id : List.of("minecraft:oak_sign", "minecraft:bamboo_hanging_sign", "minecraft:book",
				"minecraft:writable_book", "minecraft:written_book", "minecraft:enchanted_book", "minecraft:knowledge_book",
				"minecraft:shulker_box", "minecraft:red_shulker_box", "othermod:steel_sign")) {
			assertTrue(f.matches(id), id);
		}
		for (String id : List.of("minecraft:bookshelf", "minecraft:chiseled_bookshelf", "minecraft:diamond_sword",
				"minecraft:lectern", "minecraft:paper")) {
			assertFalse(f.matches(id), id);
		}
		assertTrue(ItemFilter.of(List.of("minecraft:diamond_*")).matches("minecraft:diamond_sword"));
		assertEquals(List.of("*sign", "*book"), ItemFilter.parse(" *sign , , *book "));
	}
}
