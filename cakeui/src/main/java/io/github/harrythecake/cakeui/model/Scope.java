package io.github.harrythecake.cakeui.model;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.expr.Expr;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names one folder declares. A folder sees its own names and its parents', never a sibling's;
 * a child's name hides the parent's. Only the folder's own state variables can be set.
 */
public final class Scope {
	public final Scope parent;
	/** The folder, relative to the layout ("" for the window). */
	public final String folder;
	final Map<String, Slot> slots = new LinkedHashMap<>();

	public Scope(Scope parent, String folder) {
		this.parent = parent;
		this.folder = folder;
	}

	public Slot find(String name) {
		for (Scope s = this; s != null; s = s.parent) {
			Slot slot = s.slots.get(name);
			if (slot != null) {
				return slot;
			}
		}
		return null;
	}

	public Slot own(String name) {
		return slots.get(name);
	}

	public Map<String, Slot> slots() {
		return slots;
	}

	/** One declared name. */
	public abstract static sealed class Slot {
		public final String name;
		public final String file;
		public final int line;

		Slot(String name, String file, int line) {
			this.name = name;
			this.file = file;
			this.line = line;
		}

		public abstract Object get(Env env);

		/** An expression that reads this slot; constants are read once. */
		public Expr reader() {
			return this::get;
		}
	}

	/**
	 * A constant: computed once at load. One that uses units (12r, 50%) depends on the screen,
	 * so it is computed where it is used instead.
	 */
	public static final class Constant extends Slot {
		Object value;
		Expr expr;
		boolean computing;

		Constant(String name, String file, int line) {
			super(name, file, line);
		}

		@Override
		public Object get(Env env) {
			if (expr != null && !expr.isConstant()) {
				return expr.eval(env);
			}
			if (value == null && expr != null) {
				if (computing) {
					throw new IllegalStateException("'" + name + "' depends on itself");
				}
				computing = true;
				try {
					value = expr.eval(env);
				} finally {
					computing = false;
				}
			}
			return value == null ? 0.0 : value;
		}

		@Override
		public Expr reader() {
			return value != null ? Expr.constant(value) : this::get;
		}
	}

	/** A variable that holds a value and can be set by a button action of the same folder. */
	public static final class State extends Slot {
		private Object value;

		State(String name, String file, int line, Object initial) {
			super(name, file, line);
			this.value = initial;
		}

		@Override
		public Object get(Env env) {
			return value;
		}

		public void set(Object value) {
			this.value = value;
		}
	}

	/** A variable computed from an expression, at most once per frame. */
	public static final class Computed extends Slot {
		Expr expr;
		private long frame = -1;
		private Object cached;
		private boolean computing;

		Computed(String name, String file, int line) {
			super(name, file, line);
		}

		@Override
		public Object get(Env env) {
			if (frame == env.frame && cached != null) {
				return cached;
			}
			if (computing) {
				throw new IllegalStateException("'" + name + "' depends on itself");
			}
			computing = true;
			try {
				cached = expr == null ? 0.0 : expr.eval(env);
				frame = env.frame;
			} finally {
				computing = false;
			}
			return cached;
		}
	}
}
