package io.github.harrythecake.cakeui.configmap;

/** A config file that can't be read or a value that can't be written; {@link #line} is 0 when no line applies. */
public final class ConfigDocException extends RuntimeException {
	public final int line;

	public ConfigDocException(int line, String message) {
		super(line > 0 ? "line " + line + ": " + message : message);
		this.line = line;
	}
}
