package io.github.harrythecake.cakemonu.fabric;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies CakeMonu's UMM mixins only when a supported UMM is installed ({@link UmmSupport}). */
public final class UmmMixinPlugin implements IMixinConfigPlugin {
	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = UmmSupport.supported();
		if (!apply && UmmSupport.version() != null) {
			org.slf4j.LoggerFactory.getLogger("CakeMonu").warn(
					"CakeMonu: Unofficial Monumenta Mod {} isn't one CakeMonu was checked against ({}); its ability bar stays as it is",
					UmmSupport.version(), UmmSupport.SUPPORTED);
		}
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return apply;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
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
