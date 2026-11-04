package io.github.harrythecake.cakeui.api;

import java.util.Map;

/**
 * An element type a mod adds, used in layouts as { "type": "mymod:gauge", … }. It gets every
 * common property (anchor, x, y, width, height, opacity, transform, states, …) plus the ones it
 * declares here; CakeUI places it, applies the transform and opacity, and calls {@link #draw} with
 * the matrix already at the element's top-left corner.
 */
public interface ElementType {
	/** Extra properties this type reads, by name. Names other types already use must keep their kind. */
	default Map<String, PropKind> properties() {
		return Map.of();
	}

	/** Size in design pixels when the layout sets no width. */
	default double defaultWidth() {
		return 100;
	}

	/** Size in design pixels when the layout sets no height. */
	default double defaultHeight() {
		return 20;
	}

	/** Draws the element. Render thread, every frame it is visible: keep it cheap. */
	void draw(ElementContext context);
}
