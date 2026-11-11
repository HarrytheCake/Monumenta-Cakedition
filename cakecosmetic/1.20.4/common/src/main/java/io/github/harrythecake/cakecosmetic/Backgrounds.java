package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
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
public final class Backgrounds implements ResourceManagerReloadListener {
	static final String DIR = "cakecosmetic/backgrounds";
	/** The listener's id (Fabric wants one). */
	public static final ResourceLocation ID = new ResourceLocation(CakeCosmetic.MOD_ID, "backgrounds");

	/** One background. */
	record Background(String id, ResourceLocation png, BackgroundMeta meta) {
		/** Its title in the game's language, else the file name. */
		String title() {
			String t = meta.title(Minecraft.getInstance().getLanguageManager().getSelected());
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
	public void onResourceManagerReload(ResourceManager manager) {
		List<Background> found = new ArrayList<>();
		for (ResourceLocation png : manager.listResources(DIR, id -> id.getPath().endsWith(".png")).keySet()) {
			String name = png.getPath().substring(DIR.length() + 1, png.getPath().length() - 4);
			if (name.isEmpty() || name.contains("/")) {
				continue;
			}
			ResourceLocation json = new ResourceLocation(png.getNamespace(), DIR + "/" + name + ".json");
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

	private static BackgroundMeta meta(ResourceManager manager, ResourceLocation json) {
		Optional<Resource> r = manager.getResource(json);
		if (r.isEmpty()) {
			return BackgroundMeta.NONE;
		}
		try (InputStream in = r.get().open()) {
			return BackgroundMeta.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), json.toString());
		} catch (IOException e) {
			CakeCosmetic.LOG.warn("CakeCosmetic: can't read {}: {}", json, e.toString());
			return BackgroundMeta.NONE;
		}
	}
}
