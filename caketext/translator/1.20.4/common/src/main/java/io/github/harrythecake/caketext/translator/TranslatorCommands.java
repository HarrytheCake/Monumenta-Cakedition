package io.github.harrythecake.caketext.translator;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.GameContext;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.strings.ExchangeFolder;
import io.github.harrythecake.caketext.core.template.TextTemplate;
import io.github.harrythecake.caketext.core.template.TextUtil;
import io.github.harrythecake.caketext.core.template.Translated;
import io.github.harrythecake.caketext.translator.ui.TranslatorScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * {@code /cttranslate} [settings | status | reload | cache clear | exchange | test <text> | model folder | model install | model delete]
 */
public final class TranslatorCommands {
	private TranslatorCommands() {
	}

	private static <S> LiteralArgumentBuilder<S> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	/**
	 * A client command on both loaders: {@code S} is the loader's client command source, {@code feedback} prints a
	 * line in chat.
	 */
	public static <S> void register(CommandDispatcher<S> dispatcher, BiConsumer<S, Component> feedback) {
		dispatcher.register(TranslatorCommands.<S>literal("cttranslate")
				.executes(c -> settings())
				.then(TranslatorCommands.<S>literal("settings").executes(c -> settings()))
				.then(TranslatorCommands.<S>literal("status").executes(c -> status(line -> feedback.accept(c.getSource(), line))))
				.then(TranslatorCommands.<S>literal("reload").executes(c -> {
					TranslationService s = TranslatorMod.service();
					s.setConfig(TranslatorConfig.load());  // the settings file too, after a hand edit
					s.reload();
					feedback.accept(c.getSource(), Component.translatable("caketext_translator.command.reloaded",
							s.dictionary().humanCount(), s.dictionary().files()));
					return 1;
				}))
				.then(TranslatorCommands.<S>literal("test").then(RequiredArgumentBuilder.<S, String>argument("text", StringArgumentType.greedyString())
						.executes(c -> test(line -> feedback.accept(c.getSource(), line), StringArgumentType.getString(c, "text")))))
				.then(TranslatorCommands.<S>literal("cache").then(TranslatorCommands.<S>literal("clear").executes(c -> {
					TranslationService s = TranslatorMod.service();
					s.machine().clearCache();
					feedback.accept(c.getSource(), Component.translatable("caketext_translator.command.cache_cleared", s.engine().label()));
					return 1;
				})))
				.then(TranslatorCommands.<S>literal("exchange").executes(c -> {
					feedback.accept(c.getSource(), ExchangeFolder.run());
					return 1;
				}))
				.then(TranslatorCommands.<S>literal("model")
						.then(TranslatorCommands.<S>literal("folder").executes(c -> {
							ModelManager.openImportDir();
							feedback.accept(c.getSource(), Component.translatable("caketext_translator.command.model_folder",
									ModelManager.FILE_NAMES, Component.literal(ModelManager.PAGE_URL).withStyle(s -> s
											.withColor(ChatFormatting.AQUA).withUnderlined(true)
											.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ModelManager.PAGE_URL)))));
							return 1;
						}))
						.then(TranslatorCommands.<S>literal("install").executes(c -> {
							TranslatorMod.service().models().install();
							feedback.accept(c.getSource(), Component.translatable("caketext_translator.command.model_install"));
							return 1;
						}))
						.then(TranslatorCommands.<S>literal("delete").executes(c -> {
							TranslatorMod.service().models().delete();
							feedback.accept(c.getSource(), Component.translatable("caketext_translator.command.model_deleted"));
							return 1;
						}))));
	}

	private static int settings() {
		Minecraft client = Minecraft.getInstance();
		// opened next tick: the chat screen closes after this command returns
		client.tell(() -> client.setScreen(new TranslatorScreen(null)));
		return 1;
	}

	private static int status(Consumer<Component> source) {
		TranslationService s = TranslatorMod.service();
		Dictionary d = s.dictionary();
		source.accept(Component.translatable("caketext_translator.title").withStyle(ChatFormatting.GOLD));
		source.accept(Component.translatable("caketext_translator.status.dictionary", d.humanCount(), d.files(), d.machineCount()));
		source.accept(s.engine().describe());
		source.accept(Component.translatable("caketext_translator.status.queue", s.machine().pending()));
		GameContext ctx = GameContext.of(Surfaces.CHAT, null);
		source.accept(Component.translatable("caketext_translator.status.context", String.valueOf(ctx.server()),
				String.valueOf(ctx.dimension()), String.valueOf(ctx.shard()), String.valueOf(ctx.area())).withStyle(ChatFormatting.GRAY));
		source.accept(Component.translatable("caketext_translator.status.folder")
				.withStyle(st -> st.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
						CakeText.translations(s.config().language).toAbsolutePath().toString()))));
		return 1;
	}

	/** Translates {@code text} as if it were a chat line; a model translation is reported when it's done. */
	private static int test(Consumer<Component> source, String text) {
		TranslationService s = TranslatorMod.service();
		Component line = Component.literal(text);
		TranslationService.Result r = s.translate(line, Surfaces.CHAT, null);
		if (r != null) {
			source.accept(shown(r));
			return 1;
		}
		if (!s.engine().ready() || !s.config().machine) {
			source.accept(Component.translatable("caketext_translator.command.test_none").withStyle(ChatFormatting.GRAY));
			return 1;
		}
		String template = TextTemplate.of(line).template();
		source.accept(Component.translatable("caketext_translator.command.test_queued").withStyle(ChatFormatting.GRAY));
		Thread waiter = new Thread(() -> {
			for (int i = 0; i < 600 && !s.dictionary().hasMachine(template); i++) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					return;
				}
			}
			Minecraft.getInstance().execute(() -> {
				TranslationService.Result done = s.translate(line, Surfaces.CHAT, null);
				source.accept(done != null ? shown(done)
						: Component.translatable("caketext_translator.command.test_failed").withStyle(ChatFormatting.RED));
			});
		}, "CakeText-test");
		waiter.setDaemon(true);
		waiter.start();
		return 1;
	}

	/** Icon + translation, hover shows the original; marked so the chat hook leaves it alone. */
	private static Component shown(TranslationService.Result r) {
		Component card = Markers.card(r.hit(), r.original(), true);
		return Translated.mark(Component.empty().append(Markers.hoverIcon(r.hit(), card)).append(" ")
				.append(TextUtil.withHover(r.text(), card)));
	}
}
