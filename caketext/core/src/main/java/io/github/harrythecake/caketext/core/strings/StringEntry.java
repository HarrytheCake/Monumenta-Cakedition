package io.github.harrythecake.caketext.core.strings;

import java.util.List;
import java.util.Map;

/**
 * One string in a strings file. The collector fills {@code src}, {@code contexts} and the counters;
 * translators fill {@code dst} (and {@code scope} when one source text means different things in different places).
 */
public final class StringEntry {
	/** Source template (see {@link io.github.harrythecake.caketext.core.template.TextTemplate}). */
	public String src;
	/** Translated template; null or empty = not translated yet. */
	public String dst;
	/**
	 * Where this translation applies. Keys: {@code surface}, {@code server}, {@code dimension}, {@code shard},
	 * {@code area} (all exact, case-insensitive) and {@code box} ({@code "x1 y1 z1 x2 y2 z2"}, inclusive).
	 * All keys must match; the most specific matching entry wins. Absent = everywhere.
	 */
	public Map<String, String> scope;
	/** Who translated it: absent or {@code "human"} for people, otherwise the model, e.g. {@code "opus-mt-en-zh int8"}. */
	public String engine;
	/** Free text for translators. */
	public String note;
	/** Where the collector saw it (surface, server, dimension, shard, area, pos). Informational. */
	public List<Map<String, Object>> contexts;
	/** How often it was seen (repeats within a few seconds count once). */
	public Long count;
	/** First and last time seen, ISO-8601. */
	public String first;
	public String last;

	public StringEntry() {
	}

	public StringEntry(String src, String dst) {
		this.src = src;
		this.dst = dst;
	}

	public boolean translated() {
		return dst != null && !dst.isEmpty();
	}
}
