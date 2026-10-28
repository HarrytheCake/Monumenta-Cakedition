package io.github.harrythecake.cakebook;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Page images, loaded once each into a texture. Cleared when books reload. Render thread only. */
final class Images {
	record Image(Identifier texture, int width, int height) {}

	private static final Map<String, Optional<Image>> CACHE = new HashMap<>();
	private static int next;

	private Images() {}

	/** The image at a locator ("res:ns:path" or "file:/abs/path"), or null if it can't be read as PNG. */
	static @Nullable Image get(String locator) {
		return CACHE.computeIfAbsent(locator, Images::load).orElse(null);
	}

	static void clear() {
		MinecraftClient client = MinecraftClient.getInstance();
		for (Optional<Image> image : CACHE.values()) {
			image.ifPresent(i -> client.getTextureManager().destroyTexture(i.texture()));
		}
		CACHE.clear();
	}

	private static Optional<Image> load(String locator) {
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			NativeImage image;
			if (locator.startsWith("res:")) {
				Identifier id = Identifier.tryParse(locator.substring(4));
				Optional<Resource> resource = id == null ? Optional.empty() : client.getResourceManager().getResource(id);
				if (resource.isEmpty()) {
					CakeBook.LOGGER.warn("Book image not found: {}", locator);
					return Optional.empty();
				}
				try (InputStream in = resource.get().getInputStream()) {
					image = NativeImage.read(in);
				}
			} else if (locator.startsWith("file:")) {
				try (InputStream in = Files.newInputStream(Path.of(locator.substring(5)))) {
					image = NativeImage.read(in);
				}
			} else {
				return Optional.empty();
			}
			Identifier texture = new Identifier(CakeBook.MOD_ID, "image/" + next++);
			int width = image.getWidth();
			int height = image.getHeight();
			client.getTextureManager().registerTexture(texture, new NativeImageBackedTexture(image));
			return Optional.of(new Image(texture, width, height));
		} catch (Exception e) {
			CakeBook.LOGGER.warn("Could not load book image {}: {}", locator, e.toString());
			return Optional.empty();
		}
	}
}
