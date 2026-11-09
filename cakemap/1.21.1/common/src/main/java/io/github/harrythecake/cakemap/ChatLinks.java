package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.ChatCoords;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Coordinates in chat get a clickable [+] after the message: it runs CakeMap's own client command
 * ({@code /cakemap temp x y z}), which never reaches the server, and adds a temporary waypoint.
 */
public final class ChatLinks {
	private ChatLinks() {
	}

	public static Component modify(Component message, boolean overlay) {
		if (overlay || !CakeMap.settings().chatCoordinates || CakeMap.session() == null) {
			return message;
		}
		String plain = message.getString();
		if (plain.contains("[+]")) {
			return message;
		}
		List<ChatCoords.Found> found = ChatCoords.find(plain);
		if (found.isEmpty()) {
			return message;
		}
		MutableComponent out = message.copy();
		for (ChatCoords.Found f : found.subList(0, Math.min(3, found.size()))) {
			int y = f.y() == null ? 64 : f.y();
			String cmd = "/cakemap temp " + f.x() + " " + y + " " + f.z();
			Component hover = Component.translatable("cakemap.chat.add", f.x() + ", " + y + ", " + f.z());
			out.append(Component.literal(" [+]").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))));
		}
		return out;
	}
}
