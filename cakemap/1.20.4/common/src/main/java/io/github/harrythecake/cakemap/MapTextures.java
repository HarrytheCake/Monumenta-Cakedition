package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.RegionShader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * GPU textures of map regions, full size (512 px) and quarter size (128 px, for zoomed-out views).
 * Pixels are worked out on a worker thread and uploaded on the render thread, a few per frame.
 * Least recently drawn textures are freed first.
 */
public final class MapTextures {
	static final ExecutorService SHADE = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "CakeMap shading");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private static final int MAX_FULL = 40;
	private static final int MAX_QUARTER = 600;
	private static final int UPLOADS_PER_FRAME = 3;

	/** Pixels for one region: full 512 x 512 ARGB and its quarter, or null when there is nothing. */
	public record Pixels(int[] full, int[] quarter) {
	}

	public static final class Tex {
		final String key;
		ResourceLocation fullId;
		ResourceLocation quarterId;
		DynamicTexture full;
		DynamicTexture quarter;
		/** Revision of the data it shows (-1 = never built). */
		int revision = -1;
		int colorGeneration = -1;
		boolean building;
		boolean empty;
		/** Freed by {@link #clear}: a build still running must not upload. */
		boolean dead;
		long lastDrawn;
		long lastBuild;
		Pixels pending;
		int pendingRevision;

		Tex(String key) {
			this.key = key;
		}

		public boolean ready() {
			return !empty && (full != null || quarter != null);
		}

		public ResourceLocation id(boolean wantFull) {
			if (wantFull && full != null) {
				return fullId;
			}
			if (quarter != null) {
				return quarterId;
			}
			return fullId;
		}

		/** Pixels across the texture {@link #id} gives. */
		public int texels(boolean wantFull) {
			return wantFull && full != null || quarter == null ? RegionData.SIZE : RegionData.SIZE / 4;
		}

		public boolean hasFull() {
			return full != null;
		}
	}

	private static final Map<String, Tex> TEXTURES = new HashMap<>();
	private static final List<Tex> UPLOAD = new ArrayList<>();
	private static int counter;

	private MapTextures() {
	}

	/**
	 * The texture for {@code key}, built or rebuilt when {@code revision} differs from the one shown.
	 * {@code source} runs on a worker and gives the pixels (or null for nothing there).
	 */
	public static Tex get(String key, int revision, boolean needFull, Supplier<CompletableFuture<Pixels>> source) {
		Tex t = TEXTURES.computeIfAbsent(key, Tex::new);
		t.lastDrawn = System.currentTimeMillis();
		int gen = GameColors.INSTANCE.generation;
		boolean missing = t.revision == -1 || t.colorGeneration != gen || (needFull && t.full == null && !t.empty);
		// a region being scanned changes every few ticks: rebuild it at most once a second
		boolean changed = t.revision != revision && t.lastDrawn - t.lastBuild > 1000;
		if ((missing || changed) && !t.building) {
			t.building = true;
			t.lastBuild = t.lastDrawn;
			int rev = revision;
			CompletableFuture<Pixels> f;
			try {
				f = source.get();
			} catch (RuntimeException e) {
				f = CompletableFuture.failedFuture(e);
			}
			f.whenComplete((px, err) -> CakeMap.onRender(() -> {
				if (t.dead) {
					return;
				}
				if (err != null) {
					CakeMap.LOG.warn("CakeMap: map texture {} failed: {}", key, err.toString());
					t.revision = rev;
					t.colorGeneration = gen;
					t.building = false;
					t.empty = true;
					return;
				}
				t.pending = px;
				t.pendingRevision = rev;
				t.colorGeneration = gen;
				UPLOAD.add(t);
			}));
		}
		return t;
	}

	/** Shades a region on a worker (colours prepared on the render thread first). */
	public static CompletableFuture<Pixels> shade(RegionData d) {
		if (d == null) {
			return CompletableFuture.completedFuture(null);
		}
		RegionShader.Colors colors = GameColors.INSTANCE.prepare(d.states(), d.biomes());
		return CompletableFuture.supplyAsync(() -> {
			int[] full = RegionShader.shade(d, colors);
			return new Pixels(full, RegionShader.quarter(full, RegionData.SIZE));
		}, SHADE);
	}

	/** Render thread, once per frame: upload a few finished textures, free old ones. */
	public static void frame() {
		int n = 0;
		while (!UPLOAD.isEmpty() && n < UPLOADS_PER_FRAME) {
			Tex t = UPLOAD.remove(0);
			upload(t);
			n++;
		}
		if (TEXTURES.size() > MAX_QUARTER || countFull() > MAX_FULL) {
			evict();
		}
	}

	private static void upload(Tex t) {
		Pixels px = t.pending;
		t.pending = null;
		t.revision = t.pendingRevision;
		t.building = false;
		if (px == null || px.full == null) {
			t.empty = true;
			release(t);
			return;
		}
		t.empty = false;
		if (px.full.length == RegionData.AREA) {
			t.full = put(t.full, px.full, RegionData.SIZE);
			if (t.fullId == null) {
				t.fullId = register(t.full);
			}
		}
		if (px.quarter != null) {
			t.quarter = put(t.quarter, px.quarter, RegionData.SIZE / 4);
			if (t.quarterId == null) {
				t.quarterId = register(t.quarter);
			}
		}
	}

	private static DynamicTexture put(DynamicTexture tex, int[] argb, int size) {
		DynamicTexture t = tex;
		if (t == null) {
			t = new DynamicTexture(size, size, false);
		}
		NativeImage img = t.getPixels();
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				int c = argb[z * size + x];
				// ARGB → ABGR
				img.setPixelRGBA(x, z, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
			}
		}
		t.upload();
		return t;
	}

	private static ResourceLocation register(DynamicTexture t) {
		ResourceLocation id = new ResourceLocation(CakeMap.MOD_ID, "map/" + (counter++));
		Minecraft.getInstance().getTextureManager().register(id, t);
		return id;
	}

	private static int countFull() {
		int n = 0;
		for (Tex t : TEXTURES.values()) {
			if (t.full != null) {
				n++;
			}
		}
		return n;
	}

	private static void evict() {
		List<Tex> all = new ArrayList<>(TEXTURES.values());
		all.sort((a, b) -> Long.compare(a.lastDrawn, b.lastDrawn));
		int full = countFull();
		long now = System.currentTimeMillis();
		for (Tex t : all) {
			if (now - t.lastDrawn < 2000 || t.building) {
				continue;
			}
			if (TEXTURES.size() > MAX_QUARTER) {
				t.dead = true;
				release(t);
				TEXTURES.remove(t.key);
				if (t.full != null) {
					full--;
				}
			} else if (full > MAX_FULL && t.full != null) {
				Minecraft.getInstance().getTextureManager().release(t.fullId);
				t.full = null;
				t.fullId = null;
				full--;
			} else {
				break;
			}
		}
	}

	private static void release(Tex t) {
		var tm = Minecraft.getInstance().getTextureManager();
		if (t.fullId != null) {
			tm.release(t.fullId);
		}
		if (t.quarterId != null) {
			tm.release(t.quarterId);
		}
		t.full = null;
		t.quarter = null;
		t.fullId = null;
		t.quarterId = null;
	}

	/** Frees every texture whose key starts with {@code prefix} (a world or static set going away). */
	public static void clear(String prefix) {
		TEXTURES.values().removeIf(t -> {
			if (t.key.startsWith(prefix)) {
				t.dead = true;
				release(t);
				UPLOAD.remove(t);
				return true;
			}
			return false;
		});
	}

	public static void clearAll() {
		clear("");
	}
}
