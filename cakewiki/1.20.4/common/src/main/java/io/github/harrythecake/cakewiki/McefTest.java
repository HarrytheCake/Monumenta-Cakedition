package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Development only: the blocked path end to end. Run with MCEF in the dev run's mods and
 * {@code -Dcakewiki.mcefTest=1 -Dcakewiki.forceBlocked=monumenta.wiki.gg}: empties CakeWiki's page cache, opens the
 * reader (the forced block shows the blocked screen), opens MCEF's browser there, presses Done, and checks the page
 * then loads through the in-game browser. Logs PASS/FAIL and "cakewiki mcef test done"; screenshots
 * {@code cakewiki-mcef-<n>.png}. Does nothing in a normal game.
 * <p>
 * With {@code -Dcakewiki.mcefExpect=downloadFailed} (and MCEF's {@code download-mirror} pointed at a server that
 * answers 403, as Cloudflare does for a challenged VPN): checks that pressing "Verify in game browser" explains the
 * failed download instead of "try again in a moment".
 */
final class McefTest {
	private static boolean active;
	private static int step;
	private static int ticks;
	private static int shot;
	private static WikiScreen reader;
	private static final boolean EXPECT_DOWNLOAD_FAILED = "downloadFailed".equals(System.getProperty("cakewiki.mcefExpect"));

	private McefTest() {
	}

	static void startIfAsked() {
		if (!Boolean.getBoolean("cakewiki.mcefTest")) {
			return;
		}
		active = true;
		Path cache = Services.PLATFORM.gameDir().resolve("cakewiki").resolve("cache");
		if (Files.isDirectory(cache)) {
			try (Stream<Path> all = Files.walk(cache)) {
				all.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			} catch (IOException e) {
				CakeWiki.LOG.warn("cakewiki mcef test: couldn't empty the cache: {}", e.toString());
			}
		}
		CakeWiki.LOG.info("cakewiki mcef test: started");
	}

	static void tick(Minecraft mc) {
		if (!active) {
			return;
		}
		ticks++;
		switch (step) {
			case 0 -> { // the title screen, and MCEF's browser set up (its first start downloads Chromium)
				if (mc.screen instanceof TitleScreen && mc.getOverlay() == null && (Mcef.ready() || !Mcef.present() || settled()) && ticks > 40) {
					CakeWiki.open("Monumenta Wiki");
					next();
				} else if (ticks > 20 * 600) {
					fail("MCEF never became ready (present " + Mcef.present() + ")");
				}
			}
			case 1 -> {
				if (mc.screen instanceof WikiScreen w && w.blockedForTest() && ticks > 20) {
					reader = w;
					CakeWiki.LOG.info("cakewiki mcef test: blocked screen shown (PASS)");
					shot(mc);
					next();
				} else if (ticks > 20 * 30) {
					fail("no blocked screen");
				}
			}
			case 2 -> {
				if (!Mcef.present()) {
					CakeWiki.LOG.info("cakewiki mcef test: MCEF not installed: the blocked screen offers MCEF's page and the browser (PASS)");
					step = 99;
					ticks = 0;
				} else if (ticks == 10) {
					reader.verifyForTest();
				} else if (EXPECT_DOWNLOAD_FAILED && ticks == 40) {
					McefSetup setup = Mcef.setup();
					boolean ok = mc.screen == reader && reader.mcefWaitingForTest() && setup != null
							&& setup.stage() == McefSetup.Stage.DOWNLOAD_FAILED && "cakewiki.mcef.downloadFailed".equals(setup.messageKey());
					shot(mc);
					if (ok) {
						CakeWiki.LOG.info("cakewiki mcef test: failed MCEF download explained on the blocked screen ({}) (PASS)", setup);
						step = 99;
						ticks = 0;
					} else {
						fail("expected the failed-download line, got " + setup + " (screen " + mc.screen + ")");
					}
				} else if (mc.screen instanceof McefVerifyScreen v && v.onHostForTest() && ticks > 20 * 10) {
					CakeWiki.LOG.info("cakewiki mcef test: in-game browser on the wiki (PASS)");
					shot(mc);
					v.doneForTest();
					next();
				} else if (ticks > 20 * 60) {
					fail("the in-game browser never reached the wiki (screen " + mc.screen + ")");
				}
			}
			case 3 -> {
				if (mc.screen instanceof WikiScreen w && w.readyForTest() && ticks > 120) { // pictures too
					CakeWiki.LOG.info("cakewiki mcef test: page loaded through the in-game browser (PASS)");
					shot(mc);
					next();
				} else if (ticks > 20 * 60) {
					fail("the page didn't load through the in-game browser (blocked " + (mc.screen instanceof WikiScreen w && w.blockedForTest()) + ")");
				}
			}
			default -> {
				if (ticks > 40) {
					CakeWiki.LOG.info("cakewiki mcef test done");
					active = false;
				}
			}
		}
	}

	/** The expected-failure run: MCEF has given up (no point waiting for it to get ready). */
	private static boolean settled() {
		McefSetup setup = Mcef.setup();
		return EXPECT_DOWNLOAD_FAILED && setup != null && !setup.mayBecomeReady();
	}

	private static void next() {
		step++;
		ticks = 0;
	}

	private static void fail(String why) {
		CakeWiki.LOG.error("cakewiki mcef test: {} (FAIL)", why);
		CakeWiki.LOG.info("cakewiki mcef test done");
		active = false;
	}

	private static void shot(Minecraft mc) {
		Screenshot.grab(mc.gameDirectory, "cakewiki-mcef-" + (++shot) + ".png", mc.getMainRenderTarget(), msg -> { });
	}
}
