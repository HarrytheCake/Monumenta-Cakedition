package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.harrythecake.cakemap.xaero.XaeroSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Runs an Xaero import in the game (XaeroImport does the files): on its own thread; settings, keys
 * and the open server's waypoints are changed on the render thread.
 */
public final class XaeroImportRun {
	private XaeroImportRun() {
	}

	public static Thread start(Path xaero, XaeroImport.Options o, XaeroImport.Progress p) {
		Thread t = new Thread(() -> run(xaero, o, p), "CakeMap Xaero import");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		t.start();
		return t;
	}

	static void run(Path xaero, XaeroImport.Options o, XaeroImport.Progress p) {
		CakeMap.setImporting(true);
		try {
			Minecraft mc = Minecraft.getInstance();
			// what the game holds in memory is saved first, so the import doesn't race it
			callOnRender(() -> {
				if (CakeMap.session() != null) {
					CakeMap.session().close();
				}
			});
			Path game = XaeroImport.gameDir(xaero);
			if (o.settings) {
				Path cfg = game.resolve("config").resolve("xaerominimap.txt");
				if (Files.exists(cfg)) {
					String text = Files.readString(cfg);
					callOnRender(() -> {
						p.settings = XaeroSettings.apply(text, CakeMap.settings());
						CakeMap.saveSettings();
					});
				}
			}
			if (o.keys) {
				Path opts = game.resolve("options.txt");
				if (Files.exists(opts)) {
					Map<String, String> keys = XaeroSettings.keys(Files.readString(opts));
					callOnRender(() -> p.keys = applyKeys(mc, keys));
				}
			}
			if (o.waypoints) {
				// the server the player is on: into its live store (the session saves it again when it ends,
				// so writing only the file would be overwritten with the old list)
				XaeroImport.LiveStore live = (key, change) -> {
					int[] n = {-1};
					callOnRender(() -> {
						MapSession s = CakeMap.session();
						if (s != null && s.server.equals(key)) {
							n[0] = change.applyAsInt(s.waypoints);
							s.waypoints.save();
						}
					});
					return n[0];
				};
				Path worldMap = xaero.resolve("world-map");
				p.waypoints = XaeroImport.importWaypoints(xaero.resolve("minimap"), worldMap, CakeMap.dataRoot(), o.waypointMaps, live)
						+ XaeroImport.importWaypoints(game.resolve(XaeroImport.LEGACY_WAYPOINTS), worldMap, CakeMap.dataRoot(), o.waypointMaps, live);
			}
			if (o.maps) {
				XaeroImport.importMaps(xaero.resolve("world-map"), CakeMap.dataRoot(), p, null);
			}
		} catch (IOException | RuntimeException e) {
			CakeMap.LOG.error("CakeMap: Xaero import failed", e);
			p.error = e.toString();
		} finally {
			RegionStore.flush();
			CakeMap.setImporting(false);
			CakeMap.onRender(() -> {
				MapTextures.clear("dyn/");
				CakeMap.endSession(); // the next tick opens the session again, with the imported waypoints and maps
			});
			p.finished = true;
		}
	}

	private static void callOnRender(Runnable r) {
		java.util.concurrent.CompletableFuture<Void> f = new java.util.concurrent.CompletableFuture<>();
		CakeMap.onRender(() -> {
			try {
				r.run();
				f.complete(null);
			} catch (RuntimeException e) {
				f.completeExceptionally(e);
			}
		});
		try {
			f.get();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static int applyKeys(Minecraft mc, Map<String, String> keys) {
		int n = 0;
		for (Map.Entry<String, String> e : keys.entrySet()) {
			KeyMapping k = CakeMap.KEYS.get(e.getKey());
			if (k == null) {
				continue;
			}
			try {
				k.setKey(InputConstants.getKey(e.getValue()));
				n++;
			} catch (RuntimeException ignored) {
				// a key name this version doesn't know
			}
		}
		KeyMapping.resetMapping();
		mc.options.save();
		return n;
	}
}
