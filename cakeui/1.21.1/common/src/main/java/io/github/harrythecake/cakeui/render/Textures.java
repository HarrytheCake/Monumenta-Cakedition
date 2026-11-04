package io.github.harrythecake.cakeui.render;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.harrythecake.cakeui.model.Problems;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** PNGs from a layout's assets folder, loaded on first use and freed when the layout goes away. */
final class Textures {
	record Tex(ResourceLocation id, DynamicTexture texture, int width, int height) {
	}

	private static int generation;
	private final Path assets;
	private final Problems problems;
	private final String prefix;
	private final Map<String, Tex> loaded = new HashMap<>();
	private final Set<String> failed = new HashSet<>();

	Textures(Path layoutRoot, Problems problems) {
		this.assets = layoutRoot.resolve("assets").normalize();
		this.problems = problems;
		this.prefix = "layout/" + (generation++) + "/";
	}

	/** The texture for an assets-relative path, or null (the problem is reported once). */
	Tex get(String path, String file, int line) {
		Tex t = loaded.get(path);
		if (t != null || failed.contains(path)) {
			return t;
		}
		Path p = assets.resolve(path).normalize();
		if (!p.startsWith(assets) || !Files.isRegularFile(p)) {
			failed.add(path);
			problems.add(file, line, "image not found: assets/" + path);
			return null;
		}
		if (!path.toLowerCase(Locale.ROOT).endsWith(".png")) {
			failed.add(path);
			problems.add(file, line, "images must be PNG: assets/" + path);
			return null;
		}
		try (InputStream in = Files.newInputStream(p)) {
			NativeImage image = NativeImage.read(in);
			DynamicTexture texture = new DynamicTexture(image);
			String clean = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cakeui", prefix + clean);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			t = new Tex(id, texture, image.getWidth(), image.getHeight());
			loaded.put(path, t);
			return t;
		} catch (Exception e) {
			failed.add(path);
			problems.add(file, line, "could not read assets/" + path + ": " + e.getMessage());
			return null;
		}
	}

	void close() {
		for (Tex t : loaded.values()) {
			Minecraft.getInstance().getTextureManager().release(t.id());
		}
		loaded.clear();
	}
}
