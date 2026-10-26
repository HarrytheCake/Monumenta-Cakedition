package io.github.harrythecake.caketext.collector.ui;

import io.github.harrythecake.caketext.collector.CollectorConfig;
import io.github.harrythecake.caketext.collector.CollectorMod;
import io.github.harrythecake.caketext.collector.CollectorStatus;
import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.Json;
import io.github.harrythecake.caketext.core.ui.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/** Collector settings (Mod Menu, or {@code /ctcollect}). */
public final class CollectorScreen extends OptionsScreen {
	private final CollectorConfig config;
	private Text exportResult;

	public CollectorScreen(@Nullable Screen parent) {
		super(Text.translatable("caketext_collector.title"), parent);
		this.config = Json.copy(CollectorMod.config(), CollectorConfig.class);
	}

	private static Text t(String key, Object... args) {
		return Text.translatable("caketext_collector." + key, args);
	}

	@Override
	protected void build() {
		status(CollectorStatus::summary);
		status(CollectorStatus::fileLine);
		if (exportResult != null) {
			Text result = exportResult;
			status(() -> result);
		}
		add(tip(toggle(t("option.enabled"), config.enabled, v -> config.enabled = v), t("option.enabled.tip")));
		add(tip(cycle(t("option.format"), java.util.List.of(true, false), config.gzip,
				gz -> Text.literal(gz ? ".json.gz" : ".json"), v -> config.gzip = v), t("option.format.tip")));
		add(tip(toggle(t("option.positions"), config.positions, v -> config.positions = v), t("option.positions.tip")));
		add(tip(toggle(t("option.multiplayer_only"), config.multiplayerOnly, v -> config.multiplayerOnly = v),
				t("option.multiplayer_only.tip")));
		add(tip(toggle(t("option.export_positions"), config.exportPositions, v -> config.exportPositions = v),
				t("option.export_positions.tip")));
		add(tip(toggle(t("option.auto_export"), config.autoExport, v -> config.autoExport = v),
				t("option.auto_export.tip", config.autoExportMinutes)));
		add(button(t("option.sources"), b -> client.setScreen(new SourcesScreen(this, config))));
		add(button(t("option.save"), b -> {
			CollectorMod.setConfig(config);
			CollectorStatus.saveNow();
		}));
		add(tip(button(t("option.export"), b -> {
			CollectorMod.setConfig(config);
			exportResult = CollectorStatus.exportNow();
			clearAndInit();
		}), t("option.export.tip")));
		add(tip(button(t("option.exchange"), b -> {
			exportResult = io.github.harrythecake.caketext.core.strings.ExchangeFolder.run();
			clearAndInit();
		}), Text.translatable("caketext.exchange.tip")));
		add(button(t("option.open_folder"), b -> {
			java.nio.file.Path dir = CakeText.dir();
			try {
				java.nio.file.Files.createDirectories(dir);
			} catch (java.io.IOException ignored) {
				// opening still shows the parent
			}
			Util.getOperatingSystem().open(dir.toFile());
		}));
	}

	@Override
	protected void onClose() {
		CollectorMod.setConfig(config);
	}
}
