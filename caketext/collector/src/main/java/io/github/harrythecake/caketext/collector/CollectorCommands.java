package io.github.harrythecake.caketext.collector;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import io.github.harrythecake.caketext.collector.ui.CollectorScreen;
import io.github.harrythecake.caketext.core.Json;
import io.github.harrythecake.caketext.core.strings.ExchangeFolder;
import io.github.harrythecake.caketext.core.module.CakeTextModule;
import io.github.harrythecake.caketext.core.module.Modules;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** {@code /ctcollect} [settings | status | save | export | exchange | on | off] */
final class CollectorCommands {
	private CollectorCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("ctcollect")
				.executes(c -> settings())
				.then(literal("settings").executes(c -> settings()))
				.then(literal("status").executes(c -> status(c.getSource())))
				.then(literal("save").executes(c -> {
					CollectorStatus.saveNow();
					c.getSource().sendFeedback(Text.translatable("caketext_collector.command.saved", CollectorMod.store().size()));
					return 1;
				}))
				.then(literal("export").executes(c -> {
					c.getSource().sendFeedback(CollectorStatus.exportNow());
					return 1;
				}))
				.then(literal("exchange").executes(c -> {
					c.getSource().sendFeedback(ExchangeFolder.run());
					return 1;
				}))
				.then(literal("on").executes(c -> enable(c.getSource(), true)))
				.then(literal("off").executes(c -> enable(c.getSource(), false))));
	}

	private static int settings() {
		MinecraftClient client = MinecraftClient.getInstance();
		// opened next tick: the chat screen closes after this command returns
		client.send(() -> client.setScreen(new CollectorScreen(null)));
		return 1;
	}

	private static int status(FabricClientCommandSource source) {
		source.sendFeedback(Text.translatable("caketext_collector.title").formatted(Formatting.GOLD));
		source.sendFeedback(Text.translatable(CollectorMod.config().enabled ? "caketext_collector.status.on" : "caketext_collector.status.off"));
		source.sendFeedback(CollectorStatus.summary());
		source.sendFeedback(CollectorStatus.fileLine());
		CollectorConfig config = CollectorMod.config();
		source.sendFeedback(config.autoExport
				? Text.translatable("caketext_collector.status.auto_export_on", config.autoExportMinutes)
				: Text.translatable("caketext_collector.status.auto_export_off"));
		StringBuilder modules = new StringBuilder();
		for (CakeTextModule m : Modules.active()) {
			modules.append(modules.isEmpty() ? "" : ", ").append(m.id());
		}
		source.sendFeedback(Text.translatable("caketext_collector.status.modules", modules.isEmpty() ? "-" : modules.toString())
				.formatted(Formatting.GRAY));
		return 1;
	}

	private static int enable(FabricClientCommandSource source, boolean on) {
		CollectorConfig config = Json.copy(CollectorMod.config(), CollectorConfig.class);
		config.enabled = on;
		CollectorMod.setConfig(config);
		source.sendFeedback(Text.translatable(on ? "caketext_collector.status.on" : "caketext_collector.status.off"));
		return 1;
	}
}
