package io.github.harrythecake.cakechat;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's guild channel, read from Monumenta's tab list. Plain Java, so tests can run it.
 *
 * <p>Monumenta's tab list has a section headed "Guild" listing the player's guild members as
 * {@code <shard> [TAG] Name}; the guild's chat channel is that tag ({@code /join TAG}). Guild tags are 2–5 letters,
 * upper or lower case. A tab whose channel is {@link #PLACEHOLDER} joins it. The tab list is only read when the
 * player clicks such a tab.
 *
 * <p>Lenient on purpose (the first strict version found nothing in game, 2026-11-03): formatting codes and
 * private-use glyphs are ignored, the header is any line without a tag whose letters start with "guild" ("Guild",
 * "GUILD:", "Guild (3)"), blank lines under it are skipped, and the tag may sit anywhere on the member's line.
 * Without a Guild section, the player's own line gives the tag.
 */
public final class GuildTag {
	/** A tab channel that stands for the player's guild channel. */
	public static final String PLACEHOLDER = "{guild}";
	private static final String HEADER = "guild";
	/** A guild tag in square brackets: 2–5 letters, upper or lower case. */
	private static final Pattern TAG = Pattern.compile("\\[([A-Za-z]{2,5})]");
	/** § formatting codes and private-use glyphs (resource-pack icons). */
	private static final Pattern NOISE = Pattern.compile("§.|[\\uE000-\\uF8FF]");

	private GuildTag() {
	}

	public static boolean isPlaceholder(String channel) {
		return channel.strip().equalsIgnoreCase(PLACEHOLDER);
	}

	/**
	 * The guild tag, given the tab list's lines in the order it shows them: the first tag under the "Guild" header,
	 * else the tag on the line ending in {@code ownName}. Null when neither is there (not in a guild, not on
	 * Monumenta, or the tab list hasn't arrived yet).
	 */
	@Nullable
	public static String find(List<String> tabList, @Nullable String ownName) {
		boolean inGuild = false;
		for (String raw : tabList) {
			String line = clean(raw);
			if (!inGuild) {
				inGuild = isHeader(line);
				continue;
			}
			if (line.isEmpty()) {
				continue;
			}
			Matcher m = TAG.matcher(line);
			if (m.find()) {
				return m.group(1);
			}
			// the next header: the Guild section had no members
			break;
		}
		if (ownName == null || ownName.isBlank()) {
			return null;
		}
		Pattern own = Pattern.compile("(?i)(^|[^A-Za-z0-9_])" + Pattern.quote(ownName.strip()) + "$");
		for (String raw : tabList) {
			String line = clean(raw);
			if (own.matcher(line).find()) {
				Matcher m = TAG.matcher(line);
				if (m.find()) {
					return m.group(1);
				}
			}
		}
		return null;
	}

	static String clean(String line) {
		return NOISE.matcher(line).replaceAll("").strip();
	}

	private static boolean isHeader(String line) {
		StringBuilder letters = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (Character.isLetter(c)) {
				letters.append(c);
			}
		}
		return letters.toString().toLowerCase(Locale.ROOT).startsWith(HEADER) && !TAG.matcher(line).find();
	}

	/** A tab-list line for {@code /cakechat tablist}: characters outside printable ASCII shown as {@code \\uXXXX}. */
	public static String escape(String line) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c >= 0x20 && c < 0x7F) {
				out.append(c);
			} else {
				out.append(String.format("\\u%04X", (int) c));
			}
		}
		return out.toString();
	}
}
