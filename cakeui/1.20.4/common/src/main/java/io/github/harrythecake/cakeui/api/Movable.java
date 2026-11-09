package io.github.harrythecake.cakeui.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Something a mod places on the HUD itself (a chat window, a minimap) that CakeUI's HUD editor can move
 * and maybe resize (API version 3). The mod keeps its own placement: the editor reads {@link #box},
 * calls {@link #place} while the player drags (so it shows at once), and on Done {@link #save}; on
 * Cancel it puts every box back with {@link #place} and calls {@link #cancel}. GUI pixels, render thread.
 */
public interface Movable {
	/** Unique within its mod, e.g. "window1". */
	String id();

	/** What the editor calls it, e.g. "Chat window 1". */
	Component label();

	/** Where it is now: {x, y, width, height}; null while it isn't shown (the editor leaves it out). */
	@Nullable
	double[] box(int screenWidth, int screenHeight);

	/** Put it there (live while dragging). Round and clamp as you like; the editor reads {@link #box} back. */
	void place(double x, double y, double width, double height, int screenWidth, int screenHeight);

	/** True when the player may also change its size (a handle at the bottom-right corner, or scrolling). */
	default boolean resizable() {
		return false;
	}

	/** The editor's Done: write your config. */
	void save();

	/** The editor's Cancel, after every box was put back with {@link #place}. */
	default void cancel() {
	}
}
