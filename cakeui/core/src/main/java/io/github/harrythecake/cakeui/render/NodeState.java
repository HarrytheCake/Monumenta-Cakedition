package io.github.harrythecake.cakeui.render;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.model.Node;

import java.util.HashMap;
import java.util.Map;

/** What the renderer remembers about one drawn node between frames: hover, press, animations, smoothed mouse. */
final class NodeState implements Env.SelfValues {
	final Node node;
	boolean hovered;
	boolean wasHovered;
	boolean pressed;
	boolean enabled = true;
	double width;
	double height;
	String text = "";
	String id = "";
	/** Parallax: the mouse as this node has followed it so far. */
	double smoothX;
	double smoothY;
	long parallaxFrame = -1;
	final Map<String, Anim> anims = new HashMap<>();

	NodeState(Node node) {
		this.node = node;
		this.id = node.name;
	}

	@Override
	public Object get(String name) {
		return switch (name) {
			case "hovered" -> hovered;
			case "pressed" -> pressed;
			case "active" -> enabled;
			case "width" -> width;
			case "height" -> height;
			case "text" -> text;
			case "id" -> id;
			default -> null;
		};
	}

	/** One property easing from its old value to a new one. */
	static final class Anim {
		boolean started;
		double from;
		double to;
		double current;
		double startTime;
	}
}
