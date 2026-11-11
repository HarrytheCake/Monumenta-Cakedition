package io.github.harrythecake.caketext.collector;

import java.util.function.BiConsumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.harrythecake.caketext.collector.ui.CollectorScreen;
import io.github.harrythecake.caketext.core.Json;
import io.github.harrythecake.caketext.core.strings.ExchangeFolder;
import io.github.harrythecake.caketext.core.module.CakeTextModule;
import io.github.harrythecake.caketext.core.module.Modules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * {@code /ctcollect} [settings | status | save | export | exchange | on | off]. A client command on both loaders:
 * {@code S} is the loader's client command source, {@code feedback} prints a line in chat.
 */
public final class CollectorCommands {
	private CollectorCommands() {
	}

	private static <S> LiteralArgumentBuilder<S> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	public static <S> void register(CommandDispatcher<S> dispatcher, BiConsumer<S, Component> feedback) {
		dispatcher.register(CollectorCommands.<S>literal("ctcollect")
				.executes(c -> settings())
				.then(CollectorCommands.<S>literal("settings").executes(c -> settings()))
				.then(CollectorCommands.<S>literal("status").executes(c -> status(line -> feedback.accept(c.getSource(), line))))
				.then(CollectorCommands.<S>literal("save").executes(c -> {
					CollectorStatus.saveNow();
					feedback.accept(c.getSource(), Component.translatable("caketext_collector.command.saved", CollectorMod.store().size()));
					return 1;
				}))
				.then(CollectorCommands.<S>literal("export").executes(c -> {
					feedback.accept(c.getSource(), CollectorStatus.exportNow());
					return 1;
				}))
				.then(CollectorCommands.<S>literal("exchange").executes(c -> {
					feedback.accept(c.getSource(), ExchangeFolder.run());
					return 1;
				}))
				.then(CollectorCommands.<S>literal("on").executes(c -> enable(line -> feedback.accept(c.getSource(), line), true)))
				.then(CollectorCommands.<S>literal("off").executes(c -> enable(line -> feedback.accept(c.getSource(), line), false))));
	}

	private static int settings() {
		Minecraft client = Minecraft.getInstance();
		// opened next tick: the chat screen closes after this command returns
		client.tell(() -> client.setScreen(new CollectorScreen(null)));
		return 1;
	}

	private static int status(java.util.function.Consumer<Component> source) {
		source.accept(Component.translatable("caketext_collector.title").withStyle(ChatFormatting.GOLD));
		source.accept(Component.translatable(CollectorMod.config().enabled ? "caketext_collector.status.on" : "caketext_collector.status.off"));
		source.accept(CollectorStatus.summary());
		source.accept(CollectorStatus.fileLine());
		CollectorConfig config = CollectorMod.config();
		source.accept(config.autoExport
				? Component.translatable("caketext_collector.status.auto_export_on", config.autoExportMinutes)
				: Component.translatable("caketext_collector.status.auto_export_off"));
		StringBuilder modules = new StringBuilder();
		for (CakeTextModule m : Modules.active()) {
			modules.append(modules.isEmpty() ? "" : ", ").append(m.id());
		}
		source.accept(Component.translatable("caketext_collector.status.modules", modules.isEmpty() ? "-" : modules.toString())
				.withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int enable(java.util.function.Consumer<Component> source, boolean on) {
		CollectorConfig config = Json.copy(CollectorMod.config(), CollectorConfig.class);
		config.enabled = on;
		CollectorMod.setConfig(config);
		source.accept(Component.translatable(on ? "caketext_collector.status.on" : "caketext_collector.status.off"));
		return 1;
	}
}
