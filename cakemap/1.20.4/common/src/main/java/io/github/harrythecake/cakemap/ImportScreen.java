package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.platform.Services;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

/**
 * Import from Xaero's Minimap and World Map: this game folder's {@code xaero/}, or one picked from
 * another install. Choose what comes over, then watch the progress. Xaero's files are only read.
 */
public final class ImportScreen extends Screen {
	private final Screen parent;
	private final XaeroImport.Options options = new XaeroImport.Options();
	private Path xaero;
	private int[] counts;
	private XaeroImport.Progress progress;
	private Button start;
	private volatile boolean picking;

	public ImportScreen(@Nullable Screen parent) {
		super(Component.translatable("cakemap.import.title"));
		this.parent = parent;
		choose(XaeroImport.xaeroDir(Services.PLATFORM.gameDir()));
	}

	private void choose(Path dir) {
		xaero = dir;
		counts = dir == null ? null : XaeroImport.count(dir);
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int y = height / 2 - 40;
		addRenderableWidget(Button.builder(Component.translatable("cakemap.import.pick"), b -> pick()).bounds(cx - 100, y - 24, 200, 20).build());
		addRenderableWidget(toggle("settings", () -> options.settings, v -> options.settings = v, cx - 152, y + 10));
		addRenderableWidget(toggle("keys", () -> options.keys, v -> options.keys = v, cx + 2, y + 10));
		addRenderableWidget(toggle("waypoints", () -> options.waypoints, v -> options.waypoints = v, cx - 152, y + 34));
		addRenderableWidget(toggle("maps", () -> options.maps, v -> options.maps = v, cx + 2, y + 34));
		addRenderableWidget(Button.builder(showOnLabel(), b -> {
			options.waypointMaps = WaypointEditScreen.nextMaps(options.waypointMaps);
			b.setMessage(showOnLabel());
		}).bounds(cx - 152, y + 58, 304, 20).build());
		start = addRenderableWidget(Button.builder(Component.translatable("cakemap.import.start"), b -> {
			if (xaero != null && progress == null) {
				progress = new XaeroImport.Progress();
				XaeroImportRun.start(xaero, options, progress);
			}
		}).bounds(cx - 152, height - 30, 150, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 2, height - 30, 150, 20).build());
	}

	private Button toggle(String key, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set, int x, int y) {
		return Button.builder(label(key, get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			b.setMessage(label(key, get.getAsBoolean()));
		}).bounds(x, y, 150, 20).build();
	}

	private Component showOnLabel() {
		return Component.translatable("cakemap.import.show_on", Component.translatable("cakemap.waypoint.maps." + options.waypointMaps));
	}

	private static Component label(String key, boolean on) {
		return Component.translatable("cakemap.import." + key).append(": ").append(Component.translatable(on ? "options.on" : "options.off"));
	}

	private void pick() {
		if (picking) {
			return;
		}
		picking = true;
		Thread t = new Thread(() -> {
			String chosen = TinyFileDialogs.tinyfd_selectFolderDialog(Component.translatable("cakemap.import.pick").getString(),
					Services.PLATFORM.gameDir().toString());
			picking = false;
			if (chosen != null) {
				Path dir = XaeroImport.xaeroDir(Path.of(chosen));
				CakeMap.onRender(() -> choose(dir));
			}
		}, "CakeMap folder picker");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		start.active = xaero != null && progress == null;
		super.render(ctx, mouseX, mouseY, delta);
		int cx = width / 2;
		int y = height / 2 - 40;
		ctx.drawCenteredString(font, title, cx, 14, 0xFFFFFFFF);
		Component where = xaero == null ? Component.translatable("cakemap.import.none") : Component.literal(xaero.toString());
		ctx.drawCenteredString(font, where, cx, y - 46, xaero == null ? 0xFFFF9090 : 0xFFC8C8C8);
		if (counts != null) {
			ctx.drawCenteredString(font, Component.translatable("cakemap.import.counts", counts[0], counts[1]), cx, y - 36, 0xFFA0A0A0);
		}
		if (progress != null) {
			int total = Math.max(1, progress.total);
			int done = progress.done.get();
			int barW = 300;
			int bx = cx - barW / 2;
			int by = y + 90;
			ctx.fill(bx, by, bx + barW, by + 8, 0xFF202020);
			ctx.fill(bx, by, bx + (int) ((long) barW * done / total), by + 8, 0xFF5CB85C);
			Component status = progress.finished
					? (progress.error != null ? Component.translatable("cakemap.import.failed", progress.error)
							: Component.translatable("cakemap.import.done", progress.waypoints, progress.regions, progress.settings.size(), progress.keys))
					: Component.translatable("cakemap.import.running", done, progress.total);
			ctx.drawCenteredString(font, status, cx, by + 14, progress.finished && progress.error == null ? 0xFF8FE08F : 0xFFFFFFFF);
			if (progress.failed > 0) {
				ctx.drawCenteredString(font, Component.translatable("cakemap.import.skipped", progress.failed), cx, by + 26, 0xFFFFC070);
			}
		} else {
			ctx.drawCenteredString(font, Component.translatable("cakemap.import.note"), cx, y + 90, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
