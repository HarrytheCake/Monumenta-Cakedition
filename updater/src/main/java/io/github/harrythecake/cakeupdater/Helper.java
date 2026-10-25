package io.github.harrythecake.cakeupdater;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * The updater window. Runs outside Minecraft with plain Java (no game classes):
 * waits for the game to exit, runs packwiz-installer, then tells the player to start the game again.
 *
 * <p>Usage: {@code java -cp cakeupdater.jar io.github.harrythecake.cakeupdater.Helper <game pid> <.minecraft dir> <pack.toml url>}
 */
public final class Helper {
	private static final String BOOTSTRAP = "packwiz-installer-bootstrap.jar";
	private static final String BOOTSTRAP_URL =
			"https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar";
	private static final String BOOTSTRAP_SHA256 = "a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c";

	private Helper() {}

	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println("Usage: Helper <game pid> <.minecraft dir> <pack.toml url>");
			System.exit(2);
		}
		long pid = Long.parseLong(args[0]);
		Path gameDir = Path.of(args[1]);
		String packUrl = args[2];
		boolean console = System.console() != null;

		int exitCode;
		try {
			say("=== Cakedition Updater ===");
			say("");
			waitForGame(pid);
			cleanOldCopies();
			Path bootstrap = ensureBootstrap(gameDir);
			say("Updating the modpack (a packwiz-installer window will show the progress)...");
			Process installer = new ProcessBuilder(currentJava(), "-jar", bootstrap.toString(), "-s", "client", packUrl)
					.directory(gameDir.toFile())
					.inheritIO()
					.start();
			exitCode = installer.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
			exitCode = -1;
		}

		String message = exitCode == 0
				? "Update finished! You can start Minecraft again from your launcher."
				: "The update did not finish (exit code " + exitCode + ").\n"
				+ "You can still start Minecraft; the update will be offered again next time.";
		say("");
		say(message);
		if (console) {
			say("");
			say("Press Enter to close this window.");
			System.console().readLine();
		} else {
			try {
				JOptionPane.showMessageDialog(null, message, "Cakedition Updater",
						exitCode == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
			} catch (Exception ignored) {
				// Headless system: the message is already on stdout.
			}
		}
		System.exit(exitCode == 0 ? 0 : 1);
	}

	private static void waitForGame(long pid) throws InterruptedException {
		var game = ProcessHandle.of(pid);
		if (game.isPresent() && game.get().isAlive()) {
			say("Waiting for Minecraft to close...");
			game.get().onExit().join();
		}
		// Give Windows a moment to release the file locks on the mod jars.
		Thread.sleep(2000);
	}

	private static Path ensureBootstrap(Path gameDir) throws IOException, InterruptedException {
		Path bootstrap = gameDir.resolve(BOOTSTRAP);
		if (Files.exists(bootstrap)) {
			return bootstrap;
		}
		say("Downloading " + BOOTSTRAP + "...");
		HttpClient http = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(15))
				.build();
		HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(BOOTSTRAP_URL)).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode() + " downloading " + BOOTSTRAP_URL);
		}
		String hash = sha256(response.body());
		if (!BOOTSTRAP_SHA256.equals(hash)) {
			throw new IOException("Downloaded " + BOOTSTRAP + " has an unexpected hash: " + hash);
		}
		Path tmp = Files.createTempFile(gameDir, BOOTSTRAP, ".part");
		Files.write(tmp, response.body());
		Files.move(tmp, bootstrap, StandardCopyOption.REPLACE_EXISTING);
		return bootstrap;
	}

	/** Remove helper copies left in the temp folder by earlier updates (they can't delete themselves). */
	private static void cleanOldCopies() {
		Path self;
		try {
			self = Path.of(Helper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (Exception e) {
			return;
		}
		try (DirectoryStream<Path> copies = Files.newDirectoryStream(self.getParent(), "cakeupdater-helper-*.jar")) {
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

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void say(String line) {
		System.out.println(line);
	}
}
