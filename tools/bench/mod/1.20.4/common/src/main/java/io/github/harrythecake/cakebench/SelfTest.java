package io.github.harrythecake.cakebench;

import io.github.harrythecake.cakebench.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Developer self-test for the dev client ({@code -Dcakebench.selftest=play}, used with smoke.py and a singleplayer
 * world): a few seconds after the join it adds a fake player with {@code /cbbot} on the integrated server, then runs
 * {@code /cakebench start selftest} through the loader's client command dispatcher, plays for a few seconds, runs
 * {@code /cakebench stop}, takes a screenshot of the chat summary and logs {@code CakeBench self-test done: ...}
 * (or {@code CakeBench self-test FAILED: ...}). Does nothing without the property.
 */
final class SelfTest {
	private static final Logger LOG = LoggerFactory.getLogger("cakebench");
	private static final double START_AT = 4, STOP_AT = 10, SHOT_AT = 12, DONE_AT = 13;

	private static boolean enabled;
	private static long joinedAt;
	private static int step;
	private static int playersSeen;

	private SelfTest() {}

	static void init() {
		enabled = "play".equals(System.getProperty("cakebench.selftest"));
		if (enabled) {
			LOG.info("CakeBench self-test armed: records a few seconds after the join");
		}
	}

	static void onJoin() {
		if (enabled && joinedAt == 0) {
			joinedAt = System.nanoTime();
		}
	}

	static void onFrame(Minecraft client) {
		if (!enabled || joinedAt == 0 || step > 3 || client.player == null) {
			return;
		}
		double t = (System.nanoTime() - joinedAt) / 1e9;
		if (step == 0 && t >= START_AT) {
			step = 1;
			spawnBot(client);
			CakeBench.clientCommand("cakebench start selftest");
			if (!Play.recording()) {
				fail("/cakebench start didn't start a recording");
			}
		} else if (step == 1 && t >= STOP_AT) {
			step = 2;
			playersSeen = client.level == null ? 0 : client.level.players().size();
			CakeBench.clientCommand("cakebench stop");
		} else if (step == 2 && t >= SHOT_AT) {
			step = 3;
			Compat.screenshot(client, "cakebench-selftest.png");
		} else if (step == 3 && t >= DONE_AT) {
			step = 4;
			Path dir = Play.lastDir();
			Map<String, Object> r = Play.lastResult();
			if (dir == null || r == null) {
				fail("/cakebench stop saved nothing");
			} else if (!Files.isRegularFile(dir.resolve("summary.json")) || !Files.isRegularFile(dir.resolve("play.csv"))) {
				fail("no summary.json / play.csv in " + dir);
			} else if (((Number) r.get("frames")).intValue() < 10) {
				fail("only " + r.get("frames") + " frames recorded");
			} else {
				Object gpu = r.get("gpu") instanceof Map<?, ?> g ? g.get("framesRead") + " GPU-timed frames, "
						+ ((Map<?, ?>) g.get("phases")).size() + " phases" : "no GPU timers";
				LOG.info(String.format(Locale.ROOT,
						"CakeBench self-test done: loader %s, %s frames, avg %s FPS, %s, players in the world %d (fake player %s), saved to %s",
						Services.PLATFORM.loader(), r.get("frames"), r.get("avgFps"), gpu, playersSeen,
						playersSeen >= 2 ? "seen" : "NOT seen", dir));
			}
		}
	}

	/** {@code /cbbot CakeBot} three blocks east of the player, run as the integrated server itself. */
	private static void spawnBot(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			LOG.warn("CakeBench self-test: not singleplayer, no fake player");
			return;
		}
		String command = String.format(Locale.ROOT, "cbbot CakeBot %.1f %.1f %.1f",
				client.player.getX() + 3, client.player.getY(), client.player.getZ());
		server.execute(() -> {
			try {
				int r = server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
				LOG.info("CakeBench self-test: /{} -> {}", command, r);
			} catch (Exception e) {
				fail("/" + command + ": " + e);
			}
		});
	}

	private static void fail(String why) {
		LOG.error("CakeBench self-test FAILED: {}", why);
	}
}
