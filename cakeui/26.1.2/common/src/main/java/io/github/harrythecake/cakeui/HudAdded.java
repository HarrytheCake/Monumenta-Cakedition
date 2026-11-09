package io.github.harrythecake.cakeui;

import com.google.gson.JsonObject;
import io.github.harrythecake.cakeui.edit.AddedElements;
import io.github.harrythecake.cakeui.edit.HudEdits;
import io.github.harrythecake.cakeui.edit.Placement;
import io.github.harrythecake.cakeui.edit.Placement.Box;
import io.github.harrythecake.cakeui.edit.Placement.Xform;
import io.github.harrythecake.cakeui.edit.PortEdit;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Loader;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Problems;
import io.github.harrythecake.cakeui.platform.Services;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elements the HUD editor added (E3, key {@code +id}): each is an ordinary CakeUI element, parsed from
 * its definition and drawn at its edit's position after the rest of the HUD. A layout's own added
 * elements (dev mode) use the layout's renderer (its declarations, fonts, assets/); a player's own use a
 * small renderer without a layout, whose images come from {@code config/cakeui/images}. Render thread.
 */
public final class HudAdded {
	@Nullable
	private static Renderer personal;

	private record Cached(String json, @Nullable Node node, Renderer renderer) {
	}

	private static final Map<String, Cached> NODES = new HashMap<>();

	private HudAdded() {
	}

	/** Where a player's own image elements come from. */
	public static Path imagesDir() {
		return Services.PLATFORM.configDir().resolve(CakeUI.MOD_ID).resolve("images");
	}

	/** After a layout reload: parse everything again (the layout's scope may have changed). */
	static void reset() {
		if (personal != null) {
			personal.close();
			personal = null;
		}
		NODES.clear();
	}

	static Renderer personalRenderer() {
		if (personal == null) {
			Layout l = new Layout("personal", Services.PLATFORM.configDir().resolve(CakeUI.MOD_ID));
			l.assets = imagesDir();
			personal = new Renderer(l);
		}
		return personal;
	}

	/** The renderer an added element is drawn with: the layout's for its own, else the personal one. */
	static Renderer rendererFor(String key) {
		Renderer layout = Ui.renderer();
		return layout != null && HudEditor.fromLayout(key) ? layout : personalRenderer();
	}

	/** The element's node, parsed again only when its definition changed; null when it can't be used. */
	@Nullable
	static Node node(String key, JsonObject def, Renderer r) {
		String json = def.toString();
		Cached c = NODES.get(key);
		if (c == null || !c.json.equals(json) || c.renderer != r) {
			c = new Cached(json, Loader.element(r.layout, key, json, "hud.json5"), r);
			NODES.put(key, c);
		}
		return c.node;
	}

	/** The problems a definition has (for the properties panel), checked in the scope it will be drawn in. */
	static List<Problems.Problem> check(String key, JsonObject def) {
		Renderer r = rendererFor(key);
		Layout probe = new Layout(r.layout.name, r.layout.root, r.layout.scope);
		probe.assets = r.layout.assets;
		Node n = Loader.element(probe, key, def.toString(), "element");
		List<Problems.Problem> out = new java.util.ArrayList<>(probe.problems.list());
		if (n != null && n.type.equals("image")) {
			String src = def.has("src") ? def.get("src").getAsString() : "";
			if (src.isEmpty() || !java.nio.file.Files.isRegularFile(r.layout.assets.resolve(src))) {
				out.add(new Problems.Problem("element", 0, "image not found: " + src));
			}
		}
		return out;
	}

	/** Draws every added element of this frame's edits (not with F1). */
	static void draw(GuiGraphicsExtractor ctx, Map<String, PortEdit> edits, int w, int h) {
		Renderer began1 = null;
		Renderer began2 = null;
		for (Map.Entry<String, PortEdit> en : edits.entrySet()) {
			String key = en.getKey();
			PortEdit e = en.getValue();
			if (!key.startsWith(HudEdits.ADDED) || e.element == null) {
				continue;
			}
			HudBounds.hooked(key);
			if (Boolean.TRUE.equals(e.hide)) {
				continue;
			}
			Renderer r = rendererFor(key);
			if (r != began1 && r != began2) {
				r.beginFrame(w, h, 0, 0, false);
				if (began1 == null) {
					began1 = r;
				} else {
					began2 = r;
				}
			}
			Node n = node(key, e.element, r);
			if (n == null) {
				continue;
			}
			Box inner = HudBounds.box(key);
			if (inner == null) {
				double[] s = AddedElements.guessSize(e.element);
				inner = new Box(0, 0, s[0], s[1]);
			}
			Xform x = Placement.transform(e, inner, w, h);
			ctx.pose().pushMatrix();
			ctx.pose().translate((float) (x.tx()), (float) (x.ty()));
			ctx.pose().scale((float) x.s(), (float) x.s());
			String before = HudBounds.begin(key, x);
			Node box = new Node("added", "group", r.layout.scope, "hud.json5", 0);
			box.children.add(n);
			r.drawElements(ctx, box, e.opacity == null ? 1 : e.opacity);
			HudBounds.end(before);
			ctx.pose().popMatrix();
		}
	}
}
