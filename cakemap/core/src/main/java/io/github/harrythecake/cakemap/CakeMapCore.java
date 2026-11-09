package io.github.harrythecake.cakemap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * What CakeMap's version-independent code (stores, import, export) shares: the log and a way to
 * run something on the game's render thread. The game sets {@link #renderThread} at start; until
 * then (and in unit tests) the task runs at once.
 */
public final class CakeMapCore {
	public static final String MOD_ID = "cakemap";
	public static final Logger LOG = LoggerFactory.getLogger("CakeMap");
	/** Runs a task on the render thread (now, when already there). */
	public static volatile Consumer<Runnable> renderThread = Runnable::run;

	private CakeMapCore() {
	}

	public static void onRender(Runnable r) {
		renderThread.accept(r);
	}
}
