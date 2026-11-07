package io.github.harrythecake.cakechat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Development only, off unless the JVM runs with {@code -Dcakechat.selftest=true} (smoke.py passes it with
 * {@code --jvm}). In a world it adds made-up chat lines (never real players' lines), opens the chat, checks the text
 * under a point (click event), checks the window-editing lock (no grip by default; {@code /cakechat edit} typed
 * through the client command path turns it on and the grip shows, again turns it off), right-clicks a line (copy menu), opens the find bar and the settings screen, takes
 * screenshots ({@code run/screenshots/cakechat-selftest-*.png}) and logs "cakechat self-test done". Sends nothing to
 * a server.
 */
final class SelfTest {
	static final boolean ON = Boolean.getBoolean("cakechat.selftest");

	private static int ticks = -1;
	private static boolean editingAtStart;
	private static boolean gripBefore;
	private static boolean gripOn;

	private SelfTest() {
	}

	static void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		ticks++;
		switch (ticks) {
			case 40 -> {
				for (int i = 1; i <= 12; i++) {
					client.gui.getChat().addMessage(Component.literal("<g> [ABC] Mock_Pl4yer » test line " + i));
				}
				for (int i = 0; i < 3; i++) {
					client.gui.getChat().addMessage(Component.literal("<lfg> [XY] qwertfox » same line (compacted)"));
				}
				client.gui.getChat().addMessage(Component.literal("<tr> a long line to see the wrapping in a narrow box: "
						+ "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore"));
				client.gui.getChat().addMessage(Component.literal("click me").withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cakechat"))));
				CakeChat.LOGGER.info("cakechat self-test: {} entries in the store", CakeChat.store().entries().size());
			}
			case 60 -> client.setScreen(new ChatScreen(""));
			case 80 -> shot(client, "open");
			case 90 -> {
				// right-click the newest line: the copy menu
				ChatView view = CakeChat.windows().views().get(0);
				double x = view.boxX() + 20;
				double y = view.boxBottom() - 4;
				Style clicked = client.gui.getChat().getClickedComponentStyleAt(x, y);
				CakeChat.LOGGER.info("cakechat self-test: click event under the newest line={}", clicked == null ? null : clicked.getClickEvent());
				boolean used = CakeChat.windows().mouseClicked(x, y, 1);
				CakeChat.LOGGER.info("cakechat self-test: right-click used={} menu open={}", used, CakeChat.windows().copyMenu().isOpen());
			}
			case 95 -> {
				ChatEntry first = CakeChat.store().entries().get(0);
				CakeChat.LOGGER.info("cakechat self-test: copy text={} codes={} json={}", ChatCopy.copy(first.content, ChatCopy.Format.TEXT),
						ChatCopy.copy(first.content, ChatCopy.Format.CODES), ChatCopy.copy(first.content, ChatCopy.Format.JSON));
			}
			case 100 -> shot(client, "copymenu");
			case 102 -> {
				CakeChat.windows().copyMenu().close();
				editingAtStart = CakeChat.config().windowEditing;
				gripBefore = inGrip();
				// the command as the player types it: a client command, never sent to the server
				client.player.connection.sendCommand("cakechat edit");
			}
			case 104 -> gripOn = CakeChat.config().windowEditing && inGrip();
			case 106 -> shot(client, "edit");
			case 108 -> client.player.connection.sendCommand("cakechat edit");
			case 109 -> {
				boolean gripAfter = inGrip();
				boolean ok = !editingAtStart && !gripBefore && gripOn && !CakeChat.config().windowEditing && !gripAfter;
				CakeChat.LOGGER.info("cakechat self-test: edit lock ok={} (start editing={} grip={}; after /cakechat edit grip={}; after again editing={} grip={})",
						ok, editingAtStart, gripBefore, gripOn, CakeChat.config().windowEditing, gripAfter);
			}
			case 110 -> {
				CakeChat.windows().copyMenu().close();
				if (client.screen != null) {
					CakeChat.windows().findBar().open(client.screen);
					CakeChat.windows().findBar().search("compacted");
				}
				CakeChat.LOGGER.info("cakechat self-test: find searching={} found={}", CakeChat.windows().findBar().searching(),
						CakeChat.windows().findBar().found());
			}
			case 130 -> shot(client, "find");
			case 140 -> client.setScreen(SettingsScreen.create(null));
			case 160 -> {
				CakeChat.LOGGER.info("cakechat self-test: settings screen {}", client.screen == null ? null : client.screen.getClass().getName());
				shot(client, "settings");
			}
			case 165 -> {
				// with CakeUI 3+ in the run (-Pcakeui_dir): the windows are movables; place one as the editor would
				if (CakeUIBridge.available()) {
					ChatView view = CakeChat.windows().views().get(0);
					int w = client.getWindow().getGuiScaledWidth();
					int h = client.getWindow().getGuiScaledHeight();
					try {
						Class<?> mv = Class.forName("io.github.harrythecake.cakeui.HudMovables");
						java.lang.reflect.Method all = mv.getDeclaredMethod("all");
						all.setAccessible(true);
						java.util.Map<?, ?> movables = (java.util.Map<?, ?>) all.invoke(null);
						Object m = movables.get("~cakechat:window1");
						m.getClass().getMethod("place", double.class, double.class, double.class, double.class, int.class, int.class)
								.invoke(m, 40.0, 30.0, 200.0, 80.0, w, h);
						boolean ok = view.boxX() == 40 && view.boxWidth() == 200 && view.boxTop() == 30
								&& CakeChat.config().windows.get(0).x == 40;
						CakeChat.LOGGER.info("cakechat self-test: CakeUI movables {}, window 1 placed at {} {} {} ({})", movables.keySet(),
								view.boxX(), view.boxTop(), view.boxWidth(), ok ? "PASS" : "FAIL");
					} catch (ReflectiveOperationException | RuntimeException e) {
						CakeChat.LOGGER.info("cakechat self-test: CakeUI movables FAIL {}", e.toString());
					}
				} else {
					CakeChat.LOGGER.info("cakechat self-test: no CakeUI 3 in this run, movables not checked");
				}
			}
			case 170 -> {
				client.setScreen(null);
				CakeChat.LOGGER.info("cakechat self-test done");
			}
			default -> {
			}
		}
	}

	/** Whether a press on the main window's resize corner would be taken as the grip. */
	private static boolean inGrip() {
		ChatView view = CakeChat.windows().views().get(0);
		return view.inGrip(view.boxX() + view.boxWidth() - 2, view.boxTop() + 2);
	}

	private static void shot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, "cakechat-selftest-" + name + ".png", client.getMainRenderTarget(),
				message -> CakeChat.LOGGER.info("cakechat self-test: screenshot {}", name));
	}
}
