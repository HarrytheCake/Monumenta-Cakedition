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
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;

/**
 * Dev self-test ({@code -Dcakeupdater.selftest=1}, used by tools/multiloader/smoke.py with {@code --world}). After the
 * first join it shows every chat message with made-up versions (urgent, update available, "Modpack updated" with a
 * made-up changelog), the urgent toast, then clicks [Changelog] and [Backups] the way the chat screen does (Screen.clickCommandAction) and checks
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
				client.setScreen(new ChatScreen("", false));
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
			client.setScreen(new ChatScreen("", false));
			return null;
		});
		sleep(500);
		// what Screen.clickCommandAction does for a chat button's RunCommand (it is protected)
		on(client, () -> {
			if (CakeUpdater.button("cakeupdater.backups.open", command, ChatFormatting.AQUA).getStyle().getClickEvent()
					instanceof ClickEvent.RunCommand(String run)) {
				client.player.connection.sendUnattendedCommand(Commands.trimOptionalPrefix(run), client.screen);
			}
			return null;
		});
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
			Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, message -> {});
			return null;
		});
		sleep(1000);
	}

	private static <T> T on(Minecraft client, Supplier<T> task) throws Exception {
		return client.submit(task).get();
	}

	private static void sleep(long ms) throws InterruptedException {
		Thread.sleep(ms);
	}
}
