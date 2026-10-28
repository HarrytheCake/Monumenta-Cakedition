package io.github.harrythecake.cakebench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.GlDebugInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Benchmark harness. With {@code config/cakebench/plan.json} present it waits for the join and for operator rights
 * (the driver ops the player from the server console), then for each scene: teleports there, turns slowly through
 * warm-up and measurement, records every frame's time while measuring, and finally writes the results and quits.
 * Without a plan it does nothing.
 * <p>
 * A {@code live} plan joins a real server (e.g. Monumenta) instead: it sends <b>no chat and no commands</b>, needs no
 * operator rights and never moves; its one scene starts from wherever the character stands and only turns the camera.
 * The server's resource pack is declined, so a prompt can't block the join.
 */
public final class CakeBench implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("cakebench");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	record Scene(String name, double x, double y, double z, float yaw, float pitch) {}

	static final class Plan {
		String run = "run";
		String out = "cakebench-results";
		String server;
		boolean live;
		int settleSeconds = 15;
		int warmupSeconds = 20;
		int measureSeconds = 60;
		float turnDegreesPerSecond = 6;
		boolean gpuTimers; // per-phase GPU/CPU time (GpuTimer); off for timed runs, the queries cost a little
		int gpuTimerDepth = 4;
		List<Scene> scenes = List.of();
		Map<String, Object> notes = Map.of();
	}

	private enum Phase { IDLE, SETTLE, WARMUP, MEASURE, DONE }

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
	private static final Map<String, Object> results = new LinkedHashMap<>();

	@Override
	public void onInitializeClient() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("cakebench").resolve("plan.json");
		if (!Files.exists(file)) {
			return;
		}
		try {
			plan = GSON.fromJson(Files.readString(file), Plan.class);
		} catch (Exception e) {
			LOG.error("Could not read {}", file, e);
			return;
		}
		LOG.info("CakeBench plan '{}': {} scenes", plan.run, plan.scenes.size());
		if (plan.gpuTimers) {
			GpuTimer.enable(plan.gpuTimerDepth);
		}
		// 1.20 ignores the launcher's --server argument, so join the bench server from the title screen ourselves
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof TitleScreen && phase == Phase.IDLE && !connecting && plan.server != null) {
				connecting = true;
				ServerInfo info = new ServerInfo("CakeBench", plan.server, ServerInfo.ServerType.OTHER);
				if (plan.live) {
					info.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.DISABLED);
				}
				client.send(() -> ConnectScreen.connect(screen, client, ServerAddress.parse(plan.server), info, false));
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (phase == Phase.IDLE) {
				start(Phase.SETTLE);
			}
		});
	}

	/** Called at the end of every frame. */
	public static void onFrame(MinecraftClient client) {
		long now = System.nanoTime();
		long dt = now - lastFrame;
		lastFrame = now;
		if (plan == null || phase == Phase.IDLE || phase == Phase.DONE) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		double elapsed = (now - phaseStart) / 1e9;
		switch (phase) {
			case SETTLE -> {
				// the driver ops the player when it sees the join; wait for that and for the first chunks
				// (a live server never ops anyone: just wait)
				if (elapsed >= plan.settleSeconds && (plan.live || player.hasPermissionLevel(2))) {
					if (plan.scenes.isEmpty()) { // a priming run: only lets yosbr and the mods write their files
						phase = Phase.DONE;
						Path dir = Path.of(plan.out, plan.run);
						try {
							Files.createDirectories(dir);
						} catch (IOException e) {
							LOG.error("Could not create {}", dir, e);
						}
						writeSummary(client, dir);
						client.scheduleStop();
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
					long[] gc = gc();
					gcCount0 = gc[0];
					gcMillis0 = gc[1];
					GpuTimer.start();
					start(Phase.MEASURE);
				}
			}
			case MEASURE -> {
				turn(player, plan.warmupSeconds + elapsed);
				record(dt / 1e6f);
				// a covered, minimized or unfocused window renders differently: count it so such runs can be flagged
				if (!client.isWindowFocused()) {
					unfocusedFrames++;
				}
				if (GLFW.glfwGetWindowAttrib(client.getWindow().getHandle(), GLFW.GLFW_ICONIFIED) != 0) {
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
			default -> {}
		}
	}

	private static void enterScene(MinecraftClient client, int index) {
		scene = index;
		Scene s = plan.scenes.get(index);
		if (plan.live) {
			// a real server: never send anything, start from where the character already looks
			baseYaw = client.player.getYaw();
			basePitch = client.player.getPitch();
		} else {
			client.player.networkHandler.sendChatCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f",
					s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
			baseYaw = s.yaw();
			basePitch = s.pitch();
		}
		LOG.info("CakeBench scene '{}'", s.name());
		start(Phase.WARMUP);
	}

	private static void turn(ClientPlayerEntity player, double seconds) {
		float yaw = (float) (baseYaw + plan.turnDegreesPerSecond * seconds);
		player.prevYaw = yaw;
		player.setYaw(yaw);
		player.prevPitch = basePitch;
		player.setPitch(basePitch);
	}

	private static void record(float ms) {
		if (frameCount == frames.length) {
			frames = Arrays.copyOf(frames, frames.length * 2);
		}
		frames[frameCount++] = ms;
	}

	private static void finishScene(MinecraftClient client, double seconds) {
		Scene s = plan.scenes.get(scene);
		float[] f = Arrays.copyOf(frames, frameCount);
		long[] gc = gc();
		Map<String, Object> gpu = GpuTimer.stop();
		Map<String, Object> r = stats(f, seconds);
		r.put("gcCount", gc[0] - gcCount0);
		r.put("gcMillis", gc[1] - gcMillis0);
		r.put("heapPeakMB", heapPeak >> 20);
		r.put("unfocusedFrames", unfocusedFrames);
		r.put("minimizedFrames", minimizedFrames);
		// what the client actually had: an empty or half-built scene shows here
		r.put("entitiesLoaded", client.world.getRegularEntityCount());
		r.put("entitiesDebug", client.worldRenderer.getEntitiesDebugString());
		// on a live server the scene is whatever is there: where, and how many other players were around
		r.put("dimension", client.world.getRegistryKey().getValue().toString());
		r.put("position", String.format(Locale.ROOT, "%.0f %.0f %.0f", client.player.getX(), client.player.getY(), client.player.getZ()));
		r.put("playersNearby", client.world.getPlayers().size() - 1);
		if (gpu != null) {
			r.put("gpu", gpu);
		}
		results.put(s.name(), r);
		Path dir = Path.of(plan.out, plan.run);
		try {
			Files.createDirectories(dir);
			StringBuilder csv = new StringBuilder("frame_ms\n");
			for (float v : f) {
				csv.append(String.format(Locale.ROOT, "%.3f%n", v));
			}
			Files.writeString(dir.resolve(s.name() + ".csv"), csv);
		} catch (IOException e) {
			LOG.error("Could not write frame times", e);
		}
		LOG.info("CakeBench scene '{}': {}", s.name(), r);
		if (scene + 1 < plan.scenes.size()) {
			enterScene(client, scene + 1);
		} else {
			phase = Phase.DONE;
			writeSummary(client, dir);
			client.scheduleStop();
		}
	}

	static Map<String, Object> stats(float[] f, double seconds) {
		Map<String, Object> r = new LinkedHashMap<>();
		float[] sorted = f.clone();
		Arrays.sort(sorted);
		double sum = 0;
		for (float v : f) {
			sum += v;
		}
		int worst = Math.max(1, sorted.length / 100);
		double worstSum = 0;
		for (int i = sorted.length - worst; i < sorted.length; i++) {
			worstSum += sorted[i];
		}
		int over100 = 0;
		for (float v : f) {
			if (v > 100) {
				over100++;
			}
		}
		r.put("frames", f.length);
		r.put("avgFps", round(f.length / seconds));
		r.put("onePercentLowFps", round(1000 / (worstSum / worst)));
		r.put("medianFrameMs", round(sorted.length == 0 ? 0 : sorted[sorted.length / 2]));
		r.put("maxFrameMs", round(sorted.length == 0 ? 0 : sorted[sorted.length - 1]));
		r.put("framesOver100ms", over100);
		r.put("meanFrameMs", round(f.length == 0 ? 0 : sum / f.length));
		return r;
	}

	private static void writeSummary(MinecraftClient client, Path dir) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("run", plan.run);
		out.put("notes", plan.notes);
		out.put("renderer", GlDebugInfo.getRenderer());
		out.put("glVersion", GlDebugInfo.getVersion());
		out.put("java", System.getProperty("java.version"));
		out.put("maxHeapMB", Runtime.getRuntime().maxMemory() >> 20);
		out.put("cpus", Runtime.getRuntime().availableProcessors());
		out.put("renderDistance", client.options.getViewDistance().getValue());
		out.put("graphics", client.options.getGraphicsMode().getValue().toString());
		out.put("maxFps", client.options.getMaxFps().getValue());
		Map<String, String> mods = new TreeMap<>();
		FabricLoader.getInstance().getAllMods().forEach(m -> {
			String id = m.getMetadata().getId();
			if (!id.startsWith("fabric-") && !id.equals("java") && !id.equals("minecraft")) {
				mods.put(id, m.getMetadata().getVersion().getFriendlyString());
			}
		});
		out.put("mods", mods);
		out.put("scenes", results);
		try {
			Files.writeString(dir.resolve("summary.json"), GSON.toJson(out));
		} catch (IOException e) {
			LOG.error("Could not write the summary", e);
		}
	}

	private static long[] gc() {
		long count = 0, millis = 0;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			count += Math.max(0, b.getCollectionCount());
			millis += Math.max(0, b.getCollectionTime());
		}
		return new long[] {count, millis};
	}

	private static double round(double v) {
		return Math.round(v * 10) / 10.0;
	}

	private static void start(Phase next) {
		phase = next;
		phaseStart = System.nanoTime();
	}
}
