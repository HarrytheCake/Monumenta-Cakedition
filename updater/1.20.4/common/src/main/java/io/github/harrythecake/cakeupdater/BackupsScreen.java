package io.github.harrythecake.cakeupdater;

import java.nio.file.Files;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** The backups list (Mod Menu's configure button, or /cakeupdate backups): restore the server list or the other files. */
public final class BackupsScreen extends Screen {
	private static final int ROW = 24;
	private static final int TOP = 52;

	private final @Nullable Screen parent;
	private List<Backups.Backup> shown = List.of();
	private Component status = Component.empty();

	public BackupsScreen(@Nullable Screen parent) {
		super(Component.translatable("cakeupdater.backups.title"));
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
			status = Component.translatable("cakeupdater.restore.configs.scheduled", date(pending)).withStyle(ChatFormatting.YELLOW);
		}

		int y = TOP;
		for (Backups.Backup b : shown) {
			Button servers = Button.builder(Component.translatable("cakeupdater.backups.servers"), button -> {
				try {
					backups.restoreServers(b.name(), CakeUpdater.keep());
					status = Component.translatable("cakeupdater.restore.servers.done", date(b.name())).withStyle(ChatFormatting.GREEN);
				} catch (Exception e) {
					CakeUpdater.LOGGER.error("Server list restore failed", e);
					status = Component.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED);
				}
				rebuildWidgets();
			}).bounds(width / 2 + 50, y, 90, 20).tooltip(Tooltip.create(Component.translatable("cakeupdater.backups.servers.tip"))).build();
			servers.active = b.hasServers();
			addRenderableWidget(servers);

			Button configs = Button.builder(Component.translatable("cakeupdater.backups.configs"), button -> {
				try {
					CakeUpdater.scheduleConfigRestore(b.name());
					status = Component.translatable("cakeupdater.restore.configs.scheduled", date(b.name())).withStyle(ChatFormatting.YELLOW);
				} catch (Exception e) {
					CakeUpdater.LOGGER.error("Config restore failed", e);
					status = Component.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED);
				}
				rebuildWidgets();
			}).bounds(width / 2 + 144, y, 90, 20).tooltip(Tooltip.create(Component.translatable("cakeupdater.backups.configs.tip"))).build();
			configs.active = b.hasFiles();
			addRenderableWidget(configs);
			y += ROW;
		}

		int bottom = height - 28;
		addRenderableWidget(Button.builder(Component.translatable("cakeupdater.backups.folder"), button -> {
			try {
				Files.createDirectories(backups.root());
			} catch (Exception ignored) {
				// opening still shows the game folder
			}
			Util.getPlatform().openFile(backups.root().toFile());
		}).bounds(width / 2 - 154, bottom, 100, 20).build());
		Button cancel = Button.builder(Component.translatable("cakeupdater.backups.cancel"), button -> {
			try {
				backups.cancelConfigRestore();
				status = Component.translatable("cakeupdater.restore.cancelled").withStyle(ChatFormatting.GRAY);
			} catch (Exception e) {
				CakeUpdater.LOGGER.warn("Could not cancel the config restore: {}", e.toString());
			}
			rebuildWidgets();
		}).bounds(width / 2 - 50, bottom, 100, 20).build();
		cancel.active = pending != null;
		addRenderableWidget(cancel);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 + 54, bottom, 100, 20).build());
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
		context.drawCenteredString(font, status, width / 2, 32, 0xFFFFFF);
		if (shown.isEmpty()) {
			context.drawCenteredString(font, Component.translatable("cakeupdater.backups.none"), width / 2, TOP + 6, 0xAAAAAA);
		}
		int y = TOP;
		for (Backups.Backup b : shown) {
			Component label = switch (b.kind()) {
				case "update" -> Component.translatable("cakeupdater.backups.update", date(b.name()), b.from(), b.to());
				case "import" -> Component.translatable("cakeupdater.backups.import", date(b.name()), b.from());
				case "auto" -> Component.translatable("cakeupdater.backups.auto", date(b.name()));
				default -> Component.translatable("cakeupdater.backups.restore", date(b.name()));
			};
			context.drawString(font, label, width / 2 - 234, y + 6, 0xFFFFFF);
			y += ROW;
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	/** "2026-10-27_14-03-11" → "2026-10-27 14:03". */
	static String date(String name) {
		if (name.length() < 16 || name.charAt(10) != '_') {
			return name;
		}
		return name.substring(0, 10) + " " + name.substring(11, 13) + ":" + name.substring(14, 16);
	}
}
