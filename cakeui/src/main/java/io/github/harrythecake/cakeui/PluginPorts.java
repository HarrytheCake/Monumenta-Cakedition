package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.PortFiller;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Ports a plugin may fill when the active layout doesn't mention them (ADR 0008): the main menu's
 * and the loading screens' background. One filler per port. With no filler, each check is one
 * null test.
 */
public final class PluginPorts {
	public static final String MAIN_MENU_BACKGROUND = "main_menu/background";
	public static final String LOADING_BACKGROUND = "loading_screen/background";
	/** Every port a plugin may fill. */
	public static final List<String> FILLABLE = List.of(MAIN_MENU_BACKGROUND, LOADING_BACKGROUND);

	private static final Logger LOG = LoggerFactory.getLogger("CakeUI");
	private static final Holder MAIN_MENU = new Holder();
	private static final Holder LOADING = new Holder();

	private PluginPorts() {
	}

	private static final class Holder {
		volatile PortFiller filler;
		volatile String owner;
		volatile boolean failed;
	}

	private static Holder holder(String port) {
		if (MAIN_MENU_BACKGROUND.equals(port)) {
			return MAIN_MENU;
		}
		if (LOADING_BACKGROUND.equals(port)) {
			return LOADING;
		}
		throw new IllegalArgumentException("CakeUI: a plugin can fill only " + FILLABLE + ", not '" + port + "'");
	}

	/** Called by CakeUIApi.fillPort. A second filler for the same port is refused. */
	public static synchronized void register(String port, String owner, PortFiller filler) {
		Holder h = holder(port);
		if (h.filler != null) {
			throw new IllegalStateException("CakeUI: port '" + port + "' is already filled by " + h.owner);
		}
		h.owner = owner;
		h.failed = false;
		h.filler = filler;
	}

	/** Which mod fills the port, or null. */
	@Nullable
	public static String owner(String port) {
		return holder(port).owner;
	}

	/**
	 * Lets the plugin draw the port when the layout doesn't mention it. True = the plugin drew it,
	 * so vanilla must not; false = vanilla draws (no filler, the layout mentions the port, the
	 * filler has nothing to show, or it threw).
	 */
	public static boolean fill(String port, boolean layoutMentionsPort, DrawContext ctx, Screen screen, float alpha) {
		Holder h = holder(port);
		PortFiller f = h.filler;
		if (f == null || layoutMentionsPort) {
			return false;
		}
		try {
			return f.fill(ctx, screen, alpha);
		} catch (RuntimeException | LinkageError e) {
			if (!h.failed) {
				h.failed = true;
				LOG.error("CakeUI: the filler of {} for {} failed; vanilla draws instead", h.owner, port, e);
			}
			return false;
		}
	}

	/** Tests only: forget every filler. */
	static synchronized void clear() {
		for (Holder h : List.of(MAIN_MENU, LOADING)) {
			h.filler = null;
			h.owner = null;
			h.failed = false;
		}
	}
}
