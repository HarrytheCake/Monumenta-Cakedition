package io.github.harrythecake.caketext.translator;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.module.Modules;
import io.github.harrythecake.caketext.core.template.PlayerNames;
import io.github.harrythecake.caketext.translator.mixin.ScreenAccessor;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * CakeText Translator: wires the translation service into chat, tooltips, screens and the HUD mixins.
 *
 * <p>Loader-neutral: the Fabric and NeoForge entry classes call {@link #init()} and pass their events to the
 * {@code on…} methods.
 */
public final class TranslatorMod {
	public static final String MOD_ID = "caketext_translator";

	private static TranslationService service;
	private static int ticks;

	private TranslatorMod() {
	}

	public static TranslationService service() {
		return service;
	}

	/** For the mixins: the decorated translation, or {@code text} unchanged. Never throws. */
	public static Component translate(Component text, String surface) {
		return translate(text, surface, null);
	}

	public static Component translate(Component text, String surface, @Nullable Vec3 pos) {
		if (service == null || text == null) {
			return text;
		}
		try {
			Component t = service.decorated(text, surface, pos);
			return t != null ? t : text;
		} catch (RuntimeException e) {
			CakeText.LOG.warn("Translation failed on {}", surface, e);
			return text;
		}
	}

	/** For the chat mixin: the line as it should appear in chat. Never throws. */
	public static Component chat(Component message, boolean signed) {
		if (service == null || message == null) {
			return message;
		}
		try {
			return service.chatLine(message, signed);
		} catch (RuntimeException e) {
			CakeText.LOG.warn("Chat translation failed", e);
			return message;
		}
	}

	/** Client start: settings, dictionary, modules, the model if installed. */
	public static void init() {
		service = new TranslationService(TranslatorConfig.load());
		Modules.active();
		service.reload();
		service.models().loadIfInstalled();
	}

	/**
	 * A game (server) message on its way to the screen. Chat lines are handled where they enter the chat box
	 * (ChatHudMixin); this translates only the action bar ({@code overlay}).
	 */
	public static Component onGameMessage(Component message, boolean overlay) {
		return overlay ? translate(message, Surfaces.ACTION_BAR) : message;
	}

	/** Tooltips are translated after every other listener (the collector reads them first). */
	public static void onTooltip(ItemStack stack, List<Component> lines) {
		if (service == null) {
			return;
		}
		try {
			service.translateTooltip(stack, lines);
		} catch (RuntimeException e) {
			CakeText.LOG.warn("Tooltip translation failed", e);
		}
	}

	/** After a screen's init: menu titles from the server. */
	public static void onScreenInit(Screen screen) {
		Component title = screen.getTitle();
		// vanilla and mod screens use translation keys and are already in the player's language
		if (title == null || title.getContents() instanceof TranslatableContents) {
			return;
		}
		Component t = translate(title, Surfaces.SCREEN_TITLE);
		if (t != title) {
			((ScreenAccessor) screen).caketext$setTitle(t);
		}
	}

	/** End of every client tick. */
	public static void onTick(Minecraft client) {
		if (++ticks % 20 == 0) {
			PlayerNames.refresh(client);
		}
	}

	public static void onJoin(Minecraft client) {
		PlayerNames.refresh(client);
	}

	/** Leaving a server, and quitting: saves the machine cache. */
	public static void onDisconnect() {
		if (service != null) {
			service.save();
		}
	}
}
