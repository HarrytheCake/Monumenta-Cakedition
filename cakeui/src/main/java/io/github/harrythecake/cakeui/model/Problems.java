package io.github.harrythecake.cakeui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Problems found in a layout, each with its file (relative to the layout folder) and line. */
public final class Problems {
	public record Problem(String file, int line, String message) {
		@Override
		public String toString() {
			return line > 0 ? file + ":" + line + ": " + message : file + ": " + message;
		}
	}

	private static final int MAX = 200;
	private final List<Problem> list = new ArrayList<>();
	private final Set<String> seen = new LinkedHashSet<>();

	public synchronized void add(String file, int line, String message) {
		Problem p = new Problem(file, line, message);
		if (list.size() < MAX && seen.add(p.toString())) {
			list.add(p);
		}
	}

	public synchronized List<Problem> list() {
		return Collections.unmodifiableList(new ArrayList<>(list));
	}

	public synchronized boolean isEmpty() {
		return list.isEmpty();
	}

	public synchronized int size() {
		return list.size();
	}
}
