package io.github.harrythecake.caketext.translator.ui;

import static io.github.harrythecake.caketext.translator.ui.TranslatorScreen.key;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import io.github.harrythecake.caketext.core.ui.OptionsScreen;
import io.github.harrythecake.caketext.translator.ModelManager;
import io.github.harrythecake.caketext.translator.TranslationService;
import io.github.harrythecake.caketext.translator.TranslatorConfig;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Which engine translates new lines: the built-in opus-mt model, an LLM in a local server (e.g. Hunyuan-MT-7B) or an
 * online service. Only the chosen engine's settings are shown (the screen doesn't scroll); switching rebuilds it.
 */
final class EngineScreen extends OptionsScreen {
	private final TranslatorScreen parent;
	private final TranslatorConfig config;

	EngineScreen(TranslatorScreen parent, TranslatorConfig config) {
		super(Text.translatable(key("engine.title")), parent);
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void build() {
		TranslationService service = TranslatorMod.service();
		switch (config.engine) {
			case OPUS_MT -> status(() -> Text.translatable(key("engine.status_opus"), service.models().describe()));
			case SERVER -> status(() -> Text.translatable(key("engine.status_server"), service.serverStatus()));
			case ONLINE -> status(() -> Text.translatable(key("engine.status_online"), service.onlineStatus()));
		}

		add(tip(cycle(Text.translatable(key("option.engine_type")), Arrays.asList(TranslatorConfig.Engine.values()), config.engine,
				e -> Text.translatable(key("engine." + e.name().toLowerCase(Locale.ROOT))), v -> {
					config.engine = v;
					clearAndInit();
				}), Text.translatable(key("option.engine_type.tip"))));
		add(tip(button(Text.translatable(key("option.cache_clear")), b -> {
			parent.apply();
			service.machine().clearCache();
			b.active = false;
		}), Text.translatable(key("option.cache_clear.tip"))));

		switch (config.engine) {
			case OPUS_MT -> opus(service);
			case SERVER -> server(service);
			case ONLINE -> online();
		}
	}

	private void opus(TranslationService service) {
		add(tip(cycle(Text.translatable(key("option.quality")), List.of("best", "fast"), config.machineQuality,
				q -> Text.translatable(key("quality." + q)), v -> config.machineQuality = v),
				Text.translatable(key("option.quality.tip"))));
		ModelManager models = service.models();
		if (models.state() == ModelManager.State.READY) {
			add(button(Text.translatable(key("option.model_delete")), b -> {
				models.delete();
				clearAndInit();
			}));
		} else {
			// the mod downloads nothing: the player fetches the files from the page into the folder, then installs
			add(tip(button(Text.translatable(key("option.model_page")), b -> Util.getOperatingSystem().open(ModelManager.PAGE_URL)),
					Text.translatable(key("option.model_page.tip"), ModelManager.FILE_NAMES, ModelManager.DOWNLOAD_BYTES >> 20)));
			add(tip(button(Text.translatable(key("option.model_folder")), b -> ModelManager.openImportDir()),
					Text.translatable(key("option.model_folder.tip"), ModelManager.FILE_NAMES)));
			add(tip(button(Text.translatable(key("option.model_install")), b -> {
				models.install();
				b.active = false;
			}), Text.translatable(key("option.model_install.tip"))));
		}
	}

	private void server(TranslationService service) {
		add(tip(cycle(Text.translatable(key("option.protocol")), Arrays.asList(TranslatorConfig.Protocol.values()),
				config.serverProtocol, p -> Text.translatable(key("protocol." + p.name().toLowerCase(Locale.ROOT))), v -> {
					config.serverProtocol = v;
					clearAndInit();
				}), Text.translatable(key("option.protocol.tip"))));
		add(tip(button(Text.translatable(key("option.server_check")), b -> {
			parent.apply();
			service.recheckServer();
		}), Text.translatable(key("option.server_check.tip"))));
		field("option.server_url", config.serverUrl, v -> config.serverUrl = v.trim(), false);
		field("option.server_model", config.serverModel, v -> config.serverModel = v.trim(), false);
		if (config.serverProtocol == TranslatorConfig.Protocol.OPENAI) {
			field("option.server_key", config.serverApiKey, v -> config.serverApiKey = v.trim(), true);
		}
	}

	private void online() {
		add(tip(cycle(Text.translatable(key("option.online_service")), Arrays.asList(TranslatorConfig.OnlineService.values()),
				config.onlineService, s -> Text.translatable(key("online." + s.name().toLowerCase(Locale.ROOT))), v -> {
					config.onlineService = v;
					clearAndInit();
				}), Text.translatable(key("option.online_service.tip"), config.onlineCharLimit)));
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
		addWide(label(Text.translatable(key(name))));
		TextFieldWidget f = textField(Text.translatable(key(name)), value, set);
		if (secret) {
			f.setRenderTextProvider((s, first) -> OrderedText.styledForwardsVisitedString("*".repeat(s.length()), Style.EMPTY));
		}
		addWide(tip(f, Text.translatable(key(name + ".tip"))));
	}

	@Override
	protected void onClose() {
		parent.apply();
	}
}
