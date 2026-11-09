package io.github.harrythecake.cakemap.core;

import java.util.Map;

/**
 * Block states as CakeMap stores them: {@code namespace:block[prop=value,…]} with the properties
 * sorted by name, so the scanner and the Xaero importer write the same text for the same state.
 */
public final class BlockStates {
	public static final String WATER = "minecraft:water[level=0]";

	private BlockStates() {
	}

	/** {@code props} must already be sorted by name (a TreeMap). */
	public static String join(String name, Map<String, String> props) {
		if (props.isEmpty()) {
			return name;
		}
		StringBuilder sb = new StringBuilder(name).append('[');
		boolean first = true;
		for (Map.Entry<String, String> e : props.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		return sb.append(']').toString();
	}

	public static String blockName(String state) {
		int b = state.indexOf('[');
		return b < 0 ? state : state.substring(0, b);
	}

	public static boolean isAir(String state) {
		String n = blockName(state);
		return n.equals("minecraft:air") || n.equals("minecraft:cave_air") || n.equals("minecraft:void_air");
	}

	public static boolean isWater(String state) {
		return blockName(state).equals("minecraft:water");
	}
}
