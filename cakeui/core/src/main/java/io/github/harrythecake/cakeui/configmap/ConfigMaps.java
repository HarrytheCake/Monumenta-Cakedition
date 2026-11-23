package io.github.harrythecake.cakeui.configmap;

import io.github.harrythecake.cakeui.json.Json5;
import io.github.harrythecake.cakeui.json.Json5Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every config map CakeUI knows, by map id (the file name without ".json5", usually the mod id). Sources in order,
 * a later one replacing an earlier map of the same id: each mod's own {@code assets/<modid>/configmaps/} (CakeUI's
 * jar holds the maps for the pack's mods; any mod may ship maps for itself), then {@code config/cakeui/configmaps/}
 * (a pack's), then the active layout's {@code configmaps/}.
 */
public final class ConfigMaps {
	/** One map file's text and where it came from (for problems). */
	public record Source(String id, String name, String text) {
	}

	public record Loaded(Map<String, ConfigMap> maps, List<String> problems) {
	}

	private ConfigMaps() {
	}

	/** Reads the sources in order; later ids replace earlier ones. */
	public static Loaded load(List<Source> sources) {
		Map<String, ConfigMap> maps = new LinkedHashMap<>();
		List<String> problems = new ArrayList<>();
		for (Source s : sources) {
			try {
				ConfigMap.Read r = ConfigMap.read(Json5.parse(s.text, s.name), s.name);
				problems.addAll(r.problems());
				if (r.map() != null) {
					maps.remove(s.id);  // keep the order of the last source
					maps.put(s.id, r.map());
				}
			} catch (Json5Exception e) {
				problems.add(e.getMessage());
			}
		}
		return new Loaded(maps, problems);
	}

	/** The *.json5 files directly in a folder (a real folder or one inside a jar), sorted by name. */
	public static List<Source> scan(Path folder, String label, List<String> problems) {
		List<Source> out = new ArrayList<>();
		if (folder == null || !Files.isDirectory(folder)) {
			return out;
		}
		try (Stream<Path> files = Files.list(folder)) {
			for (Path f : files.sorted().toList()) {
				String name = f.getFileName().toString();
				if (!name.endsWith(".json5") || !Files.isRegularFile(f)) {
					continue;
				}
				try {
					out.add(new Source(name.substring(0, name.length() - ".json5".length()), label + "/" + name,
							Files.readString(f, StandardCharsets.UTF_8)));
				} catch (IOException e) {
					problems.add(label + "/" + name + ": can't read it: " + e.getMessage());
				}
			}
		} catch (IOException e) {
			problems.add(label + ": can't list it: " + e.getMessage());
		}
		return out;
	}
}
