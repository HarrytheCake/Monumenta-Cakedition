package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.MapSettings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimap and map settings (Y, or Mod Menu). Every click saves at once. */
public final class SettingsScreen extends Screen {
	private static final int[] SIZES = {64, 80, 96, 110, 128, 160, 192, 224, 256};
	private static final double[] OPACITIES = {1, 0.9, 0.75, 0.6, 0.4};

	private final Screen parent;
	private final List<Runnable> refresh = new ArrayList<>();

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("cakemap.settings.title"));
		this.parent = parent;
	}

	private record Option(String key, Supplier<Component> value, Runnable click) {
	}

	private static Component onOff(boolean b) {
		return Component.translatable(b ? "options.on" : "options.off");
	}

	private static String low(Enum<?> e) {
		return e.name().toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> E next(E e) {
		E[] all = e.getDeclaringClass().getEnumConstants();
		return all[(e.ordinal() + 1) % all.length];
	}

	@Override
	protected void init() {
		refresh.clear();
		MapSettings s = CakeMap.settings();
		List<Option> opts = List.of(
				new Option("minimap", () -> onOff(s.minimap), () -> s.minimap = !s.minimap),
				new Option("shape", () -> Component.translatable("cakemap.settings.shape." + low(s.shape)), () -> s.shape = next(s.shape)),
				new Option("frame", () -> Component.translatable("cakemap.settings.frame." + low(s.frame)), () -> s.frame = next(s.frame)),
				new Option("size", () -> Component.literal(String.valueOf(s.size)), () -> s.size = nextInt(SIZES, s.size)),
				new Option("zoom", () -> Component.literal(trim(s.zoom) + "x"), () -> s.zoom = nextZoom(s.zoom)),
				new Option("rotate", () -> Component.translatable(s.rotate ? "cakemap.settings.rotate.on" : "cakemap.settings.rotate.off"), () -> s.rotate = !s.rotate),
				CakeMapPlugin.hudEditor
						// CakeUI 3+: place (and size) the minimap in CakeUI's HUD editor
						? new Option("move", () -> Component.translatable("cakemap.settings.move.value"), () -> io.github.harrythecake.cakeui.api.CakeUIApi.get().openHudEditor(this))
						: new Option("corner", () -> Component.translatable("cakemap.settings.corner." + low(s.corner)), () -> s.corner = next(s.corner)),
				new Option("coordinates", () -> onOff(s.coordinates), () -> s.coordinates = !s.coordinates),
				new Option("opacity", () -> Component.literal(Math.round(s.opacity * 100) + "%"), () -> s.opacity = nextDouble(OPACITIES, s.opacity)),
				new Option("cave", () -> Component.translatable("cakemap.settings.cave." + low(s.cave)), () -> s.cave = next(s.cave)),
				new Option("mode", () -> Component.translatable("cakemap.settings.mode." + low(s.mode)), () -> s.mode = next(s.mode)),
				new Option("marker", () -> Component.translatable("cakemap.settings.marker." + low(s.marker)), () -> s.marker = next(s.marker)),
				new Option("waypointsOnMinimap", () -> onOff(s.waypointsOnMinimap), () -> s.waypointsOnMinimap = !s.waypointsOnMinimap),
				new Option("waypointsInWorld", () -> onOff(s.waypointsInWorld), () -> s.waypointsInWorld = !s.waypointsInWorld),
				new Option("builtinInWorld", () -> Component.translatable(s.builtinInWorldOnlyBookmarked ? "cakemap.settings.builtinInWorld.bookmarked"
						: "cakemap.settings.builtinInWorld.all"), () -> s.builtinInWorldOnlyBookmarked = !s.builtinInWorldOnlyBookmarked),
				new Option("questCompass", () -> onOff(s.questCompass), () -> s.questCompass = !s.questCompass),
				new Option("deathWaypoints", () -> onOff(s.deathWaypoints), () -> s.deathWaypoints = !s.deathWaypoints),
				new Option("chatCoordinates", () -> onOff(s.chatCoordinates), () -> s.chatCoordinates = !s.chatCoordinates));
		int colW = 155;
		int top = 32;
		int rowH = 22;
		int rows = (opts.size() + 1) / 2;
		if (top + rows * rowH + 56 > height) {
			rowH = Math.max(20, (height - top - 56) / rows);
		}
		for (int i = 0; i < opts.size(); i++) {
			Option o = opts.get(i);
			int x = width / 2 - colW - 2 + (i % 2) * (colW + 4);
			int y = top + (i / 2) * rowH;
			Button b = Button.builder(label(o), btn -> {
				o.click.run();
				CakeMap.saveSettings();
				btn.setMessage(label(o));
			}).bounds(x, y, colW, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cakemap.settings." + o.key + ".tip"))).build();
			addRenderableWidget(b);
		}
		int by = height - 26;
		addRenderableWidget(Button.builder(Component.translatable("cakemap.import.button"), b -> minecraft.setScreen(new ImportScreen(this)))
				.bounds(width / 2 - 155, by, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cakemap.waypoints.title"), b -> minecraft.setScreen(new WaypointListScreen(this)))
				.bounds(width / 2 - 50, by, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 + 55, by, 100, 20).build());
	}

	private static Component label(Option o) {
		return Component.translatable("cakemap.settings." + o.key).append(": ").append(o.value.get());
	}

	private static int nextInt(int[] steps, int v) {
		for (int s : steps) {
			if (s > v) {
				return s;
			}
		}
		return steps[0];
	}

	private static double nextDouble(double[] steps, double v) {
		for (int i = 0; i < steps.length; i++) {
			if (Math.abs(steps[i] - v) < 1e-6) {
				return steps[(i + 1) % steps.length];
			}
		}
		return steps[0];
	}

	private static double nextZoom(double z) {
		double n = MapSettings.nextZoom(z, true);
		return Math.abs(n - z) < 1e-6 ? MapSettings.ZOOMS[0] : n;
	}

	private static String trim(double d) {
		return d == Math.rint(d) ? String.valueOf((int) d) : String.valueOf(d);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
