package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Withdrawn pack versions: config/cakeupdater/withdrawn.json in the published pack, next to pack.toml,
 * {"&lt;version&gt;": {"en_us": "&lt;reason&gt;", "zh_cn": "...", ...}}. An install on a listed version gets an
 * urgent update notice. The file is read from the published pack, so a version can be withdrawn after it shipped.
 */
final class Withdrawn {
	static final String PATH = "config/" + CakeUpdater.MOD_ID + "/withdrawn.json";

	private Withdrawn() {}

	/** The published withdrawn.json for a pack.toml URL: the same folder. */
	static String url(String packUrl) {
		return packUrl.substring(0, packUrl.lastIndexOf('/') + 1) + PATH;
	}

	/** The reasons by language if {@code version} is withdrawn (empty map if it has none), else null. */
	static Map<String, String> reasons(String json, String version) {
		if (json == null || version == null) {
			return null;
		}
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonObject() || !root.getAsJsonObject().has(version)) {
			return null;
		}
		Map<String, String> reasons = new LinkedHashMap<>();
		JsonElement entry = root.getAsJsonObject().get(version);
		if (entry.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : entry.getAsJsonObject().entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					reasons.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
				}
			}
		} else if (entry.isJsonPrimitive()) {
			reasons.put("en_us", entry.getAsString());
		}
		return reasons;
	}

	/** The reason in the player's language (same fallback as the changelog), else any; "" if none was given. */
	static String pick(Map<String, String> reasons, String language) {
		for (String lang : Changelog.fallback(language)) {
			String reason = reasons.get(lang);
			if (reason != null && !reason.isBlank()) {
				return reason;
			}
		}
		return reasons.values().stream().filter(r -> !r.isBlank()).findFirst().orElse("");
	}
}
