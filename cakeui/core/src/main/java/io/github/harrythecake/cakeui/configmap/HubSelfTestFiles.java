package io.github.harrythecake.cakeui.configmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Test files for the dev client's self-test (only with -Dcakeui.selftest): a config map for a made-up file, the file,
 * and pack defaults, written at preLaunch before the pack defaults run, so the self-test can check both the startup run
 * and the hub screen. The self-test removes them again ({@link #remove}).
 */
public final class HubSelfTestFiles {
	public static final String MAP_ID = "cakeui_selftest";
	public static final String FILE = "config/cakeui-selftest/hub.json5";
	public static final String COMMENT = "// a comment that must stay";

	private HubSelfTestFiles() {
	}

	public static void write(Path configDir) throws IOException {
		Path cakeui = configDir.resolve("cakeui");
		Files.createDirectories(cakeui.resolve("configmaps"));
		Files.writeString(cakeui.resolve("configmaps").resolve(MAP_ID + ".json5"), """
				{
				  format: 1, mod: "cakeui", versions: "*", file: "%s", type: "json5", title: "CakeUI self-test",
				  sections: [
				    { title: "Shown", options: [
				      { key: "show.enabled", type: "bool", label: "Enabled" },
				      { key: "show.mode", type: "enum", label: "Mode", values: ["a", "b", { value: "c", label: "Mode C" }] },
				    ] },
				    { title: "Look", options: [
				      { key: "size", type: "int", min: 1, max: 10, label: "Size", default: 2 },
				      { key: "scale", type: "float", min: 0.5, max: 2, step: 0.25, label: "Scale" },
				      { key: "color", type: "colour", label: "Colour" },
				      { key: "name", type: "string", label: "Name" },
				    ] },
				  ],
				}
				""".formatted(FILE), StandardCharsets.UTF_8);
		Path file = configDir.resolve("cakeui-selftest").resolve("hub.json5");  // = FILE under the game folder
		Files.createDirectories(file.getParent());
		Files.writeString(file, """
				{
					%s
					"show": { "enabled": false, "mode": "a" },
					"size": 3,
					"scale": 1.0,
					"color": "#FF8800",
					"name": "player"
				}
				""".formatted(COMMENT), StandardCharsets.UTF_8);
		// size 3 is the pack's old value (history): becomes 5; name "player" is the player's: kept
		Files.writeString(cakeui.resolve("pack-defaults.json5"), """
				{ format: 1, %s: { size: { value: 5, history: [3] }, name: { value: "pack" } } }
				""".formatted(MAP_ID), StandardCharsets.UTF_8);
		Files.deleteIfExists(cakeui.resolve("pack-defaults-applied.json5"));
	}

	public static void remove(Path configDir) throws IOException {
		Path cakeui = configDir.resolve("cakeui");
		Files.deleteIfExists(cakeui.resolve("configmaps").resolve(MAP_ID + ".json5"));
		Files.deleteIfExists(cakeui.resolve("pack-defaults.json5"));
		Files.deleteIfExists(cakeui.resolve("pack-defaults-applied.json5"));
		Path dir = configDir.resolve("cakeui-selftest");
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.walk(dir)) {
				for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(p);
				}
			}
		}
	}
}
