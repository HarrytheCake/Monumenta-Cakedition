package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.HudDefaults;
import io.github.harrythecake.cakeui.edit.HudEdits;
import io.github.harrythecake.cakeui.edit.PortEdit;
import io.github.harrythecake.cakeui.platform.Services;
import io.github.harrythecake.cakeui.render.Renderer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The HUD editor's state: the player's personal edits ({@code config/cakeui/hud.json5}, one set per
 * layout, "vanilla" without one) and, while the editor is open, the working copy the HUD draws from
 * so every drag shows at once. Render thread.
 */
public final class HudEditor {
	@Nullable
	private static HudEdits saved;
	/** While the editor is open: the edits being changed (the HUD uses these). */
	@Nullable
	private static Map<String, PortEdit> working;

	private HudEditor() {
	}

	public static Path file() {
		return Services.PLATFORM.configDir().resolve(CakeUI.MOD_ID).resolve("hud.json5");
	}

	/** Which set of edits applies: the active layout's name, or "vanilla". */
	public static String layoutKey() {
		Renderer r = Ui.renderer();
		return r == null ? HudEdits.VANILLA : r.layout.name;
	}

	private static HudEdits saved() {
		if (saved == null) {
			saved = HudEdits.load(file());
		}
		return saved;
	}

	/** The edits the HUD draws with this frame. */
	public static Map<String, PortEdit> current() {
		return working != null ? working : saved().set(layoutKey());
	}

	public static boolean editing() {
		return working != null;
	}

	/** Re-reads the file (after a layout reload). */
	public static void reload() {
		saved = null;
	}

	/** Opens a working copy of the active layout's edits. */
	static Map<String, PortEdit> start() {
		working = HudEdits.copy(saved().set(layoutKey()));
		return working;
	}

	/** Closes the editor: saves the working copy or throws it away. */
	static void finish(boolean save) {
		if (save && working != null) {
			HudEdits all = saved();
			all.put(layoutKey(), working);
			try {
				all.save(file());
			} catch (IOException e) {
				CakeUI.LOGGER.error("CakeUI: could not save {}", file(), e);
			}
		}
		working = null;
	}

	/** Every piece the editor offers: vanilla's movable ports and the pieces mods added. */
	static List<String> pieces() {
		List<String> out = new ArrayList<>(HudDefaults.EDITABLE);
		out.addAll(PluginHud.names());
		return out;
	}
}
