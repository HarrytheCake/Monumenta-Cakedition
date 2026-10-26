package io.github.harrythecake.caketext.collector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Status lines and actions shared by {@code /ctcollect} and the settings screen. */
public final class CollectorStatus {
	private CollectorStatus() {
	}

	/** "1,234 strings for server.playmonumenta.com (56 new this session)" */
	public static Text summary() {
		Store store = CollectorMod.store();
		String server = store.server();
		if (server == null) {
			return Text.translatable("caketext_collector.status.no_server");
		}
		return Text.translatable("caketext_collector.status.strings", store.size(), server, store.added());
	}

	/** "File: server.playmonumenta.com.json.gz (48 KB)" */
	public static Text fileLine() {
		Path file = CollectorMod.store().currentFile();
		long kb = 0;
		try {
			kb = file != null && Files.exists(file) ? Files.size(file) / 1024 : 0;
		} catch (IOException ignored) {
			// shown as 0
		}
		return Text.translatable("caketext_collector.status.file", file == null ? "-" : file.getFileName().toString(), kb);
	}

	public static void saveNow() {
		CollectorMod.store().flush();
	}

	/** Writes the Crowdin files now; the message links to the folder. */
	public static Text exportNow() {
		CollectorConfig config = CollectorMod.config();
		try {
			CollectorMod.store().flush();
			Path dir = CollectorMod.store().export(config.exportPositions);
			return Text.translatable("caketext_collector.command.exported", dir.getFileName().toString())
					.styled(s -> s.withUnderline(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
							dir.toAbsolutePath().toString())));
		} catch (IOException e) {
			return Text.translatable("caketext_collector.command.export_failed", String.valueOf(e.getMessage()))
					.formatted(Formatting.RED);
		}
	}
}
