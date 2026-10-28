package io.github.harrythecake.cakechat;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CakeChat's message history, in memory only (player chat is personal data, so it is never written to disk).
 *
 * <p>Vanilla's {@code ChatHud} stays the way in: every line still enters through its {@code addMessage}, after other
 * mods (CakeText Translator) have changed it, and vanilla keeps its own 100 lines. Vanilla and mods change a line in
 * place by {@code messages.set(i, …)} followed by {@code refresh()} (the server deleting a message, CakeText swapping
 * in a translation); {@link #onRefresh} spots those through a shadow copy of vanilla's list and mirrors them here.
 */
public final class ChatStore {
	/** Oldest first. */
	private final List<ChatEntry> entries = new ArrayList<>();
	/** Vanilla's {@code messages} as of the last sync, and which entry each of its lines belongs to. */
	private List<ChatHudLine> shadow = List.of();
	private final Map<ChatHudLine, ChatEntry> byVanillaLine = new IdentityHashMap<>();
	private final ChatView view;

	ChatStore(ChatView view) {
		this.view = view;
	}

	public List<ChatEntry> entries() {
		return entries;
	}

	/** A line vanilla just stored as {@code vanillaMessages.get(0)}. */
	public void onAdded(List<ChatHudLine> vanillaMessages) {
		if (vanillaMessages.isEmpty()) {
			return;
		}
		ChatHudLine line = vanillaMessages.get(0);
		if (byVanillaLine.containsKey(line)) {
			return;
		}
		ChatConfig config = CakeChat.config();
		long now = System.currentTimeMillis();
		String key = key(line.content(), config.compact.compareStyle);

		int count = 1;
		int dup = Compactor.findDuplicate(Lists.reverse(entries), key, now, config.compact);
		if (dup >= 0) {
			ChatEntry old = entries.remove(entries.size() - 1 - dup);
			view.removed(old);
			count = old.count + 1;
		}
		ChatEntry entry = new ChatEntry(line.content(), line.signature(), line.indicator(), line.creationTick(), now, key, count);
		entries.add(entry);
		view.added(entry);

		int max = config.history.maxMessages;
		while (max > 0 && entries.size() > max) {
			view.removed(entries.remove(0));
		}
		sync(vanillaMessages);
		byVanillaLine.put(line, entry);
	}

	/** Vanilla rebuilt its visible lines; mirror lines it replaced in place since the last sync. */
	public void onRefresh(List<ChatHudLine> vanillaMessages) {
		if (vanillaMessages.size() == shadow.size()) {
			for (int i = 0; i < vanillaMessages.size(); i++) {
				ChatHudLine now = vanillaMessages.get(i);
				ChatHudLine before = shadow.get(i);
				if (now == before) {
					continue;
				}
				ChatEntry entry = byVanillaLine.get(before);
				if (entry != null) {
					entry.content = now.content();
					entry.signature = now.signature();
					byVanillaLine.put(now, entry);
					view.changed(entry);
				}
			}
		}
		sync(vanillaMessages);
	}

	/** F3+D, or any other caller of {@code ChatHud.clear}. Leaving a server doesn't get here when chat is kept. */
	public void clear() {
		entries.clear();
		shadow = List.of();
		byVanillaLine.clear();
		view.cleared();
	}

	private void sync(List<ChatHudLine> vanillaMessages) {
		shadow = new ArrayList<>(vanillaMessages);
		Set<ChatHudLine> live = Collections.newSetFromMap(new IdentityHashMap<>());
		live.addAll(vanillaMessages);
		byVanillaLine.keySet().retainAll(live);
	}

	static String key(Text text, boolean withStyle) {
		if (withStyle) {
			try {
				return Text.Serialization.toJsonString(text);
			} catch (RuntimeException e) {
				// a component that doesn't serialize: compare the words only
			}
		}
		return text.getString();
	}
}
