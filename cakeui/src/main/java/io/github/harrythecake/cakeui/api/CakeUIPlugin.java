package io.github.harrythecake.cakeui.api;

/**
 * A mod's hook into CakeUI. Declare it in fabric.mod.json as a "cakeui" entrypoint:
 *
 * <pre>
 * "entrypoints": { "cakeui": ["com.example.MyCakeUIPlugin"] }
 * </pre>
 *
 * CakeUI calls every plugin once, before it loads the first layout, so whatever is registered
 * here is there when layouts are read. Keep CakeUI an optional dependency: this class is only
 * loaded when CakeUI is installed.
 */
public interface CakeUIPlugin {
	void register(CakeUIApi api);
}
