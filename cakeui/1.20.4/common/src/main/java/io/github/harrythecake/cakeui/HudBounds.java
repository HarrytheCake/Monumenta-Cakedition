package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.Placement.Box;
import io.github.harrythecake.cakeui.edit.Placement.Xform;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Where each HUD port was drawn: while a port's piece draws, {@code GuiGraphicsBoundsMixin} adds every
 * blit, fill, text and item to its box. Boxes are kept in the port's "inner" coordinates (without the
 * HUD editor's transform), so the editor can place the piece and the transform doesn't feed back.
 * Render thread only.
 */
public final class HudBounds {
	/** A piece seen within this time counts as live (drawn now, not a preview). */
	private static final long LIVE_MS = 600;

	@Nullable
	private static String current;
	private static Xform currentX = Xform.IDENTITY;
	private static final Map<String, Box> FRAME = new HashMap<>();
	private static final Map<String, Box> LAST = new HashMap<>();
	private static final Map<String, Long> SEEN = new HashMap<>();
	/** When each piece's hook last ran (drawn, or hidden by an edit): a piece whose hook never runs is drawn by another mod. */
	private static final Map<String, Long> HOOKED = new HashMap<>();
	private static final Vector3f V = new Vector3f();

	private HudBounds() {
	}

	/** Starts recording a port; returns the port recorded before (to hand back to {@link #end}). */
	@Nullable
	public static String begin(String port, Xform editorTransform) {
		String before = current;
		current = port;
		currentX = editorTransform;
		return before;
	}

	public static void end(@Nullable String before) {
		current = before;
		if (before == null) {
			currentX = Xform.IDENTITY;
		}
	}

	public static boolean recording() {
		return current != null;
	}

	/** A drawn rectangle (GUI coordinates before the pose). */
	public static void add(Matrix4f pose, float x1, float y1, float x2, float y2) {
		if (current == null || x2 <= x1 || y2 <= y1) {
			return;
		}
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			pose.transformPosition((i & 1) == 0 ? x1 : x2, (i & 2) == 0 ? y1 : y2, 0, V);
			minX = Math.min(minX, V.x);
			minY = Math.min(minY, V.y);
			maxX = Math.max(maxX, V.x);
			maxY = Math.max(maxY, V.y);
		}
		double ix = currentX.innerX(minX);
		double iy = currentX.innerY(minY);
		Box b = new Box(ix, iy, currentX.innerX(maxX) - ix, currentX.innerY(maxY) - iy);
		FRAME.merge(current, b, Box::union);
	}

	/** End of a HUD frame: this frame's boxes become the known ones. */
	public static void endFrame() {
		current = null;
		currentX = Xform.IDENTITY;
		if (FRAME.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Map.Entry<String, Box> e : FRAME.entrySet()) {
			LAST.put(e.getKey(), e.getValue());
			SEEN.put(e.getKey(), now);
		}
		FRAME.clear();
	}

	/** The port's inner box from the last frame it was drawn in, or null if never seen. */
	@Nullable
	public static Box box(String port) {
		return LAST.get(port);
	}

	public static void hooked(String port) {
		HOOKED.put(port, System.currentTimeMillis());
	}

	/** CakeUI's hook for the piece ran within the last moment. */
	public static boolean hookedRecently(String port) {
		Long t = HOOKED.get(port);
		return t != null && System.currentTimeMillis() - t < LIVE_MS;
	}

	/** Drawn within the last moment. */
	public static boolean live(String port) {
		Long t = SEEN.get(port);
		return t != null && System.currentTimeMillis() - t < LIVE_MS;
	}

	/** For screens other than the HUD: forget everything (a new world, a resize). */
	public static void clear() {
		LAST.clear();
		SEEN.clear();
		FRAME.clear();
	}

	static Box orDefault(String port, int w, int h) {
		Box b = box(port);
		return b != null ? b : io.github.harrythecake.cakeui.edit.HudDefaults.box(port, w, h);
	}
}
