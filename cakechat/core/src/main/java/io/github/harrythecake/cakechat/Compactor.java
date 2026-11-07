package io.github.harrythecake.cakechat;

import java.util.List;

/** Finds an earlier copy of a new chat line, per {@link ChatConfig.Compact}. Plain Java, so tests can run it. */
public final class Compactor {
	private Compactor() {
	}

	/** What the matcher needs to know about a stored line. */
	public interface Keyed {
		/** The line's text (plus its styles when {@code compareStyle} is on), as it arrived. */
		String compactKey();

		/** When it arrived, in epoch milliseconds. */
		long arrivedMillis();
	}

	/**
	 * Index in {@code newestFirst} of the line the new one repeats, or -1.
	 * LATEST looks at the {@code searchLines} newest lines; WITHIN_SECONDS at every line from the last {@code seconds}.
	 */
	public static int findDuplicate(List<? extends Keyed> newestFirst, String key, long nowMillis, ChatConfig.Compact settings) {
		if (!settings.enabled || key.isBlank()) {
			return -1;
		}
		if (settings.mode == ChatConfig.Compact.Mode.WITHIN_SECONDS) {
			long oldest = nowMillis - settings.seconds * 1000L;
			for (int i = 0; i < newestFirst.size(); i++) {
				Keyed line = newestFirst.get(i);
				if (line.arrivedMillis() < oldest) {
					return -1;
				}
				if (line.compactKey().equals(key)) {
					return i;
				}
			}
			return -1;
		}
		int limit = Math.min(Math.max(settings.searchLines, 1), newestFirst.size());
		for (int i = 0; i < limit; i++) {
			if (newestFirst.get(i).compactKey().equals(key)) {
				return i;
			}
		}
		return -1;
	}

	/** The counter text appended to a compacted line. */
	public static String counter(String format, int count) {
		return format.replace("{count}", Integer.toString(count));
	}
}
