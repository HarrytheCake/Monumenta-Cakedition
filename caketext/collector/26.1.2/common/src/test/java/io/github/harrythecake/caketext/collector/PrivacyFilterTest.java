package io.github.harrythecake.caketext.collector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.harrythecake.caketext.core.context.Surfaces;
import org.junit.jupiter.api.Test;

class PrivacyFilterTest {
	@Test
	void playerChatIsRejected() {
		for (String chat : new String[] {
				"<{n}> anyone selling a hoard key?",
				"{c1}[Guild] {c2}{n}{c3}: lol",
				"{n} » brb",
				"{c1}[g] {n}> hi",
				"{n} whispers to you: hey",
				"From {n}: meet at sierhaven",
				"To {n}: ok",
				"[Valley-2] {n}: wtb"}) {
			assertTrue(PrivacyFilter.reject(Surfaces.CHAT, chat), chat);
		}
	}

	@Test
	void storedMonumentaChatIsPurged() {
		for (String src : new String[] {
				"{c1}<{c2}lfg{c1}>{c3} {c4}[AB] {c5}qwertfox{c6} {c3}» {c7}omw arr",
				"{c1}<{c2}tr{c1}>{c3} {c4}[AbC] {c5}Mock_Pl{~}yer{c6} {c3}» {c7}wts {c8}[{c9}Salazar's Greed{c8}]{c7} {~}%",
				"{c1}<{c2}g{c1}>{c3} {c4}zxcvbnm{c5} {c3}» {c5}hi.",
				"{c1}[Cakedition] {c2}整合包有新版本（{~} → {~}）{c1} {c3}[点击更新]"}) {
			assertTrue(PrivacyFilter.rejectStored(stored(src)), src);
		}
		for (String src : new String[] {
				"{c1}You are eligible to {c2}/vote {c1}on {~} sites!",
				"{c1}[{c2}{n}{c1} got mail]"}) {
			assertFalse(PrivacyFilter.rejectStored(stored(src)), src);
		}
	}

	private static io.github.harrythecake.caketext.core.strings.StringEntry stored(String src) {
		io.github.harrythecake.caketext.core.strings.StringEntry e = new io.github.harrythecake.caketext.core.strings.StringEntry(src, "");
		e.contexts = java.util.List.of(java.util.Map.of("surface", "chat"));
		return e;
	}

	@Test
	void serverMessagesAreKept() {
		for (String msg : new String[] {
				"{n} has completed the dungeon in {~} minutes!",
				"{n} joined the party.",
				"Welcome to the Valley!",
				"{c1}[Quest] {c2}Talk to the guard",
				"Total Damage: {~}"}) {
			assertFalse(PrivacyFilter.reject(Surfaces.CHAT, msg), msg);
		}
		// only chat-like surfaces are checked: a nameplate "{n}: shop" is an NPC sign, not chat
		assertFalse(PrivacyFilter.reject(Surfaces.NAMEPLATE, "{n}: shop"));
	}
}
