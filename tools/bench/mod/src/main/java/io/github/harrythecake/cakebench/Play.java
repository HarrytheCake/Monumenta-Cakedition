package io.github.harrythecake.cakebench;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * A recording while you play, without a plan: {@code /cakebench start [name]}, play, {@code /cakebench stop}. In
 * between it records every frame's time, GPU/CPU time per render phase ({@link GpuTimer}) and, with spark installed,
 * a spark CPU profile of every thread. Stop writes them to {@code cakebench-results/play/<date>-<name>/} and sums them
 * up in chat. Purely local: a client command never reaches the server, and the spark profile is saved to a file
 * (never uploaded). A shard switch is no disconnect, so one recording can span several; leaving the server saves it.
 */
final class Play {
	private static final Logger LOG = LoggerFactory.getLogger("cakebench");
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
	private static final double SAVE_TIMEOUT_SECONDS = 30;
	private static final int CHAT_PHASES = 6;

	private static boolean recording, gpuStartPending, spark;
	private static long started;
	private static String name;
	private static float[] frames = new float[1 << 16];
	private static int frameCount, unfocusedFrames, minimizedFrames, screenFrames;
	private static long gcCount0, gcMillis0, heapPeak;

	// after stop: waiting for spark to write its profile
	private static Path saving;
	private static long sparkSince, savingSince, sparkSeenAt;
	private static Path sparkSaved;

	private Play() {}

	static boolean recording() {
		return recording;
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("cakebench")
				.then(literal("start")
						.executes(c -> start(c.getSource(), "play"))
						.then(argument("name", StringArgumentType.word())
								.executes(c -> start(c.getSource(), StringArgumentType.getString(c, "name")))))
				.then(literal("stop").executes(c -> stop(c.getSource().getClient())))
				.then(literal("cancel").executes(c -> cancel(c.getSource())))
				.then(literal("status").executes(c -> status(c.getSource()))));
	}

	private static int start(FabricClientCommandSource source, String label) {
		if (CakeBench.planActive()) {
			source.sendError(Text.literal("CakeBench: a benchmark plan is running; its recordings come first."));
			return 0;
		}
		if (recording || saving != null) {
			source.sendError(Text.literal("CakeBench: already " + (recording ? "recording" : "saving")
					+ ". /cakebench stop or /cakebench cancel first."));
			return 0;
		}
		name = label;
		frameCount = unfocusedFrames = minimizedFrames = screenFrames = 0;
		heapPeak = 0;
		long[] gc = CakeBench.gc();
		gcCount0 = gc[0];
		gcMillis0 = gc[1];
		// the timer only learns whether the driver has timestamp queries when it wraps the next frame's profiler
		GpuTimer.enable(CakeBench.GPU_TIMER_DEPTH);
		gpuStartPending = true;
		spark = FabricLoader.getInstance().isModLoaded("spark");
		if (spark) {
			sparkSince = System.currentTimeMillis();
			CakeBench.clientCommand(source.getClient(), "sparkc profiler start --thread *");
		}
		started = System.nanoTime();
		recording = true;
		source.sendFeedback(Text.literal("CakeBench: recording '" + name + "' (frame times, GPU timers"
				+ (spark ? ", spark CPU profile" : "; spark isn't installed, so no CPU profile")
				+ "). /cakebench stop saves it.").formatted(Formatting.GOLD));
		return 1;
	}

	private static int status(FabricClientCommandSource source) {
		if (recording) {
			double s = (System.nanoTime() - started) / 1e9;
			source.sendFeedback(Text.literal(String.format(Locale.ROOT, "CakeBench: recording '%s' for %.0f s, %d frames",
					name, s, frameCount)));
		} else {
			source.sendFeedback(Text.literal(saving != null ? "CakeBench: saving the spark profile..."
					: "CakeBench: not recording. /cakebench start [name]"));
		}
		return 1;
	}

	private static int cancel(FabricClientCommandSource source) {
		if (!recording) {
			source.sendError(Text.literal("CakeBench: not recording."));
			return 0;
		}
		recording = false;
		GpuTimer.stop();
		if (spark) {
			CakeBench.clientCommand(source.getClient(), "sparkc profiler cancel");
		}
		source.sendFeedback(Text.literal("CakeBench: recording '" + name + "' thrown away."));
		return 1;
	}

	/** Also called when leaving the server, so a recording isn't lost. */
	static int stop(MinecraftClient client) {
		if (!recording) {
			say(client, Text.literal("CakeBench: not recording.").formatted(Formatting.RED));
			return 0;
		}
		recording = false;
		double seconds = (System.nanoTime() - started) / 1e9;
		float[] f = Arrays.copyOf(frames, frameCount);
		long[] gc = CakeBench.gc();
		Map<String, Object> gpu = GpuTimer.stop();
		Map<String, Object> r = CakeBench.stats(f, seconds);
		r.put("seconds", Math.round(seconds));
		r.put("gcCount", gc[0] - gcCount0);
		r.put("gcMillis", gc[1] - gcMillis0);
		r.put("heapPeakMB", heapPeak >> 20);
		r.put("unfocusedFrames", unfocusedFrames);
		r.put("minimizedFrames", minimizedFrames);
		r.put("screenFrames", screenFrames); // a menu or inventory open: it renders differently
		CakeBench.describeWorld(client, r);
		if (gpu != null) {
			r.put("gpu", gpu);
		}
		Path dir = FabricLoader.getInstance().getGameDir().resolve("cakebench-results").resolve("play")
				.resolve(LocalDateTime.now().format(STAMP) + "-" + name);
		Map<String, Object> out = CakeBench.environment(client);
		out.put("run", name);
		out.put("play", true);
		out.put("scenes", Map.of("play", r));
		try {
			Files.createDirectories(dir);
			StringBuilder csv = new StringBuilder("frame_ms\n");
			for (float v : f) {
				csv.append(String.format(Locale.ROOT, "%.3f%n", v));
			}
			Files.writeString(dir.resolve("play.csv"), csv);
			Files.writeString(dir.resolve("summary.json"), CakeBench.GSON.toJson(out));
		} catch (IOException e) {
			LOG.error("Could not write the recording to {}", dir, e);
		}
		LOG.info("CakeBench recording '{}': {}", name, r);
		say(client, summary(r, gpu));
		if (spark) {
			CakeBench.clientCommand(client, "sparkc profiler stop --save-to-file");
			saving = dir;
			savingSince = System.nanoTime();
			sparkSaved = null;
		} else {
			saved(client, dir);
		}
		return 1;
	}

	/** Called at the end of every frame. */
	static void onFrame(MinecraftClient client, long dt) {
		if (recording) {
			if (gpuStartPending) {
				gpuStartPending = false;
				GpuTimer.start();
			}
			if (frameCount == frames.length) {
				frames = Arrays.copyOf(frames, frames.length * 2);
			}
			frames[frameCount++] = dt / 1e6f;
			if (!client.isWindowFocused()) {
				unfocusedFrames++;
			}
			if (GLFW.glfwGetWindowAttrib(client.getWindow().getHandle(), GLFW.GLFW_ICONIFIED) != 0) {
				minimizedFrames++;
			}
			if (client.currentScreen != null) {
				screenFrames++;
			}
			if ((frameCount & 63) == 0) {
				Runtime rt = Runtime.getRuntime();
				heapPeak = Math.max(heapPeak, rt.totalMemory() - rt.freeMemory());
			}
		} else if (saving != null) {
			// spark writes the file in the background: wait until it appears, then a moment for the write to end
			long now = System.nanoTime();
			if (sparkSaved == null) {
				sparkSaved = CakeBench.newestProfile(sparkSince);
				sparkSeenAt = now;
			} else if ((now - sparkSeenAt) / 1e9 >= 2) {
				try {
					Files.copy(sparkSaved, saving.resolve("play.sparkprofile"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					LOG.error("Could not copy the spark profile", e);
				}
				Path dir = saving;
				saving = null;
				saved(client, dir);
			}
			if (saving != null && (now - savingSince) / 1e9 >= SAVE_TIMEOUT_SECONDS) {
				LOG.warn("CakeBench: no spark profile appeared within {} s", SAVE_TIMEOUT_SECONDS);
				Path dir = saving;
				saving = null;
				say(client, Text.literal("CakeBench: spark saved no profile; the rest is saved.").formatted(Formatting.RED));
				saved(client, dir);
			}
		}
	}

	private static Text summary(Map<String, Object> r, Map<String, Object> gpu) {
		MutableText t = Text.literal(String.format(Locale.ROOT,
				"CakeBench '%s': %s s, avg %s FPS, 1%% low %s, worst frame %s ms, %s frames over 100 ms, GC %s ms",
				name, r.get("seconds"), r.get("avgFps"), r.get("onePercentLowFps"), r.get("maxFrameMs"),
				r.get("framesOver100ms"), r.get("gcMillis"))).formatted(Formatting.GOLD);
		if (gpu != null && gpu.get("phases") instanceof Map<?, ?> phases && !phases.isEmpty()) {
			// the costliest phases either way: a CPU-bound phase matters as much as a GPU-bound one
			List<Map.Entry<?, ?>> rows = new ArrayList<>(phases.entrySet());
			rows.sort((a, b) -> Double.compare(cost(b.getValue()), cost(a.getValue())));
			t.append(Text.literal("\nms per frame, GPU / CPU:").formatted(Formatting.GRAY));
			for (Map.Entry<?, ?> e : rows.subList(0, Math.min(CHAT_PHASES, rows.size()))) {
				Map<?, ?> v = (Map<?, ?>) e.getValue();
				t.append(Text.literal(String.format(Locale.ROOT, "\n  %s  %s / %s", e.getKey(), v.get("gpuMs"),
						v.get("cpuMs"))).formatted(Formatting.GRAY));
			}
		}
		return t;
	}

	private static double cost(Object phase) {
		Map<?, ?> v = (Map<?, ?>) phase;
		return Math.max(((Number) v.get("gpuMs")).doubleValue(), ((Number) v.get("cpuMs")).doubleValue());
	}

	private static void saved(MinecraftClient client, Path dir) {
		say(client, Text.literal("CakeBench: saved to cakebench-results/play/" + dir.getFileName() + " ")
				.append(Text.literal("[Open folder]").setStyle(Style.EMPTY.withColor(Formatting.AQUA).withUnderline(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, dir.toAbsolutePath().toString())))));
	}

	/** Chat for the player only (never sent), or the log when there's no player. */
	private static void say(MinecraftClient client, Text text) {
		if (client.player != null) {
			client.player.sendMessage(text, false);
		} else {
			LOG.info(text.getString());
		}
	}
}
