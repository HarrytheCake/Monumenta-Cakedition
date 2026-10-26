package io.github.harrythecake.caketext.core.strings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Gettext PO/POT file: what Crowdin, Weblate, Poedit and most translation tools exchange.
 * <pre>
 * #. note for translators          (extracted comment)
 * #: reference                      (source reference)
 * #, fuzzy                          (flags)
 * msgctxt "nameplate|valley"        (same English, different meaning)
 * msgid "Back"                      (source text; together with msgctxt, the key)
 * msgstr "向后"                     (translation; empty = not translated)
 * </pre>
 * The entry with an empty msgid is the header ({@code Language}, {@code Content-Type}…). Plural forms are read as
 * their first form; obsolete ({@code #~}) entries are skipped.
 */
public final class PoFile {
	public static final class Entry {
		public String context;
		public String id = "";
		public String str = "";
		/** {@code #.} lines (shown to translators as context). */
		public final List<String> comments = new ArrayList<>();
		/** {@code #:} lines. */
		public final List<String> references = new ArrayList<>();
		/** {@code #,} flags, e.g. {@code fuzzy}. */
		public final Set<String> flags = new LinkedHashSet<>();
	}

	public final Map<String, String> header = new LinkedHashMap<>();
	public final List<Entry> entries = new ArrayList<>();

	// ---- reading ----

	public static PoFile read(Path path) throws IOException {
		return parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
	}

	public static PoFile parse(String text) throws IOException {
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		PoFile po = new PoFile();
		Entry entry = new Entry();
		boolean started = false;
		boolean obsolete = false;
		String keyword = null;
		int lineNo = 0;
		for (String raw : text.split("\r?\n", -1)) {
			lineNo++;
			String line = raw.strip();
			if (line.isEmpty()) {
				if (started) {
					po.add(entry, obsolete);
				}
				entry = new Entry();
				started = false;
				obsolete = false;
				keyword = null;
				continue;
			}
			if (line.startsWith("#~")) {
				obsolete = true;
				started = true;
				continue;
			}
			if (line.startsWith("#")) {
				if (started && keyword != null) {
					// a comment after msgstr starts the next entry (some writers leave no blank line)
					po.add(entry, obsolete);
					entry = new Entry();
					obsolete = false;
					keyword = null;
				}
				started = true;
				if (line.startsWith("#.")) {
					entry.comments.add(line.substring(2).strip());
				} else if (line.startsWith("#:")) {
					entry.references.add(line.substring(2).strip());
				} else if (line.startsWith("#,")) {
					for (String f : line.substring(2).split(",")) {
						if (!f.isBlank()) {
							entry.flags.add(f.strip());
						}
					}
				}
				continue;
			}
			if (line.startsWith("\"")) {
				if (keyword == null) {
					throw new IOException("line " + lineNo + ": text outside an entry");
				}
				append(entry, keyword, unquote(line, lineNo));
				continue;
			}
			int space = line.indexOf(' ');
			if (space < 0) {
				throw new IOException("line " + lineNo + ": can't read '" + line + "'");
			}
			String kw = line.substring(0, space);
			if (keyword != null && (kw.equals("msgctxt") && !keyword.equals("msgctxt")
					|| kw.equals("msgid") && keyword.startsWith("msgstr"))) {
				// msgctxt/msgid right after the previous msgstr: new entry
				po.add(entry, obsolete);
				entry = new Entry();
				obsolete = false;
			}
			keyword = kw;
			started = true;
			set(entry, kw, unquote(line.substring(space + 1).strip(), lineNo));
		}
		if (started) {
			po.add(entry, obsolete);
		}
		return po;
	}

	private void add(Entry entry, boolean obsolete) {
		if (obsolete) {
			return;
		}
		if (entry.id.isEmpty() && entry.context == null) {
			for (String line : entry.str.split("\n")) {
				int colon = line.indexOf(':');
				if (colon > 0) {
					header.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
				}
			}
			return;
		}
		entries.add(entry);
	}

	private static void set(Entry e, String keyword, String value) {
		switch (keyword) {
			case "msgctxt" -> e.context = value;
			case "msgid" -> e.id = value;
			case "msgstr", "msgstr[0]" -> e.str = value;
			default -> {
				// msgid_plural, msgstr[1..]: not used, numbers are placeholders here
			}
		}
	}

	private static void append(Entry e, String keyword, String value) {
		switch (keyword) {
			case "msgctxt" -> e.context = (e.context == null ? "" : e.context) + value;
			case "msgid" -> e.id += value;
			case "msgstr", "msgstr[0]" -> e.str += value;
			default -> {
			}
		}
	}

	private static String unquote(String s, int lineNo) throws IOException {
		if (s.length() < 2 || !s.startsWith("\"") || !s.endsWith("\"")) {
			throw new IOException("line " + lineNo + ": expected a quoted string");
		}
		StringBuilder out = new StringBuilder();
		for (int i = 1; i < s.length() - 1; i++) {
			char c = s.charAt(i);
			if (c != '\\' || i + 1 >= s.length() - 1) {
				out.append(c);
				continue;
			}
			char n = s.charAt(++i);
			switch (n) {
				case 'n' -> out.append('\n');
				case 't' -> out.append('\t');
				case 'r' -> out.append('\r');
				case '"' -> out.append('"');
				case '\\' -> out.append('\\');
				default -> out.append('\\').append(n);
			}
		}
		return out.toString();
	}

	// ---- writing ----

	public void write(Path path) throws IOException {
		StringsFile.writeAtomically(path, toString().getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		StringBuilder head = new StringBuilder();
		header.forEach((k, v) -> head.append(k).append(": ").append(v).append('\n'));
		sb.append("msgid \"\"\n");
		writeString(sb, "msgstr", head.toString());
		for (Entry e : entries) {
			sb.append('\n');
			for (String c : e.comments) {
				for (String line : c.split("\n")) {
					sb.append("#. ").append(line).append('\n');
				}
			}
			for (String r : e.references) {
				sb.append("#: ").append(r).append('\n');
			}
			if (!e.flags.isEmpty()) {
				sb.append("#, ").append(String.join(", ", e.flags)).append('\n');
			}
			if (e.context != null) {
				writeString(sb, "msgctxt", e.context);
			}
			writeString(sb, "msgid", e.id);
			writeString(sb, "msgstr", e.str);
		}
		return sb.toString();
	}

	/** One line, or {@code ""} plus one line per {@code \n} for text with line breaks. */
	private static void writeString(StringBuilder sb, String keyword, String value) {
		if (!value.contains("\n") || value.equals("\n")) {
			sb.append(keyword).append(' ').append(quote(value)).append('\n');
			return;
		}
		sb.append(keyword).append(" \"\"\n");
		int start = 0;
		while (start < value.length()) {
			int nl = value.indexOf('\n', start);
			int end = nl < 0 ? value.length() : nl + 1;
			sb.append(quote(value.substring(start, end))).append('\n');
			start = end;
		}
	}

	private static String quote(String s) {
		StringBuilder out = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\' -> out.append("\\\\");
				case '"' -> out.append("\\\"");
				case '\n' -> out.append("\\n");
				case '\t' -> out.append("\\t");
				case '\r' -> out.append("\\r");
				default -> out.append(c);
			}
		}
		return out.append('"').toString();
	}
}
