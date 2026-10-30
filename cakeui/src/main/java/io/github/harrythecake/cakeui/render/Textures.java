package io.github.harrythecake.cakeui.render;

import io.github.harrythecake.cakeui.model.Problems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** PNGs from a layout's assets folder, loaded on first use and freed when the layout goes away. */
final class Textures {
	record Tex(Identifier id, NativeImageBackedTexture texture, int width, int height) {
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
			NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
			String clean = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
			Identifier id = new Identifier("cakeui", prefix + clean);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
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
			MinecraftClient.getInstance().getTextureManager().destroyTexture(t.id());
		}
		loaded.clear();
	}
}
