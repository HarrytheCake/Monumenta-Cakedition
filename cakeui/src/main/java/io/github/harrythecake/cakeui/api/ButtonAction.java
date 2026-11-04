package io.github.harrythecake.cakeui.api;

import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * A button action a mod adds, used in layouts as "action": "mymod:open_map" or
 * { "mymod:open_map": "argument" }. CakeUI's own actions never send chat or commands; an action a
 * mod registers is that mod's responsibility.
 */
@FunctionalInterface
public interface ButtonAction {
	/**
	 * Runs on the render thread when the button is pressed.
	 *
	 * @param argument the text after the action name in the layout, or null
	 * @param screen   the screen the button is on, or null on the HUD
	 */
	void run(@Nullable String argument, @Nullable Screen screen);
}
