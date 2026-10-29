package io.github.harrythecake.caketext.core.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * A line of game text with its variable parts taken out, so one entry covers every value.
 *
 * <p>Placeholders in a template:
 * <ul>
 *   <li>{@code {~}} a number ({@code 12}, {@code 1,250}, {@code 0.5})</li>
 *   <li>{@code {n}} a player name (names are never stored)</li>
 *   <li>{@code {c1}}, {@code {c2}}… the Nth distinct style of the line, by first appearance. Only written
 *       when the line has more than one style.</li>
 *   <li>{@code {{} and {@code }}} a literal brace</li>
 * </ul>
 * A translation may reorder values with {@code {~2}} / {@code {n1}}; plain {@code {~}} takes the next one.
 */
public final class TextTemplate {
	private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
	private static final Pattern TOKEN = Pattern.compile("c(\\d+)|~(\\d*)|n(\\d*)");

	/** One style run: which style ({@code {cN}} index, 1-based) and its template text. */
	public record Run(int style, String template) {
	}

	private final String template;
	private final String original;
	private final List<Style> styles;
	private final List<Run> runs;
	private final List<String> numbers;
	private final List<String> names;

	private TextTemplate(String template, String original, List<Style> styles, List<Run> runs,
			List<String> numbers, List<String> names) {
		this.template = template;
		this.original = original;
		this.styles = styles;
		this.runs = runs;
		this.numbers = numbers;
		this.names = names;
	}

	/** Template of {@code text}, with the currently known player names taken out. */
	public static TextTemplate of(Text text) {
		return of(text, PlayerNames.pattern());
	}

	public static TextTemplate of(Text text, @Nullable Pattern playerNames) {
		List<Style> runStyles = new ArrayList<>();
		List<StringBuilder> runTexts = new ArrayList<>();
		text.visit((style, s) -> {
			legacyCodes(style, s, (st, part) -> {
				int last = runStyles.size() - 1;
				if (last >= 0 && runStyles.get(last).equals(st)) {
					runTexts.get(last).append(part);
				} else {
					runStyles.add(st);
					runTexts.add(new StringBuilder(part));
				}
			});
			return Optional.empty();
		}, Style.EMPTY);

		List<Style> styles = new ArrayList<>();
		for (Style style : runStyles) {
			if (!styles.contains(style)) {
				styles.add(style);
			}
		}
		List<String> numbers = new ArrayList<>();
		List<String> names = new ArrayList<>();
		List<Run> runs = new ArrayList<>();
		StringBuilder template = new StringBuilder();
		StringBuilder original = new StringBuilder();
		for (int i = 0; i < runStyles.size(); i++) {
			String s = runTexts.get(i).toString();
			original.append(s);
			int style = styles.indexOf(runStyles.get(i)) + 1;
			String piece = templatize(s, withStyleName(playerNames, runStyles.get(i)), numbers, names);
			runs.add(new Run(style, piece));
			if (styles.size() > 1) {
				template.append("{c").append(style).append('}');
			}
			template.append(piece);
		}
		return new TextTemplate(template.toString(), original.toString(), List.copyOf(styles), List.copyOf(runs),
				List.copyOf(numbers), List.copyOf(names));
	}

	/**
	 * Old-style {@code §} codes inside literal text (servers still use them, e.g. {@code §6City Guide}) become real
	 * styles, so the {@code 6} isn't taken for a number. A colour code clears earlier § modifiers; {@code §r} goes back
	 * to the component's own style. Unknown codes are kept as text.
	 */
	private static void legacyCodes(Style base, String s, java.util.function.BiConsumer<Style, String> out) {
		if (s.isEmpty()) {
			return;
		}
		if (s.indexOf('§') < 0) {
			out.accept(base, s);
			return;
		}
		Style current = base;
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			Formatting f = c == '§' && i + 1 < s.length() ? Formatting.byCode(s.charAt(i + 1)) : null;
			if (f == null) {
				buf.append(c);
				continue;
			}
			if (!buf.isEmpty()) {
				out.accept(current, buf.toString());
				buf.setLength(0);
			}
			if (f == Formatting.RESET) {
				current = base;
			} else if (f.isColor()) {
				current = base.withColor(f);
			} else {
				current = current.withFormatting(f);
			}
			i++;
		}
		if (!buf.isEmpty()) {
			out.accept(current, buf.toString());
		}
	}

	/** Style indices (1-based, as in {@code {cN}}) of item links: text with an item hover, i.e. an item's name. */
	public java.util.Set<Integer> itemStyles() {
		java.util.Set<Integer> out = new java.util.HashSet<>();
		for (int i = 0; i < styles.size(); i++) {
			net.minecraft.text.HoverEvent h = styles.get(i).getHoverEvent();
			if (h != null && h.getAction() == net.minecraft.text.HoverEvent.Action.SHOW_ITEM) {
				out.add(i + 1);
			}
		}
		return out;
	}

	private static final Pattern MESSAGE_COMMAND = Pattern.compile("/(?:msg|tell|w|whisper|m|pm|dm)\\s+(\\S+).*",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The player name a style points at, or null: servers mark names with a player hover, a shift-click insertion or
	 * a click that starts a private message. This catches players who aren't in the tab list (other shards).
	 */
	static @Nullable String styleName(Style style) {
		net.minecraft.text.HoverEvent hover = style.getHoverEvent();
		net.minecraft.text.HoverEvent.EntityContent entity = hover == null ? null
				: hover.getValue(net.minecraft.text.HoverEvent.Action.SHOW_ENTITY);
		if (entity != null && entity.entityType == net.minecraft.entity.EntityType.PLAYER && entity.name.isPresent()
				&& PlayerNames.valid(entity.name.get().getString())) {
			return entity.name.get().getString();
		}
		if (PlayerNames.valid(style.getInsertion())) {
			return style.getInsertion();
		}
		net.minecraft.text.ClickEvent click = style.getClickEvent();
		if (click != null) {
			Matcher m = MESSAGE_COMMAND.matcher(click.getValue().trim());
			if (m.matches() && PlayerNames.valid(m.group(1))) {
				return m.group(1);
			}
		}
		return null;
	}

	/** {@code playerNames} plus the name this run's style points at, if any. */
	private static @Nullable Pattern withStyleName(@Nullable Pattern playerNames, Style style) {
		String name = styleName(style);
		if (name == null || playerNames != null && playerNames.matcher(name).matches()) {
			return playerNames;
		}
		String one = "(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])";
		return Pattern.compile(playerNames == null ? one : one + "|" + playerNames.pattern());
	}

	private static String templatize(String s, @Nullable Pattern playerNames, List<String> numbers, List<String> names) {
		StringBuilder out = new StringBuilder(s.length() + 8);
		int pos = 0;
		if (playerNames != null) {
			Matcher m = playerNames.matcher(s);
			while (m.find()) {
				literal(s.substring(pos, m.start()), out, numbers);
				out.append("{n}");
				names.add(m.group());
				pos = m.end();
			}
		}
		literal(s.substring(pos), out, numbers);
		return out.toString();
	}

	private static void literal(String s, StringBuilder out, List<String> numbers) {
		Matcher m = NUMBER.matcher(s);
		int pos = 0;
		while (m.find()) {
			escape(s.substring(pos, m.start()), out);
			out.append("{~}");
			numbers.add(m.group());
			pos = m.end();
		}
		escape(s.substring(pos), out);
	}

	private static void escape(String s, StringBuilder out) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '{' || c == '}') {
				out.append(c);
			}
			out.append(c);
		}
	}

	/** The template, e.g. {@code {c1}Damage: {c2}{~}}. This is the dictionary key. */
	public String template() {
		return template;
	}

	/** The line as the game showed it. */
	public String original() {
		return original;
	}

	public List<Run> runs() {
		return runs;
	}

	public List<String> numbers() {
		return numbers;
	}

	public List<String> names() {
		return names;
	}

	/** True when the line has a letter outside placeholders; lines of only numbers and symbols are not worth translating. */
	public boolean hasWords() {
		for (Token token : parse(template)) {
			if (token instanceof Token.Literal literal && literal.text().codePoints().anyMatch(Character::isLetter)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Builds the translated line: {@code dst} is a template in the same placeholder language, filled with
	 * this line's styles, numbers and names. Text before the first {@code {cN}} gets style 1.
	 */
	public MutableText render(String dst) {
		MutableText root = Text.empty();
		Style current = styles.isEmpty() ? Style.EMPTY : styles.get(0);
		StringBuilder buf = new StringBuilder();
		int nextNumber = 0;
		int nextName = 0;
		for (Token token : parse(dst)) {
			if (token instanceof Token.Literal literal) {
				buf.append(literal.text());
			} else if (token instanceof Token.StyleRef ref) {
				Style next = ref.index() >= 1 && ref.index() <= styles.size() ? styles.get(ref.index() - 1) : current;
				if (!next.equals(current)) {
					flush(root, buf, current);
					current = next;
				}
			} else if (token instanceof Token.NumberRef ref) {
				int i = ref.index() > 0 ? ref.index() - 1 : nextNumber++;
				buf.append(i < numbers.size() ? numbers.get(i) : "?");
			} else if (token instanceof Token.NameRef ref) {
				int i = ref.index() > 0 ? ref.index() - 1 : nextName++;
				buf.append(i < names.size() ? names.get(i) : "?");
			}
		}
		flush(root, buf, current);
		return root;
	}

	private static void flush(MutableText root, StringBuilder buf, Style style) {
		if (!buf.isEmpty()) {
			root.append(Text.literal(buf.toString()).setStyle(style));
			buf.setLength(0);
		}
	}

	/** Pieces of a template. */
	public sealed interface Token {
		record Literal(String text) implements Token {
		}

		/** {@code {cN}} */
		record StyleRef(int index) implements Token {
		}

		/** {@code {~}} (index 0 = next) or {@code {~N}} */
		record NumberRef(int index) implements Token {
		}

		/** {@code {n}} (index 0 = next) or {@code {nN}} */
		record NameRef(int index) implements Token {
		}
	}

	/** Splits a template into literals and placeholders; adjacent literal text is joined. */
	public static List<Token> parse(String template) {
		List<Token> tokens = new ArrayList<>();
		StringBuilder buf = new StringBuilder();
		int i = 0;
		while (i < template.length()) {
			char c = template.charAt(i);
			if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
				buf.append('{');
				i += 2;
				continue;
			}
			if (c == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
				buf.append('}');
				i += 2;
				continue;
			}
			if (c == '{') {
				int close = template.indexOf('}', i);
				Matcher m = close < 0 ? null : TOKEN.matcher(template.substring(i + 1, close));
				if (m != null && m.matches()) {
					if (!buf.isEmpty()) {
						tokens.add(new Token.Literal(buf.toString()));
						buf.setLength(0);
					}
					if (m.group(1) != null) {
						tokens.add(new Token.StyleRef(Integer.parseInt(m.group(1))));
					} else if (m.group(2) != null) {
						tokens.add(new Token.NumberRef(m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2))));
					} else {
						tokens.add(new Token.NameRef(m.group(3).isEmpty() ? 0 : Integer.parseInt(m.group(3))));
					}
					i = close + 1;
					continue;
				}
			}
			buf.append(c);
			i++;
		}
		if (!buf.isEmpty()) {
			tokens.add(new Token.Literal(buf.toString()));
		}
		return tokens;
	}

	/** Template text with its placeholders, but the literal braces unescaped: for showing to people. */
	public static String readable(String template) {
		return template.replace("{{", "{").replace("}}", "}");
	}

	@Override
	public String toString() {
		return template;
	}
}
