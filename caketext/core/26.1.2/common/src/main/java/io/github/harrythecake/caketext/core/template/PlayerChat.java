package io.github.harrythecake.caketext.core.template;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * Is a chat line a player talking, and where does what they said start? Servers often send player chat as plain
 * system messages, so this looks at the text:
 * <ul>
 *   <li>a name from the tab list, with only tags/ranks/icons before it and a separator after it
 *       ({@code [g] ★ Harry_BC: hi}, {@code <Harry_BC> hi}, {@code Harry_BC » hi}, {@code Harry_BC whispers to you: hi})</li>
 *   <li>any player-shaped name in the vanilla {@code <name> } form or in {@code From name: } / {@code To name: }</li>
 *   <li>any player-shaped name before {@code »}, after an optional {@code <channel>} and {@code [tags]}: Monumenta's
 *       {@code <lfg> [AB] SomePlayer » text}, where speakers are often on another shard and so not in the tab list</li>
 * </ul>
 * Everything before the returned index (tags, rank, name, separator) is the prefix; it is never translated.
 * Names of players who aren't in the tab list are only caught in the vanilla, {@code »} and whisper forms.
 */
public final class PlayerChat {
	private static final Pattern SEPARATOR = Pattern.compile("^(?:>|\\s*(?:[:»>|]|whispers(?: to you)?:))\\s*");
	private static final Pattern VANILLA = Pattern.compile("^((?:\\[[^\\]]{0,32}]\\s*)*)<([A-Za-z0-9_]{3,16})>\\s");
	/** {@code <g> [TAG] Name » text}: chat plugins (Monumenta) mark the speaker with » instead of a colon. */
	private static final Pattern ARROW = Pattern.compile("^(?:<[^>]{1,24}>\\s*)?(?:\\[[^\\]]{0,24}]\\s*)*[A-Za-z0-9_]{3,16}\\s*»\\s*");
	private static final Pattern WHISPER = Pattern.compile("^(?:From|To) [A-Za-z0-9_]{3,16}:\\s|^[A-Za-z0-9_]{3,16} whispers(?: to you)?:\\s");
	private static final Pattern BRACKETED = Pattern.compile("\\[[^\\]]*]|\\([^)]*\\)|<[^>]*>");
	private static final Pattern WORD = Pattern.compile("\\p{L}+");

	private PlayerChat() {
	}

	/** Index in {@code line} where the player's message starts, or -1 if it doesn't look like player chat. */
	public static int bodyStart(String line) {
		return bodyStart(line, PlayerNames.pattern());
	}

	public static int bodyStart(String line, @Nullable Pattern names) {
		if (names != null) {
			Matcher m = names.matcher(line);
			while (m.find()) {
				String prefix = line.substring(0, m.start());
				if (!tagsOnly(prefix)) {
					continue;
				}
				Matcher sep = SEPARATOR.matcher(line.substring(m.end()));
				if (sep.find()) {
					return m.end() + sep.end();
				}
			}
		}
		// before the vanilla form: in "<lfg> [AB] Name » hi" the <lfg> is a channel, not a speaker
		Matcher arrow = ARROW.matcher(line);
		if (arrow.find()) {
			return arrow.end();
		}
		Matcher vanilla = VANILLA.matcher(line);
		if (vanilla.find()) {
			return vanilla.end();
		}
		Matcher whisper = WHISPER.matcher(line);
		return whisper.find() ? whisper.end() : -1;
	}

	/** Before a name: channel tags, a rank word at most, icons and symbols. */
	private static boolean tagsOnly(String prefix) {
		if (prefix.length() > 48) {
			return false;
		}
		Matcher words = WORD.matcher(BRACKETED.matcher(prefix).replaceAll(""));
		int count = 0;
		while (words.find()) {
			if (++count > 1 || words.group().length() > 12) {
				return false;
			}
		}
		return true;
	}
}
