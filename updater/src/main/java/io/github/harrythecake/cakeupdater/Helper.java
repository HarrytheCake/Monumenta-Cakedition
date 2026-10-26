package io.github.harrythecake.cakeupdater;

import javax.swing.JOptionPane;
import java.io.Console;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The updater window. Runs outside Minecraft with plain Java (no game or Gson classes):
 * waits for the game to exit, runs packwiz-installer, then tells the player to start the game again.
 * It never downloads anything itself: it runs the packwiz-installer-bootstrap.jar already in the game folder
 * (a packwiz install needs it there for the launcher's pre-launch command anyway).
 *
 * <p>Usage: {@code java -cp cakeupdater.jar io.github.harrythecake.cakeupdater.Helper
 * <game pid> <.minecraft dir> <pack.toml url> [language] [name]}
 */
public final class Helper {
	static final String BOOTSTRAP = "packwiz-installer-bootstrap.jar";
	static final String BOOTSTRAP_PAGE = "https://github.com/packwiz/packwiz-installer-bootstrap/releases";

	// Minecraft's lang files live in the game; this window needs its own copy of its few lines.
	private static final Map<String, Map<String, String>> MESSAGES = Map.of(
			"en_us", Map.of(
					"title", "=== %s Updater ===",
					"waiting", "Waiting for Minecraft to close...",
					"missing", "%s is not in %s, so the modpack can't update.\nDownload it from %s and put it there.",
					"updating", "Updating the modpack (a packwiz-installer window will show the progress)...",
					"done", "Update finished! You can start Minecraft again from your launcher.",
					"failed", "The update did not finish (exit code %d).\nYou can still start Minecraft; the update will be offered again next time.",
					"enter", "Press Enter to close this window."),
			"zh_cn", Map.of(
					"title", "=== %s 更新程序 ===",
					"waiting", "正在等待 Minecraft 关闭……",
					"missing", "%s 不在 %s 中，因此无法更新整合包。\n请从 %s 下载并放到该文件夹。",
					"updating", "正在更新整合包（packwiz-installer 窗口会显示进度）……",
					"done", "更新完成！现在可以从启动器重新启动 Minecraft。",
					"failed", "更新未完成（退出代码 %d）。\n仍然可以启动 Minecraft，下次会再次提示更新。",
					"enter", "按 Enter 关闭此窗口。"),
			"zh_tw", Map.of(
					"title", "=== %s 更新程式 ===",
					"waiting", "正在等待 Minecraft 關閉……",
					"missing", "%s 不在 %s 中，因此無法更新模組包。\n請從 %s 下載並放到該資料夾。",
					"updating", "正在更新模組包（packwiz-installer 視窗會顯示進度）……",
					"done", "更新完成！現在可以從啟動器重新啟動 Minecraft。",
					"failed", "更新未完成（結束代碼 %d）。\n仍然可以啟動 Minecraft，下次會再次提示更新。",
					"enter", "按 Enter 關閉此視窗。"));

	private static Map<String, String> messages = MESSAGES.get("en_us");

	private Helper() {}

	public static void main(String[] args) {
		if (args.length < 3) {
			System.err.println("Usage: Helper <game pid> <.minecraft dir> <pack.toml url> [language] [name]");
			System.exit(2);
		}
		long pid = Long.parseLong(args[0]);
		Path gameDir = Path.of(args[1]);
		String packUrl = args[2];
		String name = args.length > 4 ? args[4] : "Cakedition";
		Console console = System.console();
		messages = pickLanguage(args.length > 3 ? args[3] : "en_us", console);

		if (console != null && !System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			// macOS/Linux terminals take their window title from this escape sequence.
			System.out.print("\033]0;" + name + " Updater\007");
		}

		int exitCode;
		try {
			say(text("title", name));
			say("");
			waitForGame(pid);
			cleanOldCopies();
			Path bootstrap = gameDir.resolve(BOOTSTRAP);
			if (Files.exists(bootstrap)) {
				say(text("updating"));
				Process installer = new ProcessBuilder(currentJava(), "-jar", bootstrap.toString(), "-s", "client", packUrl)
						.directory(gameDir.toFile())
						.inheritIO()
						.start();
				exitCode = installer.waitFor();
			} else {
				say(text("missing", BOOTSTRAP, gameDir, BOOTSTRAP_PAGE));
				exitCode = -1;
			}
		} catch (Exception e) {
			e.printStackTrace();
			exitCode = -1;
		}

		String message = exitCode == 0 ? text("done") : text("failed", exitCode);
		say("");
		say(message);
		if (console != null) {
			say("");
			say(text("enter"));
			console.readLine();
		} else {
			try {
				JOptionPane.showMessageDialog(null, message, name,
						exitCode == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
			} catch (Exception ignored) {
				// Headless system: the message is already on stdout.
			}
		}
		System.exit(exitCode == 0 ? 0 : 1);
	}

	/** Minecraft language code -> messages; English if the console can't show the characters. */
	private static Map<String, String> pickLanguage(String language, Console console) {
		String code = language.toLowerCase();
		if (code.equals("zh_hk")) {
			code = "zh_tw";
		}
		Map<String, String> chosen = MESSAGES.getOrDefault(code, MESSAGES.get("en_us"));
		if (console != null && !console.charset().newEncoder().canEncode(String.join("", chosen.values()))) {
			return MESSAGES.get("en_us");
		}
		return chosen;
	}

	private static String text(String key, Object... args) {
		return String.format(messages.get(key), args);
	}

	private static void waitForGame(long pid) throws InterruptedException {
		var game = ProcessHandle.of(pid);
		if (game.isPresent() && game.get().isAlive()) {
			say(text("waiting"));
			game.get().onExit().join();
		}
		// Give Windows a moment to release the file locks on the mod jars.
		Thread.sleep(2000);
	}

	/** Remove helper jars and launch scripts left in the temp folder by earlier updates (they can't delete themselves). */
	private static void cleanOldCopies() {
		Path self;
		try {
			self = Path.of(Helper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (Exception e) {
			return;
		}
		try (DirectoryStream<Path> copies = Files.newDirectoryStream(self.getParent(), "cakeupdater-helper-*")) {
			for (Path copy : copies) {
				if (!copy.equals(self)) {
					Files.deleteIfExists(copy);
				}
			}
		} catch (IOException ignored) {
			// Best effort.
		}
	}

	private static String currentJava() {
		return ProcessHandle.current().info().command().orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
	}

	private static void say(String line) {
		System.out.println(line);
	}
}
