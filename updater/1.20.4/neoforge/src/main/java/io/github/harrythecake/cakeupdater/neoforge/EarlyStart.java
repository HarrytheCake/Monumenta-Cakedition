package io.github.harrythecake.cakeupdater.neoforge;

import io.github.harrythecake.cakeupdater.CakeUpdaterPreLaunch;
import net.neoforged.fml.loading.FMLPaths;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * NeoForge has no preLaunch entrypoint. Mixin loads this plugin with the mod's mixin config, before any mod is
 * constructed and before Minecraft reads options.txt: the moment Fabric's preLaunch has. It runs the pre-launch tasks
 * (a pending config restore or import, missing defaults); the mod's constructor runs them too in case it didn't.
 * No Minecraft classes may load here.
 */
public final class EarlyStart implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {
		CakeUpdaterPreLaunch.run(FMLPaths.GAMEDIR.get());
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
