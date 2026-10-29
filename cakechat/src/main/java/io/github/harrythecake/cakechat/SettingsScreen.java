package io.github.harrythecake.cakechat;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
		YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
				.title(Text.translatable("cakechat.config.title"));
		for (int w = 0; w < c.windows.size(); w++) {
			builder.category(window(parent, c, w));
		}
		return builder
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
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("cakechat.config.chatplus"))
						.option(confirmedButton(parent, "import", Text.translatable("cakechat.config.import.confirm"),
								CakeChat::importChatPlus, CakeChat.chatPlusSetup().isPresent()))
						.build())
				.save(CakeChat::saveConfig)
				.build()
				.generateScreen(parent);
	}

	/** One window: its tabs (a group each) and buttons that add or remove tabs and windows. */
	private static ConfigCategory window(Screen parent, ChatConfig c, int w) {
		ChatConfig.Window window = c.windows.get(w);
		ConfigCategory.Builder category = ConfigCategory.createBuilder()
				.name(Text.translatable(w == 0 ? "cakechat.config.window.main" : "cakechat.config.window", w + 1))
				.tooltip(Text.translatable("cakechat.config.window.desc"));
		ChatConfig.Tab defaults = new ChatConfig.Tab();
		for (int t = 0; t < window.tabs.size(); t++) {
			ChatConfig.Tab tab = window.tabs.get(t);
			int tabIndex = t;
			OptionGroup.Builder group = OptionGroup.createBuilder()
					.name(Text.translatable("cakechat.config.tab", CakeChat.tabName(tab)))
					.collapsed(window.tabs.size() > 3)
					.option(Option.<String>createBuilder()
							.name(Text.translatable("cakechat.config.tab.name"))
							.description(desc("tab.name"))
							// the default tabs keep their translation key until renamed
							.binding(CakeChat.tabName(defaults), () -> CakeChat.tabName(tab),
									v -> tab.name = v.isBlank() || v.equals(CakeChat.tabName(tab)) ? tab.name : v)
							.controller(StringControllerBuilder::create)
							.build())
					.option(Option.<String>createBuilder()
							.name(Text.translatable("cakechat.config.tab.pattern"))
							.description(patternDesc(tab.pattern))
							// an invalid regex is refused, so a tab never silently shows nothing
							.binding(defaults.pattern, () -> tab.pattern, v -> tab.pattern = TabRules.patternError(v) == null ? v : tab.pattern)
							.controller(StringControllerBuilder::create)
							.build())
					.option(Option.<Integer>createBuilder()
							.name(Text.translatable("cakechat.config.tab.priority"))
							.description(desc("tab.priority"))
							.binding(defaults.priority, () -> tab.priority, v -> tab.priority = v)
							.controller(o -> IntegerFieldControllerBuilder.create(o).range(-1000, 1000))
							.build())
					.option(bool("tab.skipOthers", defaults.skipOthers, () -> tab.skipOthers, v -> tab.skipOthers = v))
					.option(bool("tab.alwaysAdd", defaults.alwaysAdd, () -> tab.alwaysAdd, v -> tab.alwaysAdd = v))
					.option(Option.<String>createBuilder()
							.name(Text.translatable("cakechat.config.tab.prefix"))
							.description(desc("tab.prefix"))
							.binding(defaults.prefix, () -> tab.prefix, v -> tab.prefix = v)
							.controller(StringControllerBuilder::create)
							.build())
					.option(Option.<String>createBuilder()
							.name(Text.translatable("cakechat.config.tab.channel"))
							.description(desc("tab.channel"))
							.binding(defaults.channel, () -> tab.channel, v -> tab.channel = v.strip())
							.controller(StringControllerBuilder::create)
							.build())
					.option(Option.<String>createBuilder()
							.name(Text.translatable("cakechat.config.tab.server"))
							.description(desc("tab.server"))
							.binding(defaults.server, () -> tab.server, v -> tab.server = TabRules.patternError(v) == null ? v : tab.server)
							.controller(StringControllerBuilder::create)
							.build());
			if (window.tabs.size() > 1) {
				group.option(confirmedButton(parent, "tab.remove", Text.translatable("cakechat.config.tab.remove.confirm", CakeChat.tabName(tab)),
						() -> window.tabs.remove(tabIndex), true));
			}
			category.group(group.build());
		}
		category.option(button(parent, "tab.add", () -> window.tabs.add(ChatConfig.Tab.named(
				Text.translatable("cakechat.config.tab.newName").getString(), ""))));
		category.option(button(parent, "window.resetSize", () -> {
			if (w == 0) {
				window.width = window.height = window.unfocusedHeight = -1;
			} else {
				ChatConfig.Window fresh = ChatConfig.Window.extra(w);
				window.width = fresh.width;
				window.height = fresh.height;
				window.unfocusedHeight = fresh.unfocusedHeight;
			}
		}));
		category.option(button(parent, "window.add", () -> c.windows.add(ChatConfig.Window.extra(c.windows.size()))));
		if (w > 0) {
			category.option(confirmedButton(parent, "window.remove", Text.translatable("cakechat.config.window.remove.confirm", w + 1),
					() -> c.windows.remove(w), true));
		}
		return category.build();
	}

	/**
	 * A button that changes the windows or tabs: unsaved edits are saved first, then the change is saved and the
	 * screen rebuilt (YACL can't add or remove options on an open screen).
	 */
	private static ButtonOption button(Screen parent, String key, Runnable change) {
		return ButtonOption.createBuilder()
				.name(Text.translatable("cakechat.config." + key))
				.description(desc(key))
				.action((screen, option) -> {
					if (screen.pendingChanges()) {
						screen.finishOrSave();
					}
					change.run();
					CakeChat.saveConfig();
					MinecraftClient.getInstance().setScreen(create(parent));
				})
				.build();
	}

	/**
	 * Like {@link #button}, for removing or replacing things: Minecraft's confirm screen first. Cancel goes back to the
	 * same settings screen as it was; confirming makes the change and rebuilds it.
	 */
	private static ButtonOption confirmedButton(Screen parent, String key, Text question, Runnable change, boolean available) {
		return ButtonOption.createBuilder()
				.name(Text.translatable("cakechat.config." + key).formatted(available ? Formatting.RED : Formatting.GRAY))
				.description(desc(key))
				.available(available)
				.action((screen, option) -> {
					MinecraftClient client = MinecraftClient.getInstance();
					client.setScreen(new ConfirmScreen(confirmed -> {
						if (!confirmed) {
							client.setScreen(screen);
							return;
						}
						if (screen.pendingChanges()) {
							screen.finishOrSave();
						}
						change.run();
						CakeChat.saveConfig();
						client.setScreen(create(parent));
					}, question, Text.translatable("cakechat.config." + key + ".confirm.message"),
							Text.translatable("cakechat.config." + key).formatted(Formatting.RED), ScreenTexts.CANCEL));
				})
				.build();
	}

	/** The pattern's tooltip, with the regex error first when the saved pattern is somehow invalid. */
	private static OptionDescription patternDesc(String pattern) {
		String error = TabRules.patternError(pattern);
		Text text = Text.translatable("cakechat.config.tab.pattern.desc");
		if (error != null) {
			text = Text.empty().append(Text.translatable("cakechat.config.tab.pattern.invalid", error).formatted(Formatting.RED))
					.append("\n\n").append(text);
		}
		return OptionDescription.of(text);
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
