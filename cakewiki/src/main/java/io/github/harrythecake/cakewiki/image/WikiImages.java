package io.github.harrythecake.cakewiki.image;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.harrythecake.cakewiki.CakeWiki;
import io.github.harrythecake.cakewiki.doc.Image;
import io.github.harrythecake.cakewiki.net.WikiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Wiki pictures as textures. A picture is fetched the first time it is drawn (from the disk cache if there),
 * decoded off the render thread, then uploaded. Loaded pictures are kept least-recently-drawn first and
 * dropped beyond a pixel budget. Every method runs on the render thread.
 */
public final class WikiImages {
	private static final int MAX_PARALLEL = 6;
	private static final long PIXEL_BUDGET = 24L * 1024 * 1024; // about 96 MB of textures
	private static final long RETRY_MS = 60_000;
	private static final int MAX_BYTES = 8 * 1024 * 1024;

	private sealed interface State {}

	private record Loading() implements State {}

	private record Failed(long at) implements State {}

	private record Ready(Identifier[] frames, int[] delays, int cycleMs, int w, int h, int naturalW, int naturalH) implements State {
		long pixels() {
			return (long) w * h * frames.length;
		}
	}

	private final Map<String, State> states = new LinkedHashMap<>(64, 0.75f, true);
	private final Deque<String> queue = new ArrayDeque<>();
	private final WikiClient.Transport transport;
	private final Path dir;
	private int running;
	private long pixels;
	private int version;

	public WikiImages(WikiClient.Transport transport, Path dir) {
		this.transport = transport;
		this.dir = dir;
	}

	/** Goes up whenever a picture finishes loading; the reader lays out again if it guessed some size. */
	public int version() {
		return version;
	}

	/** Pictures asked for but not loaded yet (downloading or waiting their turn). */
	public int pending() {
		int n = 0;
		for (State s : states.values()) if (s instanceof Loading) n++;
		return n;
	}

	/** The picture's size before scaling, once loaded; asks for it otherwise. */
	public int[] naturalSize(Image img) {
		State s = state(img.url());
		return s instanceof Ready r ? new int[] {r.naturalW(), r.naturalH()} : null;
	}

	public void draw(DrawContext ctx, Image img, int x, int y, int w, int h) {
		State s = state(img.url());
		if (!(s instanceof Ready r)) {
			ctx.fill(x, y, x + w, y + h, s instanceof Failed ? 0x30FF6060 : 0x26FFFFFF);
			return;
		}
		int frame = 0;
		if (r.frames().length > 1 && r.cycleMs() > 0) {
			int t = (int) (Util.getMeasuringTimeMs() % r.cycleMs());
			while (frame < r.frames().length - 1 && t >= r.delays()[frame]) t -= r.delays()[frame++];
		}
		RenderSystem.enableBlend();
		ctx.drawTexture(r.frames()[frame], x, y, w, h, 0, 0, r.w(), r.h(), r.w(), r.h());
		RenderSystem.disableBlend();
	}

	/** Frees every texture (when the reader shuts down for good, e.g. on disconnect). */
	public void clear() {
		for (State s : states.values()) if (s instanceof Ready r) release(r);
		states.clear();
		queue.clear();
		pixels = 0;
	}

	// ---- loading ----------------------------------------------------------------------------------------

	private State state(String url) {
		State s = states.get(url);
		if (s == null || s instanceof Failed f && Util.getMeasuringTimeMs() - f.at() > RETRY_MS) {
			s = new Loading();
			states.put(url, s);
			queue.add(url);
			pump();
		}
		return s;
	}

	private void pump() {
		while (running < MAX_PARALLEL && !queue.isEmpty()) {
			String url = queue.poll();
			running++;
			fetch(url).thenApplyAsync(bytes -> {
				try {
					return ImageDecoder.decode(bytes);
				} catch (IOException e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			}, Util.getMainWorkerExecutor()).whenComplete((decoded, error) -> MinecraftClient.getInstance().execute(() -> {
				running--;
				if (states.get(url) instanceof Loading) { // not cleared meanwhile
					if (error != null) {
						CakeWiki.LOG.debug("Picture {} failed: {}", url, error.toString());
						states.put(url, new Failed(Util.getMeasuringTimeMs()));
					} else {
						upload(url, decoded);
					}
				}
				pump();
			}));
		}
	}

	private CompletableFuture<byte[]> fetch(String url) {
		Path file = dir.resolve(hash(url) + ".bin");
		return CompletableFuture.supplyAsync(() -> {
			try {
				return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
			} catch (IOException e) {
				return null;
			}
		}, Util.getIoWorkerExecutor()).thenCompose(cached -> {
			if (cached != null) return CompletableFuture.completedFuture(cached);
			return transport.get(URI.create(url)).thenApply(bytes -> {
				if (bytes.length > MAX_BYTES) throw new IllegalStateException("picture too large: " + bytes.length + " bytes");
				try {
					Files.createDirectories(dir);
					Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
					Files.write(tmp, bytes);
					Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException e) {
					CakeWiki.LOG.debug("Couldn't cache picture {}: {}", url, e.toString());
				}
				return bytes;
			});
		});
	}

	private void upload(String url, ImageDecoder.Decoded d) {
		String key = hash(url);
		Identifier[] ids = new Identifier[d.frames().size()];
		int[] delays = new int[ids.length];
		int cycle = 0;
		for (int i = 0; i < ids.length; i++) {
			ImageDecoder.Frame f = d.frames().get(i);
			NativeImage img = new NativeImage(d.width(), d.height(), false);
			int[] px = f.argb();
			for (int y = 0; y < d.height(); y++) {
				for (int x = 0; x < d.width(); x++) {
					int p = px[y * d.width() + x];
					// NativeImage stores ABGR
					img.setColor(x, y, (p & 0xFF00FF00) | (p & 0xFF) << 16 | (p >> 16) & 0xFF);
				}
			}
			ids[i] = new Identifier(CakeWiki.MOD_ID, "wiki/" + key + "/" + i);
			MinecraftClient.getInstance().getTextureManager().registerTexture(ids[i], new NativeImageBackedTexture(img));
			delays[i] = f.delayMs();
			cycle += f.delayMs();
		}
		Ready r = new Ready(ids, delays, cycle, d.width(), d.height(), d.naturalWidth(), d.naturalHeight());
		states.put(url, r);
		pixels += r.pixels();
		version++;
		trim(url);
	}

	/** Drops least recently drawn pictures until under budget, never the one just loaded. */
	private void trim(String keep) {
		Iterator<Map.Entry<String, State>> it = states.entrySet().iterator();
		while (pixels > PIXEL_BUDGET && it.hasNext()) {
			Map.Entry<String, State> e = it.next();
			if (e.getKey().equals(keep) || !(e.getValue() instanceof Ready r)) continue;
			release(r);
			pixels -= r.pixels();
			it.remove();
		}
	}

	private static void release(Ready r) {
		for (Identifier id : r.frames()) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
	}

	private static String hash(String url) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
