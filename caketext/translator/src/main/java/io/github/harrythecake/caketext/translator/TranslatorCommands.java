package io.github.harrythecake.caketext.translator;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.GameContext;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.strings.ExchangeFolder;
import io.github.harrythecake.caketext.core.template.TextTemplate;
import io.github.harrythecake.caketext.core.template.TextUtil;
import io.github.harrythecake.caketext.core.template.Translated;
import io.github.harrythecake.caketext.translator.ui.TranslatorScreen;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * {@code /cttranslate} [settings | status | reload | cache clear | exchange | test <text> | model install | model delete]
 */
final class TranslatorCommands {
	private TranslatorCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("cttranslate")
				.executes(c -> settings())
				.then(literal("settings").executes(c -> settings()))
				.then(literal("status").executes(c -> status(c.getSource())))
				.then(literal("reload").executes(c -> {
					TranslationService s = TranslatorMod.service();
					s.reload();
					c.getSource().sendFeedback(Text.translatable("caketext_translator.command.reloaded",
							s.dictionary().humanCount(), s.dictionary().files()));
					return 1;
				}))
				.then(literal("test").then(argument("text", StringArgumentType.greedyString())
						.executes(c -> test(c.getSource(), StringArgumentType.getString(c, "text")))))
				.then(literal("cache").then(literal("clear").executes(c -> {
					TranslationService s = TranslatorMod.service();
					s.machine().clearCache();
					c.getSource().sendFeedback(Text.translatable("caketext_translator.command.cache_cleared", s.engine().label()));
					return 1;
				})))
				.then(literal("exchange").executes(c -> {
					c.getSource().sendFeedback(ExchangeFolder.run());
					return 1;
				}))
				.then(literal("model")
						.then(literal("install").executes(c -> {
							TranslatorMod.service().models().install();
							c.getSource().sendFeedback(Text.translatable("caketext_translator.command.model_install",
									ModelManager.DOWNLOAD_BYTES >> 20));
							return 1;
						}))
						.then(literal("delete").executes(c -> {
							TranslatorMod.service().models().delete();
							c.getSource().sendFeedback(Text.translatable("caketext_translator.command.model_deleted"));
							return 1;
						}))));
	}

	private static int settings() {
		MinecraftClient client = MinecraftClient.getInstance();
		// opened next tick: the chat screen closes after this command returns
		client.send(() -> client.setScreen(new TranslatorScreen(null)));
		return 1;
	}

	private static int status(FabricClientCommandSource source) {
		TranslationService s = TranslatorMod.service();
		Dictionary d = s.dictionary();
		source.sendFeedback(Text.translatable("caketext_translator.title").formatted(Formatting.GOLD));
		source.sendFeedback(Text.translatable("caketext_translator.status.dictionary", d.humanCount(), d.files(), d.machineCount()));
		source.sendFeedback(s.engine().describe());
		source.sendFeedback(Text.translatable("caketext_translator.status.queue", s.machine().pending()));
		GameContext ctx = GameContext.of(Surfaces.CHAT, null);
		source.sendFeedback(Text.translatable("caketext_translator.status.context", String.valueOf(ctx.server()),
				String.valueOf(ctx.dimension()), String.valueOf(ctx.shard()), String.valueOf(ctx.area())).formatted(Formatting.GRAY));
		source.sendFeedback(Text.translatable("caketext_translator.status.folder")
				.styled(st -> st.withUnderline(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
						CakeText.translations(s.config().language).toAbsolutePath().toString()))));
		return 1;
	}

	/** Translates {@code text} as if it were a chat line; a model translation is reported when it's done. */
	private static int test(FabricClientCommandSource source, String text) {
		TranslationService s = TranslatorMod.service();
		Text line = Text.literal(text);
		TranslationService.Result r = s.translate(line, Surfaces.CHAT, null);
		if (r != null) {
			source.sendFeedback(shown(r));
			return 1;
		}
		if (!s.engine().ready() || !s.config().machine) {
			source.sendFeedback(Text.translatable("caketext_translator.command.test_none").formatted(Formatting.GRAY));
			return 1;
		}
		String template = TextTemplate.of(line).template();
		source.sendFeedback(Text.translatable("caketext_translator.command.test_queued").formatted(Formatting.GRAY));
		Thread waiter = new Thread(() -> {
			for (int i = 0; i < 600 && !s.dictionary().hasMachine(template); i++) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					return;
				}
			}
			MinecraftClient.getInstance().execute(() -> {
				TranslationService.Result done = s.translate(line, Surfaces.CHAT, null);
				source.sendFeedback(done != null ? shown(done)
						: Text.translatable("caketext_translator.command.test_failed").formatted(Formatting.RED));
			});
		}, "CakeText-test");
		waiter.setDaemon(true);
		waiter.start();
		return 1;
	}

	/** Icon + translation, hover shows the original; marked so the chat hook leaves it alone. */
	private static Text shown(TranslationService.Result r) {
		Text card = Markers.card(r.hit(), r.original(), true);
		return Translated.mark(Text.empty().append(Markers.hoverIcon(r.hit(), card)).append(" ")
				.append(TextUtil.withHover(r.text(), card)));
	}
}
