package io.github.harrythecake.cakechat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactorTest {
	private record Line(String compactKey, long arrivedMillis) implements Compactor.Keyed {
	}

	private static ChatConfig.Compact latest(int searchLines) {
		ChatConfig.Compact c = new ChatConfig.Compact();
		c.searchLines = searchLines;
		return c;
	}

	private static ChatConfig.Compact within(int seconds) {
		ChatConfig.Compact c = new ChatConfig.Compact();
		c.mode = ChatConfig.Compact.Mode.WITHIN_SECONDS;
		c.seconds = seconds;
		return c;
	}

	@Test
	void latestOnlyChecksTheNewestLines() {
		List<Line> newestFirst = List.of(new Line("b", 3), new Line("a", 2), new Line("x", 1));
		assertEquals(-1, Compactor.findDuplicate(newestFirst, "a", 4, latest(1)));
		assertEquals(1, Compactor.findDuplicate(newestFirst, "a", 4, latest(2)));
		assertEquals(0, Compactor.findDuplicate(newestFirst, "b", 4, latest(1)));
		assertEquals(2, Compactor.findDuplicate(newestFirst, "x", 4, latest(50)));
	}

	@Test
	void withinSecondsStopsAtOlderLines() {
		List<Line> newestFirst = List.of(new Line("b", 95_000), new Line("a", 70_000), new Line("c", 10_000));
		assertEquals(1, Compactor.findDuplicate(newestFirst, "a", 100_000, within(30)));
		assertEquals(-1, Compactor.findDuplicate(newestFirst, "a", 100_000, within(20)));
		assertEquals(-1, Compactor.findDuplicate(newestFirst, "c", 100_000, within(60)));
	}

	@Test
	void disabledOrBlankNeverMatches() {
		List<Line> newestFirst = List.of(new Line("a", 1), new Line("", 1));
		ChatConfig.Compact off = latest(5);
		off.enabled = false;
		assertEquals(-1, Compactor.findDuplicate(newestFirst, "a", 2, off));
		// empty lines (spacers in server messages) are never merged
		assertEquals(-1, Compactor.findDuplicate(newestFirst, "", 2, latest(5)));
		assertEquals(-1, Compactor.findDuplicate(List.of(), "a", 2, latest(5)));
	}

	@Test
	void counterFormat() {
		assertEquals(" (x3)", Compactor.counter(" (x{count})", 3));
		assertEquals(" [12]", Compactor.counter(" [{count}]", 12));
	}
}
