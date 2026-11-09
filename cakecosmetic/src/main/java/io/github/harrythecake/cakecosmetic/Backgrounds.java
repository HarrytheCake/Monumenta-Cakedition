package io.github.harrythecake.cakecosmetic;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every background the enabled resource packs offer:
 * {@code assets/<namespace>/cakecosmetic/backgrounds/<name>.png} (id {@code <namespace>:<name>}),
 * with an optional {@code <name>.json} beside it. Read again on every resource reload.
 */
final class Backgrounds implements SimpleSynchronousResourceReloadListener {
	static final String DIR = "cakecosmetic/backgrounds";

	/** One background. */
	record Background(String id, Identifier png, BackgroundMeta meta) {
		/** Its title in the game's language, else the file name. */
		String title() {
			String t = meta.title(MinecraftClient.getInstance().getLanguageManager().getLanguage());
			return t != null ? t : id.substring(id.indexOf(':') + 1);
		}
	}

	private static volatile Map<String, Background> all = Map.of();

	static List<Background> list() {
		return List.copyOf(all.values());
	}

	@Nullable
	static Background get(String id) {
		return all.get(id);
	}

	@Override
	public Identifier getFabricId() {
		return new Identifier(CakeCosmetic.MOD_ID, "backgrounds");
	}

	@Override
	public void reload(ResourceManager manager) {
		List<Background> found = new ArrayList<>();
		for (Identifier png : manager.findResources(DIR, id -> id.getPath().endsWith(".png")).keySet()) {
			String name = png.getPath().substring(DIR.length() + 1, png.getPath().length() - 4);
			if (name.isEmpty() || name.contains("/")) {
				continue;
			}
			Identifier json = new Identifier(png.getNamespace(), DIR + "/" + name + ".json");
			found.add(new Background(png.getNamespace() + ":" + name, png, meta(manager, json)));
		}
		found.sort((a, b) -> a.id().compareTo(b.id()));
		Map<String, Background> map = new LinkedHashMap<>();
		for (Background b : found) {
			map.put(b.id(), b);
		}
		all = java.util.Collections.unmodifiableMap(map);
		CakeCosmetic.LOG.info("CakeCosmetic: {} background(s) from resource packs", map.size());
		BackgroundTextures.clear();
		MenuBackground.prepare();
	}

	private static BackgroundMeta meta(ResourceManager manager, Identifier json) {
		Optional<Resource> r = manager.getResource(json);
		if (r.isEmpty()) {
			return BackgroundMeta.NONE;
		}
		try (InputStream in = r.get().getInputStream()) {
			return BackgroundMeta.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), json.toString());
		} catch (IOException e) {
			CakeCosmetic.LOG.warn("CakeCosmetic: can't read {}: {}", json, e.toString());
			return BackgroundMeta.NONE;
		}
	}
}
