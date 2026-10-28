package io.github.harrythecake.cakechat;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The YACL settings screen (Mod Menu or {@code /cakechat}). Every label is {@code cakechat.config.<key>}, every tooltip {@code <key>.desc}. */
final class SettingsScreen {
	private SettingsScreen() {
	}

	static Screen create(Screen parent) {
		ChatConfig c = CakeChat.config();
		ChatConfig d = new ChatConfig();
		return YetAnotherConfigLib.createBuilder()
				.title(Text.translatable("cakechat.config.title"))
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("cakechat.config.history"))
						.option(Option.<Integer>createBuilder()
								.name(Text.translatable("cakechat.config.history.maxMessages"))
								.description(desc("history.maxMessages"))
								.binding(d.history.maxMessages, () -> c.history.maxMessages, v -> c.history.maxMessages = v)
								.controller(o -> IntegerFieldControllerBuilder.create(o).min(0))
								.build())
						.option(bool("history.keepOnDisconnect", d.history.keepOnDisconnect,
								() -> c.history.keepOnDisconnect, v -> c.history.keepOnDisconnect = v))
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("cakechat.config.compact"))
						.option(bool("compact.enabled", d.compact.enabled, () -> c.compact.enabled, v -> c.compact.enabled = v))
						.option(Option.<String>createBuilder()
								.name(Text.translatable("cakechat.config.compact.format"))
								.description(desc("compact.format"))
								.binding(d.compact.format, () -> c.compact.format, v -> c.compact.format = v)
								.controller(StringControllerBuilder::create)
								.build())
						.option(Option.<ChatConfig.Compact.Mode>createBuilder()
								.name(Text.translatable("cakechat.config.compact.mode"))
								.description(desc("compact.mode"))
								.binding(d.compact.mode, () -> c.compact.mode, v -> c.compact.mode = v)
								.controller(o -> EnumControllerBuilder.create(o).enumClass(ChatConfig.Compact.Mode.class)
										.formatValue(v -> Text.translatable("cakechat.config.compact.mode." + v.name().toLowerCase(Locale.ROOT))))
								.build())
						.option(Option.<Integer>createBuilder()
								.name(Text.translatable("cakechat.config.compact.searchLines"))
								.description(desc("compact.searchLines"))
								.binding(d.compact.searchLines, () -> c.compact.searchLines, v -> c.compact.searchLines = v)
								.controller(o -> IntegerFieldControllerBuilder.create(o).range(1, 100))
								.build())
						.option(Option.<Integer>createBuilder()
								.name(Text.translatable("cakechat.config.compact.seconds"))
								.description(desc("compact.seconds"))
								.binding(d.compact.seconds, () -> c.compact.seconds, v -> c.compact.seconds = v)
								.controller(o -> IntegerFieldControllerBuilder.create(o).range(1, 3600))
								.build())
						.option(bool("compact.compareStyle", d.compact.compareStyle,
								() -> c.compact.compareStyle, v -> c.compact.compareStyle = v))
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("cakechat.config.timestamps"))
						.option(bool("timestamps.enabled", d.timestamps.enabled, () -> c.timestamps.enabled, v -> c.timestamps.enabled = v))
						.option(Option.<String>createBuilder()
								.name(Text.translatable("cakechat.config.timestamps.pattern"))
								.description(desc("timestamps.pattern"))
								// an invalid pattern is refused, so the chat never fails to draw a timestamp
								.binding(d.timestamps.pattern, () -> c.timestamps.pattern,
										v -> c.timestamps.pattern = ChatConfig.Timestamps.validPattern(v) ? v : c.timestamps.pattern)
								.controller(StringControllerBuilder::create)
								.build())
						.option(Option.<ChatConfig.Timestamps.Placement>createBuilder()
								.name(Text.translatable("cakechat.config.timestamps.placement"))
								.description(desc("timestamps.placement"))
								.binding(d.timestamps.placement, () -> c.timestamps.placement, v -> c.timestamps.placement = v)
								.controller(o -> EnumControllerBuilder.create(o).enumClass(ChatConfig.Timestamps.Placement.class)
										.formatValue(v -> Text.translatable("cakechat.config.timestamps.placement." + v.name().toLowerCase(Locale.ROOT))))
								.build())
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("cakechat.config.scroll"))
						.option(Option.<Integer>createBuilder()
								.name(Text.translatable("cakechat.config.scroll.linesPerNotch"))
								.description(desc("scroll.linesPerNotch"))
								.binding(d.scroll.linesPerNotch, () -> c.scroll.linesPerNotch, v -> c.scroll.linesPerNotch = v)
								.controller(o -> IntegerFieldControllerBuilder.create(o).range(1, 50))
								.build())
						.option(bool("scroll.invert", d.scroll.invert, () -> c.scroll.invert, v -> c.scroll.invert = v))
						.option(bool("scroll.scrollbar", d.scroll.scrollbar, () -> c.scroll.scrollbar, v -> c.scroll.scrollbar = v))
						.build())
				.save(CakeChat::saveConfig)
				.build()
				.generateScreen(parent);
	}

	private static Option<Boolean> bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder()
				.name(Text.translatable("cakechat.config." + key))
				.description(desc(key))
				.binding(def, get, set)
				.controller(TickBoxControllerBuilder::create)
				.build();
	}

	private static OptionDescription desc(String key) {
		return OptionDescription.of(Text.translatable("cakechat.config." + key + ".desc"));
	}
}
