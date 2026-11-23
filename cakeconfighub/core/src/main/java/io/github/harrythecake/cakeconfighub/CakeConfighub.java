package io.github.harrythecake.cakeconfighub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CakeConfighub: the config hub (one screen for the UI/HUD options of many mods, written into each mod's own file
 * through config maps) and pack defaults (the pack's values, patched in at startup while the player hasn't changed
 * them). ADR 0011 and 0013. Standalone: needs no other mod, and no mod needs it.
 */
public final class CakeConfighub {
	public static final String MOD_ID = "cakeconfighub";
	public static final Logger LOGGER = LoggerFactory.getLogger("CakeConfighub");
	/** -Dcakeconfighub.selftest=1: the dev client's self-test (made-up map, file and pack defaults; see ConfigHubSelfTest). */
	public static final boolean SELF_TEST = !System.getProperty("cakeconfighub.selftest", "").isEmpty();

	private CakeConfighub() {
	}
}
