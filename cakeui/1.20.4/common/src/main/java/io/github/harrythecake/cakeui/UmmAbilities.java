package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.api.Movable;
import io.github.harrythecake.cakeui.edit.UmmAbilityBar;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * The Unofficial Monumenta Mod's ability bar as a HUD editor {@link Movable} (UMM is Fabric 1.20.4 only).
 * UMM is GPL-3.0: none of its code is here. CakeUI only reads and writes UMM's public config fields by
 * reflection (no dependency, nothing happens without UMM): {@code UnofficialMonumentaModClient.options}
 * ({@code abilitiesDisplay_position}, an {@code ElementPosition}; {@code abilitiesDisplay_iconSize}),
 * {@code AbilitiesHud.INSTANCE.getDimension()} for the box, and {@code saveConfig()} on Done. Position
 * math: {@link UmmAbilityBar}. The bar is in the editor only while UMM shows it (abilities known, i.e.
 * on Monumenta with a class). If UMM's names ever change, it logs once and leaves the bar out.
 */
final class UmmAbilities implements Movable {
	static final String OWNER = "unofficial-monumenta-mod";
	private static final String UMM = "ch.njol.unofficialmonumentamod.";

	private final Field options;
	private final Field handler;
	private final Field abilityData;
	private final Field enabled;
	private final Field position;
	private final Field iconSize;
	private final Field iconGap;
	private final Field horizontal;
	private final Field relX;
	private final Field relY;
	private final Field absX;
	private final Field absY;
	private final Field alignX;
	private final Field alignY;
	private final Field hudInstance;
	private final Method dimension;
	@Nullable
	private Object hud;
	private final Method saveConfig;
	private boolean broken;

	private UmmAbilities(ClassLoader loader) throws ReflectiveOperationException {
		Class<?> client = Class.forName(UMM + "UnofficialMonumentaModClient", false, loader);
		options = client.getField("options");
		handler = client.getField("abilityHandler");
		saveConfig = client.getMethod("saveConfig");
		abilityData = Class.forName(UMM + "AbilityHandler", false, loader).getField("abilityData");
		Class<?> opts = Class.forName(UMM + "options.Options", false, loader);
		enabled = opts.getField("abilitiesDisplay_enabled");
		position = opts.getField("abilitiesDisplay_position");
		iconSize = opts.getField("abilitiesDisplay_iconSize");
		iconGap = opts.getField("abilitiesDisplay_iconGap");
		horizontal = opts.getField("abilitiesDisplay_horizontal");
		Class<?> pos = position.getType();
		relX = pos.getField("offsetXRelative");
		relY = pos.getField("offsetYRelative");
		absX = pos.getField("offsetXAbsolute");
		absY = pos.getField("offsetYAbsolute");
		alignX = pos.getField("alignX");
		alignY = pos.getField("alignY");
		Class<?> hudClass = Class.forName(UMM + "hud.AbilitiesHud", false, loader);
		hudInstance = hudClass.getField("INSTANCE"); // read on first use: UMM sets it up in its own time
		dimension = hudClass.getMethod("getDimension");
	}

	/** Adds the bar to the HUD editor when UMM is installed (after the plugins, render thread). */
	static void register() {
		ClassLoader loader = UmmAbilities.class.getClassLoader();
		try {
			Class.forName(UMM + "UnofficialMonumentaModClient", false, loader);
		} catch (ClassNotFoundException | LinkageError e) {
			return; // no UMM
		}
		try {
			UmmAbilities bar = new UmmAbilities(loader);
			CakeUIApi.get().addMovables(OWNER, () -> bar.broken ? List.of() : List.of(bar));
			CakeUI.LOGGER.info("CakeUI: Unofficial Monumenta Mod found, its ability bar can be moved in the HUD editor");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			CakeUI.LOGGER.warn("CakeUI: this Unofficial Monumenta Mod version isn't known; its ability bar stays out of the HUD editor ({})", e.toString());
		}
	}

	@Override
	public String id() {
		return "abilities";
	}

	@Override
	public Component label() {
		return Component.translatable("cakeui.movable.ummAbilities");
	}

	@Override
	@Nullable
	public double[] box(int screenWidth, int screenHeight) {
		try {
			Object o = options.get(null);
			Object h = handler.get(null);
			if (o == null || h == null || !enabled.getBoolean(o) || ((List<?>) abilityData.get(h)).isEmpty()) {
				return null; // UMM doesn't draw it now
			}
			if (hud == null) {
				hud = hudInstance.get(null);
			}
			java.awt.Rectangle r = hud == null ? null : (java.awt.Rectangle) dimension.invoke(hud);
			if (r == null) {
				return null;
			}
			return r.width <= 0 || r.height <= 0 ? null : new double[] {r.x, r.y, r.width, r.height};
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
			return null;
		}
	}

	@Override
	public void place(double x, double y, double width, double height, int screenWidth, int screenHeight) {
		double[] now = box(screenWidth, screenHeight);
		if (now == null) {
			return;
		}
		try {
			Object o = options.get(null);
			int icon = iconSize.getInt(o);
			int gap = iconGap.getInt(o);
			boolean horiz = horizontal.getBoolean(o);
			int n = UmmAbilityBar.count(now[2], now[3], icon, gap, horiz);
			int newIcon = UmmAbilityBar.iconSize(icon, n, gap, horiz, width, height);
			if (newIcon != icon) {
				iconSize.setInt(o, newIcon);
			}
			int[] size = UmmAbilityBar.size(n, newIcon, gap, horiz);
			double[] a = UmmAbilityBar.anchor(x, y, size[0], size[1], screenWidth, screenHeight);
			Object p = position.get(o);
			relX.setFloat(p, (float) a[0]);
			relY.setFloat(p, (float) a[1]);
			alignX.setFloat(p, (float) a[0]);
			alignY.setFloat(p, (float) a[1]);
			absX.setInt(p, (int) a[2]);
			absY.setInt(p, (int) a[3]);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	@Override
	public boolean resizable() {
		return true; // the size is UMM's icon size
	}

	@Override
	public void save() {
		try {
			saveConfig.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	// ---- self-test only (-Dcakeui.selftest=world with UMM's jar in run/mods) ----

	/** The bar CakeUI registered, or null without UMM. */
	@Nullable
	static UmmAbilities forTest() {
		Movable m = HudMovables.get(HudMovables.PREFIX + OWNER + ":abilities");
		return m instanceof UmmAbilities u ? u : null;
	}

	/** Three made-up abilities, as UMM would get from Monumenta (single player sends none). */
	void addTestAbilities() throws ReflectiveOperationException {
		ClassLoader loader = UmmAbilities.class.getClassLoader();
		Class<?> packetInfo = Class.forName(UMM + "ChannelHandler$ClassUpdatePacket$AbilityInfo", true, loader);
		Class<?> info = Class.forName(UMM + "AbilityHandler$AbilityInfo", true, loader);
		@SuppressWarnings("unchecked")
		List<Object> data = (List<Object>) abilityData.get(handler.get(null));
		for (int i = 0; i < 3; i++) {
			var ctor = packetInfo.getDeclaredConstructor();
			ctor.setAccessible(true);
			Object p = ctor.newInstance();
			setField(packetInfo, p, "name", "Self-test " + i);
			setField(packetInfo, p, "className", "selftest");
			setField(packetInfo, p, "initialCooldown", 200);
			setField(packetInfo, p, "remainingCooldown", 60 * i);
			data.add(info.getConstructor(packetInfo).newInstance(p));
		}
	}

	void clearTestAbilities() throws ReflectiveOperationException {
		((List<?>) abilityData.get(handler.get(null))).clear();
	}

	int iconSizeForTest() throws ReflectiveOperationException {
		return iconSize.getInt(options.get(null));
	}

	/** {relX, relY, absX, absY, iconSize, alignX, alignY}. */
	double[] stateForTest() throws ReflectiveOperationException {
		Object o = options.get(null);
		Object p = position.get(o);
		return new double[] {relX.getFloat(p), relY.getFloat(p), absX.getInt(p), absY.getInt(p), iconSize.getInt(o), alignX.getFloat(p), alignY.getFloat(p)};
	}

	void restoreForTest(double[] s) throws ReflectiveOperationException {
		Object o = options.get(null);
		Object p = position.get(o);
		relX.setFloat(p, (float) s[0]);
		relY.setFloat(p, (float) s[1]);
		alignX.setFloat(p, (float) s[5]);
		alignY.setFloat(p, (float) s[6]);
		absX.setInt(p, (int) s[2]);
		absY.setInt(p, (int) s[3]);
		iconSize.setInt(o, (int) s[4]);
		save();
	}

	private static void setField(Class<?> c, Object o, String name, Object value) throws ReflectiveOperationException {
		Field f = c.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, value);
	}

	private void fail(Throwable e) {
		if (!broken) {
			broken = true;
			CakeUI.LOGGER.error("CakeUI: moving the Unofficial Monumenta Mod's ability bar failed; it is left out of the HUD editor", e);
		}
	}
}
