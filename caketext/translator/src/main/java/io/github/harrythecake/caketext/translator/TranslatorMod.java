package io.github.harrythecake.caketext.translator;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.module.Modules;
import io.github.harrythecake.caketext.core.template.PlayerNames;
import io.github.harrythecake.caketext.translator.mixin.ScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** CakeText Translator: wires the translation service into chat, tooltips, screens and the HUD mixins. */
public final class TranslatorMod implements ClientModInitializer {
	/** Tooltips are translated after every other listener (the collector runs in the default phase). */
	private static final Identifier TOOLTIP_PHASE = new Identifier("caketext_translator", "translate");

	private static TranslationService service;
	private int ticks;

	public static TranslationService service() {
		return service;
	}

	/** For the mixins: the decorated translation, or {@code text} unchanged. Never throws. */
	public static Text translate(Text text, String surface) {
		return translate(text, surface, null);
	}

	public static Text translate(Text text, String surface, @Nullable Vec3d pos) {
		if (service == null || text == null) {
			return text;
		}
		try {
			Text t = service.decorated(text, surface, pos);
			return t != null ? t : text;
		} catch (RuntimeException e) {
			CakeText.LOG.warn("Translation failed on {}", surface, e);
			return text;
		}
	}

	/** For the chat mixin: the line as it should appear in chat. Never throws. */
	public static Text chat(Text message, boolean signed) {
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

	@Override
	public void onInitializeClient() {
		service = new TranslationService(TranslatorConfig.load());
		Modules.active();
		service.reload();
		service.models().loadIfInstalled();

		// chat lines are handled where they enter the chat box (ChatHudMixin); the action bar is here
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
				overlay ? translate(message, Surfaces.ACTION_BAR) : message);

		ItemTooltipCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, TOOLTIP_PHASE);
		ItemTooltipCallback.EVENT.register(TOOLTIP_PHASE, (stack, context, lines) -> {
			try {
				service.translateTooltip(stack, lines);
			} catch (RuntimeException e) {
				CakeText.LOG.warn("Tooltip translation failed", e);
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			Text title = screen.getTitle();
			// vanilla and mod screens use translation keys and are already in the player's language
			if (title == null || title.getContent() instanceof TranslatableTextContent) {
				return;
			}
			Text t = translate(title, Surfaces.SCREEN_TITLE);
			if (t != title) {
				((ScreenAccessor) screen).caketext$setTitle(t);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks % 20 == 0) {
				PlayerNames.refresh(client);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> PlayerNames.refresh(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> service.save());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> service.save());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> TranslatorCommands.register(dispatcher));
	}
}
