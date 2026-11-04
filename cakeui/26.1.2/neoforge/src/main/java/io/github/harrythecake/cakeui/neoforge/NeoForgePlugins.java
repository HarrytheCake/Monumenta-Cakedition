package io.github.harrythecake.cakeui.neoforge;

import io.github.harrythecake.cakeui.CakeUI;
import io.github.harrythecake.cakeui.CakeUIClient;
import io.github.harrythecake.cakeui.api.CakeUIPlugin;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Plugins on NeoForge: every mod jar that lists its plugin class in
 * META-INF/services/io.github.harrythecake.cakeui.api.CakeUIPlugin (Java's ServiceLoader; the class
 * needs a public no-argument constructor). A broken entry is logged and skipped.
 */
final class NeoForgePlugins {
	private NeoForgePlugins() {
	}

	static void runAll() {
		Iterator<CakeUIPlugin> it = ServiceLoader.load(CakeUIPlugin.class, CakeUIPlugin.class.getClassLoader()).iterator();
		while (true) {
			CakeUIPlugin plugin;
			try {
				if (!it.hasNext()) {
					return;
				}
				plugin = it.next();
			} catch (ServiceConfigurationError | LinkageError e) {
				CakeUI.LOGGER.error("CakeUI: a plugin could not be loaded", e);
				continue;
			}
			Module m = plugin.getClass().getModule();
			CakeUIClient.runPlugin(m.isNamed() ? m.getName() : plugin.getClass().getName(), plugin);
		}
	}
}
