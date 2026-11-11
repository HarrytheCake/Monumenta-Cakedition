package io.github.harrythecake.caketext.core.dev;

import java.util.function.Supplier;

import io.github.harrythecake.caketext.core.CakeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/**
 * Helpers for the dev self-tests of the Collector and the Translator ({@code -Dcaketext.selftest=1}, used by the
 * Cakedition repo's tools/multiloader/smoke.py with {@code --world}). Nothing here runs unless that property is set.
 * Each self-test runs on its own thread and hands game work to the client thread.
 */
public final class SelfTestKit {
	public static final boolean ENABLED = System.getProperty("caketext.selftest") != null;

	private SelfTestKit() {
	}

	/** Starts {@code test} once, on a daemon thread. */
	public static void start(String name, Runnable test) {
		Thread thread = new Thread(() -> {
			try {
				test.run();
			} catch (Throwable e) {
				CakeText.LOG.error("{} failed", name, e);
			}
		}, name);
		thread.setDaemon(true);
		thread.start();
	}

	public static <T> T on(Minecraft client, Supplier<T> task) {
		try {
			return client.submit(task).get();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static void run(Minecraft client, Runnable task) {
		on(client, () -> {
			task.run();
			return null;
		});
	}

	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** A screenshot of the game window into the run folder's screenshots/. */
	public static void screenshot(Minecraft client, String name) {
		run(client, () -> Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> {
		}));
		sleep(1000);
	}

	/** Types {@code /command} into the chat screen and sends it, as the player would. */
	public static void type(Minecraft client, String command) {
		run(client, () -> {
			ChatScreen chat = new ChatScreen("");
			client.setScreen(chat);
			chat.handleChatInput("/" + command, true);
			if (client.screen == chat) {
				client.setScreen(null);
			}
		});
	}

	/** Presses the button labelled with this translation key on the open screen; false when there is none. */
	public static boolean press(Minecraft client, String key) {
		String label = Component.translatable(key).getString();
		return on(client, () -> {
			if (client.screen == null) {
				return false;
			}
			for (GuiEventListener child : client.screen.children()) {
				if (child instanceof Button button && button.getMessage().getString().equals(label)) {
					button.onPress();
					return true;
				}
			}
			return false;
		});
	}

	/** One check: logs it and returns 1 when it passed. */
	public static int check(String test, String what, boolean ok) {
		CakeText.LOG.info("{}: {} {}", test, what, ok ? "ok" : "FAILED");
		return ok ? 1 : 0;
	}
}
