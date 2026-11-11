package io.github.harrythecake.caketext.core.module;

import java.util.ArrayList;
import java.util.List;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.platform.Platform;
import io.github.harrythecake.caketext.core.platform.Services;

/**
 * Finds {@link CakeTextModule}s once, for whichever CakeText mod starts first: Fabric entrypoint {@code caketext},
 * NeoForge {@code META-INF/services/io.github.harrythecake.caketext.core.module.CakeTextModule}.
 */
public final class Modules {
	private static List<CakeTextModule> active;

	private Modules() {
	}

	/** Loads the available modules and registers their context. Safe to call from both mods. */
	public static synchronized List<CakeTextModule> active() {
		if (active == null) {
			List<CakeTextModule> found = new ArrayList<>();
			for (Platform.Provided<CakeTextModule> container : Services.PLATFORM.entrypoints("caketext", CakeTextModule.class)) {
				String owner = container.owner();
				try {
					CakeTextModule module = container.instance().get();
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
