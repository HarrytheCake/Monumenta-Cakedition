package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Loader;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Problems;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.stream.Stream;

/**
 * The active layout. Loads it from the config, swaps it on reload, and in dev mode watches its
 * folder. A reload with problems keeps the previous layout and shows the new problems; only the
 * very first load uses a layout with problems (broken elements are skipped).
 */
public final class Ui {
	private static Config config = new Config();
	private static Renderer renderer;
	/** Problems of a reload that was not applied (the previous layout stayed). */
	private static Problems rejected;
	private static String rejectedName;
	private static Watcher watcher;
	private static volatile long changedAt;
	private static volatile boolean dirty;

	private Ui() {
	}

	public static Config config() {
		return config;
	}

	/** The renderer of the active layout, or null when none (then CakeUI does nothing). */
	public static Renderer renderer() {
		return renderer;
	}

	public static Node part(String id) {
		return renderer == null ? null : renderer.layout.part(id);
	}

	/** A built-in subwindow of a part (`hud`, `pause`), or null. */
	public static Node builtin(String partId, String name) {
		Node part = part(partId);
		return part == null ? null : part.builtins.get(name);
	}

	public static boolean devMode() {
		return config.devMode;
	}

	/** Problems to show: a rejected reload's, else the active layout's. */
	public static List<Problems.Problem> problems() {
		if (rejected != null) {
			return rejected.list();
		}
		return renderer == null ? List.of() : renderer.layout.problems.list();
	}

	public static String problemsLayoutName() {
		if (rejected != null) {
			return rejectedName;
		}
		return renderer == null ? "" : renderer.layout.name;
	}

	public static boolean keptPrevious() {
		return rejected != null && renderer != null;
	}

	/** Reads the config and loads the layout. Render thread. */
	public static void reload() {
		config = Config.load();
		Path folder = config.layoutFolder();
		rejected = null;
		if (folder == null) {
			swap(null);
			stopWatcher();
			return;
		}
		Loader loader = Loader.load(folder);
		Layout layout = loader.layout();
		for (Problems.Problem p : layout.problems.list()) {
			CakeUI.LOGGER.warn("CakeUI layout {}: {}", layout.name, p);
		}
		boolean bad = loader.fatal || (!layout.problems.isEmpty() && renderer != null);
		if (bad && renderer != null) {
			rejected = layout.problems;
			rejectedName = layout.name;
			CakeUI.LOGGER.warn("CakeUI: kept the previous layout because the new one has {} problem(s)", layout.problems.size());
		} else if (loader.fatal) {
			rejected = layout.problems;
			rejectedName = layout.name;
			swap(null);
		} else {
			swap(new Renderer(layout));
			CakeUI.LOGGER.info("CakeUI: layout {} loaded ({} problem(s))", layout.name, layout.problems.size());
		}
		if (config.devMode) {
			startWatcher(folder);
		} else {
			stopWatcher();
		}
		reinitScreen();
	}

	private static void swap(Renderer next) {
		if (renderer != null) {
			renderer.close();
		}
		renderer = next;
	}

	/** Lays the open screen out again so buttons pick up the new layout. */
	private static void reinitScreen() {
		MinecraftClient client = MinecraftClient.getInstance();
		Screen screen = client.currentScreen;
		if (screen != null) {
			screen.resize(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		}
	}

	/** Called every client tick: reloads once the layout's files have been quiet for a moment. */
	static void tick() {
		if (dirty && System.currentTimeMillis() - changedAt > 300) {
			dirty = false;
			reload();
		}
	}

	// ---- dev mode file watcher ----

	private static void startWatcher(Path folder) {
		if (watcher != null && watcher.root.equals(folder)) {
			return;
		}
		stopWatcher();
		if (!Files.isDirectory(folder)) {
			return;
		}
		try {
			watcher = new Watcher(folder);
			watcher.start();
		} catch (IOException e) {
			CakeUI.LOGGER.warn("CakeUI: can't watch {} for changes: {}", folder, e.toString());
		}
	}

	private static void stopWatcher() {
		if (watcher != null) {
			watcher.shutdown();
			watcher = null;
		}
	}

	private static final class Watcher extends Thread {
		final Path root;
		private final WatchService service;
		private volatile boolean running = true;

		Watcher(Path root) throws IOException {
			super("CakeUI layout watcher");
			setDaemon(true);
			this.root = root;
			this.service = FileSystems.getDefault().newWatchService();
			registerAll(root);
		}

		private void registerAll(Path dir) throws IOException {
			try (Stream<Path> dirs = Files.walk(dir)) {
				for (Path d : dirs.filter(Files::isDirectory).toList()) {
					d.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
				}
			}
		}

		@Override
		public void run() {
			while (running) {
				try {
					WatchKey key = service.take();
					Path dir = (Path) key.watchable();
					for (var event : key.pollEvents()) {
						if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && event.context() instanceof Path p && Files.isDirectory(dir.resolve(p))) {
							registerAll(dir.resolve(p));
						}
					}
					key.reset();
					changedAt = System.currentTimeMillis();
					dirty = true;
				} catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
					return;
				} catch (IOException e) {
					CakeUI.LOGGER.warn("CakeUI watcher: {}", e.toString());
				}
			}
		}

		void shutdown() {
			running = false;
			try {
				service.close();
			} catch (IOException ignored) {
			}
			interrupt();
		}
	}
}
