package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.net.HostRouter;
import io.github.harrythecake.cakewiki.platform.Services;
import net.minecraft.client.gui.screens.Screen;

/**
 * MCEF Modern (DimasKama's MCEF fork, LGPL-2.1, mod id {@code mcef-modern}, Fabric only): an optional mod the player
 * installs themselves; CakeWiki never ships or downloads it. When Cloudflare's "verify you are human" check blocks
 * CakeWiki (a VPN or game accelerator carrying Minecraft's traffic), its in-game browser lets the player pass the
 * check where CakeWiki's requests come from, and CakeWiki then loads that site through the same browser.
 * <p>
 * The original MCEF stops at 1.21.4; MCEF Modern covers 1.21.10+ and 26.x, on Fabric only. The classes that touch it
 * ({@code McefModern}, {@code McefVerifyScreen}, {@code McefFetcher}) live in the Fabric part, so this class finds
 * them by name: no Fabric jar of a version without them, no NeoForge jar, and no game without MCEF Modern ever loads
 * MCEF's classes. Where they're missing, the blocked screen keeps Retry and Open page in browser only.
 */
final class Mcef {
	/** MCEF Modern's page, offered when it isn't installed. */
	static final String PAGE = "https://modrinth.com/mod/mcef-modern";
	/** The button and the note shown when MCEF Modern isn't installed. */
	static final String GET_KEY = "cakewiki.blocked.getMcefModern";
	static final String NOTE_KEY = "cakewiki.blocked.mcefModernNote";
	static final String MOD_ID = "mcef-modern";
	/** The only class that talks to MCEF Modern; in the Fabric jar only. */
	private static final String BRIDGE = "io.github.harrythecake.cakewiki.McefModern";

	/** What CakeWiki asks of MCEF Modern ({@code McefModern} implements it). */
	interface Bridge {
		/** MCEF Modern has set up its browser. */
		boolean ready();

		/** MCEF Modern's setup failed (its log says why). */
		boolean failed();

		/** Starts MCEF Modern's setup (the first time, it downloads Chromium); nothing when it's running or done. */
		void prepare();

		/** Opens the in-game browser at {@code url}; false when MCEF Modern isn't ready. */
		boolean open(Screen parent, String url, HostRouter router, Runnable after);

		/** Development ({@link McefTest}): {@code screen} is the in-game browser and it is on the page's host. */
		boolean onHostForTest(Screen screen);

		/** Development ({@link McefTest}): presses Done on the in-game browser. */
		void doneForTest(Screen screen);
	}

	private static Boolean offered;
	private static Bridge bridge;

	private Mcef() {
	}

	/** An MCEF build exists for this Minecraft version and loader (the bridge is in this jar), so the in-game browser is offered. */
	static synchronized boolean offered() {
		if (offered == null) {
			try {
				Class.forName(BRIDGE, false, Mcef.class.getClassLoader());
				offered = true;
			} catch (ClassNotFoundException | LinkageError e) {
				offered = false;
			}
		}
		return offered;
	}

	static boolean present() {
		return offered() && Services.PLATFORM.isModLoaded(MOD_ID);
	}

	private static synchronized Bridge bridge() {
		if (bridge == null && present()) {
			try {
				bridge = (Bridge) Class.forName(BRIDGE).getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException | LinkageError e) {
				CakeWiki.LOG.warn("CakeWiki: couldn't use MCEF Modern ({})", e.toString());
				offered = false;
			}
		}
		return bridge;
	}

	/** MCEF Modern is installed and has set up its browser. */
	static boolean ready() {
		Bridge b = bridge();
		return b != null && b.ready();
	}

	static boolean failed() {
		Bridge b = bridge();
		return b != null && b.failed();
	}

	/** Starts MCEF Modern's setup, when it's installed. */
	static void prepare() {
		Bridge b = bridge();
		if (b != null) b.prepare();
	}

	/**
	 * Opens the in-game browser at {@code url} for the player to pass the check; on Done the host goes through that
	 * browser and {@code after} runs (the reader retries). False when MCEF Modern is missing or not ready yet; the
	 * first time, this starts its setup (it downloads Chromium), so the player can press again once it's done.
	 */
	static boolean verify(Screen parent, String url, HostRouter router, Runnable after) {
		Bridge b = bridge();
		if (b == null) return false;
		if (!b.ready()) {
			b.prepare();
			return false;
		}
		return b.open(parent, url, router, after);
	}

	static boolean onHostForTest(Screen screen) {
		Bridge b = bridge();
		return b != null && b.onHostForTest(screen);
	}

	static void doneForTest(Screen screen) {
		Bridge b = bridge();
		if (b != null) b.doneForTest(screen);
	}
}
