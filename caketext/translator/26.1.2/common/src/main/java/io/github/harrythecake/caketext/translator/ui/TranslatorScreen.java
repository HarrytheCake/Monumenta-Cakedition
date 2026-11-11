package io.github.harrythecake.caketext.translator.ui;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.util.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.Json;
import io.github.harrythecake.caketext.core.ui.OptionsScreen;
import io.github.harrythecake.caketext.translator.Dictionary;
import io.github.harrythecake.caketext.translator.TranslationService;
import io.github.harrythecake.caketext.translator.TranslatorConfig;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import org.jetbrains.annotations.Nullable;

/** Translator settings (Mod Menu, or {@code /cttranslate}). */
public final class TranslatorScreen extends OptionsScreen {
	private final TranslatorConfig config;

	public TranslatorScreen(@Nullable Screen parent) {
		super(Component.translatable("caketext_translator.title"), parent);
		// edit a copy; it replaces the live config when the screen closes
		this.config = Json.copy(TranslatorMod.service().config(), TranslatorConfig.class);
	}

	static String key(String s) {
		return "caketext_translator." + s;
	}

	static Component displayName(TranslatorConfig.Display d) {
		return Component.translatable(key("display." + d.name().toLowerCase(Locale.ROOT)));
	}

	@Override
	protected void build() {
		TranslationService service = TranslatorMod.service();
		status(() -> {
			Dictionary d = service.dictionary();
			return Component.translatable(key("status.dictionary"), d.humanCount(), d.files(), d.machineCount());
		});
		status(() -> service.engine().describe());

		add(tip(toggle(Component.translatable(key("option.enabled")), config.enabled, v -> config.enabled = v),
				Component.translatable(key("option.enabled.tip"))));
		add(cycle(Component.translatable(key("option.language")), TranslatorConfig.LANGUAGES.keySet().stream().sorted().toList(),
				config.language, l -> Component.translatable(key("language." + l)), v -> config.language = v));
		add(tip(cycle(Component.translatable(key("option.display")), Arrays.asList(TranslatorConfig.Display.values()), config.display,
				TranslatorScreen::displayName, v -> config.display = v), Component.translatable(key("option.display.tip"))));
		add(tip(cycle(Component.translatable(key("option.chat_display")), Arrays.asList(TranslatorConfig.Display.values()),
				config.chatDisplay, TranslatorScreen::displayName, v -> config.chatDisplay = v),
				Component.translatable(key("option.chat_display.tip"))));
		add(tip(cycle(Component.translatable(key("option.player_chat_display")), Arrays.asList(TranslatorConfig.Display.values()),
				config.playerChatDisplay, TranslatorScreen::displayName, v -> config.playerChatDisplay = v),
				Component.translatable(key("option.player_chat_display.tip"))));
		add(tip(toggle(Component.translatable(key("option.player_chat")), config.playerChat, v -> config.playerChat = v),
				Component.translatable(key("option.player_chat.tip"))));
		add(tip(toggle(Component.translatable(key("option.machine")), config.machine, v -> config.machine = v),
				Component.translatable(key("option.machine.tip"), service.engine().label())));
		add(tip(cycle(Component.translatable(key("option.markers")), Arrays.asList(TranslatorConfig.Markers.values()), config.markers,
				m -> Component.translatable(key("markers." + m.name().toLowerCase(Locale.ROOT))), v -> config.markers = v),
				Component.translatable(key("option.markers.tip"))));
		add(tip(toggle(Component.translatable(key("option.tooltip_footer")), config.tooltipFooter, v -> config.tooltipFooter = v),
				Component.translatable(key("option.tooltip_footer.tip"))));
		add(tip(cycle(Component.translatable(key("option.tooltip_alt")), Arrays.asList(TranslatorConfig.TooltipAlt.values()),
				config.tooltipAlt, a -> Component.translatable(key("tooltip_alt." + a.name().toLowerCase(Locale.ROOT))),
				v -> config.tooltipAlt = v), Component.translatable(key("option.tooltip_alt.tip"))));
		add(button(Component.translatable(key("option.engine")), b -> minecraft.setScreen(new EngineScreen(this, config))));
		add(button(Component.translatable(key("option.surfaces")), b -> minecraft.setScreen(new SurfacesScreen(this, config))));
		add(button(Component.translatable(key("option.reload")), b -> {
			apply();
			service.reload();
		}));
		add(button(Component.translatable(key("option.open_folder")), b -> {
			java.nio.file.Path dir = CakeText.translations(config.language);
			try {
				java.nio.file.Files.createDirectories(dir);
			} catch (java.io.IOException ignored) {
				// opening still shows the parent
			}
			Util.getPlatform().openFile(dir.toFile());
		}));
	}

	void apply() {
		TranslatorMod.service().setConfig(config);
	}

	@Override
	protected void onClosing() {
		apply();
	}
}
