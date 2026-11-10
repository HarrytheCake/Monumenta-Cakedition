package io.github.harrythecake.cakewiki;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Development only, does nothing in a normal game. A screenshot tour: each step opens a page, waits for it and its
 * pictures, saves {@code screenshots/cakewiki-<n>.png} and logs whether the page was shown.
 * <ul>
 * <li>{@code -Dcakewiki.devShot=Page|item:Name|Page@300|Page!open} (through {@code -Pcake.runJvmArgs}): this tour from
 * the title screen, then the game quits. {@code _} in a step is a space (the run arguments are split at spaces).</li>
 * <li>{@code -Dcakewiki.selftest=1}: the built-in tour from the title screen; {@code =world}: after joining a world
 * (smoke.py {@code --world}), with the steps that need a player too. Ends with "cakewiki self-test done" and leaves
 * the game running.</li>
 * </ul>
 * Steps: a page ({@code <site id>:<page>} like /wiki; {@code <site id>:} = its main page); {@code item:Name} looks a
 * name up like the item key does; {@code @y} scrolls; {@code !open} opens every collapsible; {@code !marks} bookmarks
 * the page and opens the bookmarks list. In a world: {@code inv:Name} presses the open key over an item called Name
 * in the inventory screen (the mixin's way); {@code cmd:args} runs {@code /wiki args} as if typed; {@code key}
 * presses the open key in the world.
 */
final class DevShots {
	private static final String TITLE_TOUR = "Monumenta Wiki|item:Snegovik Boots|Turtle Set!open|Monumenta Wiki!marks";
	private static final String WORLD_TOUR = "Monumenta Wiki|inv:Snegovik Boots|cmd:Turtle Set|key";

	private static DevShots active;

	private final Deque<String> steps;
	private final boolean selfTest, inWorld;
	private WikiScreen screen;
	/** A step that opens the reader by itself (a command, a key): ticks left to wait for it. */
	private int awaitScreen;
	private boolean backToInventory;
	private int wait, shot, ok, idle;
	private boolean stopped;

	private DevShots(String spec, boolean selfTest, boolean inWorld) {
		steps = new ArrayDeque<>(Arrays.asList(spec.split("\\|")));
		this.selfTest = selfTest;
		this.inWorld = inWorld;
	}

	static void startIfAsked() {
		String spec = System.getProperty("cakewiki.devShot", "");
		String test = System.getProperty("cakewiki.selftest", "");
		if (!spec.isBlank()) active = new DevShots(spec.replace('_', ' '), false, false);
		else if (test.equals("world")) active = new DevShots(WORLD_TOUR, true, true);
		else if (!test.isBlank()) active = new DevShots(TITLE_TOUR, true, false);
	}

	static void tick(Minecraft mc) {
		if (active != null && !active.stopped) active.step(mc);
	}

	private void step(Minecraft mc) {
		if (awaitScreen > 0) { // the reader opens a tick or two after a command or key press
			if (mc.screen instanceof WikiScreen w) {
				awaitScreen = 0;
				screen = w;
			} else if (--awaitScreen == 0) {
				CakeWiki.LOG.info("devShot {}: the reader did NOT open", shot + 1);
				shot++;
				next(mc);
			}
			return;
		}
		if (screen == null) {
			if (mc.getOverlay() != null) return;
			if (inWorld ? mc.player == null || mc.screen != null || ++idle < 60 : !(mc.screen instanceof TitleScreen)) return;
			next(mc);
			return;
		}
		if (screen.loading()) {
			if (++wait < 400) return; // 20 s
		}
		if (wait < 1000) { // loaded: give pictures up to 8 s
			wait = 1000;
			return;
		}
		if (++wait < 1160 && (wait < 1040 || CakeWiki.reader().images.pending() > 0)) return;
		boolean ready = screen.devReady();
		if (ready) ok++;
		CakeWiki.LOG.info("devShot {} at capture ({}): {}", shot + 1, ready ? "shown" : "NOT shown", screen.devState());
		Screenshot.grab(mc.gameDirectory, "cakewiki-" + (++shot) + ".png", mc.getMainRenderTarget(), 1,
				msg -> CakeWiki.LOG.info("devShot: {}", msg.getString()));
		if (backToInventory) { // the reader opened from the inventory goes back to it
			backToInventory = false;
			screen.onClose();
			CakeWiki.LOG.info("devShot {}: closing the reader went back to the inventory: {}", shot,
					mc.screen instanceof AbstractContainerScreen);
			mc.setScreen(null);
		}
		next(mc);
	}

	private void next(Minecraft mc) {
		wait = 0;
		screen = null;
		String step = steps.poll();
		if (step == null) {
			stopped = true;
			if (selfTest) {
				CakeWiki.LOG.info("cakewiki self-test done: {} of {} pages shown", ok, shot);
				if (inWorld) mc.setScreen(null);
			} else {
				CakeWiki.LOG.info("devShot: done");
				mc.stop();
			}
			return;
		}
		int scroll = 0;
		boolean openAll = step.endsWith("!open");
		if (openAll) step = step.substring(0, step.length() - 5);
		boolean mark = step.endsWith("!marks");
		if (mark) step = step.substring(0, step.length() - 6);
		int at = step.lastIndexOf('@');
		if (at > 0) {
			scroll = Integer.parseInt(step.substring(at + 1));
			step = step.substring(0, at);
		}
		CakeWiki.LOG.info("devShot {}: {}", shot + 1, step);
		if (step.startsWith("inv:") && mc.player != null) {
			// the item key over an item in the player's inventory, through the mixin
			InventoryScreen inv = new InventoryScreen(mc.player);
			mc.setScreen(inv);
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(step.substring(4)));
			((ItemHover) inv).cakewiki$hover(new Slot(new SimpleContainer(stack), 0, 0, 0));
			inv.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Y, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_Y), 0));
			if (mc.screen instanceof WikiScreen w) {
				screen = w;
				backToInventory = true;
			} else {
				CakeWiki.LOG.info("devShot {}: the item key did NOT open the reader", shot + 1);
				shot++;
				mc.setScreen(null);
				next(mc);
			}
			return;
		}
		if (step.startsWith("cmd:") && mc.player != null) { // as if typed in chat: the loader runs client commands
			mc.setScreen(null);
			mc.player.connection.sendCommand("wiki " + step.substring(4));
			awaitScreen = 100;
			return;
		}
		if (step.equals("key")) { // the open key in the world: the last page read
			mc.setScreen(null);
			KeyMapping.click(CakeWiki.openKey.getDefaultKey());
			awaitScreen = 100;
			return;
		}
		screen = new WikiScreen(CakeWiki.reader(), () -> mc.setScreen(null));
		mc.setScreen(screen);
		if (step.startsWith("item:")) screen.lookup(step.substring(5));
		else { // "<site id>:<page>" like /wiki; "<site id>:" is its main page
			CakeWiki.Target t = CakeWiki.target(step);
			Reader.Source site = t.site() != null ? t.site() : CakeWiki.reader().home();
			screen.reopen(new Reader.Visit(site.id(), t.page().isEmpty() ? site.info().mainPage() : t.page(), scroll));
		}
		if (openAll) screen.devOpenAll();
		if (mark) screen.devMark();
	}
}
