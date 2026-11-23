package io.github.harrythecake.cakemonu.fabric;

import ch.njol.unofficialmonumentamod.AbilityHandler;
import ch.njol.unofficialmonumentamod.ChannelHandler;
import ch.njol.unofficialmonumentamod.UnofficialMonumentaModClient;
import io.github.harrythecake.cakemonu.AbilitySource;
import io.github.harrythecake.cakemonu.CakeMonu;
import io.github.harrythecake.cakemonu.CakeMonuPlugin;
import io.github.harrythecake.cakemonu.CakeUIBridge;
import io.github.harrythecake.cakemonu.IconLayout;
import io.github.harrythecake.cakemonu.IconPlacement;
import io.github.harrythecake.cakemonu.SettingsScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Dev-client self-test ({@code -Dcakemonu.selftest=world}, needs UMM (and for the HUD editor part CakeUI) in
 * {@code 1.20.4/fabric/run/mods}): in a world, adds three made-up abilities (single player sends none), gives
 * one its own place, checks UMM's bar shrank and the icon is drawn on its own, moves and resizes it like
 * CakeUI's HUD editor would, checks the saved file, shoots {@code cakemonu-selftest-*.png}, opens the settings
 * screen, then puts everything back. Ends with the log line "cakemonu self-test done: PASS|FAIL".
 */
final class SelfTest {
	static final boolean ENABLED = System.getProperty("cakemonu.selftest") != null;
	private static final String CLASS = "selftest";
	private static final String PLACED = CLASS + "/self-test 1";

	private static int ticks;
	private static int step;
	private static final List<String> failures = new ArrayList<>();
	private static double[] barBefore;
	private static boolean ummBarWasOn;

	private SelfTest() {
	}

	static void tick(Minecraft mc) {
		if (step < 0 || mc.player == null || mc.level == null) {
			return;
		}
		ticks++;
		try {
			run(mc);
		} catch (Exception | LinkageError e) {
			CakeMonu.LOG.error("cakemonu self-test crashed", e);
			failures.add("crashed: " + e);
			finish(mc);
		}
	}

	private static void run(Minecraft mc) throws Exception {
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		AbilitySource src = CakeMonu.source();
		switch (step) {
			case 0 -> {
				if (ticks < 60) {
					return;
				}
				check(src.state() == AbilitySource.State.READY, "UMM bridge ready (state " + src.state() + ")");
				if (src.state() != AbilitySource.State.READY) {
					finish(mc);
					return;
				}
				ummBarWasOn = UnofficialMonumentaModClient.options.abilitiesDisplay_enabled;
				UnofficialMonumentaModClient.options.abilitiesDisplay_enabled = true;
				CakeMonu.clearAll();
				addAbilities();
				next();
			}
			case 1 -> {
				if (ticks < 10) {
					return;
				}
				check(src.current().size() == 3, "three abilities known (" + src.current().size() + ")");
				barBefore = src.barBox(sw, sh);
				check(barBefore != null, "UMM's bar box");
				CakeMonu.setPlaced(PLACED, true, sw, sh);
				check(CakeMonu.layout().has(PLACED), "ability got its own place");
				next();
			}
			case 2 -> {
				if (ticks < 10) {
					return;
				}
				int size = src.iconSize();
				int gap = src.iconGap();
				double[] bar = src.barBox(sw, sh);
				double along = src.horizontal() ? bar[2] : bar[3];
				double alongBefore = src.horizontal() ? barBefore[2] : barBefore[3];
				check(Math.abs(alongBefore - (3 * size + 2 * gap)) < 0.5, "bar had 3 icons (" + alongBefore + ")");
				check(Math.abs(along - (2 * size + gap)) < 0.5, "bar shrank to 2 icons (" + along + ")");
				check(Boolean.FALSE.equals(UmmAbilities.visibility(PLACED, true)), "placed ability left out of the bar");
				check(UmmAbilities.visibility(CLASS + "/self-test 0", true) == null, "other abilities stay UMM's");
				check(UmmAbilities.drawnLastFrame == 1, "placed icon drawn on its own (" + UmmAbilities.drawnLastFrame + ")");
				double[] box = CakeMonu.layout().get(PLACED).box(sw, sh, size);
				check(!overlaps(box, bar), "first place doesn't cover the bar");
				// like CakeUI's HUD editor: move it to the middle at 64 px, Done
				if (CakeUIBridge.editorAvailable()) {
					check(CakeMonuPlugin.testMove(PLACED, sw / 2.0 - 32, sh / 2.0 - 32, 64, sw, sh),
							"HUD editor movable moved and saved it");
				} else {
					CakeMonu.LOG.info("cakemonu self-test: CakeUI not in run/mods, moving through the layout instead");
					CakeMonu.layout().put(PLACED, IconPlacement.fromBox(sw / 2.0 - 32, sh / 2.0 - 32, 64, sw, sh, 0, size));
					CakeMonu.save();
				}
				IconLayout saved = IconLayout.read(CakeMonu.layoutFile());
				IconPlacement p = saved.get(PLACED);
				check(p != null && p.size == 64 && p.anchorX == 0.5 && p.anchorY == 0.5, "saved file has the new place (" + p + ")");
				next();
			}
			case 3 -> {
				if (ticks == 20) {
					Screenshot.grab(mc.gameDirectory, "cakemonu-selftest-hud.png", mc.getMainRenderTarget(), m -> {
					});
				}
				if (ticks < 30) {
					return;
				}
				check(UmmAbilities.drawnLastFrame == 1, "moved icon still drawn");
				// every ability placed: UMM's bar switches itself off
				CakeMonu.setPlaced(CLASS + "/self-test 0", true, sw, sh);
				CakeMonu.setPlaced(CLASS + "/self-test 2", true, sw, sh);
				check(UmmAbilities.allPlaced(), "all placed");
				mc.setScreen(new SettingsScreen(null));
				next();
			}
			case 4 -> {
				if (ticks == 20) {
					Screenshot.grab(mc.gameDirectory, "cakemonu-selftest-settings.png", mc.getMainRenderTarget(), m -> {
					});
				}
				if (ticks < 30) {
					return;
				}
				check(mc.screen instanceof SettingsScreen, "settings screen open");
				check(UmmAbilities.drawnLastFrame == 3, "three icons drawn (" + UmmAbilities.drawnLastFrame + ")");
				mc.setScreen(null);
				finish(mc);
			}
			default -> {
			}
		}
	}

	private static void addAbilities() {
		AbilityHandler h = UnofficialMonumentaModClient.abilityHandler;
		synchronized (h) {
			h.abilityData.clear();
			for (int i = 0; i < 3; i++) {
				ChannelHandler.ClassUpdatePacket.AbilityInfo p = new ChannelHandler.ClassUpdatePacket.AbilityInfo();
				p.name = "Self-test " + i;
				p.className = CLASS;
				AbilityHandler.AbilityInfo a = new AbilityHandler.AbilityInfo(p);
				a.initialCooldown = 20 * 600;
				a.remainingCooldown = i == 1 ? 20 * 300 : 0;
				h.abilityData.add(a);
			}
		}
	}

	private static void finish(Minecraft mc) {
		try {
			CakeMonu.clearAll();
			AbilityHandler h = UnofficialMonumentaModClient.abilityHandler;
			synchronized (h) {
				h.abilityData.clear();
			}
			UnofficialMonumentaModClient.options.abilitiesDisplay_enabled = ummBarWasOn;
		} catch (RuntimeException | LinkageError e) {
			failures.add("clean-up: " + e);
		}
		step = -1;
		CakeMonu.LOG.info("cakemonu self-test done: {}{}", failures.isEmpty() ? "PASS" : "FAIL ", failures.isEmpty() ? "" : failures);
	}

	private static void next() {
		step++;
		ticks = 0;
	}

	private static void check(boolean ok, String what) {
		CakeMonu.LOG.info("cakemonu self-test: {} {}", ok ? "ok  " : "FAIL", what);
		if (!ok) {
			failures.add(what);
		}
	}

	private static boolean overlaps(double[] a, double[] b) {
		return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}
}
