package io.github.harrythecake.cakecosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Background textures, decoded off the render thread one at a time (so at most one big PNG is in
 * memory while decoding). Full pictures are shrunk to the window size and at most two are kept
 * (the one shown and the next for a fade); thumbnails are made once per resource reload.
 * Every method except the decoding itself runs on the render thread.
 */
final class BackgroundTextures {
	static final int MAX_FULL = 2;
	static final int THUMB_W = 256;
	static final int THUMB_H = 144;

	/** A texture ready to draw. */
	record Tex(ResourceLocation id, int width, int height) {
	}

	private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "CakeCosmetic image decoder");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	/** Access-ordered: the eldest is the least recently drawn. */
	private static final LinkedHashMap<String, Tex> FULL = new LinkedHashMap<>(4, 0.75F, true);
	private static final Map<String, int[]> FULL_SIZE = new HashMap<>();
	private static final Map<String, Tex> THUMBS = new HashMap<>();
	private static final Set<String> PENDING = new HashSet<>();
	private static final Set<String> FAILED = new HashSet<>();
	private static int generation;

	private BackgroundTextures() {
	}

	/** The full picture, or null while it loads (the first call starts loading it). */
	@Nullable
	static Tex full(Backgrounds.Background b) {
		Tex t = FULL.get(b.id());
		var window = Minecraft.getInstance().getWindow();
		int w = window.getWidth();
		int h = window.getHeight();
		if (t != null) {
			// the window grew a lot since it was shrunk: load it again at the new size (keep drawing the old meanwhile)
			int[] size = FULL_SIZE.get(b.id());
			if (size != null && (w > size[0] * 1.25 || h > size[1] * 1.25)) {
				request("full:" + b.id(), b, false, w, h);
			}
			return t;
		}
		request("full:" + b.id(), b, false, w, h);
		return null;
	}

	/** The thumbnail (16:9, cropped), or null while it loads. */
	@Nullable
	static Tex thumb(Backgrounds.Background b) {
		Tex t = THUMBS.get(b.id());
		if (t == null) {
			request("thumb:" + b.id(), b, true, THUMB_W, THUMB_H);
		}
		return t;
	}

	private static void request(String key, Backgrounds.Background b, boolean thumb, int w, int h) {
		if (FAILED.contains(key) || !PENDING.add(key)) {
			return;
		}
		int gen = generation;
		Minecraft client = Minecraft.getInstance();
		DECODER.execute(() -> {
			NativeImage image = null;
			try {
				image = decode(client, b.png(), thumb, w, h);
			} catch (Exception | LinkageError e) {
				CakeCosmetic.LOG.warn("CakeCosmetic: can't load {}: {}", b.png(), e.toString());
			}
			NativeImage done = image;
			client.execute(() -> finish(key, b.id(), thumb, gen, done, w, h));
		});
	}

	private static NativeImage decode(Minecraft client, ResourceLocation png, boolean thumb, int w, int h) throws Exception {
		Optional<Resource> r = client.getResourceManager().getResource(png);
		if (r.isEmpty()) {
			throw new java.io.FileNotFoundException(png.toString());
		}
		NativeImage src;
		try (InputStream in = r.get().open()) {
			src = NativeImage.read(NativeImage.Format.RGBA, in);
		}
		try {
			if (thumb) {
				int[] region = Fit.coverRegion(src.getWidth(), src.getHeight(), w, h);
				NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false);
				src.resizeSubRectTo(region[0], region[1], region[2], region[3], out);
				return out;
			}
			int[] size = Fit.shrinkToCover(src.getWidth(), src.getHeight(), w, h);
			if (size[0] == src.getWidth() && size[1] == src.getHeight()) {
				NativeImage keep = src;
				src = null;
				return keep;
			}
			NativeImage out = new NativeImage(NativeImage.Format.RGBA, size[0], size[1], false);
			src.resizeSubRectTo(0, 0, src.getWidth(), src.getHeight(), out);
			return out;
		} finally {
			if (src != null) {
				src.close();
			}
		}
	}

	private static void finish(String key, String id, boolean thumb, int gen, @Nullable NativeImage image, int w, int h) {
		PENDING.remove(key);
		if (image == null) {
			FAILED.add(key);
			return;
		}
		if (gen != generation) {
			image.close();
			return;
		}
		ResourceLocation texId = new ResourceLocation(CakeCosmetic.MOD_ID, (thumb ? "thumb/" : "full/") + id.replace(':', '/'));
		DynamicTexture texture = new DynamicTexture(image);
		texture.setFilter(true, false);
		Minecraft.getInstance().getTextureManager().register(texId, texture);
		Tex t = new Tex(texId, image.getWidth(), image.getHeight());
		if (thumb) {
			THUMBS.put(id, t);
			return;
		}
		FULL.put(id, t);
		FULL_SIZE.put(id, new int[] {w, h});
		Iterator<Map.Entry<String, Tex>> it = FULL.entrySet().iterator();
		while (FULL.size() > MAX_FULL && it.hasNext()) {
			Map.Entry<String, Tex> eldest = it.next();
			it.remove();
			FULL_SIZE.remove(eldest.getKey());
			Minecraft.getInstance().getTextureManager().release(eldest.getValue().id());
		}
	}

	/** Forgets every texture (resource reload); loads still running are thrown away when they finish. */
	static void clear() {
		generation++;
		var manager = Minecraft.getInstance().getTextureManager();
		for (Tex t : FULL.values()) {
			manager.release(t.id());
		}
		for (Tex t : THUMBS.values()) {
			manager.release(t.id());
		}
		FULL.clear();
		FULL_SIZE.clear();
		THUMBS.clear();
		PENDING.clear();
		FAILED.clear();
	}

	/** Draws a texture so it covers the box (centred crop), with an alpha. */
	static void drawCover(GuiGraphics ctx, Tex t, int x, int y, int w, int h, float alpha) {
		int[] r = Fit.coverRegion(t.width(), t.height(), w, h);
		RenderSystem.enableBlend();
		ctx.setColor(1, 1, 1, alpha);
		ctx.blit(t.id(), x, y, w, h, r[0], r[1], r[2], r[3], t.width(), t.height());
		ctx.setColor(1, 1, 1, 1);
	}
}
