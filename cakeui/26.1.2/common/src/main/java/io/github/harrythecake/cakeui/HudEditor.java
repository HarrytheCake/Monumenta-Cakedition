package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.HudDefaults;
import io.github.harrythecake.cakeui.edit.HudEdits;
import io.github.harrythecake.cakeui.edit.PortEdit;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.platform.Services;
import io.github.harrythecake.cakeui.render.Renderer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The HUD editor's state (ADR 0010). Two kinds of edits: a player's personal edits
 * ({@code config/cakeui/hud.json5}, one set per layout, "vanilla" without one) and, in dev mode, the
 * layout's own edits ({@code <layout>/hud/edits.json5}). The HUD uses the layout's edits with the
 * player's on top ({@link HudEdits#merge}). While the editor is open it works on copies, which the
 * HUD draws from so every drag shows at once. Render thread.
 */
public final class HudEditor {
	/** Where the editor saves. */
	public enum Target {
		PERSONAL, LAYOUT
	}

	@Nullable
	private static HudEdits saved;
	@Nullable
	private static Map<String, PortEdit> layoutSaved;
	/** The merged edits while the editor is closed (rebuilt after a reload or a save). */
	@Nullable
	private static Map<String, PortEdit> merged;
	@Nullable
	private static Map<String, PortEdit> personalWorking;
	@Nullable
	private static Map<String, PortEdit> layoutWorking;
	private static Target target = Target.PERSONAL;

	private HudEditor() {
	}

	public static Path file() {
		return Services.PLATFORM.configDir().resolve(CakeUI.MOD_ID).resolve("hud.json5");
	}

	/** Which set of personal edits applies: the active layout's name, or "vanilla". */
	public static String layoutKey() {
		Renderer r = Ui.renderer();
		return r == null ? HudEdits.VANILLA : r.layout.name;
	}

	/** The active layout's {@code hud/edits.json5}, or null without a layout that has a hud folder. */
	@Nullable
	static Path layoutEditsFile() {
		Path folder = Ui.config().layoutFolder();
		if (Ui.renderer() == null || folder == null || !Files.isDirectory(folder.resolve("hud"))) {
			return null;
		}
		return folder.resolve("hud").resolve("edits.json5");
	}

	/** Dev mode with a layout that has a hud folder: the editor may save into the layout. */
	static boolean layoutTargetPossible() {
		return Ui.devMode() && layoutEditsFile() != null;
	}

	private static HudEdits saved() {
		if (saved == null) {
			saved = HudEdits.load(file());
		}
		return saved;
	}

	private static Map<String, PortEdit> layoutEdits() {
		if (layoutSaved == null) {
			Path f = layoutEditsFile();
			layoutSaved = f == null ? Map.of() : HudEdits.loadLayoutEdits(f);
		}
		return layoutSaved;
	}

	/**
	 * The edits the HUD draws with this frame. While the editor saves into the layout, the player's own
	 * edits are left out, so the layout is seen as players without edits will see it.
	 */
	public static Map<String, PortEdit> current() {
		if (personalWorking != null) {
			return target == Target.LAYOUT ? layoutWorking : HudEdits.merge(layoutEdits(), personalWorking);
		}
		if (merged == null) {
			merged = HudEdits.merge(layoutEdits(), saved().set(layoutKey()));
		}
		return merged;
	}

	public static boolean editing() {
		return personalWorking != null;
	}

	/**
	 * Personal edits on a mod's HUD piece that the editor doesn't offer any more (the mod places it itself
	 * now, API 3): once, the edit's place goes to the mod's movable of the same id (if any) and the edit is
	 * dropped. Called at the start of a HUD frame (needs the screen size).
	 */
	static void migrate(int w, int h) {
		if (personalWorking != null) {
			return;
		}
		Map<String, PortEdit> set = saved().set(layoutKey());
		List<String> stale = new ArrayList<>();
		for (String key : set.keySet()) {
			if (PluginHud.names().contains(key) && !PluginHud.editableNames().contains(key)) {
				stale.add(key);
			}
		}
		if (stale.isEmpty()) {
			return;
		}
		Map<String, PortEdit> keep = HudEdits.copy(set);
		for (String key : stale) {
			PortEdit e = keep.remove(key);
			String owner = PluginHud.owner(key);
			io.github.harrythecake.cakeui.api.Movable m = owner == null ? null : HudMovables.get(HudMovables.PREFIX + owner + ":" + key);
			double[] b = m == null ? null : m.box(w, h);
			if (b != null && e != null && (e.mode != null || e.scale != null)) {
				io.github.harrythecake.cakeui.edit.Placement.Box inner = new io.github.harrythecake.cakeui.edit.Placement.Box(b[0], b[1], b[2], b[3]);
				io.github.harrythecake.cakeui.edit.Placement.Box shown = io.github.harrythecake.cakeui.edit.Placement.transform(e, inner, w, h).apply(inner);
				m.place(shown.x(), shown.y(), m.resizable() ? shown.w() : b[2], m.resizable() ? shown.h() : b[3], w, h);
				m.save();
				CakeUI.LOGGER.info("CakeUI: {} is placed by {} now; moved your HUD edit there", key, owner);
			}
		}
		HudEdits all = saved();
		all.put(layoutKey(), keep);
		try {
			all.save(file());
		} catch (IOException e) {
			CakeUI.LOGGER.error("CakeUI: could not save {}", file(), e);
		}
		merged = null;
	}

	/** Re-reads the files (after a layout reload). */
	public static void reload() {
		saved = null;
		layoutSaved = null;
		merged = null;
		HudAdded.reset();
	}

	/** An added element (or edit) that belongs to the layout's own edits, not the player's. */
	static boolean fromLayout(String key) {
		Map<String, PortEdit> layout = personalWorking != null ? layoutWorking : layoutEdits();
		PortEdit e = layout.get(key);
		return e != null && e.element != null;
	}

	/** Opens working copies; in dev mode the editor starts on the layout's edits. */
	static void start() {
		personalWorking = HudEdits.copy(saved().set(layoutKey()));
		layoutWorking = HudEdits.copy(layoutEdits());
		target = layoutTargetPossible() ? Target.LAYOUT : Target.PERSONAL;
	}

	static Target target() {
		return target;
	}

	static void setTarget(Target t) {
		target = t == Target.LAYOUT && !layoutTargetPossible() ? Target.PERSONAL : t;
	}

	/** The edits the editor changes now (the target's working copy). */
	static Map<String, PortEdit> editingMap() {
		return target == Target.LAYOUT ? layoutWorking : personalWorking;
	}

	/** Copies of both working sets, for "anything changed?". */
	static Map<String, PortEdit>[] snapshot() {
		@SuppressWarnings("unchecked")
		Map<String, PortEdit>[] s = new Map[] {HudEdits.copy(personalWorking), HudEdits.copy(layoutWorking)};
		return s;
	}

	/** Closes the editor: saves what changed, or throws everything away. */
	static void finish(boolean save) {
		if (save && personalWorking != null) {
			HudEdits all = saved();
			all.put(layoutKey(), personalWorking);
			try {
				all.save(file());
			} catch (IOException e) {
				CakeUI.LOGGER.error("CakeUI: could not save {}", file(), e);
			}
			Path f = layoutEditsFile();
			if (f != null && !HudEdits.copy(layoutWorking).equals(HudEdits.copy(layoutEdits()))) {
				try {
					HudEdits.saveLayoutEdits(f, layoutWorking);
				} catch (IOException e) {
					CakeUI.LOGGER.error("CakeUI: could not save {}", f, e);
				}
			}
		}
		personalWorking = null;
		layoutWorking = null;
		reload();
	}

	/** Every piece the editor offers: vanilla's movable ports, the pieces mods added, the layout's own HUD elements. */
	static List<String> pieces() {
		List<String> out = new ArrayList<>(HudDefaults.EDITABLE);
		out.addAll(PluginHud.editableNames());
		out.addAll(HudMovables.all().keySet()); // things mods place themselves (chat windows, the minimap)
		Node part = HudPart.part();
		if (part != null) {
			for (Node k : part.children) {
				out.add(HudEdits.ELEMENT + k.name);
			}
		}
		for (Map.Entry<String, PortEdit> e : current().entrySet()) {
			if (e.getKey().startsWith(HudEdits.ADDED) && e.getValue().element != null) {
				out.add(e.getKey());
			}
		}
		return out;
	}
}
