package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import io.github.harrythecake.cakebook.mixin.HandledScreenAccessor;

/**
 * Dev-client check, only with {@code -Dcakebook.selftest=<page>} (e.g. {@code smoke} or {@code help/notes}): once a
 * world is joined it opens the book at that page, the note editor (inserting the location), the inventory and the
 * pause menu, a few seconds each, and logs what it found; problems are logged as errors. Ends with
 * "CakeBook self-test done". The window title names the step, so a screenshot tool can find this window.
 */
final class SelfTest {
	private static final String PAGE = System.getProperty("cakebook.selftest", "");
	private static final int STEP = 100;
	private static int ticks = -1;
	private static final List<String> PROBLEMS = new ArrayList<>();

	private SelfTest() {}

	static void tick(Minecraft client) {
		if (PAGE.isEmpty() || ticks == Integer.MAX_VALUE || client.player == null) {
			return;
		}
		ticks++;
		client.getWindow().setTitle("CakeBook self-test " + step());
		if (ticks == 40) {
			String link = PAGE.contains(":") ? PAGE : "book:" + PAGE;
			Links.Target target = Links.resolve(null, "", link);
			BookScreen book = new BookScreen(null, target, null);
			client.setScreen(book);
			CakeBook.LOGGER.info("CakeBook self-test: book open, {} books, {}", CakeBook.library().books().size(), book.summary());
			if (!book.summary().contains("pictures") || book.summary().contains(" 0 lines")) {
				PROBLEMS.add("book page empty: " + book.summary());
			}
		} else if (ticks == 40 + STEP) {
			BookScreen book = client.screen instanceof BookScreen b ? b : new BookScreen(null, null, null);
			NoteEditScreen edit = new NoteEditScreen(book, null);
			client.setScreen(edit);
			edit.insert(Location.markdown(client));
			String text = edit.editBox().value();
			CakeBook.LOGGER.info("CakeBook self-test: note editor open, inserted {}", text.strip());
			if (!text.contains("**")) {
				PROBLEMS.add("insert location wrote nothing");
			}
		} else if (ticks == 40 + 2 * STEP) {
			InventoryScreen inventory = new InventoryScreen(client.player);
			client.setScreen(inventory);
			AbstractWidget button = bookButton(inventory);
			int left = ((HandledScreenAccessor) inventory).cakebook$x();
			if (button == null) {
				PROBLEMS.add("no book button in the inventory");
			} else {
				CakeBook.LOGGER.info("CakeBook self-test: inventory button at {},{}; inventory left {}", button.getX(),
						button.getY(), left);
				if (button.getX() + button.getWidth() > left) {
					PROBLEMS.add("inventory button is not left of the inventory");
				}
			}
		} else if (ticks == 40 + 3 * STEP) {
			PauseScreen pause = new PauseScreen(true);
			client.setScreen(pause);
			AbstractWidget button = bookButton(pause);
			AbstractWidget back = null;
			for (GuiEventListener child : pause.children()) {
				if (child instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t
						&& t.getKey().equals("menu.returnToGame")) {
					back = w;
				}
			}
			if (button == null || back == null) {
				PROBLEMS.add("no book button in the pause menu");
			} else {
				CakeBook.LOGGER.info("CakeBook self-test: pause button at {},{}; Back to Game at {},{}", button.getX(),
						button.getY(), back.getX(), back.getY());
			}
		} else if (ticks == 40 + 4 * STEP) {
			client.setScreen(null);
			for (String problem : PROBLEMS) {
				CakeBook.LOGGER.error("CakeBook self-test FAILED: {}", problem);
			}
			CakeBook.LOGGER.info("CakeBook self-test done ({} problems)", PROBLEMS.size());
			ticks = Integer.MAX_VALUE;
		}
	}

	private static String step() {
		int s = ticks < 40 ? 0 : Math.min(4, (ticks - 40) / STEP + 1);
		return switch (s) {
			case 1 -> "book";
			case 2 -> "note";
			case 3 -> "inventory";
			case 4 -> "pause";
			default -> "start";
		};
	}

	private static AbstractWidget bookButton(Screen screen) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof MenuButtons.BookButton b) {
				return b;
			}
		}
		return null;
	}
}
