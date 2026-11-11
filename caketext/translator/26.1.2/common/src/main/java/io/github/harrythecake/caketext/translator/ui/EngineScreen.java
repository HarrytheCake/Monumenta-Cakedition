package io.github.harrythecake.caketext.translator.ui;

import static io.github.harrythecake.caketext.translator.ui.TranslatorScreen.key;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.util.Util;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import io.github.harrythecake.caketext.core.ui.OptionsScreen;
import io.github.harrythecake.caketext.translator.ModelManager;
import io.github.harrythecake.caketext.translator.TranslationService;
import io.github.harrythecake.caketext.translator.TranslatorConfig;
import io.github.harrythecake.caketext.translator.TranslatorMod;

/**
 * Which engine translates new lines: the built-in opus-mt model, an LLM in a local server (e.g. Hunyuan-MT-7B) or an
 * online service. Only the chosen engine's settings are shown (the screen doesn't scroll); switching rebuilds it.
 */
final class EngineScreen extends OptionsScreen {
	private final TranslatorScreen parent;
	private final TranslatorConfig config;

	EngineScreen(TranslatorScreen parent, TranslatorConfig config) {
		super(Component.translatable(key("engine.title")), parent);
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void build() {
		TranslationService service = TranslatorMod.service();
		switch (config.engine) {
			case OPUS_MT -> status(() -> Component.translatable(key("engine.status_opus"), service.models().describe()));
			case SERVER -> status(() -> Component.translatable(key("engine.status_server"), service.serverStatus()));
			case ONLINE -> status(() -> Component.translatable(key("engine.status_online"), service.onlineStatus()));
		}

		add(tip(cycle(Component.translatable(key("option.engine_type")), Arrays.asList(TranslatorConfig.Engine.values()), config.engine,
				e -> Component.translatable(key("engine." + e.name().toLowerCase(Locale.ROOT))), v -> {
					config.engine = v;
					rebuildWidgets();
				}), Component.translatable(key("option.engine_type.tip"))));
		add(tip(button(Component.translatable(key("option.cache_clear")), b -> {
			parent.apply();
			service.machine().clearCache();
			b.active = false;
		}), Component.translatable(key("option.cache_clear.tip"))));

		switch (config.engine) {
			case OPUS_MT -> opus(service);
			case SERVER -> server(service);
			case ONLINE -> online();
		}
	}

	private void opus(TranslationService service) {
		add(tip(cycle(Component.translatable(key("option.quality")), List.of("best", "fast"), config.machineQuality,
				q -> Component.translatable(key("quality." + q)), v -> config.machineQuality = v),
				Component.translatable(key("option.quality.tip"))));
		ModelManager models = service.models();
		if (models.state() == ModelManager.State.READY) {
			add(button(Component.translatable(key("option.model_delete")), b -> {
				models.delete();
				rebuildWidgets();
			}));
		} else {
			// the mod downloads nothing: the player fetches the files from the page into the folder, then installs
			add(tip(button(Component.translatable(key("option.model_page")), b -> Util.getPlatform().openUri(ModelManager.PAGE_URL)),
					Component.translatable(key("option.model_page.tip"), ModelManager.FILE_NAMES, ModelManager.DOWNLOAD_BYTES >> 20)));
			add(tip(button(Component.translatable(key("option.model_folder")), b -> ModelManager.openImportDir()),
					Component.translatable(key("option.model_folder.tip"), ModelManager.FILE_NAMES)));
			add(tip(button(Component.translatable(key("option.model_install")), b -> {
				models.install();
				b.active = false;
			}), Component.translatable(key("option.model_install.tip"))));
		}
	}

	private void server(TranslationService service) {
		add(tip(cycle(Component.translatable(key("option.protocol")), Arrays.asList(TranslatorConfig.Protocol.values()),
				config.serverProtocol, p -> Component.translatable(key("protocol." + p.name().toLowerCase(Locale.ROOT))), v -> {
					config.serverProtocol = v;
					rebuildWidgets();
				}), Component.translatable(key("option.protocol.tip"))));
		add(tip(button(Component.translatable(key("option.server_check")), b -> {
			parent.apply();
			service.recheckServer();
		}), Component.translatable(key("option.server_check.tip"))));
		field("option.server_url", config.serverUrl, v -> config.serverUrl = v.trim(), false);
		field("option.server_model", config.serverModel, v -> config.serverModel = v.trim(), false);
		if (config.serverProtocol == TranslatorConfig.Protocol.OPENAI) {
			field("option.server_key", config.serverApiKey, v -> config.serverApiKey = v.trim(), true);
		}
	}

	private void online() {
		add(tip(cycle(Component.translatable(key("option.online_service")), Arrays.asList(TranslatorConfig.OnlineService.values()),
				config.onlineService, s -> Component.translatable(key("online." + s.name().toLowerCase(Locale.ROOT))), v -> {
					config.onlineService = v;
					rebuildWidgets();
				}), Component.translatable(key("option.online_service.tip"), config.onlineCharLimit)));
		if (config.onlineService == TranslatorConfig.OnlineService.LIBRETRANSLATE) {
			field("option.online_url", config.onlineUrl, v -> config.onlineUrl = v.trim(), false);
		}
		field("option.online_key", config.onlineApiKey, v -> config.onlineApiKey = v.trim(), true);
		if (config.onlineService == TranslatorConfig.OnlineService.MICROSOFT) {
			field("option.online_region", config.onlineRegion, v -> config.onlineRegion = v.trim(), false);
		}
	}

	/** A labelled text box; {@code secret} boxes show stars, so a key never appears on a stream or screenshot. */
	private void field(String name, String value, Consumer<String> set, boolean secret) {
		addWide(label(Component.translatable(key(name))));
		EditBox f = textField(Component.translatable(key(name)), value, set);
		if (secret) {
			f.addFormatter((s, first) -> FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY));
		}
		addWide(tip(f, Component.translatable(key(name + ".tip"))));
	}

	@Override
	protected void onClosing() {
		parent.apply();
	}
}
