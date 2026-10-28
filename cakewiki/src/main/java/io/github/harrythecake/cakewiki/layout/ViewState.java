package io.github.harrythecake.cakewiki.layout;

import io.github.harrythecake.cakewiki.doc.Block.Collapsible;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What the reader has opened or picked on one page: collapsibles and tabs (tabbers, infobox picture tabs).
 * Keyed by the block object itself, so two identical sections on a page keep separate states.
 */
public final class ViewState {
	private final Map<Object, Boolean> open = new IdentityHashMap<>();
	private final Map<Object, Integer> tabs = new IdentityHashMap<>();

	public boolean isOpen(Collapsible c) {
		return open.getOrDefault(c, !c.collapsed());
	}

	public void toggle(Collapsible c) {
		open.put(c, !isOpen(c));
	}

	/** The selected tab of {@code key}, 0 by default or when out of range. */
	public int tab(Object key, int count) {
		int t = tabs.getOrDefault(key, 0);
		return t >= 0 && t < count ? t : 0;
	}

	public void select(Object key, int index) {
		tabs.put(key, index);
	}

	/** Applies a click on a {@link Laid.Control}. */
	public void apply(Laid.Control control) {
		if (control.value() < 0 && control.key() instanceof Collapsible c) toggle(c);
		else select(control.key(), control.value());
	}
}
