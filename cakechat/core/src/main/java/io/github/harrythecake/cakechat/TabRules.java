package io.github.harrythecake.cakechat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Which tabs a message goes to. Plain Java, so tests can run it.
 *
 * <p>Every tab of every window gets one index (window by window, tab by tab). Within one window, tabs are tried by
 * priority (higher first, then their order); a tab takes a message when its pattern is found in the text. After a
 * taking tab with {@code skipOthers}, only tabs with {@code alwaysAdd} still take it. Windows don't affect each other.
 * A pattern that isn't a valid regex matches nothing.
 */
public final class TabRules {
	private record Rule(int index, @Nullable Pattern pattern, boolean valid, int priority, boolean skipOthers, boolean alwaysAdd) {
		boolean matches(String text) {
			return valid && (pattern == null || pattern.matcher(text).find());
		}
	}

	/** Per window, its rules in the order they're tried. */
	private final List<List<Rule>> windows = new ArrayList<>();
	/** First index of each window's tabs. */
	private final int[] firstIndex;
	private final int size;

	private TabRules(List<ChatConfig.Window> config) {
		firstIndex = new int[config.size()];
		int index = 0;
		for (int w = 0; w < config.size(); w++) {
			firstIndex[w] = index;
			List<Rule> rules = new ArrayList<>();
			for (ChatConfig.Tab tab : config.get(w).tabs) {
				Pattern pattern = null;
				boolean valid = true;
				if (!tab.pattern.isEmpty()) {
					try {
						pattern = Pattern.compile(tab.pattern);
					} catch (PatternSyntaxException e) {
						valid = false;
					}
				}
				rules.add(new Rule(index++, pattern, valid, tab.priority, tab.skipOthers, tab.alwaysAdd));
			}
			// stable: equal priorities keep the tab order
			rules.sort(Comparator.comparingInt(Rule::priority).reversed());
			windows.add(rules);
		}
		size = index;
	}

	public static TabRules of(List<ChatConfig.Window> windows) {
		return new TabRules(windows);
	}

	/** Number of tabs over all windows. */
	public int size() {
		return size;
	}

	/** The index of a window's tab. */
	public int index(int window, int tab) {
		return firstIndex[window] + tab;
	}

	/** The tabs (by index) that take a message with this text. */
	public BitSet route(String text) {
		BitSet tabs = new BitSet(size);
		for (List<Rule> rules : windows) {
			boolean skipping = false;
			for (Rule rule : rules) {
				if ((skipping && !rule.alwaysAdd) || !rule.matches(text)) {
					continue;
				}
				tabs.set(rule.index);
				if (rule.skipOthers) {
					skipping = true;
				}
			}
		}
		return tabs;
	}

	/** Why a pattern isn't a valid regex, or null when it is. */
	@Nullable
	public static String patternError(String pattern) {
		try {
			Pattern.compile(pattern);
			return null;
		} catch (PatternSyntaxException e) {
			return e.getDescription();
		}
	}

	/**
	 * The text tabs are matched against: private-use characters removed, so the icon CakeText Translator puts in
	 * front of a line (U+E100, U+E101) doesn't break a pattern anchored with {@code ^}.
	 */
	public static String routingText(String plain) {
		StringBuilder out = new StringBuilder(plain.length());
		for (int i = 0; i < plain.length(); i++) {
			char c = plain.charAt(i);
			if (Character.getType(c) != Character.PRIVATE_USE) {
				out.append(c);
			}
		}
		return out.toString().stripLeading();
	}
}
