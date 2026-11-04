package io.github.harrythecake.cakefixes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared state. There is no entrypoint: the mixins read the config on first use. */
public final class CakeFixes {
	public static final String MOD_ID = "cakefixes";
	public static final Logger LOGGER = LoggerFactory.getLogger("CakeFixes");

	private static Config config;

	private CakeFixes() {}

	public static synchronized Config config() {
		if (config == null) {
			config = Config.load();
		}
		return config;
	}

	static synchronized void setConfig(Config newConfig) {
		config = newConfig;
	}
}
