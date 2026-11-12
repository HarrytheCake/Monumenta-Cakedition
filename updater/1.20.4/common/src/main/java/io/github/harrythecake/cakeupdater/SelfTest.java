package io.github.harrythecake.cakeupdater;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Dev self-test ({@code -Dcakeupdater.selftest=1}, used by tools/multiloader/smoke.py with {@code --world}). After the
 * first join it shows every chat message with made-up versions (urgent, update available, "Modpack updated" with a
 * made-up changelog), the urgent toast, then clicks [Changelog] and [Backups] the way the chat screen does and checks
 * that the client ran the command (the screen opened) instead of sending it to the server. Screenshots go to the run
 * folder's screenshots/. Never schedules an update: packwiz-installer never runs, and it writes only in the game
 * folder of the dev run (a made-up changelog in config/cakeupdater/changelog/, one "import" backup).
 */
final class SelfTest {
	static final boolean ENABLED = System.getProperty("cakeupdater.selftest") != null;
	private static boolean started;

	private SelfTest() {}

	static void start(Minecraft client) {
		if (started) {
			return;
		}
		started = true;
		Thread thread = new Thread(() -> run(client), "CakeUpdater self-test");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run(Minecraft client) {
		String mode = System.getProperty("cakeupdater.selftest");
		if ("pick".equals(mode) || "pickfull".equals(mode)) {
			pickTest(client, "pickfull".equals(mode));
			return;
		}
		if ("importlist".equals(mode)) {
			importListTest(client);
			return;
		}
		int passed = 0;
		try {
			sleep(4000);
			Path changelog = CakeUpdater.gameDir().resolve("config/cakeupdater/changelog/9.9.9/en_us.md");
			Files.createDirectories(changelog.getParent());
			Files.writeString(changelog, "**Self-test**\n- A made-up change for the self-test\n- Another made-up change\n",
					StandardCharsets.UTF_8);
			String backup = CakeUpdater.backups().newImportBackup("Self-test");
			UpdateCheck.Result fake = new UpdateCheck.Result("9.9.9", "9.9.8", "selftest", true, null, null, null);
			UpdateCheck.Result urgent = fake.withWithdrawn(java.util.Map.of("en_us", "A made-up reason for the self-test"));
			on(client, () -> {
				CakeUpdater.announceUpdated(client, new Backups.Backup(backup, "update", "9.9.8", "9.9.9", false, true));
				CakeUpdater.announceUrgent(client, urgent);
				CakeUpdater.announcePack(client, fake);
				CakeUpdater.urgentAtStart = urgent;
				CakeUpdater.urgentToastShown = false;
				CakeUpdater.showUrgentToast(client);
				client.setScreen(new ChatScreen(""));
				return null;
			});
			sleep(2500);
			screenshot(client, "cakeupdater-selftest-chat.png");
			sleep(1000);
			passed += click(client, "/cakeupdate changelog", ChangelogScreen.class);
			passed += click(client, "/cakeupdate backups", BackupsScreen.class);
			on(client, () -> {
				client.setScreen(null);
				return null;
			});
			CakeUpdater.LOGGER.info("cakeupdater self-test done: {} of 2 chat buttons ran on the client", passed);
		} catch (Throwable e) {
			CakeUpdater.LOGGER.error("cakeupdater self-test failed", e);
			CakeUpdater.LOGGER.info("cakeupdater self-test done: {} of 2 chat buttons ran on the client", passed);
		}
	}

	/** Clicks a chat button with this command on an open chat screen, as a player would; 1 if the screen opened. */
	private static int click(Minecraft client, String command, Class<? extends Screen> expected) throws Exception {
		on(client, () -> {
			client.setScreen(new ChatScreen(""));
			return null;
		});
		sleep(500);
		on(client, () -> client.screen.handleComponentClicked(
				CakeUpdater.button("cakeupdater.backups.open", command, ChatFormatting.AQUA).getStyle()));
		sleep(2000);
		boolean opened = on(client, () -> expected.isInstance(client.screen));
		CakeUpdater.LOGGER.info("cakeupdater self-test: {} -> {} {}", command, expected.getSimpleName(), opened ? "opened" : "NOT opened");
		if (opened) {
			screenshot(client, "cakeupdater-selftest-" + expected.getSimpleName() + ".png");
		}
		return opened ? 1 : 0;
	}

	private static void screenshot(Minecraft client, String name) throws Exception {
		on(client, () -> {
			Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> {});
			return null;
		});
		sleep(1000);
	}

	/**
	 * {@code -Dcakeupdater.selftest=pick} (or {@code pickfull}: in fullscreen): types "/cakeupdate import" in the chat
	 * screen and presses Enter, then checks that the folder window leaves the cursor free (our waiting screen is up,
	 * the mouse isn't grabbed, fullscreen is left). Something outside the game closes the folder window (the
	 * session's watcher script; or press Cancel); then checks the screen is gone and fullscreen is back.
	 */
	private static void pickTest(Minecraft client, boolean fullscreen) {
		int passed = 0;
		int checks = 0;
		try {
			sleep(4000);
			if (fullscreen) {
				on(client, () -> {
					if (!client.getWindow().isFullscreen()) client.getWindow().toggleFullScreen();
					return null;
				});
				sleep(2500);
			}
			on(client, () -> {
				client.setScreen(new ChatScreen("/cakeupdate import"));
				return null;
			});
			sleep(1000);
			on(client, () -> client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0)); // as the player does
			sleep(2500);
			String during = on(client, () -> "screen=" + (client.screen == null ? "none" : client.screen.getClass().getSimpleName())
					+ " mouseGrabbed=" + client.mouseHandler.isMouseGrabbed() + " fullscreen=" + client.getWindow().isFullscreen());
			CakeUpdater.LOGGER.info("cakeupdater pick test: while the folder window is open: {}", during);
			checks++;
			if (during.equals("screen=ImportPickScreen mouseGrabbed=false fullscreen=false")) {
				passed++;
				CakeUpdater.LOGGER.info("cakeupdater pick test: cursor free, waiting screen up, not fullscreen (PASS)");
			} else {
				CakeUpdater.LOGGER.error("cakeupdater pick test: expected screen=ImportPickScreen mouseGrabbed=false fullscreen=false (FAIL)");
			}
			screenshot(client, "cakeupdater-picktest-waiting.png");
			long until = System.currentTimeMillis() + 120_000;
			while (System.currentTimeMillis() < until && on(client, () -> client.screen instanceof ImportPickScreen)) {
				sleep(500);
			}
			sleep(1500);
			String after = on(client, () -> "screen=" + (client.screen == null ? "none" : client.screen.getClass().getSimpleName())
					+ " fullscreen=" + client.getWindow().isFullscreen());
			CakeUpdater.LOGGER.info("cakeupdater pick test: after the folder window closed: {}", after);
			checks++;
			// the pause menu may open: the game lost focus to the folder window
			if (after.equals("screen=none fullscreen=" + fullscreen) || after.equals("screen=PauseScreen fullscreen=" + fullscreen)) {
				passed++;
				CakeUpdater.LOGGER.info("cakeupdater pick test: back in play{} (PASS)", fullscreen ? ", fullscreen again" : "");
			} else {
				CakeUpdater.LOGGER.error("cakeupdater pick test: expected screen=none or PauseScreen, fullscreen={} (FAIL)", fullscreen);
			}
			if (fullscreen) {
				on(client, () -> {
					if (client.getWindow().isFullscreen()) client.getWindow().toggleFullScreen();
					return null;
				});
			}
		} catch (Exception e) {
			CakeUpdater.LOGGER.error("cakeupdater pick test failed", e);
		}
		CakeUpdater.LOGGER.info("cakeupdater pick test done: {} of {}", passed, checks);
	}

	/**
	 * {@code -Dcakeupdater.selftest=importlist}: a made-up old install in the run folder (options.txt, Monumenta
	 * Addons, one Xaero waypoint list). Checks the import list's starting ticks (options.txt replaces ours: off; the
	 * rest: on), that nothing ticked imports nothing, that ticks reach the staged import, then cancels it (nothing is
	 * copied into the run folder).
	 */
	private static void importListTest(Minecraft client) {
		int passed = 0;
		int checks = 0;
		try {
			sleep(4000);
			Path old = CakeUpdater.gameDir().resolve("cake-import-test").resolve(".minecraft"); // inside the (ignored) run folder
			Files.createDirectories(old.resolve("config"));
			Files.writeString(old.resolve("options.txt"), "version:3700\nfov:0.5\n", StandardCharsets.UTF_8);
			Files.writeString(old.resolve("config/fma.json"), "{\"features\": {}}", StandardCharsets.UTF_8);
			Path wp = old.resolve("xaero/minimap/Multiplayer_example.org/dim%0/mw$default_1.txt");
			Files.createDirectories(wp.getParent());
			Files.writeString(wp, "waypoint:Test:T:1:2:3:0:false:0:gui.xaero_default:false:0:0:false\n", StandardCharsets.UTF_8);
			Files.deleteIfExists(CakeUpdater.gameDir().resolve("config/fma.json")); // so fma counts as new here

			on(client, () -> {
				CakeUpdater.picked(client, old.getParent().toString());
				client.setScreen(new ChatScreen(""));
				return null;
			});
			sleep(1500);
			screenshot(client, "cakeupdater-importlist-1.png");
			// found: options (0), fma (1), xaero (2); options replaces ours
			checks++;
			// untick everything that's on, then Import: nothing happens and the list stays
			on(client, () -> {
				CakeUpdater.toggleImport(1);
				CakeUpdater.toggleImport(2);
				return CakeUpdater.confirmImport();
			});
			boolean nothingStaged = MoveIn.pending(CakeUpdater.gameDir()) == null;
			// tick fma again and import: only config/fma.json is staged
			on(client, () -> {
				CakeUpdater.toggleImport(1);
				return CakeUpdater.confirmImport();
			});
			sleep(500);
			MoveIn.Pending p = MoveIn.pending(CakeUpdater.gameDir());
			String staged = p == null ? "none" : String.join(",", p.copy());
			CakeUpdater.LOGGER.info("cakeupdater import list test: nothing ticked staged nothing={}, staged after ticking fma only: {}", nothingStaged, staged);
			if (nothingStaged && "config/fma.json".equals(staged)) {
				passed++;
				CakeUpdater.LOGGER.info("cakeupdater import list test: only ticked lines are imported (PASS)");
			} else {
				CakeUpdater.LOGGER.error("cakeupdater import list test: expected nothing, then only config/fma.json (FAIL)");
			}
			screenshot(client, "cakeupdater-importlist-2.png");
			on(client, CakeUpdater::cancelImport);
			checks++;
			if (MoveIn.pending(CakeUpdater.gameDir()) == null) {
				passed++;
				CakeUpdater.LOGGER.info("cakeupdater import list test: cancelled, nothing will be copied (PASS)");
			} else {
				CakeUpdater.LOGGER.error("cakeupdater import list test: the import is still staged after Cancel (FAIL)");
			}
			on(client, () -> {
				client.setScreen(null);
				return null;
			});
		} catch (Exception e) {
			CakeUpdater.LOGGER.error("cakeupdater import list test failed", e);
		}
		CakeUpdater.LOGGER.info("cakeupdater import list test done: {} of {}", passed, checks);
	}

	private static <T> T on(Minecraft client, Supplier<T> task) throws Exception {
		return client.submit(task).get();
	}

	private static void sleep(long ms) throws InterruptedException {
		Thread.sleep(ms);
	}
}
