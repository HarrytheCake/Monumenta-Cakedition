package io.github.harrythecake.cakebench;

import io.github.harrythecake.cakebench.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Benchmark harness. With {@code config/cakebench/plan.json} present it waits for the join and for operator rights
 * (the driver ops the player from the server console), then for each scene: teleports there, turns slowly through
 * warm-up and measurement, records every frame's time while measuring, and finally writes the results and quits.
 * Without a plan it does nothing.
 * <p>
 * A {@code live} plan joins a real server (e.g. Monumenta) instead: it sends <b>no chat and no commands</b>, needs no
 * operator rights and never moves; its one scene starts from wherever the character stands and only turns the camera.
 * The server's resource pack is declined, so a prompt can't block the join.
 * <p>
 * With {@code spark} set, each scene's measurement is also profiled by spark's client profiler, started and stopped
 * through the loader's client command dispatcher (runs locally; never sent to the server) and saved with
 * {@code --save-to-file} (never uploaded), then copied next to the scene's frame times.
 * <p>
 * Without a plan, {@code /cakebench start} … {@code stop} records while you play ({@link Play}).
 * <p>
 * The loader parts call {@link #init}, {@link #onScreenInit}, {@link #onJoin}, {@link #onDisconnect}; the mixin calls
 * {@link #onFrame}.
 */
public final class CakeBench {
	public static final String MOD_ID = "cakebench";
	static final Logger LOG = LoggerFactory.getLogger("cakebench");

	private enum Phase { IDLE, SETTLE, WARMUP, MEASURE, SAVING, DONE }

	/** spark saves the profile asynchronously; give it this long before carrying on without one. */
	private static final double SAVE_TIMEOUT_SECONDS = 30;

	private static Plan plan;
	private static Phase phase = Phase.IDLE;
	private static boolean connecting;
	private static int scene;
	private static long phaseStart;
	private static long lastFrame;
	private static float[] frames = new float[1 << 14];
	private static int frameCount;
	private static long gcCount0, gcMillis0;
	private static long heapPeak;
	private static int unfocusedFrames, minimizedFrames;
	/** Where the camera turns from: the scene's angles, or in a live run the character's own. */
	private static float baseYaw, basePitch;
	private static long sparkStartMillis;
	private static Path sparkSaved;
	private static long sparkSeenAt;
	private static final Map<String, Object> results = new LinkedHashMap<>();

	private CakeBench() {}

	/** Client start: reads the plan, if there is one. */
	public static void init() {
		SelfTest.init();
		Path file = Services.PLATFORM.configDir().resolve("cakebench").resolve("plan.json");
		if (!Files.exists(file)) {
			return;
		}
		try {
			plan = BenchStats.GSON.fromJson(Files.readString(file), Plan.class);
		} catch (Exception e) {
			LOG.error("Could not read {}", file, e);
			return;
		}
		LOG.info("CakeBench plan '{}': {} scenes", plan.run, plan.scenes.size());
		if (plan.gpuTimers) {
			GpuTimer.enable(plan.gpuTimerDepth);
		}
	}

	/** After a screen's init. 1.20 ignores the launcher's --server argument, so join the bench server ourselves. */
	public static void onScreenInit(Minecraft client, Screen screen) {
		if (plan != null && screen instanceof TitleScreen && phase == Phase.IDLE && !connecting && plan.server != null) {
			connecting = true;
			Compat.later(client, () -> Compat.connect(client, screen, plan.server, plan.live));
		}
	}

	/** The client has joined a world or server. */
	public static void onJoin() {
		if (plan != null && phase == Phase.IDLE) {
			start(Phase.SETTLE);
		}
		SelfTest.onJoin();
	}

	/** The client has left a world or server: a running recording is saved. */
	public static void onDisconnect(Minecraft client) {
		if (Play.recording()) {
			Play.stop(client);
		}
	}

	/** Called at the end of every frame. */
	public static void onFrame(Minecraft client) {
		long now = System.nanoTime();
		long dt = now - lastFrame;
		lastFrame = now;
		Play.onFrame(client, dt);
		SelfTest.onFrame(client);
		if (plan == null || phase == Phase.IDLE || phase == Phase.DONE) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		double elapsed = (now - phaseStart) / 1e9;
		switch (phase) {
			case SETTLE -> {
				// the driver ops the player when it sees the join; wait for that and for the first chunks
				// (a live server never ops anyone: just wait)
				if (elapsed >= plan.settleSeconds && (plan.live || Compat.isOperator(player))) {
					if (plan.scenes.isEmpty()) { // a priming run: only lets yosbr and the mods write their files
						phase = Phase.DONE;
						Path dir = Path.of(plan.out, plan.run);
						try {
							Files.createDirectories(dir);
						} catch (IOException e) {
							LOG.error("Could not create {}", dir, e);
						}
						writeSummary(client, dir);
						client.stop();
					} else {
						enterScene(client, 0);
					}
				}
			}
			case WARMUP -> {
				turn(player, elapsed);
				if (elapsed >= plan.warmupSeconds) {
					frameCount = 0;
					heapPeak = 0;
					unfocusedFrames = 0;
					minimizedFrames = 0;
					long[] gc = BenchStats.gc();
					gcCount0 = gc[0];
					gcMillis0 = gc[1];
					GpuTimer.start();
					if (plan.spark) {
						sparkStartMillis = System.currentTimeMillis();
						clientCommand("sparkc profiler start --thread *");
					}
					start(Phase.MEASURE);
				}
			}
			case MEASURE -> {
				turn(player, plan.warmupSeconds + elapsed);
				record(dt / 1e6f);
				// a covered, minimized or unfocused window renders differently: count it so such runs can be flagged
				if (!client.isWindowActive()) {
					unfocusedFrames++;
				}
				if (Compat.minimized(client)) {
					minimizedFrames++;
				}
				if ((frameCount & 63) == 0) {
					Runtime rt = Runtime.getRuntime();
					heapPeak = Math.max(heapPeak, rt.totalMemory() - rt.freeMemory());
				}
				if (elapsed >= plan.measureSeconds) {
					finishScene(client, elapsed);
				}
			}
			case SAVING -> {
				// spark writes the file in the background: wait until it appears, then a moment for the write to end
				if (sparkSaved == null) {
					sparkSaved = newestProfile(sparkStartMillis);
					sparkSeenAt = now;
				} else if ((now - sparkSeenAt) / 1e9 >= 2) {
					Path dir = Path.of(plan.out, plan.run);
					try {
						Files.createDirectories(dir);
						Files.copy(sparkSaved, dir.resolve(plan.scenes.get(scene).name() + ".sparkprofile"),
								java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						LOG.error("Could not copy the spark profile", e);
					}
					nextScene(client);
				}
				if (phase == Phase.SAVING && elapsed >= SAVE_TIMEOUT_SECONDS) {
					LOG.warn("CakeBench: no spark profile appeared within {} s", SAVE_TIMEOUT_SECONDS);
					nextScene(client);
				}
			}
			default -> {}
		}
	}

	/**
	 * Runs a client-side command (spark's /sparkc) through the loader's client dispatcher. It never reaches the
	 * server: a command the dispatcher doesn't know just fails here.
	 */
	static void clientCommand(String command) {
		String error = Services.PLATFORM.runClientCommand(command);
		if (error != null) {
			LOG.error("CakeBench: client command '{}' failed (nothing was sent to the server): {}", command, error);
		}
	}

	/** A plan is driving the game (between its join and its end). */
	static boolean planActive() {
		return plan != null && phase != Phase.IDLE && phase != Phase.DONE;
	}

	/** The spark profile saved since {@code since} (epoch ms), if any (config/spark/profile-*.sparkprofile). */
	static Path newestProfile(long since) {
		Path dir = Services.PLATFORM.configDir().resolve("spark");
		if (!Files.isDirectory(dir)) {
			return null;
		}
		try (var files = Files.list(dir)) {
			return files.filter(f -> f.getFileName().toString().endsWith(".sparkprofile"))
					.filter(f -> f.toFile().lastModified() >= since)
					.max(java.util.Comparator.comparingLong(f -> f.toFile().lastModified()))
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static void enterScene(Minecraft client, int index) {
		scene = index;
		Plan.Scene s = plan.scenes.get(index);
		if (plan.live) {
			// a real server: never send anything, start from where the character already looks
			baseYaw = client.player.getYRot();
			basePitch = client.player.getXRot();
		} else {
			client.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f",
					s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
			baseYaw = s.yaw();
			basePitch = s.pitch();
		}
		LOG.info("CakeBench scene '{}'", s.name());
		start(Phase.WARMUP);
	}

	private static void turn(LocalPlayer player, double seconds) {
		float yaw = (float) (baseYaw + plan.turnDegreesPerSecond * seconds);
		player.yRotO = yaw;
		player.setYRot(yaw);
		player.xRotO = basePitch;
		player.setXRot(basePitch);
	}

	private static void record(float ms) {
		if (frameCount == frames.length) {
			frames = Arrays.copyOf(frames, frames.length * 2);
		}
		frames[frameCount++] = ms;
	}

	private static void finishScene(Minecraft client, double seconds) {
		Plan.Scene s = plan.scenes.get(scene);
		float[] f = Arrays.copyOf(frames, frameCount);
		long[] gc = BenchStats.gc();
		Map<String, Object> gpu = GpuTimer.stop();
		Map<String, Object> r = BenchStats.stats(f, seconds);
		r.put("gcCount", gc[0] - gcCount0);
		r.put("gcMillis", gc[1] - gcMillis0);
		r.put("heapPeakMB", heapPeak >> 20);
		r.put("unfocusedFrames", unfocusedFrames);
		r.put("minimizedFrames", minimizedFrames);
		describeWorld(client, r);
		if (gpu != null) {
			r.put("gpu", gpu);
		}
		results.put(s.name(), r);
		Path dir = Path.of(plan.out, plan.run);
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(s.name() + ".csv"), BenchStats.csv(f));
		} catch (IOException e) {
			LOG.error("Could not write frame times", e);
		}
		LOG.info("CakeBench scene '{}': {}", s.name(), r);
		if (plan.spark) {
			clientCommand("sparkc profiler stop --save-to-file");
			sparkSaved = null;
			start(Phase.SAVING);
		} else {
			nextScene(client);
		}
	}

	private static void nextScene(Minecraft client) {
		if (scene + 1 < plan.scenes.size()) {
			enterScene(client, scene + 1);
		} else {
			phase = Phase.DONE;
			writeSummary(client, Path.of(plan.out, plan.run));
			client.stop();
		}
	}

	/** What the client actually had at the end of a measurement (empty when it's no longer in a world). */
	static void describeWorld(Minecraft client, Map<String, Object> r) {
		if (client.level == null || client.player == null) {
			return;
		}
		// an empty or half-built scene shows here
		r.put("entitiesLoaded", client.level.getEntityCount());
		r.put("entitiesDebug", Compat.entityStatistics(client));
		// on a live server the scene is whatever is there: where, and how many other players were around
		r.put("dimension", Compat.dimension(client.level));
		r.put("position", String.format(Locale.ROOT, "%.0f %.0f %.0f", client.player.getX(), client.player.getY(), client.player.getZ()));
		r.put("playersNearby", client.level.players().size() - 1);
	}

	private static void writeSummary(Minecraft client, Path dir) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("run", plan.run);
		out.put("notes", plan.notes);
		out.putAll(environment(client));
		out.put("scenes", results);
		try {
			Files.writeString(dir.resolve("summary.json"), BenchStats.GSON.toJson(out));
		} catch (IOException e) {
			LOG.error("Could not write the summary", e);
		}
	}

	/** The renderer, Java, settings and mods a recording ran with. */
	static Map<String, Object> environment(Minecraft client) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("renderer", Compat.renderer());
		out.put("glVersion", Compat.glVersion());
		out.put("java", System.getProperty("java.version"));
		out.put("maxHeapMB", Runtime.getRuntime().maxMemory() >> 20);
		out.put("cpus", Runtime.getRuntime().availableProcessors());
		out.put("renderDistance", client.options.renderDistance().get());
		out.put("graphics", Compat.graphics(client));
		out.put("maxFps", client.options.framerateLimit().get());
		out.put("mods", Services.PLATFORM.mods());
		return out;
	}

	private static void start(Phase next) {
		phase = next;
		phaseStart = System.nanoTime();
	}
}
