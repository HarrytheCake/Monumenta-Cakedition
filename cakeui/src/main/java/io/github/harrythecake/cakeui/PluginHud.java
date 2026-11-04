package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.HudPiece;
import io.github.harrythecake.cakeui.model.Loader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * HUD pieces other mods add (CakeMap's minimap). Each one is a port of the "hud" part like
 * vanilla's pieces: drawn through {@link HudPart#piece}, so a layout that names it can hide, move,
 * scale or fade it; with no layout it is drawn as is.
 */
public final class PluginHud {
	private static final Logger LOG = LoggerFactory.getLogger("CakeUI");
	private static final Pattern NAME = Pattern.compile("[a-z][a-zA-Z0-9]*");

	private record Entry(String name, String owner, HudPiece piece) {
	}

	private static final List<Entry> PIECES = new CopyOnWriteArrayList<>();
	private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

	private PluginHud() {
	}

	/** Called by CakeUIApi.addHudPiece. Names are plain words, unique, and never a vanilla port's. */
	public static synchronized void register(String name, String owner, HudPiece piece) {
		if (!NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("CakeUI: a HUD piece name is one word like 'minimap': " + name);
		}
		if (Loader.PORTS.get("hud").contains(name)) {
			throw new IllegalArgumentException("CakeUI: '" + name + "' is a vanilla HUD port");
		}
		for (Entry e : PIECES) {
			if (e.name.equals(name)) {
				throw new IllegalStateException("CakeUI: HUD piece '" + name + "' is already added by " + e.owner);
			}
		}
		PIECES.add(new Entry(name, owner, piece));
	}

	/** Names of every added piece (the "hud" part accepts them as ports). */
	public static List<String> names() {
		List<String> out = new ArrayList<>();
		for (Entry e : PIECES) {
			out.add(e.name);
		}
		return out;
	}

	@Nullable
	public static String owner(String name) {
		for (Entry e : PIECES) {
			if (e.name.equals(name)) {
				return e.owner;
			}
		}
		return null;
	}

	/** Every piece, in the order they were added. From InGameHud.render, before the front layer. */
	public static void drawAll(DrawContext ctx, float tickDelta) {
		if (PIECES.isEmpty() || MinecraftClient.getInstance().options.hudHidden) {
			return;
		}
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		for (Entry e : PIECES) {
			if (FAILED.containsKey(e.name)) {
				continue;
			}
			HudPart.piece(e.name, ctx, e.piece.pivotX(w), e.piece.pivotY(h), false, () -> {
				try {
					e.piece.draw(ctx, w, h, tickDelta);
				} catch (RuntimeException | LinkageError ex) {
					FAILED.put(e.name, true);
					LOG.error("CakeUI: HUD piece {} of {} failed and is switched off", e.name, e.owner, ex);
				}
			});
		}
	}

	/** Tests only. */
	static synchronized void clear() {
		PIECES.clear();
		FAILED.clear();
	}
}
