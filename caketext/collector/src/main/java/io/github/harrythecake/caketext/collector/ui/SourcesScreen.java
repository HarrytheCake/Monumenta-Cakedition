package io.github.harrythecake.caketext.collector.ui;

import io.github.harrythecake.caketext.collector.CollectorConfig;
import io.github.harrythecake.caketext.core.module.CakeTextModule;
import io.github.harrythecake.caketext.core.module.Modules;
import io.github.harrythecake.caketext.core.ui.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Which surfaces are collected; also lists the active modules. */
final class SourcesScreen extends OptionsScreen {
	private final CollectorConfig config;

	SourcesScreen(Screen parent, CollectorConfig config) {
		super(Text.translatable("caketext_collector.sources.title"), parent);
		this.config = config;
	}

	@Override
	protected void build() {
		StringBuilder modules = new StringBuilder();
		for (CakeTextModule m : Modules.active()) {
			modules.append(modules.isEmpty() ? "" : ", ").append(m.id());
		}
		String list = modules.isEmpty() ? "-" : modules.toString();
		status(() -> Text.translatable("caketext_collector.status.modules", list));
		for (String surface : config.sources.keySet()) {
			add(toggle(Text.translatable("caketext.surface." + surface), config.source(surface),
					v -> config.sources.put(surface, v)));
		}
	}
}
