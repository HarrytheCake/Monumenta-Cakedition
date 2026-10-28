package io.github.harrythecake.cakewiki;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CakeWiki implements ClientModInitializer {
	public static final String MOD_ID = "cakewiki";
	public static final Logger LOG = LoggerFactory.getLogger("CakeWiki");

	@Override
	public void onInitializeClient() {
		LOG.info("CakeWiki loaded");
	}
}
