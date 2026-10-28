package io.github.harrythecake.cakechat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelsTest {
	@Test
	void backToAllRejoinsThePlayersOwnChannel() {
		Channels channels = new Channels();
		// the player's guild channel, joined by hand (what the in-game test did)
		channels.typed("join EDE");
		assertFalse(channels.serverLine("Joined channel EDE.", 0));
		assertEquals("EDE", channels.own());

		assertEquals(List.of("join g"), channels.select("g", 1000));
		assertTrue(channels.serverLine("Joined channel g.", 1200), "CakeChat's own join is hidden");
		assertEquals(List.of(), channels.select("g", 2000));
		assertEquals(List.of("join tr"), channels.select("tr", 3000));
		assertTrue(channels.serverLine("Joined channel tr.", 3100));
		// never /leave: back on All, the player's own channel again
		assertEquals(List.of("join EDE"), channels.select("", 4000));
		assertTrue(channels.serverLine("Joined channel EDE.", 4100));
		assertEquals(List.of(), channels.select("", 5000));
		assertEquals("EDE", channels.own());
	}

	@Test
	void unknownOwnChannelStaysPut() {
		Channels channels = new Channels();
		assertEquals(List.of("join lfg"), channels.select("lfg", 0));
		assertEquals(List.of(), channels.select("", 1000));
		assertNull(channels.joined());
	}

	@Test
	void serverRepliesTheyDidntAskForAreThePlayers() {
		Channels channels = new Channels();
		// e.g. a clickable channel link in chat
		assertFalse(channels.serverLine("Joined channel zh.", 0));
		assertEquals("zh", channels.own());
		assertFalse(channels.serverLine("Left channel zh.", 10));
		assertNull(channels.own());
		assertFalse(channels.serverLine("<g> qwertfox » Joined channel g.", 20));
		assertFalse(channels.serverLine("You are not listening in that channel.", 30));
	}

	@Test
	void lateRepliesShow() {
		Channels channels = new Channels();
		channels.select("g", 0);
		assertFalse(channels.serverLine("Joined channel g.", Channels.EXPECT_MILLIS + 1));
		assertEquals("g", channels.own());
	}

	@Test
	void typedJoinEndsCakeChatsSwitch() {
		Channels channels = new Channels();
		channels.reset("EDE");
		channels.select("tr", 0);
		channels.typed("join lfg");
		assertEquals("lfg", channels.own());
		assertEquals(List.of(), channels.select("", 10));
		channels.typed("leave lfg");
		assertNull(channels.own());
		channels.typed("join");
		assertNull(channels.own());
	}
}
