package io.github.harrythecake.caketext.translator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.GameContext;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.template.ItemFilter;
import io.github.harrythecake.caketext.core.template.PlayerChat;
import io.github.harrythecake.caketext.core.template.TextTemplate;
import io.github.harrythecake.caketext.core.template.TextUtil;
import io.github.harrythecake.caketext.core.template.Translated;
import io.github.harrythecake.caketext.translator.TranslatorConfig.Display;
import io.github.harrythecake.caketext.translator.mixin.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Everything the hooks call: look up a translation, or queue the line for the model, and show it. */
public final class TranslationService {
	/** A translated line, the source it came from, and the line as the server sent it. */
	public record Result(MutableText text, Dictionary.Hit hit, Text original) {
	}

	private final ModelManager models = new ModelManager();
	private final OpusEngine opus = new OpusEngine(models);
	private final ServerEngine server;
	private final MachineTranslator machine;
	private volatile TranslatorConfig config;
	private volatile ItemFilter itemFilter;
	private volatile Dictionary dictionary = new Dictionary();
	/** Chat lines shown untranslated while the model works on them: template → the chat line objects. */
	private final Map<String, List<WeakReference<Text>>> pendingChat = new ConcurrentHashMap<>();

	public TranslationService(TranslatorConfig config) {
		this.config = config;
		this.itemFilter = ItemFilter.of(config.ignoredItems);
		this.server = new ServerEngine(config);
		this.machine = new MachineTranslator(this::engine, this::machineDone);
	}

	/** The engine for lines no string file has: the built-in model or the local server. */
	public TranslationEngine engine() {
		return config.engine == TranslatorConfig.Engine.SERVER ? server : opus;
	}

	/** The local server's state, whether or not it's the active engine. */
	public Text serverStatus() {
		return server.describe();
	}

	/** Asks the local server again now (settings screen). */
	public void recheckServer() {
		server.use(config);
	}

	public TranslatorConfig config() {
		return config;
	}

	public ModelManager models() {
		return models;
	}

	public Dictionary dictionary() {
		return dictionary;
	}

	public MachineTranslator machine() {
		return machine;
	}

	/** (Re)loads the dictionaries for the configured language. */
	public void reload() {
		machine.save();
		Dictionary dict = Dictionary.load(config.language);
		dictionary = dict;
		machine.use(dict, config);
		pendingChat.clear();
		CakeText.isTranslated = dict::hasHuman;
		CakeText.LOG.info("CakeText dictionaries ({}): {} files, {} strings, {} machine", config.language, dict.files(),
				dict.humanCount(), dict.machineCount());
	}

	public void setConfig(TranslatorConfig config) {
		boolean languageChanged = !config.language.equals(this.config.language);
		this.config = config;
		this.itemFilter = ItemFilter.of(config.ignoredItems);
		server.use(config);
		config.save();
		if (languageChanged) {
			reload();
		} else {
			machine.use(dictionary, config);
		}
	}

	// ---- lookup ----

	/** Dictionary lookup (machine cache only when machine translation is on). */
	private @Nullable Result lookup(Text text, TextTemplate t, String surface, @Nullable Vec3d pos) {
		Dictionary.Hit hit = dictionary.lookup(t.template(), () -> GameContext.of(surface, pos));
		if (hit == null || hit.machine() && !config.machine) {
			return null;
		}
		return new Result(t.render(hit.dst()), hit, text);
	}

	/** Item links (item names in chat) stay as they are unless item names are translated. */
	private java.util.Set<Integer> keep(TextTemplate t) {
		return config.translateItemNames ? java.util.Set.of() : t.itemStyles();
	}

	/** The model only knows English; lines already in Chinese (or with no Latin letters) are skipped. */
	private boolean modelCanTake(TextTemplate t) {
		String s = t.original();
		return engine().ready() && s.chars().anyMatch(c -> c < 128 && Character.isLetter(c))
				&& s.codePoints().noneMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
	}

	/**
	 * The translation of {@code text} shown on {@code surface}, or null to keep the original (not translated yet,
	 * nothing to translate, or turned off). Misses are queued for the model when it's installed and enabled.
	 */
	public @Nullable Result translate(Text text, String surface, @Nullable Vec3d pos) {
		TranslatorConfig cfg = config;
		if (text == null || !cfg.enabled || !cfg.surface(surface) || Translated.is(text)) {
			return null;
		}
		TextTemplate t = TextTemplate.of(text);
		if (!t.hasWords()) {
			return null;
		}
		Result r = lookup(text, t, surface, pos);
		if (r == null && cfg.machine && modelCanTake(t)) {
			machine.submit(t.template(), false, keep(t));
		}
		return r;
	}

	// ---- HUD, titles, nameplates, menus ----

	/** Translated HUD text with its marker, or null to keep the original. Marked so the collector skips it. */
	public @Nullable Text decorated(Text text, String surface, @Nullable Vec3d pos) {
		if (config.display == Display.ORIGINAL) {
			return null;
		}
		Result r = translate(text, surface, pos);
		if (r == null) {
			return null;
		}
		MutableText out = r.text();
		if (showIcon(r.hit())) {
			out = Text.empty().append(out).append(" ").append(Markers.icon(r.hit().machine()));
		}
		return Translated.mark(out);
	}

	private boolean showIcon(Dictionary.Hit hit) {
		TranslatorConfig.Markers m = config.markers;
		return m == TranslatorConfig.Markers.ALL || m == TranslatorConfig.Markers.MACHINE && hit.machine();
	}

	// ---- chat ----

	/**
	 * A line going into the chat box ({@code signed}: a signed player message). Player chat (see {@link PlayerChat})
	 * has only its message translated, by the model, and is never saved; everything else is server text.
	 * Returns {@code message} itself when nothing changes, so a later model result can find and replace it.
	 */
	public Text chatLine(Text message, boolean signed) {
		TranslatorConfig cfg = config;
		if (message == null || !cfg.enabled || Translated.is(message)) {
			return message;
		}
		int body = PlayerChat.bodyStart(message.getString());
		if (body >= 0) {
			return cfg.playerChat ? playerChat(message, body) : message;
		}
		if (signed || !cfg.surface(Surfaces.CHAT)) {
			// a signed message we can't split into name and text: leave it alone
			return message;
		}
		TextTemplate t = TextTemplate.of(message);
		if (!t.hasWords()) {
			return message;
		}
		Result r = lookup(message, t, Surfaces.CHAT, null);
		if (r == null) {
			if (cfg.machine && modelCanTake(t) && machine.submit(t.template(), false, keep(t))) {
				waitFor(t.template(), message);
			}
			return message;
		}
		MutableText line = Text.empty();
		if (showIcon(r.hit())) {
			line.append(Markers.hoverIcon(r.hit(), card(r, cfg.chatDisplay))).append(" ");
		}
		return Translated.mark(line.append(present(r, cfg.chatDisplay)));
	}

	private Text playerChat(Text message, int bodyStart) {
		MutableText[] parts = TextUtil.split(message, bodyStart);
		TextTemplate t = TextTemplate.of(parts[1]);
		if (!t.hasWords()) {
			return message;
		}
		Dictionary.Hit hit = dictionary.lookup(t.template(), () -> GameContext.of(Surfaces.PLAYER_CHAT, null));
		if (hit != null && hit.machine()) {
			// the saved model cache is for server text; player chat uses this session's results only
			hit = null;
		}
		if (hit == null) {
			String dst = machine.chatResult(t.template());
			if (dst != null) {
				hit = new Dictionary.Hit(dst, engine().label(), "-", null);
			}
		}
		if (hit == null) {
			if (modelCanTake(t) && machine.submit(t.template(), true, keep(t))) {
				waitFor(t.template(), message);
			}
			return message;
		}
		Result r = new Result(t.render(hit.dst()), hit, parts[1]);
		Display display = config.playerChatDisplay;
		MutableText line = Text.empty();
		if (showIcon(hit)) {
			line.append(Markers.hoverIcon(hit, card(r, display))).append(" ");
		}
		return Translated.mark(line.append(parts[0]).append(present(r, display)));
	}

	private static Text card(Result r, Display display) {
		return display == Display.ORIGINAL ? Markers.card(r.hit(), r.text(), false) : Markers.card(r.hit(), r.original(), true);
	}

	/** The chat text for a display mode, with the hover card on the text itself. */
	private static MutableText present(Result r, Display display) {
		Text card = card(r, display);
		return switch (display) {
			case TRANSLATED -> TextUtil.withHover(r.text(), card);
			case BOTH -> TextUtil.withHover(r.text(), card).append("\n  ")
					.append(TextUtil.recolor(r.original(), Formatting.GRAY));
			case ORIGINAL -> TextUtil.withHover(r.original(), card);
		};
	}

	private void waitFor(String template, Text line) {
		pendingChat.computeIfAbsent(template, k -> java.util.Collections.synchronizedList(new ArrayList<>()))
				.add(new WeakReference<>(line));
	}

	/** Called on the worker thread when the model finished (or gave up on) a template: update chat lines already shown. */
	private void machineDone(String template) {
		List<WeakReference<Text>> refs = pendingChat.remove(template);
		if (refs == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.inGameHud == null) {
				return;
			}
			ChatHudAccessor chat = (ChatHudAccessor) client.inGameHud.getChatHud();
			List<ChatHudLine> messages = chat.caketext$messages();
			boolean changed = false;
			for (WeakReference<Text> ref : List.copyOf(refs)) {
				Text original = ref.get();
				if (original == null) {
					continue;
				}
				for (int i = 0; i < messages.size(); i++) {
					ChatHudLine line = messages.get(i);
					if (line.content() == original) {
						Text translated = chatLine(original, line.signature() != null);
						if (translated != original) {
							messages.set(i, new ChatHudLine(line.creationTick(), translated, line.signature(), line.indicator()));
							changed = true;
						}
					}
				}
			}
			if (changed) {
				chat.caketext$refresh();
			}
		});
	}

	// ---- tooltips ----

	/** Translates tooltip lines (per the display mode) and adds a footer naming the sources. */
	public void translateTooltip(ItemStack stack, List<Text> lines) {
		TranslatorConfig cfg = config;
		if (lines.isEmpty() || !cfg.enabled || !cfg.surface(Surfaces.TOOLTIP) || itemFilter.matches(stack)) {
			return;
		}
		// the first line is the item name: kept as it is unless item names are turned on
		int first = cfg.translateItemNames ? 0 : 1;
		boolean anyMachine = false;
		boolean anyHuman = false;
		java.util.Set<String> engines = new java.util.LinkedHashSet<>();
		List<Text> out = new ArrayList<>(lines.size() + 4);
		for (int i = 0; i < lines.size(); i++) {
			Text line = lines.get(i);
			Result r = i < first ? null : translate(line, Surfaces.TOOLTIP, null);
			if (r == null) {
				out.add(line);
				continue;
			}
			anyMachine |= r.hit().machine();
			if (r.hit().machine()) {
				engines.add(r.hit().engine());
			}
			anyHuman |= !r.hit().machine();
			switch (cfg.display) {
				case TRANSLATED -> out.add(Translated.mark(r.text()));
				case BOTH -> {
					out.add(Translated.mark(r.text()));
					out.add(Translated.mark(TextUtil.recolor(line, Formatting.DARK_GRAY)));
				}
				case ORIGINAL -> {
					out.add(line);
					out.add(Translated.mark(TextUtil.recolor(r.text(), Formatting.DARK_GRAY)));
				}
			}
		}
		TranslatorConfig.Markers markers = cfg.markers;
		if (cfg.tooltipFooter && (anyMachine || anyHuman) && markers != TranslatorConfig.Markers.OFF
				&& !(markers == TranslatorConfig.Markers.MACHINE && !anyMachine)) {
			MutableText footer = Text.empty();
			if (anyHuman && markers == TranslatorConfig.Markers.ALL) {
				footer.append(Markers.icon(false)).append(" ")
						.append(Text.translatable("caketext_translator.marker.dictionary").formatted(Formatting.DARK_GRAY));
			}
			if (anyMachine) {
				if (!footer.getSiblings().isEmpty()) {
					footer.append("  ");
				}
				footer.append(Markers.icon(true)).append(" ").append(Text.literal(String.join(", ", engines)).formatted(Formatting.DARK_GRAY));
			}
			out.add(Translated.mark(footer));
		}
		lines.clear();
		lines.addAll(out);
	}

	/** Saves the machine cache (on exit). */
	public void save() {
		machine.save();
	}
}
