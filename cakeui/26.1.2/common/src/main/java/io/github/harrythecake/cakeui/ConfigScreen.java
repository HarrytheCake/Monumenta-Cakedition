package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.platform.Services;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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
		super(Component.translatable("cakeui.config.title"));
		this.parent = parent;
	}

	private static Path layoutsFolder() {
		return Services.PLATFORM.gameDir().resolve("cakeui").resolve("layouts");
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

	private static Component layoutName(String path) {
		if (path.isEmpty()) {
			return Component.translatable("cakeui.config.layout.none");
		}
		int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return Component.literal(path.substring(slash + 1));
	}

	@Override
	protected void init() {
		Config config = Ui.config();
		int x = width / 2 - 155;
		addRenderableWidget(CycleButton.<String>builder(ConfigScreen::layoutName, config.layoutPath)
				.withValues(layoutChoices(config.layoutPath))
				.create(x, 40, 310, 20, Component.translatable("cakeui.config.layout"), (b, value) -> apply(c -> c.layoutPath = value)));
		addRenderableWidget(CycleButton.onOffBuilder(config.devMode)
				.withTooltip(v -> Tooltip.create(Component.translatable("cakeui.config.devMode.tooltip")))
				.create(x, 64, 150, 20, Component.translatable("cakeui.config.devMode"), (b, value) -> apply(c -> c.devMode = value)));
		CycleButton<Boolean> ids = addRenderableWidget(CycleButton.onOffBuilder(config.showButtonIds)
				.withTooltip(v -> Tooltip.create(Component.translatable("cakeui.config.buttonIds.tooltip")))
				.create(x + 160, 64, 150, 20, Component.translatable("cakeui.config.buttonIds"), (b, value) -> apply(c -> c.showButtonIds = value)));
		ids.active = config.devMode;
		addRenderableWidget(Button.builder(Component.translatable("cakeui.config.reload"), b -> Ui.reload())
				.bounds(x, 88, 150, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakeui.config.openFolder"), b -> openFolder())
				.bounds(x + 160, 88, 150, 20).build());
		Button editHud = addRenderableWidget(Button.builder(Component.translatable("cakeui.config.editHud"),
				b -> minecraft.setScreen(new HudEditorScreen(this))).bounds(x, 112, 310, 20).build());
		editHud.active = minecraft != null && minecraft.level != null;
		if (!editHud.active) {
			editHud.setTooltip(Tooltip.create(Component.translatable("cakeui.config.editHud.noWorld")));
		}
		// E6: the same editor on the other screens CakeUI redraws (pause menu and inventory need a world)
		boolean inWorld = minecraft != null && minecraft.level != null && minecraft.player != null;
		int w4 = (310 - 9) / 4;
		addRenderableWidget(Button.builder(Component.translatable("cakeui.config.editMainMenu"), b -> HudEditorScreen.openFor(new net.minecraft.client.gui.screens.TitleScreen()))
				.bounds(x, 136, w4, 20).build());
		Button pause = addRenderableWidget(Button.builder(Component.translatable("cakeui.config.editPause"), b -> HudEditorScreen.openFor(new net.minecraft.client.gui.screens.PauseScreen(true)))
				.bounds(x + w4 + 3, 136, w4, 20).build());
		Button inv = addRenderableWidget(Button.builder(Component.translatable("cakeui.config.editInventory"),
				b -> HudEditorScreen.openFor(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player))).bounds(x + 2 * (w4 + 3), 136, w4, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakeui.config.editLoading"), b -> HudEditorScreen.openFor(ScreenEditTargets.loadingPreview()))
				.bounds(x + 3 * (w4 + 3), 136, w4, 20).build());
		pause.active = inWorld;
		inv.active = inWorld;
		if (!inWorld) {
			pause.setTooltip(Tooltip.create(Component.translatable("cakeui.config.editHud.noWorld")));
			inv.setTooltip(Tooltip.create(Component.translatable("cakeui.config.editHud.noWorld")));
		}
		// the config hub (ADR 0011): other mods' UI/HUD options, written into their own files
		addRenderableWidget(Button.builder(Component.translatable("cakeui.config.otherMods"), b -> minecraft.setScreen(new ConfigHubScreen(this)))
				.bounds(x, 160, 310, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 27, 200, 20).build());
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
		Util.getPlatform().openFile(folder.toFile());
	}

	/** What the active layout is doing, under the buttons. */
	private Component status() {
		String path = Ui.config().layoutPath;
		if (path.isEmpty()) {
			return Component.translatable("cakeui.config.status.none");
		}
		int problems = Ui.problems().size();
		if (Ui.keptPrevious()) {
			return Component.translatable("cakeui.config.status.kept", Ui.problemsLayoutName(), problems);
		}
		if (Ui.renderer() == null) {
			return Component.translatable("cakeui.config.status.failed", layoutName(path), problems);
		}
		return Component.translatable("cakeui.config.status.loaded", Ui.renderer().layout.name, problems);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
		context.centeredText(font, status(), width / 2, 192, 0xFFFFFFFF);
		if (!Ui.problems().isEmpty()) {
			context.centeredText(font, Component.translatable("cakeui.config.status.hint"), width / 2, 206, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
