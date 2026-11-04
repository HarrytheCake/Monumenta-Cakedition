package io.github.harrythecake.cakeui.json;

import java.util.List;
import java.util.Map;

/** A parsed JSON5 value that knows the file and line it came from. */
public abstract sealed class JValue {
	public final String file;
	public final int line;

	JValue(String file, int line) {
		this.file = file;
		this.line = line;
	}

	/** "file:line", for problem messages. */
	public String where() {
		return file + ":" + line;
	}

	public abstract String typeName();

	public static final class JObject extends JValue {
		public final Map<String, JValue> map;
		private final Map<String, Integer> keyLines;

		public JObject(Map<String, JValue> map, Map<String, Integer> keyLines, String file, int line) {
			super(file, line);
			this.map = map;
			this.keyLines = keyLines;
		}

		public JValue get(String key) {
			return map.get(key);
		}

		/** The line the key is written on (falls back to the object's line). */
		public int keyLine(String key) {
			return keyLines.getOrDefault(key, line);
		}

		@Override
		public String typeName() {
			return "an object";
		}
	}

	public static final class JArray extends JValue {
		public final List<JValue> list;

		public JArray(List<JValue> list, String file, int line) {
			super(file, line);
			this.list = list;
		}

		@Override
		public String typeName() {
			return "a list";
		}
	}

	public static final class JString extends JValue {
		public final String value;

		public JString(String value, String file, int line) {
			super(file, line);
			this.value = value;
		}

		@Override
		public String typeName() {
			return "text";
		}
	}

	public static final class JNumber extends JValue {
		public final double value;

		public JNumber(double value, String file, int line) {
			super(file, line);
			this.value = value;
		}

		@Override
		public String typeName() {
			return "a number";
		}
	}

	public static final class JBool extends JValue {
		public final boolean value;

		public JBool(boolean value, String file, int line) {
			super(file, line);
			this.value = value;
		}

		@Override
		public String typeName() {
			return "true/false";
		}
	}

	public static final class JNull extends JValue {
		public JNull(String file, int line) {
			super(file, line);
		}

		@Override
		public String typeName() {
			return "null";
		}
	}
}
