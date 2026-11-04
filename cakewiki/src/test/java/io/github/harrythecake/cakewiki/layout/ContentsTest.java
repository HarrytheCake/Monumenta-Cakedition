package io.github.harrythecake.cakewiki.layout;

import io.github.harrythecake.cakewiki.net.NavItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentsTest {
	private static NavItem page(String key) {
		return new NavItem(key, key, false, List.of());
	}

	private final NavItem deep = new NavItem("Deep", null, true, List.of(page("D1")));
	private final NavItem basics = new NavItem("Basics", null, true, List.of(page("B1"), deep));
	private final NavItem quests = new NavItem("Quests", "Quest List", true, List.of(page("Q1")));
	private final NavItem heading = new NavItem("Contents", null, false, List.of(basics, page("Classes"), quests));

	private static List<String> texts(Contents c) {
		return c.rows().stream().map(r -> "  ".repeat(r.depth()) + r.item().text()).toList();
	}

	@Test
	void headingsAreOpenAndGroupsStartClosed() {
		Contents c = new Contents(List.of(heading));
		assertEquals(List.of("Contents", "  Basics", "  Classes", "  Quests"), texts(c));
		assertTrue(c.rows().get(0).open());
		assertFalse(c.rows().get(1).open());
	}

	@Test
	void revealOpensTheWayToThePage() {
		Contents c = new Contents(List.of(heading));
		assertTrue(c.reveal("D1"));
		assertEquals(List.of("Contents", "  Basics", "    B1", "    Deep", "      D1", "  Classes", "  Quests"), texts(c));
		assertFalse(c.isOpen(quests));
		assertFalse(c.reveal("Nowhere"));
		assertTrue(c.reveal("Quest List"), "a group that is a page itself");
		assertFalse(c.isOpen(quests), "it is found, not opened: its children aren't the way to it");
	}

	@Test
	void toggleOpensAndClosesButHeadingsStayOpen() {
		Contents c = new Contents(List.of(heading));
		c.toggle(quests);
		assertTrue(texts(c).contains("    Q1"));
		c.toggle(quests);
		assertFalse(texts(c).contains("    Q1"));
		c.toggle(heading);
		assertTrue(c.isOpen(heading));
	}
}
