package io.github.harrythecake.cakeui.api;

import java.util.List;

/** A mod's {@link Movable}s, asked again each time (their number may change, e.g. chat windows). */
@FunctionalInterface
public interface MovableSource {
	List<Movable> movables();
}
