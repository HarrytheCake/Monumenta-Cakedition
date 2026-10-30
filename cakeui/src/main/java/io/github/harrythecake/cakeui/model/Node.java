package io.github.harrythecake.cakeui.model;

import io.github.harrythecake.cakeui.expr.Expr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One loaded thing to draw: a part's root, a subwindow, an element, a port, or a button override.
 * Built once per load; the renderer keeps per-frame state elsewhere.
 */
public final class Node {
	public final String name;
	public final String type;
	public final Scope scope;
	public final String file;
	public final int line;
	public final Map<String, Prop> props = new LinkedHashMap<>();
	public final List<Node> children = new ArrayList<>();
	/** A container's background drawn under its children (from `background: { … }`). */
	public Node background;
	/** Nine-slice borders in texture pixels (left, top, right, bottom), or null. */
	public int[] slice;
	public Action action;
	/** Ports of a part, by name. */
	public final Map<String, Node> ports = new LinkedHashMap<>();
	/** Button overrides of a `buttons` port, by button id. */
	public final Map<String, Node> buttons = new LinkedHashMap<>();
	/** Splash list of a `splash` port (null = vanilla's). */
	public List<String> splashes;
	/** True when the splash port is drawn by CakeUI rather than vanilla. */
	public boolean customStyle;
	/** A container's background: it defaults to the container's full size. */
	public boolean fill;
	/** True when any prop has states, so the renderer tracks hover for it. */
	public boolean interactive;

	public Node(String name, String type, Scope scope, String file, int line) {
		this.name = name;
		this.type = type;
		this.scope = scope;
		this.file = file;
		this.line = line;
	}

	public Prop prop(String key) {
		return props.get(key);
	}

	public boolean has(String key) {
		return props.containsKey(key);
	}

	public boolean isContainer() {
		return PropKinds.isContainer(type) || type.startsWith("port");
	}

	@Override
	public String toString() {
		return type + " '" + name + "' (" + file + ":" + line + ")";
	}

	/** What a button does when clicked. Client-side only: never chat or commands. */
	public record Action(String kind, String argument, List<Assign> assigns) {
		public static final List<String> SCREENS = List.of("singleplayer", "multiplayer", "realms", "options", "language",
				"accessibility", "modmenu", "quit", "back");

		public Action {
			assigns = assigns == null ? new ArrayList<>() : assigns;
		}
	}

	/** `set: { name: value }` or `toggle: "name"` on one of the folder's own state variables. */
	public record Assign(Scope.State target, Expr value, boolean toggle) {
	}
}
