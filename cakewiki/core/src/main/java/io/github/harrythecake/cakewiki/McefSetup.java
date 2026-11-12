package io.github.harrythecake.cakewiki;

import java.net.URI;

/**
 * Where MCEF 2.x (the original MCEF, 1.20.4 / 1.21.1) is with setting up its browser, read from its own download
 * listener; decides what the blocked screen says after "Verify in game browser" didn't open the browser.
 * <p>
 * MCEF fetches a checksum from its download site at <em>every</em> game start (and Chromium on the first one) and gives
 * up for the whole session when that fails. That site is behind Cloudflare too, so a VPN / game accelerator that
 * Cloudflare challenges on the wiki usually breaks MCEF as well: then "try again in a moment" is wrong, and the
 * screen says what to do instead.
 */
public record McefSetup(Stage stage, int percent, String downloadHost) {
	public enum Stage {
		/** Downloading or unpacking Chromium; the browser can open once it's done. */
		WORKING,
		/** Couldn't reach its download site at game start: no browser until the game restarts. */
		DOWNLOAD_FAILED,
		/** Downloaded, but Chromium didn't start (or is about to: MCEF starts it about a second after the download). */
		NOT_STARTED,
		/** The browser is ready. */
		READY,
		/** Couldn't read MCEF's state (another MCEF version). */
		UNKNOWN
	}

	/** MCEF's download site when its settings don't say. */
	public static final String DEFAULT_HOST = "mcef-download.cinemamod.com";
	public static final McefSetup UNKNOWN = new McefSetup(Stage.UNKNOWN, 0, DEFAULT_HOST);

	/**
	 * @param initialized MCEF's {@code isInitialized()}
	 * @param done        its download listener's {@code isDone()}
	 * @param failed      its download listener's {@code isFailed()}
	 * @param progress    its download listener's {@code getProgress()} (0 to 1)
	 * @param mirror      its settings' {@code download-mirror} (a URL), or null
	 */
	public static McefSetup of(boolean initialized, boolean done, boolean failed, float progress, String mirror) {
		Stage stage = initialized ? Stage.READY : failed ? Stage.DOWNLOAD_FAILED : done ? Stage.NOT_STARTED : Stage.WORKING;
		int percent = Float.isNaN(progress) ? 0 : Math.max(0, Math.min(100, Math.round(progress * 100)));
		return new McefSetup(stage, percent, host(mirror));
	}

	static String host(String mirror) {
		if (mirror == null || mirror.isBlank()) return DEFAULT_HOST;
		try {
			String h = URI.create(mirror.trim()).getHost();
			return h != null && !h.isEmpty() ? h : DEFAULT_HOST;
		} catch (IllegalArgumentException e) {
			return DEFAULT_HOST;
		}
	}

	/** The line under the blocked message (translation key; its arguments are {@link #args()}), or null when ready. */
	public String messageKey() {
		return switch (stage) {
			case WORKING -> "cakewiki.mcef.working";
			case DOWNLOAD_FAILED -> "cakewiki.mcef.downloadFailed";
			case NOT_STARTED -> "cakewiki.mcef.failed";
			case UNKNOWN -> "cakewiki.mcef.notReady";
			case READY -> null;
		};
	}

	public Object[] args() {
		return switch (stage) {
			case WORKING -> new Object[] {percent};
			case DOWNLOAD_FAILED -> new Object[] {downloadHost, downloadHost};
			default -> new Object[0];
		};
	}

	/** Worth waiting for: the blocked screen opens the browser by itself once MCEF gets there. */
	public boolean mayBecomeReady() {
		return stage == Stage.WORKING || stage == Stage.NOT_STARTED || stage == Stage.UNKNOWN;
	}
}
