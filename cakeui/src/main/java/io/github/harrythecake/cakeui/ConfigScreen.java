package io.github.harrythecake.cakeui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * CakeUI's settings (Mod Menu): which layout, dev mode, button ids, reload, the layouts folder.
 * Every change is saved and applied at once, so the result shows straight away.
 */
public final class ConfigScreen extends Screen {
	private final Screen parent;

	public ConfigScreen(Screen parent) {
		super(Text.translatable("cakeui.config.title"));
		this.parent = parent;
	}

	private static Path layoutsFolder() {
		return FabricLoader.getInstance().getGameDir().resolve("cakeui").resolve("layouts");
	}

	/** "" (no layout), every folder in cakeui/layouts/, and the configured path if it is elsewhere. */
	private static List<String> layoutChoices(String current) {
		List<String> choices = new ArrayList<>();
		choices.add("");
		try (Stream<Path> dirs = Files.list(layoutsFolder())) {
			for (Path p : dirs.sorted().toList()) {
				if (Files.isDirectory(p)) {
					choices.add("cakeui/layouts/" + p.getFileName());
				}
			}
		} catch (IOException e) {
			// no layouts folder yet
		}
		if (!choices.contains(current)) {
			choices.add(current);
		}
		return choices;
	}

	private static Text layoutName(String path) {
		if (path.isEmpty()) {
			return Text.translatable("cakeui.config.layout.none");
		}
		int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return Text.literal(path.substring(slash + 1));
	}

	@Override
	protected void init() {
		Config config = Ui.config();
		int x = width / 2 - 155;
		addDrawableChild(CyclingButtonWidget.<String>builder(ConfigScreen::layoutName)
				.values(layoutChoices(config.layoutPath))
				.initially(config.layoutPath)
				.build(x, 40, 310, 20, Text.translatable("cakeui.config.layout"), (b, value) -> apply(c -> c.layoutPath = value)));
		addDrawableChild(CyclingButtonWidget.onOffBuilder(config.devMode)
				.tooltip(v -> Tooltip.of(Text.translatable("cakeui.config.devMode.tooltip")))
				.build(x, 64, 150, 20, Text.translatable("cakeui.config.devMode"), (b, value) -> apply(c -> c.devMode = value)));
		CyclingButtonWidget<Boolean> ids = addDrawableChild(CyclingButtonWidget.onOffBuilder(config.showButtonIds)
				.tooltip(v -> Tooltip.of(Text.translatable("cakeui.config.buttonIds.tooltip")))
				.build(x + 160, 64, 150, 20, Text.translatable("cakeui.config.buttonIds"), (b, value) -> apply(c -> c.showButtonIds = value)));
		ids.active = config.devMode;
		addDrawableChild(ButtonWidget.builder(Text.translatable("cakeui.config.reload"), b -> Ui.reload())
				.dimensions(x, 88, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("cakeui.config.openFolder"), b -> openFolder())
				.dimensions(x + 160, 88, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 - 100, height - 27, 200, 20).build());
	}

	/** Changes a setting, saves the file and loads the layout again (which also lays this screen out again). */
	private void apply(java.util.function.Consumer<Config> change) {
		Config config = Ui.config();
		change.accept(config);
		config.save();
		Ui.reload();
	}

	private static void openFolder() {
		Path folder = layoutsFolder();
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			CakeUI.LOGGER.warn("CakeUI: could not create {}", folder, e);
		}
		Util.getOperatingSystem().open(folder.toFile());
	}

	/** What the active layout is doing, under the buttons. */
	private Text status() {
		String path = Ui.config().layoutPath;
		if (path.isEmpty()) {
			return Text.translatable("cakeui.config.status.none");
		}
		int problems = Ui.problems().size();
		if (Ui.keptPrevious()) {
			return Text.translatable("cakeui.config.status.kept", Ui.problemsLayoutName(), problems);
		}
		if (Ui.renderer() == null) {
			return Text.translatable("cakeui.config.status.failed", layoutName(path), problems);
		}
		return Text.translatable("cakeui.config.status.loaded", Ui.renderer().layout.name, problems);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, status(), width / 2, 120, 0xFFFFFF);
		if (!Ui.problems().isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("cakeui.config.status.hint"), width / 2, 134, 0xA0A0A0);
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
