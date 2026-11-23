package io.github.harrythecake.cakemonu;

import io.github.harrythecake.cakemonu.platform.Services;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CakeMonu: Monumenta helpers that bridge other mods. First (and so far only) part: UMM's ability icons,
 * each one placeable on its own. Holds the player's {@link IconLayout} and the {@link AbilitySource}.
 * Render thread.
 */
public final class CakeMonu {
	public static final String MOD_ID = "cakemonu";
	public static final Logger LOG = LoggerFactory.getLogger("CakeMonu");

	private static AbilitySource source = AbilitySource.NONE;
	private static IconLayout layout = new IconLayout();

	private CakeMonu() {
	}

	public static Path layoutFile() {
		return Services.PLATFORM.configDir().resolve(MOD_ID).resolve("ability-icons.json");
	}

	public static void setSource(AbilitySource s) {
		source = s;
	}

	public static AbilitySource source() {
		return source;
	}

	public static IconLayout layout() {
		return layout;
	}

	/** Reads the layout file again (startup, {@code /cakemonu reload}, CakeUI's config hub, the HUD editor's Cancel). */
	public static void load() {
		layout = IconLayout.read(layoutFile());
	}

	public static void save() {
		try {
			layout.write(layoutFile());
		} catch (IOException e) {
			LOG.warn("CakeMonu: can't save {}: {}", layoutFile(), e.toString());
		}
	}

	/** True when this ability has its own place (and CakeMonu is working). */
	public static boolean placed(String id) {
		return source.state() == AbilitySource.State.READY && layout.has(id);
	}

	/**
	 * Gives the ability its own place (next to UMM's bar, see {@link IconPlacement#nextTo}) or puts it back
	 * into the bar; saves.
	 */
	public static void setPlaced(String id, boolean on, int screenWidth, int screenHeight) {
		if (on == layout.has(id)) {
			return;
		}
		if (on) {
			List<double[]> taken = new ArrayList<>();
			for (IconPlacement p : layout.all().values()) {
				taken.add(p.box(screenWidth, screenHeight, source.iconSize()));
			}
			layout.put(id, IconPlacement.nextTo(source.barBox(screenWidth, screenHeight), source.horizontal(), source.iconSize(), source.iconGap(),
					taken, screenWidth, screenHeight));
		} else {
			layout.remove(id);
		}
		save();
	}

	/** Every ability back into UMM's bar; saves. */
	public static void clearAll() {
		layout.clear();
		save();
	}
}
