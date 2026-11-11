package io.github.harrythecake.caketext.translator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import io.github.harrythecake.caketext.core.template.TextTemplate;
import io.github.harrythecake.caketext.core.template.TextTemplate.Token;
import org.jetbrains.annotations.Nullable;

/**
 * Translates templates nobody has translated yet with the active {@link TranslationEngine}, one at a time on a
 * background thread, newest first (what is on screen now matters most).
 *
 * <h2>Cache</h2>
 * Server text goes into the dictionary's machine cache and is saved to
 * {@code translations/<lang>/_machine/<engine id>.json.gz} (one file per engine, so switching engines never mixes
 * their results): 30 s after the last save when results keep coming, after 30 s without new results, on leaving a
 * server and on quitting. Player chat is kept in memory only.
 *
 * <h2>How a template is fed to the engine</h2>
 * The template is split at its style markers ({@code {cN}}) and each piece is translated on its own, so colours stay
 * on the right words. Inside a piece, {@code {~}} and {@code {n}} become stand-in numbers (7301, 7302…), which the
 * engine copies through; each must come back exactly once, or the piece is retried as the separate chunks between
 * placeholders. Private-use characters (resource-pack icons) are never sent.
 */
public final class MachineTranslator {
	private static final int MAX_QUEUE = 300;
	private static final int SENTINEL = 7301;
	private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
	private static final int CHAT_CACHE = 1000;
	private static final long SAVE_MS = 30_000;

	private final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
	private final Set<String> queued = ConcurrentHashMap.newKeySet();
	private final Set<String> failed = ConcurrentHashMap.newKeySet();
	/** Player chat: translated like the rest, but kept in memory only (other people's words never go to disk). */
	private final Set<String> chatJobs = ConcurrentHashMap.newKeySet();
	/** Style indices to copy unchanged, per queued template (item links). */
	private final Map<String, Set<Integer>> keepStyles = new ConcurrentHashMap<>();
	private final Map<String, String> chatResults = java.util.Collections.synchronizedMap(
			new java.util.LinkedHashMap<>(256, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
					return size() > CHAT_CACHE;
				}
			});
	private final Supplier<TranslationEngine> engines;
	private final Consumer<String> onDone;
	private volatile Dictionary dictionary;
	private volatile TranslatorConfig config;
	private volatile String engineId;
	/** Cache files with unsaved results. */
	private final Set<String> dirtyFiles = ConcurrentHashMap.newKeySet();
	private volatile long lastSave = System.currentTimeMillis();

	public MachineTranslator(Supplier<TranslationEngine> engines, Consumer<String> onDone) {
		this.engines = engines;
		this.onDone = onDone;
		Thread t = new Thread(this::run, "CakeText-translate");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	/** New dictionary, settings or engine: forget the queue (and this session's chat results if the language or engine changed). */
	public void use(Dictionary dictionary, TranslatorConfig config) {
		TranslationEngine engine = engines.get();
		boolean changed = this.config != null && (!this.config.language.equals(config.language) || !engine.id().equals(engineId));
		this.dictionary = dictionary;
		this.config = config;
		this.engineId = engine.id();
		dictionary.setActiveMachineFile(cacheName(engine));
		queue.clear();
		queued.clear();
		chatJobs.clear();
		keepStyles.clear();
		failed.clear();
		if (changed) {
			chatResults.clear();
		}
	}

	/**
	 * Queues {@code template} unless it's queued, failed before, or already translated. {@code playerChat} results stay
	 * in memory ({@link #chatResult}); others go to the dictionary's machine cache and are saved.
	 * True if a result will arrive (queued now or before).
	 */
	public boolean submit(String template, boolean playerChat) {
		return submit(template, playerChat, Set.of());
	}

	/** Like {@link #submit(String, boolean)}; runs of the styles in {@code keep} (item links) are copied unchanged. */
	public boolean submit(String template, boolean playerChat, Set<Integer> keep) {
		if (failed.contains(template)) {
			return false;
		}
		if (playerChat ? chatResults.containsKey(template) : dictionary.hasMachine(template)) {
			return false;
		}
		if (playerChat) {
			chatJobs.add(template);
		}
		if (!keep.isEmpty()) {
			keepStyles.put(template, keep);
		}
		if (!queued.add(template)) {
			return true;
		}
		queue.offerFirst(template);
		while (queue.size() > MAX_QUEUE) {
			String dropped = queue.pollLast();
			if (dropped != null) {
				queued.remove(dropped);
				chatJobs.remove(dropped);
			}
		}
		return true;
	}

	/** A player-chat translation from this session, or null. */
	public @Nullable String chatResult(String template) {
		return chatResults.get(template);
	}

	public int pending() {
		return queue.size();
	}

	private void run() {
		while (true) {
			String template;
			try {
				template = queue.poll(SAVE_MS, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				return;
			}
			if (template == null || System.currentTimeMillis() - lastSave > SAVE_MS) {
				save();
			}
			if (template != null) {
				process(template);
			}
		}
	}

	private void process(String template) {
		TranslationEngine engine = engines.get();
		Dictionary dict = dictionary;
		TranslatorConfig cfg = config;
		boolean chat = chatJobs.contains(template);
		try {
			if (!engine.ready() || dict == null || cfg == null) {
				return;
			}
			String dst = translate(template, keepStyles.getOrDefault(template, Set.of()), s -> {
				try {
					return engine.translate(s, cfg);
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new EngineException(e);
				}
			});
			if (dst == null) {
				failed.add(template);
			} else if (chat) {
				chatResults.put(template, dst);
			} else {
				String file = cacheName(engine);
				dict.putMachine(template, dst, engine.label(), file);
				dirtyFiles.add(file);
			}
		} catch (EngineException e) {
			// the engine itself failed (server down, timeout): not this line's fault, so it can be tried again later
			CakeText.LOG.warn("{} failed: {}", engine.label(), e.getCause().toString());
		} catch (Throwable t) {
			failed.add(template);
			CakeText.LOG.warn("Machine translation failed for '{}'", template, t);
		} finally {
			queued.remove(template);
			chatJobs.remove(template);
			keepStyles.remove(template);
			// also when nothing came of it: lets waiting chat lines stop waiting
			onDone.accept(template);
		}
	}

	private static final class EngineException extends RuntimeException {
		EngineException(Exception cause) {
			super(cause);
		}
	}

	static String cacheName(TranslationEngine engine) {
		return Dictionary.MACHINE_DIR + "/" + engine.id() + ".json.gz";
	}

	/** Forgets the active engine's cache and deletes its file, so everything is translated again. */
	public synchronized void clearCache() {
		Dictionary dict = dictionary;
		TranslatorConfig cfg = config;
		TranslationEngine engine = engines.get();
		if (dict == null || cfg == null) {
			return;
		}
		dict.clearActiveMachine();
		dirtyFiles.remove(cacheName(engine));
		chatResults.clear();
		failed.clear();
		try {
			java.nio.file.Files.deleteIfExists(CakeText.translations(cfg.language).resolve(cacheName(engine)));
		} catch (IOException e) {
			CakeText.LOG.warn("Could not delete the machine cache", e);
		}
	}

	/** Writes the machine cache files that changed. */
	public synchronized void save() {
		lastSave = System.currentTimeMillis();
		Dictionary dict = dictionary;
		TranslatorConfig cfg = config;
		if (dirtyFiles.isEmpty() || dict == null || cfg == null) {
			return;
		}
		for (String name : List.copyOf(dirtyFiles)) {
			dirtyFiles.remove(name);
			StringsFile file = new StringsFile();
			file.lang = cfg.language;
			file.entries = new ArrayList<>(dict.machineEntries(name));
			Path path = CakeText.translations(cfg.language).resolve(name);
			try {
				file.write(path, true);
			} catch (IOException e) {
				dirtyFiles.add(name);
				CakeText.LOG.warn("Could not save {}", path, e);
			}
		}
	}

	// ---- template → model → template ----

	/** The translated template, or null if the model output can't be mapped back safely. */
	static @Nullable String translate(String template, java.util.function.Function<String, String> model) {
		return translate(template, Set.of(), model);
	}

	/** Runs of the styles in {@code keep} (item links) are copied unchanged. */
	static @Nullable String translate(String template, Set<Integer> keep, java.util.function.Function<String, String> model) {
		StringBuilder dst = new StringBuilder();
		List<Token> piece = new ArrayList<>();
		int[] counters = {0, 0};
		// a template without {cN} markers is all one style: style 1
		int style = 1;
		for (Token token : TextTemplate.parse(template)) {
			if (token instanceof Token.StyleRef ref) {
				if (!appendPiece(piece, counters, keep.contains(style) ? null : model, dst)) {
					return null;
				}
				piece.clear();
				style = ref.index();
				dst.append("{c").append(ref.index()).append('}');
			} else {
				piece.add(token);
			}
		}
		return appendPiece(piece, counters, keep.contains(style) ? null : model, dst) ? dst.toString() : null;
	}

	/** Translates one style run; {@code counters} numbers the placeholders across the whole template. */
	private static boolean appendPiece(List<Token> piece, int[] counters, java.util.function.Function<String, String> model,
			StringBuilder dst) {
		// placeholder tokens of this piece, with their global index
		List<String> placeholders = new ArrayList<>();
		StringBuilder input = new StringBuilder();
		for (Token t : piece) {
			if (t instanceof Token.Literal lit) {
				input.append(lit.text());
			} else if (t instanceof Token.NumberRef ref) {
				int index = ref.index() > 0 ? ref.index() : ++counters[0];
				input.append(SENTINEL + placeholders.size());
				placeholders.add("{~" + index + "}");
			} else if (t instanceof Token.NameRef ref) {
				int index = ref.index() > 0 ? ref.index() : ++counters[1];
				input.append(SENTINEL + placeholders.size());
				placeholders.add("{n" + index + "}");
			}
		}
		String text = input.toString();
		if (model == null || !LATIN.matcher(text).find()) {
			// nothing to translate: keep the piece as it was
			for (Token t : piece) {
				dst.append(t instanceof Token.Literal lit ? escape(lit.text())
						: placeholders.remove(0));
			}
			return true;
		}
		String mapped = restore(translateKeepingIcons(text, model), placeholders);
		if (mapped == null) {
			// the model lost or doubled a stand-in: translate the text between placeholders separately,
			// keeping the placeholders in source order
			StringBuilder fallback = new StringBuilder();
			int p = 0;
			for (Token t : piece) {
				if (t instanceof Token.Literal lit) {
					String s = lit.text();
					fallback.append(escape(LATIN.matcher(s).find() ? translateKeepingIcons(s, model) : s));
				} else {
					fallback.append(placeholders.get(p++));
				}
			}
			mapped = fallback.toString();
		}
		dst.append(mapped);
		return true;
	}

	/** Puts the placeholders back in place of the stand-ins; null if one is missing or repeated. */
	private static @Nullable String restore(String out, List<String> placeholders) {
		String escaped = escape(out);
		for (int i = 0; i < placeholders.size(); i++) {
			String s = String.valueOf(SENTINEL + i);
			Matcher m = Pattern.compile("(?<!\\d)" + s + "(?!\\d)").matcher(escaped);
			if (!m.find()) {
				return null;
			}
			int at = m.start();
			if (m.find()) {
				return null;
			}
			escaped = escaped.substring(0, at) + placeholders.get(i) + escaped.substring(at + s.length());
		}
		return escaped;
	}

	/** Translates around private-use characters (resource pack icons), which the model must never see. */
	private static String translateKeepingIcons(String text, java.util.function.Function<String, String> model) {
		StringBuilder out = new StringBuilder();
		StringBuilder chunk = new StringBuilder();
		text.codePoints().forEach(cp -> {
			if (isPrivateUse(cp)) {
				flushChunk(chunk, out, model);
				out.appendCodePoint(cp);
			} else {
				chunk.appendCodePoint(cp);
			}
		});
		flushChunk(chunk, out, model);
		return out.toString();
	}

	private static void flushChunk(StringBuilder chunk, StringBuilder out, java.util.function.Function<String, String> model) {
		if (chunk.isEmpty()) {
			return;
		}
		String s = chunk.toString();
		out.append(LATIN.matcher(s).find() ? edgeSpaces(s, ChineseText.tidy(model.apply(s.strip()))) : s);
		chunk.setLength(0);
	}

	private static boolean isPrivateUse(int cp) {
		return (cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000;
	}

	/** The model strips leading and trailing spaces; keep the source's, since they separate style runs. */
	private static String edgeSpaces(String source, String translated) {
		String lead = source.substring(0, source.length() - source.stripLeading().length());
		String trail = source.substring(source.stripTrailing().length());
		return lead + translated.strip() + trail;
	}

	private static String escape(String s) {
		return s.replace("{", "{{").replace("}", "}}");
	}
}
