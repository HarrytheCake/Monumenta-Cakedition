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
 * Development only: the blocked path end to end. Run with MCEF Modern in the dev run's mods and
 * {@code -Dcakewiki.mcefTest=true -Dcakewiki.forceBlocked=monumenta.wiki.gg}: empties CakeWiki's page cache, starts
 * MCEF Modern's setup, opens the reader (the forced block shows the blocked screen), opens the in-game browser there,
 * presses Done, and checks the page then loads through it. Logs PASS/FAIL and "cakewiki mcef test done"; screenshots
 * {@code cakewiki-mcef-<n>.png}. Does nothing in a normal game.
 */
final class McefTest {
	private static boolean active;
	private static int step;
	private static int ticks;
	private static int shot;
	private static WikiScreen reader;

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
		CakeWiki.LOG.info("cakewiki mcef test: started (MCEF offered {}, present {})", Mcef.offered(), Mcef.present());
	}

	static void tick(Minecraft mc) {
		if (!active) {
			return;
		}
		ticks++;
		switch (step) {
			case 0 -> { // the title screen, and MCEF Modern's browser set up (its first start downloads Chromium)
				if (ticks == 1) Mcef.prepare();
				if (Mcef.failed()) {
					fail("MCEF Modern's setup failed");
				} else if (mc.screen instanceof TitleScreen && mc.getOverlay() == null && (Mcef.ready() || !Mcef.present()) && ticks > 40) {
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
					CakeWiki.LOG.info("cakewiki mcef test: MCEF not installed: the blocked screen offers {} (PASS)",
							Mcef.offered() ? "MCEF's page and the browser" : "the browser only (no MCEF build here)");
					step = 99;
					ticks = 0;
				} else if (ticks == 10) {
					reader.verifyForTest();
				} else if (Mcef.onHostForTest(mc.screen) && ticks > 20 * 10) {
					CakeWiki.LOG.info("cakewiki mcef test: in-game browser on the wiki (PASS)");
					shot(mc);
					Mcef.doneForTest(mc.screen);
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
		Screenshot.grab(mc.gameDirectory, "cakewiki-mcef-" + (++shot) + ".png", mc.getMainRenderTarget(), 1, msg -> { });
	}
}
