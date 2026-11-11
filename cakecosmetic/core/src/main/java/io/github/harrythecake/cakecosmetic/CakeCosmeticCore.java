package io.github.harrythecake.cakecosmetic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What CakeCosmetic's version-independent code (files, rules, picks) shares: the mod id and the log. */
public final class CakeCosmeticCore {
	public static final String MOD_ID = "cakecosmetic";
	public static final Logger LOG = LoggerFactory.getLogger("CakeCosmetic");

	private CakeCosmeticCore() {
	}
}
