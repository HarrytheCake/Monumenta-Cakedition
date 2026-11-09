package io.github.harrythecake.cakemap;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointKind;
import io.github.harrythecake.cakemap.platform.Services;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The client-only {@code /cakemap} command (never sent to the server): temp, map, waypoints,
 * settings, import, export. Built for any command source; the loader registers it with its own
 * client-command event and says how feedback and errors reach the player.
 */
public final class CakeMapCommands {
	private CakeMapCommands() {
	}

	/** True for a command line (without the slash) that is ours. */
	public static boolean isOurs(String command) {
		return command.equals("cakemap") || command.startsWith("cakemap ");
	}

	public static <S> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> feedback, BiConsumer<S, Component> error) {
		return LiteralArgumentBuilder.<S>literal("cakemap")
				.then(LiteralArgumentBuilder.<S>literal("temp")
						.then(RequiredArgumentBuilder.<S, Integer>argument("x", IntegerArgumentType.integer())
								.then(RequiredArgumentBuilder.<S, Integer>argument("y", IntegerArgumentType.integer())
										.then(RequiredArgumentBuilder.<S, Integer>argument("z", IntegerArgumentType.integer())
												.executes(c -> temp(c, "", feedback))
												.then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.greedyString())
														.executes(c -> temp(c, StringArgumentType.getString(c, "name"), feedback)))))))
				.then(LiteralArgumentBuilder.<S>literal("map").executes(c -> openLater(new WorldMapScreen(null))))
				.then(LiteralArgumentBuilder.<S>literal("waypoints").executes(c -> openLater(new WaypointListScreen(null))))
				.then(LiteralArgumentBuilder.<S>literal("settings").executes(c -> openLater(new SettingsScreen(null))))
				.then(LiteralArgumentBuilder.<S>literal("import").executes(c -> openLater(new ImportScreen(null))))
				.then(LiteralArgumentBuilder.<S>literal("export").executes(c -> export(c, feedback, error))
						.then(RequiredArgumentBuilder.<S, String>argument("server", StringArgumentType.word()).executes(c -> export(c, feedback, error))));
	}

	private static <S> int temp(CommandContext<S> c, String name, BiConsumer<S, Component> feedback) {
		MapSession session = CakeMap.session();
		if (session == null) {
			return 0;
		}
		int x = IntegerArgumentType.getInteger(c, "x");
		int y = IntegerArgumentType.getInteger(c, "y");
		int z = IntegerArgumentType.getInteger(c, "z");
		Waypoint w = new Waypoint(name.isEmpty() ? Component.translatable("cakemap.kind.quest_objective").getString() : name, WaypointKind.QUEST_OBJECTIVE,
				session.dimension(), x, y, z);
		w.source = "chat";
		session.waypoints.add(w);
		feedback.accept(c.getSource(), Component.translatable("cakemap.waypoint.added", w.name + " (" + x + ", " + y + ", " + z + ")"));
		return 1;
	}

	/** Chat closes after a command: open the screen on the next tick. */
	private static int openLater(Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		mc.schedule(() -> mc.setScreen(screen));
		return 1;
	}

	private static <S> int export(CommandContext<S> c, BiConsumer<S, Component> feedback, BiConsumer<S, Component> error) {
		if (!CakeMap.settings().devMode) {
			error.accept(c.getSource(), Component.translatable("cakemap.export.dev_only"));
			return 0;
		}
		String server;
		try {
			server = StringArgumentType.getString(c, "server");
		} catch (IllegalArgumentException e) {
			server = CakeMap.session() == null ? "monumenta" : CakeMap.session().server;
		}
		CakeMap.closeSessionForExport();
		StaticExport.run(server, CakeMap.dataRoot(), Services.PLATFORM.gameDir().resolve("cakemap-export"),
				msg -> feedback.accept(c.getSource(), Component.literal(msg)));
		return 1;
	}
}
