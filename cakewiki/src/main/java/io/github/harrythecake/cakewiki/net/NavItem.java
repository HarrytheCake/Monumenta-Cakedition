package io.github.harrythecake.cakewiki.net;

import java.util.List;

/**
 * One entry of a site's contents (a VuePress sidebar): a page ({@code key}, as the site names pages), a group
 * ({@code children}), or both. A group that isn't {@code collapsible} is always open (a heading such as "Contents").
 */
public record NavItem(String text, String key, boolean collapsible, List<NavItem> children) {
	public boolean group() {
		return !children.isEmpty();
	}
}
