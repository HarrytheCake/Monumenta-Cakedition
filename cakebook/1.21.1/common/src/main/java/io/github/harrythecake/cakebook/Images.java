package io.github.harrythecake.cakebook;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

/** Page images, loaded once each into a texture. Cleared when books reload. Render thread only. */
final class Images {
	record Image(ResourceLocation texture, int width, int height) {}

	private static final Map<String, Optional<Image>> CACHE = new HashMap<>();
	private static int next;

	private Images() {}

	/** The image at a locator ("res:ns:path" or "file:/abs/path"), or null if it can't be read as PNG. */
	static @Nullable Image get(String locator) {
		return CACHE.computeIfAbsent(locator, Images::load).orElse(null);
	}

	static void clear() {
		Minecraft client = Minecraft.getInstance();
		for (Optional<Image> image : CACHE.values()) {
			image.ifPresent(i -> client.getTextureManager().release(i.texture()));
		}
		CACHE.clear();
	}

	private static Optional<Image> load(String locator) {
		Minecraft client = Minecraft.getInstance();
		try {
			NativeImage image;
			if (locator.startsWith("res:")) {
				ResourceLocation id = ResourceLocation.tryParse(locator.substring(4));
				Optional<Resource> resource = id == null ? Optional.empty() : client.getResourceManager().getResource(id);
				if (resource.isEmpty()) {
					CakeBook.LOGGER.warn("Book image not found: {}", locator);
					return Optional.empty();
				}
				try (InputStream in = resource.get().open()) {
					image = NativeImage.read(in);
				}
			} else if (locator.startsWith("file:")) {
				try (InputStream in = Files.newInputStream(Path.of(locator.substring(5)))) {
					image = NativeImage.read(in);
				}
			} else {
				return Optional.empty();
			}
			ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(CakeBook.MOD_ID, "image/" + next++);
			int width = image.getWidth();
			int height = image.getHeight();
			client.getTextureManager().register(texture, new DynamicTexture(image));
			return Optional.of(new Image(texture, width, height));
		} catch (Exception e) {
			CakeBook.LOGGER.warn("Could not load book image {}: {}", locator, e.toString());
			return Optional.empty();
		}
	}
}
