package io.github.harrythecake.cakecosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * config/cakecosmetic/rules.json5: which advancements each background needs (all of them). Pack-owned
 * (ADR 0007): the pack ships it, dev mode writes it. A background with no rule, or an empty one, is free.
 */
public final class Rules {
	static final String FILE = "rules.json5";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** One advancement of a rule; the title is kept so a locked tile can name it while offline. */
	public record Need(String id, String title) {
	}

	/** A background's unlock rule. */
	public record Rule(List<Need> needs, boolean secret) {
		public Rule {
			needs = List.copyOf(needs);
		}

		public boolean isEmpty() {
			return needs.isEmpty();
		}
	}

	private final Map<String, Rule> rules = new TreeMap<>();

	public Rule get(String background) {
		return rules.get(background);
	}

	public Map<String, Rule> all() {
		return java.util.Collections.unmodifiableMap(rules);
	}

	/** Sets or (with no advancements) removes a background's rule. */
	public void put(String background, Rule rule) {
		if (rule == null || rule.isEmpty()) {
			rules.remove(background);
		} else {
			rules.put(background, rule);
		}
	}

	/** Every advancement id any rule names. */
	public Set<String> advancementIds() {
		Set<String> ids = new LinkedHashSet<>();
		for (Rule r : rules.values()) {
			for (Need n : r.needs) {
				ids.add(n.id);
			}
		}
		return ids;
	}

	/**
	 * Backgrounds whose rule is now met by {@code earned} and that aren't in {@code unlocked} yet,
	 * in id order.
	 */
	public List<String> newlyUnlocked(Collection<String> earned, Collection<String> unlocked) {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Rule> e : rules.entrySet()) {
			if (e.getValue().isEmpty() || unlocked.contains(e.getKey())) {
				continue;
			}
			boolean met = true;
			for (Need n : e.getValue().needs) {
				if (!earned.contains(n.id)) {
					met = false;
					break;
				}
			}
			if (met) {
				out.add(e.getKey());
			}
		}
		return out;
	}

	static Rules parse(String text) {
		Rules r = new Rules();
		JValue root;
		try {
			root = Json5.parse(text, FILE);
		} catch (RuntimeException e) {
			CakeCosmetic.LOG.warn("CakeCosmetic: {} can't be read, no unlock rules: {}", FILE, e.getMessage());
			return r;
		}
		if (!(root instanceof JValue.JObject o)) {
			return r;
		}
		for (Map.Entry<String, JValue> e : o.map.entrySet()) {
			if (!(e.getValue() instanceof JValue.JObject rule)) {
				continue;
			}
			List<Need> needs = new ArrayList<>();
			if (rule.get("advancements") instanceof JValue.JArray list) {
				for (JValue v : list.list) {
					if (v instanceof JValue.JString s) {
						needs.add(new Need(s.value, s.value));
					} else if (v instanceof JValue.JObject n && n.get("id") instanceof JValue.JString id) {
						String title = n.get("title") instanceof JValue.JString t ? t.value : id.value;
						needs.add(new Need(id.value, title));
					}
				}
			}
			boolean secret = rule.get("secret") instanceof JValue.JBool b && b.value;
			r.put(e.getKey(), new Rule(needs, secret));
		}
		return r;
	}

	String toText() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Rule> e : rules.entrySet()) {
			JsonObject rule = new JsonObject();
			JsonArray list = new JsonArray();
			for (Need n : e.getValue().needs) {
				JsonObject o = new JsonObject();
				o.addProperty("id", n.id);
				o.addProperty("title", n.title);
				list.add(o);
			}
			rule.add("advancements", list);
			rule.addProperty("secret", e.getValue().secret);
			root.add(e.getKey(), rule);
		}
		return "// CakeCosmetic unlock rules: background id -> the advancements it needs (all of them).\n"
				+ "// A background without a rule is free. Written by the gallery's dev mode; titles are shown while offline.\n"
				+ GSON.toJson(root) + "\n";
	}

	static Rules load(Path dir) {
		Path file = dir.resolve(FILE);
		if (!Files.exists(file)) {
			return new Rules();
		}
		try {
			return parse(Files.readString(file));
		} catch (IOException e) {
			CakeCosmetic.LOG.warn("CakeCosmetic: can't read {}: {}", file, e.toString());
			return new Rules();
		}
	}

	void save(Path dir) throws IOException {
		Files.createDirectories(dir);
		Path tmp = dir.resolve(FILE + ".tmp");
		Files.writeString(tmp, toText());
		Files.move(tmp, dir.resolve(FILE), StandardCopyOption.REPLACE_EXISTING);
	}
}
