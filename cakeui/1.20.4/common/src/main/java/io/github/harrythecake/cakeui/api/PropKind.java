package io.github.harrythecake.cakeui.api;

/** What kind of value an element property takes in a layout file. */
public enum PropKind {
	/** A plain number (or an expression giving one). */
	NUMBER,
	/** A length: a plain number is design pixels; "12px", "50%" and expressions work too. */
	LENGTH,
	/** A colour: #RGB, #RRGGBB, #RRGGBBAA, or an expression giving one. */
	COLOR,
	/** true / false, or an expression. */
	BOOL,
	/** Text with {expressions} and {t:keys}. */
	TEXT,
	/** A word or id, like an item id or a mode name. */
	WORD
}
