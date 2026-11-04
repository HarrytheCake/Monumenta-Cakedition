package io.github.harrythecake.cakeui.api;

/**
 * A mod's hook into CakeUI. Needs a public no-argument constructor. Register it:
 *
 * <ul>
 * <li>Fabric: as a "cakeui" entrypoint in fabric.mod.json:
 * <pre>
 * "entrypoints": { "cakeui": ["com.example.MyCakeUIPlugin"] }
 * </pre></li>
 * <li>NeoForge: as a Java service, a file
 * {@code META-INF/services/io.github.harrythecake.cakeui.api.CakeUIPlugin} in your jar whose one line
 * is {@code com.example.MyCakeUIPlugin}.</li>
 * </ul>
 *
 * CakeUI calls every plugin once, before it loads the first layout (Fabric: in its client
 * initializer; NeoForge: in client setup, on the render thread's queue), so whatever is registered
 * here is there when layouts are read. Keep CakeUI an optional dependency: this class is only
 * loaded when CakeUI is installed.
 */
public interface CakeUIPlugin {
	void register(CakeUIApi api);
}
