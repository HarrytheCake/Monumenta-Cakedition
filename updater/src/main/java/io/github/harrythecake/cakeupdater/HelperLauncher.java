package io.github.harrythecake.cakeupdater;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Starts {@link Helper} as a separate process that outlives the game. */
final class HelperLauncher {
	private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

	private HelperLauncher() {}

	static void launch(Path gameDir, String packUrl, String language, String name) throws IOException {
		// Run the helper from a temp copy: the update may replace this mod's jar, and Windows
		// can't overwrite a jar that a running process has open.
		Path modJar = FabricLoader.getInstance().getModContainer(CakeUpdater.MOD_ID).orElseThrow()
				.getOrigin().getPaths().get(0);
		Path copy = Files.createTempFile("cakeupdater-helper-", ".jar");
		Files.copy(modJar, copy, StandardCopyOption.REPLACE_EXISTING);

		List<String> command = new ArrayList<>();
		if (WINDOWS) {
			// Java starts child processes without a window; "start" gives the helper its own console.
			command.addAll(List.of("cmd.exe", "/c", "start", name + " Updater"));
		}
		command.addAll(List.of(consoleJava(), "-cp", copy.toString(), Helper.class.getName(),
				Long.toString(ProcessHandle.current().pid()), gameDir.toAbsolutePath().toString(), packUrl,
				language, name));
		CakeUpdater.LOGGER.info("Starting updater: {}", command);
		new ProcessBuilder(command).directory(gameDir.toFile()).start();
	}

	/** The game usually runs on javaw.exe, which has no console; use java.exe next to it. */
	private static String consoleJava() {
		String current = ProcessHandle.current().info().command()
				.orElse(Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString());
		if (WINDOWS && current.toLowerCase().endsWith("javaw.exe")) {
			Path java = Path.of(current).resolveSibling("java.exe");
			if (Files.exists(java)) {
				return java.toString();
			}
		}
		return current;
	}
}
