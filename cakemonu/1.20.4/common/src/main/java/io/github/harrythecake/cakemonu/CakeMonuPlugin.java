package io.github.harrythecake.cakemonu;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.api.CakeUIPlugin;
import io.github.harrythecake.cakeui.api.Movable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * CakeMonu's hook into CakeUI (optional; the {@code cakeui} entrypoint, so only CakeUI loads this class): every
 * ability icon with its own place is a movable, resizable box in CakeUI's HUD editor. UMM's bar itself stays
 * CakeUI's own "UMM ability bar" movable.
 */
public final class CakeMonuPlugin implements CakeUIPlugin {
	@Override
	public void register(CakeUIApi api) {
		try {
			api.addMovables(CakeMonu.MOD_ID, CakeMonuPlugin::movables);
			CakeUIBridge.editor = true;
		} catch (NoSuchMethodError e) {
			CakeMonu.LOG.info("CakeMonu: CakeUI is older than 0.3.0 (no HUD editor movables); placed ability icons can't be moved in game");
		}
	}

	/** Asked by the editor whenever it needs them: the current class's placed icons. */
	static List<Movable> movables() {
		List<Movable> list = new ArrayList<>();
		if (CakeMonu.source().state() != AbilitySource.State.READY) {
			return list;
		}
		for (AbilitySource.Ability a : CakeMonu.source().current()) {
			if (CakeMonu.layout().has(a.id())) {
				list.add(new IconMovable(a.id(), Component.translatable("cakemonu.movable.ability", a.name())));
			}
		}
		return list;
	}

	/** Self-test: moves and resizes a placed icon through its movable, as CakeUI's HUD editor does, then Done. */
	public static boolean testMove(String abilityId, double x, double y, double size, int screenWidth, int screenHeight) {
		for (Movable m : movables()) {
			if (m.id().equals("ability/" + abilityId)) {
				m.place(x, y, size, size, screenWidth, screenHeight);
				m.save();
				double[] b = m.box(screenWidth, screenHeight);
				return b != null && Math.abs(b[0] - x) < 1 && Math.abs(b[1] - y) < 1 && b[2] == size;
			}
		}
		return false;
	}

	/** One placed ability icon in the HUD editor; CakeMonu keeps its place ({@link IconLayout}). */
	private record IconMovable(String abilityId, Component label) implements Movable {
		@Override
		public String id() {
			return "ability/" + abilityId;
		}

		@Override
		public double[] box(int screenWidth, int screenHeight) {
			IconPlacement p = CakeMonu.layout().get(abilityId);
			if (p == null || !CakeMonu.source().barEnabled()) {
				return null;
			}
			return p.box(screenWidth, screenHeight, CakeMonu.source().iconSize());
		}

		@Override
		public void place(double x, double y, double width, double height, int screenWidth, int screenHeight) {
			IconPlacement old = CakeMonu.layout().get(abilityId);
			if (old != null) {
				CakeMonu.layout().put(abilityId, IconPlacement.fromBox(x, y, width, screenWidth, screenHeight, old.size, CakeMonu.source().iconSize()));
			}
		}

		@Override
		public boolean resizable() {
			return true;
		}

		@Override
		public void save() {
			CakeMonu.save();
		}

		@Override
		public void cancel() {
			CakeMonu.load();
		}
	}
}
