package io.github.harrythecake.caketext.core.strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The in-game exchanger: everything dropped into {@code config/caketext/exchange/} is converted into
 * {@code exchange/converted/} ({@code .json}/{@code .json.gz} → PO, {@code .po}/{@code .pot} → {@code .json}).
 * Both mods run it ({@code /ctcollect exchange}, {@code /cttranslate exchange}).
 */
public final class ExchangeFolder {
	private ExchangeFolder() {
	}

	public static Path dir() {
		return CakeText.dir().resolve("exchange");
	}

	/** Converts the folder; the message says what happened and links to the result. */
	public static Text run() {
		Path in = dir();
		Path out = in.resolve("converted");
		try {
			Files.createDirectories(in);
			List<Path> inputs;
			try (Stream<Path> list = Files.list(in)) {
				inputs = list.filter(Files::isRegularFile).sorted().toList();
			}
			List<Path> written = inputs.isEmpty() ? List.of() : Exchanger.convert(inputs, out);
			if (written.isEmpty()) {
				return link(Text.translatable("caketext.exchange.empty", in.toAbsolutePath().toString()), in);
			}
			return link(Text.translatable("caketext.exchange.done", inputs.size(), written.size()), out);
		} catch (IOException | RuntimeException e) {
			return Text.translatable("caketext.exchange.failed", String.valueOf(e.getMessage())).formatted(Formatting.RED);
		}
	}

	private static Text link(Text text, Path folder) {
		return text.copy().styled(s -> s.withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, folder.toAbsolutePath().toString())));
	}
}
