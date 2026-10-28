package io.github.harrythecake.cakechat;

import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

/**
 * One message in CakeChat's history. {@code content} can change after it arrived: the server deleting it,
 * or CakeText Translator swapping in a late translation (both show up as a replaced vanilla line).
 */
public final class ChatEntry implements Compactor.Keyed {
	Text content;
	@Nullable MessageSignatureData signature;
	@Nullable final MessageIndicator indicator;
	/** {@code InGameHud} ticks when it arrived (drives the fade, like vanilla's {@code creationTick}). */
	final int tick;
	final long millis;
	final String key;
	/** How many copies arrived; above 1 the line shows the compact counter. */
	final int count;
	/** The text tabs match against ({@link TabRules#routingText}). */
	String text;
	/** The tabs (by {@link TabRules} index) that show it. */
	BitSet tabs = new BitSet();

	ChatEntry(Text content, @Nullable MessageSignatureData signature, @Nullable MessageIndicator indicator,
			int tick, long millis, String key, int count) {
		this.content = content;
		this.signature = signature;
		this.indicator = indicator;
		this.tick = tick;
		this.millis = millis;
		this.key = key;
		this.count = count;
		this.text = TabRules.routingText(content.getString());
	}

	@Override
	public String compactKey() {
		return key;
	}

	@Override
	public long arrivedMillis() {
		return millis;
	}
}
