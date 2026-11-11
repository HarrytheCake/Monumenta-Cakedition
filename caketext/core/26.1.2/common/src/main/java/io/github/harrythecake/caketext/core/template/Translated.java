package io.github.harrythecake.caketext.core.template;

import java.util.concurrent.ConcurrentMap;
import net.minecraft.network.chat.Component;
import com.google.common.collect.MapMaker;

/**
 * Remembers which {@link Component} objects the translator produced, so the collector never collects a translation
 * as if it were game text. Weak and by identity: entries vanish with the text.
 */
public final class Translated {
	private static final ConcurrentMap<Component, Boolean> TEXTS = new MapMaker().weakKeys().makeMap();

	private Translated() {
	}

	public static <T extends Component> T mark(T text) {
		TEXTS.put(text, Boolean.TRUE);
		return text;
	}

	public static boolean is(Component text) {
		return text != null && TEXTS.containsKey(text);
	}
}
