package io.github.harrythecake.cakewiki.translate;

import io.github.harrythecake.cakewiki.CakeWiki;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * CakeText Translator 0.3.0+, when installed: its {@code api.TranslatorApi} called by reflection, so CakeWiki builds
 * and runs without it. Surface {@code wiki} (the player can turn it off in the translator's "Where to translate").
 * Anything that goes wrong in a call means "show the original".
 */
public final class CakeTextBridge implements Translator {
	public static final String MOD_ID = "caketext-translator";
	private static final String API = "io.github.harrythecake.caketext.translator.api.TranslatorApi";
	private static final String SURFACE = "wiki";

	private final MethodHandle active, changes, translate;
	private boolean failed;

	private CakeTextBridge(MethodHandle active, MethodHandle changes, MethodHandle translate) {
		this.active = active;
		this.changes = changes;
		this.translate = translate;
	}

	/** The bridge, or null without CakeText Translator 0.3.0+. */
	public static CakeTextBridge find() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
		try {
			Class<?> api = Class.forName(API);
			MethodHandles.Lookup l = MethodHandles.publicLookup();
			int version = (int) l.findStatic(api, "version", MethodType.methodType(int.class)).invoke();
			if (version < 1) return null;
			return new CakeTextBridge(
					l.findStatic(api, "active", MethodType.methodType(boolean.class, String.class)),
					l.findStatic(api, "changes", MethodType.methodType(long.class)),
					l.findStatic(api, "translate", MethodType.methodType(Object[].class, String.class, String[].class, boolean[].class)));
		} catch (Throwable t) {
			CakeWiki.LOG.info("CakeText Translator is installed but has no API CakeWiki can use (0.3.0+ needed): {}", t.toString());
			return null;
		}
	}

	/** Translation is on for wiki pages in the translator's settings. */
	public boolean active() {
		if (failed) return false;
		try {
			return (boolean) active.invoke(SURFACE);
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	@Override
	public long changes() {
		if (failed) return 0;
		try {
			return (long) changes.invoke();
		} catch (Throwable t) {
			fail(t);
			return 0;
		}
	}

	@Override
	public Object[] translate(String[] parts, boolean[] keep) {
		if (failed) return null;
		try {
			return (Object[]) translate.invoke(SURFACE, parts, keep);
		} catch (Throwable t) {
			fail(t);
			return null;
		}
	}

	private void fail(Throwable t) {
		failed = true;
		CakeWiki.LOG.warn("CakeText Translator call failed; showing wiki pages untranslated", t);
	}
}
