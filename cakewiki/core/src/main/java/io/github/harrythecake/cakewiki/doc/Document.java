package io.github.harrythecake.cakewiki.doc;

import java.util.List;

/** A converted wiki page. {@code title} is the page's display title. */
public record Document(String title, List<Block> blocks) {}
