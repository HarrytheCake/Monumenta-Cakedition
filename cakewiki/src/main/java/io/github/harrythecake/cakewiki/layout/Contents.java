package io.github.harrythecake.cakewiki.layout;

import io.github.harrythecake.cakewiki.net.NavItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The contents panel's state for one tree of {@link NavItem}s: which groups are open, and the rows that shows.
 * Groups that aren't collapsible are always open; the others start closed, except those on the way to the page shown.
 */
public final class Contents {
	/** One visible line: the entry, how deep it is, and (for a group) whether it is open. */
	public record Row(NavItem item, int depth, boolean open) {}

	private final List<NavItem> roots;
	private final Set<NavItem> open = Collections.newSetFromMap(new IdentityHashMap<>()); // by identity: equal entries can repeat

	public Contents(List<NavItem> roots) {
		this.roots = roots;
	}

	public List<NavItem> roots() {
		return roots;
	}

	public boolean isOpen(NavItem group) {
		return !group.collapsible() || open.contains(group);
	}

	public void toggle(NavItem group) {
		if (!group.collapsible()) return;
		if (!open.remove(group)) open.add(group);
	}

	/** Opens the groups that lead to the page {@code key}; false when the tree doesn't have it. */
	public boolean reveal(String key) {
		return reveal(roots, key);
	}

	private boolean reveal(List<NavItem> items, String key) {
		for (NavItem item : items) {
			if (key.equals(item.key())) return true;
			if (item.group() && reveal(item.children(), key)) {
				if (item.collapsible()) open.add(item);
				return true;
			}
		}
		return false;
	}

	/** The lines shown: every entry whose groups are all open, in order. */
	public List<Row> rows() {
		List<Row> out = new ArrayList<>();
		rows(roots, 0, out);
		return out;
	}

	private void rows(List<NavItem> items, int depth, List<Row> out) {
		for (NavItem item : items) {
			boolean isOpen = item.group() && isOpen(item);
			out.add(new Row(item, depth, isOpen));
			if (isOpen) rows(item.children(), depth + 1, out);
		}
	}
}
