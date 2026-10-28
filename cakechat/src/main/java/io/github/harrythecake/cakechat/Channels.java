package io.github.harrythecake.cakechat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Switching the server's chat channel with the tab (Monumenta: {@code /join <name>}). Plain Java, so tests can run it.
 *
 * <p>Selecting a tab with a channel joins it. Selecting a tab without one (All) joins the player's own channel again:
 * the one they last picked themselves ({@code /join} typed, or a "Joined channel X." the server sent without CakeChat
 * asking). CakeChat never sends {@code /leave}: on Monumenta that takes you out of the channel, it doesn't bring
 * back the previous one (found in game 2026-10-29). The server's "Joined channel X." replies to CakeChat's own
 * {@code /join} are hidden from the chat.
 */
public final class Channels {
	/** Monumenta's reply to /join and /leave. */
	private static final Pattern REPLY = Pattern.compile("^(Joined|Left) channel (\\S+)\\.$");
	/** A reply to CakeChat's /join arrives well within this. */
	static final long EXPECT_MILLIS = 10_000;

	private record Expected(String channel, long until) {
	}

	/** The channel CakeChat switched to and hasn't switched away from, or null. */
	@Nullable
	private String joined;
	/** The player's own channel (remembered per server by the caller), or null when not known yet. */
	@Nullable
	private String own;
	private final List<Expected> expected = new ArrayList<>();

	/** Commands (without the slash) to send for selecting a tab with this channel ("" = none). */
	public List<String> select(String channel, long nowMillis) {
		String name = channel.strip();
		if (name.isEmpty()) {
			if (joined == null) {
				return List.of();
			}
			joined = null;
			if (own == null) {
				// nothing known to go back to: stay where the server is
				return List.of();
			}
			return join(own, nowMillis);
		}
		if (name.equalsIgnoreCase(joined)) {
			return List.of();
		}
		joined = name;
		return join(name, nowMillis);
	}

	private List<String> join(String name, long nowMillis) {
		expected.add(new Expected(name, nowMillis + EXPECT_MILLIS));
		return List.of("join " + name);
	}

	/** A command the player typed (without the slash): their own /join sets their channel, and ends CakeChat's switch. */
	public void typed(String command) {
		String[] words = command.strip().split("\\s+");
		if (words.length < 2) {
			return;
		}
		String verb = words[0].toLowerCase(Locale.ROOT);
		if (verb.equals("join")) {
			own = words[1];
			joined = null;
		} else if (verb.equals("leave") && words[1].equalsIgnoreCase(own)) {
			own = null;
		}
	}

	/**
	 * A chat line from the server. Returns true when it is the reply to CakeChat's own {@code /join} and should be
	 * hidden; any other "Joined channel X." is the player's own doing and becomes their channel.
	 */
	public boolean serverLine(String text, long nowMillis) {
		Matcher m = REPLY.matcher(text.strip());
		if (!m.matches()) {
			return false;
		}
		boolean joinedReply = m.group(1).equals("Joined");
		String channel = m.group(2);
		expected.removeIf(e -> e.until < nowMillis);
		if (joinedReply) {
			for (Iterator<Expected> it = expected.iterator(); it.hasNext(); ) {
				if (it.next().channel.equalsIgnoreCase(channel)) {
					it.remove();
					return true;
				}
			}
			own = channel;
			joined = null;
		} else if (channel.equalsIgnoreCase(own)) {
			own = null;
		}
		return false;
	}

	/** Another server: what CakeChat switched to there doesn't carry over; {@code own} is that server's remembered channel. */
	public void reset(@Nullable String own) {
		this.own = own;
		joined = null;
		expected.clear();
	}

	@Nullable
	public String joined() {
		return joined;
	}

	@Nullable
	public String own() {
		return own;
	}
}
