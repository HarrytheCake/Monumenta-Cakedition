package io.github.harrythecake.caketext.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Pattern;

import io.github.harrythecake.caketext.core.template.PlayerChat;
import io.github.harrythecake.caketext.core.template.PlayerNames;
import io.github.harrythecake.caketext.core.template.TextUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

class PlayerChatTest {
	private static String body(String line, Pattern names) {
		int i = PlayerChat.bodyStart(line, names);
		return i < 0 ? null : line.substring(i);
	}

	@Test
	void findsWhereThePlayerStartsTalking() {
		PlayerNames.update(List.of("Harry_BC", "Steve"));
		Pattern names = PlayerNames.pattern();
		assertEquals("anyone selling a key?", body("<Harry_BC> anyone selling a key?", names));
		assertEquals("lol", body("[g]  Harry_BC: lol", names));
		assertEquals("brb", body("[Valley-2] Mod Steve » brb", names));
		assertEquals("hi there", body("Steve whispers to you: hi there", names));
		assertEquals("ok", body("From Notch: ok", names));
		assertEquals("hello", body("<Notch> hello", names));
		// Monumenta: <channel> [guild tag] name » text, speakers usually not in the tab list
		Pattern none = null;
		assertEquals("omw arr", body("<lfg> [AB] qwertfox » omw arr", none));
		assertEquals("hi.", body("<g> zxcvbnm » hi.", none));
		assertEquals("wts [Salazar's Greed] 5%", body("<tr> [AbC] Mock_Pl4yer » wts [Salazar's Greed] 5%", none));
		assertEquals("full", body("<lfg> [XYZW] testcrafter » full", none));
	}

	@Test
	void serverMessagesAreNotPlayerChat() {
		Pattern names = Pattern.compile("(?<![A-Za-z0-9_])(?:Harry_BC|Steve)(?![A-Za-z0-9_])");
		assertEquals(-1, PlayerChat.bodyStart("Harry_BC has completed the dungeon in 5 minutes!", names));
		assertEquals(-1, PlayerChat.bodyStart("Welcome to the Valley!", names));
		assertEquals(-1, PlayerChat.bodyStart("[Quest] Aledar: Bring me 5 apples.", names));
		assertEquals(-1, PlayerChat.bodyStart("The guard tells Steve: halt!", names));
	}

	@Test
	void splitKeepsStyles() {
		MutableText line = Text.literal("[g] ").formatted(Formatting.GRAY)
				.append(Text.literal("Steve: ").formatted(Formatting.GOLD))
				.append(Text.literal("hello all").formatted(Formatting.WHITE));
		MutableText[] parts = TextUtil.split(line, "[g] Steve: ".length());
		assertEquals("[g] Steve: ", parts[0].getString());
		assertEquals("hello all", parts[1].getString());
		MutableText[] mid = TextUtil.split(line, 6);
		assertEquals("[g] St", mid[0].getString());
		assertEquals("eve: hello all", mid[1].getString());
	}
}
