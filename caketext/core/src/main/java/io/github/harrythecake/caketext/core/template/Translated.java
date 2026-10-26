package io.github.harrythecake.caketext.core.template;

import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;
import net.minecraft.text.Text;

/**
 * Remembers which {@link Text} objects the translator produced, so the collector never collects a translation
 * as if it were game text. Weak and by identity: entries vanish with the text.
 */
public final class Translated {
	private static final ConcurrentMap<Text, Boolean> TEXTS = new MapMaker().weakKeys().makeMap();

	private Translated() {
	}

	public static <T extends Text> T mark(T text) {
		TEXTS.put(text, Boolean.TRUE);
		return text;
	}

	public static boolean is(Text text) {
		return text != null && TEXTS.containsKey(text);
	}
}
