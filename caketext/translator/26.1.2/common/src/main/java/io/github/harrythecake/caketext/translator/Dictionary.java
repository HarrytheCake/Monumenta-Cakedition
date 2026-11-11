package io.github.harrythecake.caketext.translator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.GameContext;
import io.github.harrythecake.caketext.core.strings.Exchanger;
import io.github.harrythecake.caketext.core.strings.StringEntry;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import org.jetbrains.annotations.Nullable;

/**
 * Template → translation, from every strings file under {@code config/caketext/translations/<lang>/}
 * (any name, {@code .json}, {@code .json.gz} or Gettext {@code .po} as Crowdin returns them, subfolders too). Files under {@code _machine/} are the machine
 * translation caches, one per engine ({@code _machine/<engine id>.json.gz}); only the active engine's is used, and a
 * human translation always beats it.
 *
 * <p>Lookup order for one source text: the scoped human entry matching the most scope keys, then the unscoped
 * human entry, then the machine translation. Whitespace differences are forgiven (a second index with runs of
 * whitespace collapsed), since the same sentence can arrive wrapped differently.
 */
public final class Dictionary {
	public static final String MACHINE_DIR = "_machine";

	/** One translation and where it came from. {@code engine} is null for people. */
	public record Hit(String dst, @Nullable String engine, String file, @Nullable Map<String, String> scope) {
		public boolean machine() {
			return engine != null && !engine.equals("human");
		}
	}

	private final Map<String, List<Hit>> human = new ConcurrentHashMap<>();
	private final Map<String, List<Hit>> humanLoose = new ConcurrentHashMap<>();
	/** Machine caches: file (one per engine) → template → hit. */
	private final Map<String, Map<String, Hit>> machine = new ConcurrentHashMap<>();
	private volatile String activeMachineFile = "";
	private int files;

	public static Dictionary load(String lang) {
		Dictionary dict = new Dictionary();
		Path root = CakeText.translations(lang);
		if (!Files.isDirectory(root)) {
			return dict;
		}
		List<Path> paths;
		try (Stream<Path> walk = Files.walk(root)) {
			paths = walk.filter(Files::isRegularFile).filter(Exchanger::isReadable).sorted().toList();
		} catch (IOException e) {
			CakeText.LOG.warn("Could not list {}", root, e);
			return dict;
		}
		for (Path path : paths) {
			boolean isMachine = root.relativize(path).getName(0).toString().equals(MACHINE_DIR);
			String name = root.relativize(path).toString().replace('\\', '/');
			try {
				StringsFile file = Exchanger.read(path);
				for (StringEntry e : file.entries) {
					if (!e.translated()) {
						continue;
					}
					if (isMachine && e.src.indexOf('§') >= 0) {
						// cached before § colour codes were read as styles: never matches again
						continue;
					}
					if (isMachine) {
						dict.machine.computeIfAbsent(name, k -> new ConcurrentHashMap<>())
								.put(e.src, new Hit(e.dst, e.engine == null ? "machine" : e.engine, name, null));
					} else {
						dict.addHuman(e, name);
					}
				}
				dict.files++;
			} catch (IOException | RuntimeException ex) {
				CakeText.LOG.warn("Skipping unreadable strings file {}", path, ex);
			}
		}
		return dict;
	}

	private void addHuman(StringEntry e, String file) {
		Map<String, String> scope = e.scope == null || e.scope.isEmpty() ? null : Map.copyOf(e.scope);
		String engine = e.engine == null || e.engine.equals("human") ? null : e.engine;
		Hit hit = new Hit(e.dst, engine, file, scope);
		human.computeIfAbsent(e.src, k -> new ArrayList<>()).add(hit);
		humanLoose.computeIfAbsent(loose(e.src), k -> new ArrayList<>()).add(hit);
	}

	static String loose(String template) {
		return template.strip().replaceAll("\\s+", " ");
	}

	/** Best translation for {@code template}; {@code context} is only built when a scoped entry needs it. */
	public @Nullable Hit lookup(String template, Supplier<GameContext> context) {
		List<Hit> hits = human.get(template);
		if (hits == null) {
			hits = humanLoose.get(loose(template));
		}
		if (hits != null) {
			Hit best = pick(hits, context);
			if (best != null) {
				return best;
			}
		}
		return activeMachine().get(template);
	}

	/** Which engine's cache lookups use, as its file name ({@code _machine/<id>.json.gz}). */
	public void setActiveMachineFile(String file) {
		activeMachineFile = file;
	}

	private Map<String, Hit> activeMachine() {
		return machine.getOrDefault(activeMachineFile, Map.of());
	}

	private static @Nullable Hit pick(List<Hit> hits, Supplier<GameContext> context) {
		Hit global = null;
		Hit scoped = null;
		GameContext ctx = null;
		for (Hit h : hits) {
			if (h.scope == null) {
				if (global == null) {
					global = h;
				}
				continue;
			}
			if (ctx == null) {
				ctx = context.get();
			}
			if (ctx.matches(h.scope) && (scoped == null || h.scope.size() > scoped.scope.size())) {
				scoped = h;
			}
		}
		return scoped != null ? scoped : global;
	}

	/** Does any human entry (scoped or not) translate this? Used by the collector's export. */
	public boolean hasHuman(String template) {
		return human.containsKey(template) || humanLoose.containsKey(loose(template));
	}

	public void putMachine(String template, String dst, String engine, String file) {
		machine.computeIfAbsent(file, k -> new ConcurrentHashMap<>()).put(template, new Hit(dst, engine, file, null));
	}

	/** Does the active engine's cache have this? */
	public boolean hasMachine(String template) {
		return activeMachine().containsKey(template);
	}

	/** One machine cache file's entries, sorted, for saving. */
	public List<StringEntry> machineEntries(String file) {
		return machine.getOrDefault(file, Map.of()).entrySet().stream()
				.sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).map(e -> {
					StringEntry s = new StringEntry(e.getKey(), e.getValue().dst());
					s.engine = e.getValue().engine();
					return s;
				}).toList();
	}

	public int files() {
		return files;
	}

	public int humanCount() {
		return human.size();
	}

	/** Forgets the active engine's cache (the caller deletes its file). */
	public void clearActiveMachine() {
		machine.remove(activeMachineFile);
	}

	/** Entries in the active engine's cache. */
	public int machineCount() {
		return activeMachine().size();
	}
}
