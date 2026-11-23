package io.github.harrythecake.cakeui.configmap;

/** The file formats a config map can name ({@code type} in the map). */
public enum Format {
	JSON("json"),
	JSON5("json5"),
	TOML("toml"),
	/** Vanilla's options.txt: one {@code key:value} per line. */
	OPTIONS("options"),
	/** Java .properties: {@code key=value} (or {@code key: value}), # comments. */
	PROPERTIES("properties");

	public final String id;

	Format(String id) {
		this.id = id;
	}

	/** The format with this id, or null. */
	public static Format of(String id) {
		for (Format f : values()) {
			if (f.id.equals(id)) {
				return f;
			}
		}
		return null;
	}

	/** Whether values in this format carry no type (everything is the raw text of the line). */
	public boolean untyped() {
		return this == OPTIONS || this == PROPERTIES;
	}
}
