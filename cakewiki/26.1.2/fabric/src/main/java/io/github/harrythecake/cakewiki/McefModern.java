package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.net.HostRouter;
import net.dimaskama.mcef.api.MCEFApi;
import net.minecraft.client.gui.screens.Screen;

import java.util.concurrent.CompletableFuture;

/**
 * The only way into MCEF Modern ({@link Mcef} makes it by name, and only when MCEF Modern is loaded). MCEF Modern
 * sets itself up only when a mod asks: CakeWiki asks when the player presses "Verify in game browser" (or in
 * {@link McefTest}), never at startup; the first setup downloads Chromium (MCEF Modern does that, not CakeWiki).
 */
final class McefModern implements Mcef.Bridge {
	private volatile boolean started;

	McefModern() {
	}

	private CompletableFuture<MCEFApi> future() {
		return MCEFApi.getInstanceFuture();
	}

	@Override
	public boolean ready() {
		if (!started) return false;
		CompletableFuture<MCEFApi> f = future();
		return f.isDone() && !f.isCompletedExceptionally();
	}

	@Override
	public boolean failed() {
		return started && future().isCompletedExceptionally();
	}

	@Override
	public void prepare() {
		if (started) return;
		started = true;
		CakeWiki.LOG.info("CakeWiki: asking MCEF Modern to set up its browser (its first setup downloads Chromium)");
		MCEFApi.initialize();
	}

	@Override
	public boolean open(Screen parent, String url, HostRouter router, Runnable after) {
		return ready() && McefVerifyScreen.open(future().join(), parent, url, router, after);
	}

	@Override
	public boolean onHostForTest(Screen screen) {
		return screen instanceof McefVerifyScreen v && v.onHostForTest();
	}

	@Override
	public void doneForTest(Screen screen) {
		if (screen instanceof McefVerifyScreen v) v.doneForTest();
	}
}
