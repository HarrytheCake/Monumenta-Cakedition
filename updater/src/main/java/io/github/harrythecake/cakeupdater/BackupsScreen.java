package io.github.harrythecake.cakeupdater;

import java.nio.file.Files;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/** The backups list (Mod Menu's configure button, or /cakeupdate backups): restore the server list or configs. */
final class BackupsScreen extends Screen {
	private static final int ROW = 24;
	private static final int TOP = 52;

	private final @Nullable Screen parent;
	private List<Backups.Backup> shown = List.of();
	private Text status = Text.empty();

	BackupsScreen(@Nullable Screen parent) {
		super(Text.translatable("cakeupdater.backups.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Backups backups = CakeUpdater.backups();
		List<Backups.Backup> all = backups.list();
		int rows = Math.max(1, (height - TOP - 40) / ROW);
		shown = all.subList(0, Math.min(rows, all.size()));
		String pending = backups.pendingConfigRestore();
		if (pending != null && status.getString().isEmpty()) {
			status = Text.translatable("cakeupdater.restore.configs.scheduled", date(pending)).formatted(Formatting.YELLOW);
		}

		int y = TOP;
		for (Backups.Backup b : shown) {
			ButtonWidget servers = ButtonWidget.builder(Text.translatable("cakeupdater.backups.servers"), button -> {
				try {
					backups.restoreServers(b.name(), CakeUpdater.keep());
					status = Text.translatable("cakeupdater.restore.servers.done", date(b.name())).formatted(Formatting.GREEN);
				} catch (Exception e) {
					CakeUpdater.LOGGER.error("Server list restore failed", e);
					status = Text.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage())).formatted(Formatting.RED);
				}
				clearAndInit();
			}).dimensions(width / 2 + 50, y, 90, 20).tooltip(Tooltip.of(Text.translatable("cakeupdater.backups.servers.tip"))).build();
			servers.active = b.hasServers();
			addDrawableChild(servers);

			ButtonWidget configs = ButtonWidget.builder(Text.translatable("cakeupdater.backups.configs"), button -> {
				try {
					CakeUpdater.scheduleConfigRestore(b.name());
					status = Text.translatable("cakeupdater.restore.configs.scheduled", date(b.name())).formatted(Formatting.YELLOW);
				} catch (Exception e) {
					CakeUpdater.LOGGER.error("Config restore failed", e);
					status = Text.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage())).formatted(Formatting.RED);
				}
				clearAndInit();
			}).dimensions(width / 2 + 144, y, 90, 20).tooltip(Tooltip.of(Text.translatable("cakeupdater.backups.configs.tip"))).build();
			configs.active = b.hasConfigs();
			addDrawableChild(configs);
			y += ROW;
		}

		int bottom = height - 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable("cakeupdater.backups.folder"), button -> {
			try {
				Files.createDirectories(backups.root());
			} catch (Exception ignored) {
				// opening still shows the game folder
			}
			Util.getOperatingSystem().open(backups.root().toFile());
		}).dimensions(width / 2 - 154, bottom, 100, 20).build());
		ButtonWidget cancel = ButtonWidget.builder(Text.translatable("cakeupdater.backups.cancel"), button -> {
			try {
				backups.cancelConfigRestore();
				status = Text.translatable("cakeupdater.restore.cancelled").formatted(Formatting.GRAY);
			} catch (Exception e) {
				CakeUpdater.LOGGER.warn("Could not cancel the config restore: {}", e.toString());
			}
			clearAndInit();
		}).dimensions(width / 2 - 50, bottom, 100, 20).build();
		cancel.active = pending != null;
		addDrawableChild(cancel);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(width / 2 + 54, bottom, 100, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 32, 0xFFFFFF);
		if (shown.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("cakeupdater.backups.none"), width / 2, TOP + 6, 0xAAAAAA);
		}
		int y = TOP;
		for (Backups.Backup b : shown) {
			Text label = "update".equals(b.kind())
					? Text.translatable("cakeupdater.backups.update", date(b.name()), b.from(), b.to())
					: Text.translatable("cakeupdater.backups.restore", date(b.name()));
			context.drawTextWithShadow(textRenderer, label, width / 2 - 234, y + 6, 0xFFFFFF);
			y += ROW;
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	/** "2026-10-27_14-03-11" → "2026-10-27 14:03". */
	static String date(String name) {
		if (name.length() < 16 || name.charAt(10) != '_') {
			return name;
		}
		return name.substring(0, 10) + " " + name.substring(11, 13) + ":" + name.substring(14, 16);
	}
}
