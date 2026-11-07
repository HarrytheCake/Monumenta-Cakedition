package io.github.harrythecake.cakechat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Made-up players, shards and guilds, laid out like Monumenta's tab list. */
class GuildTagTest {
	private static final List<String> TAB = List.of(
			"Moderators",
			"<plots> [ABC] qwertfox",
			"<ring> [chim] Mock_Pl4yer",
			"",
			"Friends",
			"<playerplots> [XYZ] Someone_Else",
			"",
			"Guild",
			"<fortune> [QRS] Mock_Pl4yer2",
			"<valley> [QRS] another_one");

	@Test
	void tagOfTheFirstMemberUnderGuild() {
		assertEquals("QRS", GuildTag.find(TAB, null));
		assertEquals("QRS", GuildTag.find(TAB, "qwertfox"), "the Guild section wins over the own line");
	}

	@Test
	void noGuildSection() {
		assertNull(GuildTag.find(List.of("Moderators", "<plots> [ABC] qwertfox", "", "Friends"), null));
		assertNull(GuildTag.find(List.of(), "qwertfox"));
	}

	@Test
	void emptyGuildSectionDoesNotTakeTheNextOne() {
		assertNull(GuildTag.find(List.of("Guild", "", "Friends", "<plots> [ABC] qwertfox"), null));
		assertNull(GuildTag.find(List.of("Guild", "Friends", "<plots> [ABC] qwertfox"), null));
	}

	@Test
	void tagIsTwoToFiveLetters() {
		assertEquals("ab", GuildTag.find(List.of("Guild", "<plots> [ab] qwertfox"), null));
		assertEquals("AbCdE", GuildTag.find(List.of("Guild", "<plots> [AbCdE] qwertfox"), null));
		assertNull(GuildTag.find(List.of("Guild", "<plots> [A] qwertfox"), null));
		assertNull(GuildTag.find(List.of("Guild", "<plots> [Abcdef] qwertfox"), null));
		assertNull(GuildTag.find(List.of("Guild", "<plots> [AB1] qwertfox"), null));
	}

	@Test
	void decoratedHeaderAndLines() {
		// formatting codes, icons, padding, a blank under the header, no shard part
		assertEquals("QRS", GuildTag.find(List.of("§c§l Guild: ", "", " §7[QRS]§r Mock_Pl4yer"), null));
		assertEquals("QRS", GuildTag.find(List.of("  GUILD ", " <fortune>[QRS]Mock_Pl4yer "), null));
		assertEquals("QRS", GuildTag.find(List.of("Guild (2)", "<fortune> [QRS] Mock_Pl4yer"), null));
		assertNull(GuildTag.find(List.of("<plots> [ABC] Guildmaster", "<fortune> [QRS] Mock_Pl4yer"), null), "a member isn't a header");
	}

	@Test
	void ownLineWhenThereIsNoGuildHeader() {
		List<String> tab = List.of("Online", "<plots> [ABC] qwertfox", "<fortune> [QRS] Mock_Pl4yer");
		assertEquals("QRS", GuildTag.find(tab, "mock_pl4yer"));
		assertNull(GuildTag.find(tab, "Pl4yer"), "only the whole name");
		assertNull(GuildTag.find(tab, "Nobody"));
	}

	@Test
	void escapeShowsHiddenCharacters() {
		assertEquals("\\uE101 [QRS]", GuildTag.escape(" [QRS]"));
	}

	@Test
	void placeholder() {
		assertTrue(GuildTag.isPlaceholder(" {Guild} "));
		assertEquals("{guild}", ChatConfig.Window.main().tabs.get(5).channel);
	}
}
