package io.github.harrythecake.caketext.translator.ui;

import static io.github.harrythecake.caketext.translator.ui.TranslatorScreen.key;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.harrythecake.caketext.core.ui.OptionsScreen;
import io.github.harrythecake.caketext.translator.ModelManager;
import io.github.harrythecake.caketext.translator.TranslationService;
import io.github.harrythecake.caketext.translator.TranslatorConfig;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Which engine translates new lines: the built-in opus-mt model, or an LLM in a local server (e.g. Hunyuan-MT-7B). */
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
		status(() -> Text.translatable(key("engine.status_opus"), service.models().describe()));
		status(() -> Text.translatable(key("engine.status_server"), service.serverStatus()));

		add(tip(cycle(Text.translatable(key("option.engine_type")), Arrays.asList(TranslatorConfig.Engine.values()), config.engine,
				e -> Text.translatable(key("engine." + e.name().toLowerCase(Locale.ROOT))), v -> config.engine = v),
				Text.translatable(key("option.engine_type.tip"))));
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
		add(tip(cycle(Text.translatable(key("option.protocol")), Arrays.asList(TranslatorConfig.Protocol.values()),
				config.serverProtocol, p -> Text.translatable(key("protocol." + p.name().toLowerCase(Locale.ROOT))),
				v -> config.serverProtocol = v), Text.translatable(key("option.protocol.tip"))));

		addWide(label(Text.translatable(key("option.server_url"))));
		addWide(tip(textField(Text.translatable(key("option.server_url")), config.serverUrl, v -> config.serverUrl = v.trim()),
				Text.translatable(key("option.server_url.tip"))));
		addWide(label(Text.translatable(key("option.server_model"))));
		addWide(tip(textField(Text.translatable(key("option.server_model")), config.serverModel, v -> config.serverModel = v.trim()),
				Text.translatable(key("option.server_model.tip"))));
		add(tip(button(Text.translatable(key("option.cache_clear")), b -> {
			parent.apply();
			service.machine().clearCache();
			b.active = false;
		}), Text.translatable(key("option.cache_clear.tip"))));
		addWide(tip(button(Text.translatable(key("option.server_check")), b -> {
			parent.apply();
			service.recheckServer();
		}), Text.translatable(key("option.server_check.tip"))));
	}

	@Override
	protected void onClose() {
		parent.apply();
	}
}
