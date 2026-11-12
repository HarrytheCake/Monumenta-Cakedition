package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.net.HostRouter;
import io.github.harrythecake.cakewiki.platform.Services;
import net.minecraft.client.gui.screens.Screen;

/**
 * MCEF (Minecraft Chromium Embedded Framework, LGPL-2.1, mod id {@code mcef}): an optional mod the player installs
 * themselves; CakeWiki never ships or downloads it. When Cloudflare's "verify you are human" check blocks CakeWiki
 * (a VPN or game accelerator carrying Minecraft's traffic), MCEF's in-game browser lets the player pass the check
 * where CakeWiki's requests come from, and CakeWiki then loads that site through the same browser
 * ({@link McefFetcher}). This class never touches MCEF's classes itself, so CakeWiki runs without MCEF.
 */
final class Mcef {
	/** MCEF's page, offered when it isn't installed. */
	static final String PAGE = "https://modrinth.com/mod/mcef";

	private Mcef() {
	}

	static boolean present() {
		return Services.PLATFORM.isModLoaded("mcef");
	}

	/** MCEF is installed and has set up its browser (its first start downloads Chromium). */
	static boolean ready() {
		return present() && McefVerifyScreen.ready();
	}

	/** Where MCEF is with setting up its browser; null when MCEF isn't installed. */
	static McefSetup setup() {
		return present() ? McefVerifyScreen.setup() : null;
	}

	/**
	 * Opens the in-game browser at {@code url} for the player to pass the check; on Done the host goes through that
	 * browser and {@code after} runs (the reader retries). False when MCEF is missing or not ready yet.
	 */
	static boolean verify(Screen parent, String url, HostRouter router, Runnable after) {
		return present() && McefVerifyScreen.open(parent, url, router, after);
	}
}
