package io.github.harrythecake.cakecosmetic;

import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A background's optional {@code <id>.json} next to its PNG:
 * {@code { "title": "Sunset" | { "en_us": "Sunset", "zh_cn": "日落" }, "artist": "Someone" }}.
 */
record BackgroundMeta(Map<String, String> titles, String artist) {
	static final BackgroundMeta NONE = new BackgroundMeta(Map.of(), "");

	static BackgroundMeta parse(String text, String file) {
		JValue root;
		try {
			root = Json5.parse(text, file);
		} catch (RuntimeException e) {
			CakeCosmetic.LOG.warn("CakeCosmetic: {} can't be read: {}", file, e.getMessage());
			return NONE;
		}
		if (!(root instanceof JValue.JObject o)) {
			return NONE;
		}
		Map<String, String> titles = new LinkedHashMap<>();
		JValue t = o.get("title");
		if (t instanceof JValue.JString s) {
			titles.put("", s.value);
		} else if (t instanceof JValue.JObject byLang) {
			for (Map.Entry<String, JValue> e : byLang.map.entrySet()) {
				if (e.getValue() instanceof JValue.JString s) {
					titles.put(e.getKey(), s.value);
				}
			}
		}
		String artist = o.get("artist") instanceof JValue.JString a ? a.value : "";
		return new BackgroundMeta(Map.copyOf(titles), artist);
	}

	/** The title in this language, else English, else any, else null. */
	String title(String lang) {
		String t = titles.get(lang);
		if (t == null) {
			t = titles.get("en_us");
		}
		if (t == null) {
			t = titles.get("");
		}
		if (t == null && !titles.isEmpty()) {
			t = titles.values().iterator().next();
		}
		return t;
	}
}
