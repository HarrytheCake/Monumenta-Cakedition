package io.github.harrythecake.cakeui.json;

/** A JSON5 syntax error at a file and line. */
public final class Json5Exception extends RuntimeException {
	public final String file;
	public final int line;
	public final String problem;

	public Json5Exception(String file, int line, String problem) {
		super(file + ":" + line + ": " + problem);
		this.file = file;
		this.line = line;
		this.problem = problem;
	}
}
