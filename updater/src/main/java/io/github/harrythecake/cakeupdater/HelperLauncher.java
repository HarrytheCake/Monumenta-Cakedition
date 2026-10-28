package io.github.harrythecake.cakeupdater;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Starts {@link Helper} as a separate process that outlives the game, in a terminal window:
 * cmd on Windows, Terminal.app on macOS, the first terminal emulator found on Linux.
 * Without a terminal (none installed, Flatpak sandbox) the helper runs in the background and
 * uses packwiz-installer's window plus a dialog instead.
 */
final class HelperLauncher {
	private static final String OS = System.getProperty("os.name").toLowerCase();
	private static final boolean WINDOWS = OS.startsWith("windows");
	private static final boolean MAC = OS.startsWith("mac");

	/** Linux terminals and the arguments that make them run one program: {name, args before the script...}. */
	private static final String[][] LINUX_TERMINALS = {
			{"x-terminal-emulator", "-e"},
			{"gnome-terminal", "--"},
			{"konsole", "-e"},
			{"xfce4-terminal", "-x"},
			{"mate-terminal", "-x"},
			{"tilix", "-e"},
			{"kitty"},
			{"alacritty", "-e"},
			{"foot"},
			{"xterm", "-e"},
	};

	private HelperLauncher() {}

	/**
	 * {@code mrpack} (may be null): the Modrinth pack file to update from first; {@code packUrls}: then these pack.toml
	 * addresses, in order. On Windows the arguments pass through cmd, so a source with {@code & ^ | < > %} is left out.
	 */
	static void launch(Path gameDir, UpdateCheck.Mrpack mrpack, List<String> packUrls, String language, String name)
			throws IOException {
		List<String> urls = packUrls.stream().filter(HelperLauncher::safe).toList();
		if (urls.isEmpty()) {
			throw new IOException("no usable pack.toml address in " + packUrls);
		}
		// Run the helper from a temp copy: the update may replace this mod's jar, and Windows
		// can't overwrite a jar that a running process has open.
		Path modJar = FabricLoader.getInstance().getModContainer(CakeUpdater.MOD_ID).orElseThrow()
				.getOrigin().getPaths().get(0);
		Path copy = Files.createTempFile("cakeupdater-helper-", ".jar");
		Files.copy(modJar, copy, StandardCopyOption.REPLACE_EXISTING);

		List<String> helper = new ArrayList<>(List.of(consoleJava(), "-cp", copy.toString(), Helper.class.getName(),
				Long.toString(ProcessHandle.current().pid()), gameDir.toAbsolutePath().toString(), urls.get(0),
				language, name));
		if (mrpack != null && safe(mrpack.url()) && safe(mrpack.sha512())) {
			helper.addAll(List.of("--mrpack", mrpack.url(), mrpack.sha512()));
		}
		for (String url : urls.subList(1, urls.size())) {
			helper.addAll(List.of("--fallback", url));
		}
		List<String> command = new ArrayList<>();
		if (WINDOWS) {
			// Java starts child processes without a window; "start" gives the helper its own console.
			command.addAll(List.of("cmd.exe", "/c", "start", name + " Updater"));
			command.addAll(helper);
		} else if (MAC) {
			command.addAll(List.of("open", "-a", "Terminal", script(helper, ".command").toString()));
		} else {
			String[] terminal = findLinuxTerminal();
			if (terminal != null) {
				command.addAll(List.of(terminal));
				command.add(script(helper, ".sh").toString());
			} else {
				command.addAll(helper);
			}
		}
		CakeUpdater.LOGGER.info("Starting updater: {}", command);
		new ProcessBuilder(command).directory(gameDir.toFile()).start();
	}

	/** Passes through cmd /c start unchanged. */
	static boolean safe(String arg) {
		return arg != null && !arg.isBlank() && arg.chars().noneMatch(c -> "&^|<>%\"".indexOf(c) >= 0);
	}

	/** An executable sh script that execs the helper, for terminals that take one program to run. */
	static Path script(List<String> command, String suffix) throws IOException {
		Path script = Files.createTempFile("cakeupdater-helper-", suffix);
		String line = command.stream().map(HelperLauncher::shellQuote).collect(Collectors.joining(" "));
		Files.writeString(script, "#!/bin/sh\nexec " + line + "\n", StandardCharsets.UTF_8);
		if (!script.toFile().setExecutable(true)) {
			throw new IOException("Could not make " + script + " executable");
		}
		return script;
	}

	static String shellQuote(String arg) {
		return "'" + arg.replace("'", "'\\''") + "'";
	}

	/** Returns {executable path, args...} for the first terminal on PATH, or null. */
	private static String[] findLinuxTerminal() {
		String path = System.getenv("PATH");
		if (path == null || System.getenv("FLATPAK_ID") != null) {
			return null;
		}
		for (String[] terminal : LINUX_TERMINALS) {
			for (String dir : path.split(File.pathSeparator)) {
				Path exe = Path.of(dir, terminal[0]);
				if (Files.isExecutable(exe)) {
					String[] found = terminal.clone();
					found[0] = exe.toString();
					return found;
				}
			}
		}
		return null;
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
