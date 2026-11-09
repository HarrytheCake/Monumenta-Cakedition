package io.github.harrythecake.cakemap.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds block coordinates in chat text: "x: -1234, y: 64, z: 567", "(-1234, 64, 567)",
 * "-1234 64 567", "-1234/64/567". Two numbers ("x -1234 z 567") also count when labelled. y must
 * look like a height (-64…320); unlabelled triples need a separator or a bracket around them, so
 * times and scores rarely match. Pure Java.
 */
public final class ChatCoords {
	public record Found(int x, Integer y, int z, int start, int end) {
	}

	private static final String N = "(-?\\d{1,7})";
	private static final Pattern LABELLED = Pattern.compile(
			"(?i)\\bx\\s*[:=]?\\s*" + N + "\\s*[,;/ ]?\\s*(?:y\\s*[:=]?\\s*" + N + "\\s*[,;/ ]?\\s*)?z\\s*[:=]?\\s*" + N);
	private static final Pattern BRACKETED = Pattern.compile("[(\\[]\\s*" + N + "\\s*[,/ ]\\s*" + N + "\\s*[,/ ]\\s*" + N + "\\s*[)\\]]");
	private static final Pattern PLAIN = Pattern.compile("(?<![\\w.:-])" + N + "\\s*(?:,\\s*|/|\\s+)" + N + "\\s*(?:,\\s*|/|\\s+)" + N + "(?![\\w.:%])");

	private ChatCoords() {
	}

	public static List<Found> find(String text) {
		List<Found> out = new ArrayList<>();
		if (text == null || text.length() < 5) {
			return out;
		}
		Matcher m = LABELLED.matcher(text);
		while (m.find()) {
			Integer y = m.group(2) == null ? null : Integer.parseInt(m.group(2));
			if (y == null || height(y)) {
				out.add(new Found(Integer.parseInt(m.group(1)), y, Integer.parseInt(m.group(3)), m.start(), m.end()));
			}
		}
		if (!out.isEmpty()) {
			return out;
		}
		m = BRACKETED.matcher(text);
		while (m.find()) {
			add(out, m);
		}
		if (!out.isEmpty()) {
			return out;
		}
		m = PLAIN.matcher(text);
		while (m.find()) {
			String all = m.group();
			// "1 2 3" and the like: at least one coordinate must be a real map position
			if (Math.abs(Integer.parseInt(m.group(1))) < 10 && Math.abs(Integer.parseInt(m.group(3))) < 10) {
				continue;
			}
			if (all.contains(",") || all.contains("/") || all.contains("-")) {
				add(out, m);
			}
		}
		return out;
	}

	private static void add(List<Found> out, Matcher m) {
		int y = Integer.parseInt(m.group(2));
		if (height(y)) {
			out.add(new Found(Integer.parseInt(m.group(1)), y, Integer.parseInt(m.group(3)), m.start(), m.end()));
		}
	}

	private static boolean height(int y) {
		return y >= -64 && y <= 320;
	}
}
