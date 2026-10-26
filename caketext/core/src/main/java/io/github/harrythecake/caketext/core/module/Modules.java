package io.github.harrythecake.caketext.core.module;

import java.util.ArrayList;
import java.util.List;

import io.github.harrythecake.caketext.core.CakeText;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/** Finds {@link CakeTextModule}s (entrypoint {@code caketext}) once, for whichever CakeText mod starts first. */
public final class Modules {
	private static List<CakeTextModule> active;

	private Modules() {
	}

	/** Loads the available modules and registers their context. Safe to call from both mods. */
	public static synchronized List<CakeTextModule> active() {
		if (active == null) {
			List<CakeTextModule> found = new ArrayList<>();
			for (EntrypointContainer<CakeTextModule> container : FabricLoader.getInstance()
					.getEntrypointContainers("caketext", CakeTextModule.class)) {
				String owner = container.getProvider().getMetadata().getId();
				try {
					CakeTextModule module = container.getEntrypoint();
					if (module.isAvailable()) {
						module.registerContext();
						found.add(module);
						CakeText.LOG.info("CakeText module '{}' (from {}) active", module.id(), owner);
					}
				} catch (Throwable t) {
					CakeText.LOG.warn("CakeText module from {} failed to load", owner, t);
				}
			}
			active = List.copyOf(found);
		}
		return active;
	}
}
