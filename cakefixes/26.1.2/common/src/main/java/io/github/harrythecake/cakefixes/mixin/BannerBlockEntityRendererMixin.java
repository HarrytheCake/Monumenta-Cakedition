package io.github.harrythecake.cakefixes.mixin;

import io.github.harrythecake.cakefixes.BannerDistance;
import io.github.harrythecake.cakefixes.CakeFixes;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * bannerDrawDistance: see {@link BannerDistance}. BannerBlockEntityRenderer doesn't override the interface's default
 * isInRenderDistance, so this adds the override to that one class; BlockEntityRenderDispatcher asks it before every
 * draw. Raw BlockEntityRenderer so the method has the interface's erased signature (no bridge method needed); the
 * jar remap renames it with the interface method (intermediary method_33892).
 */
@SuppressWarnings({"rawtypes", "unused"})
@Mixin(BannerRenderer.class)
abstract class BannerBlockEntityRendererMixin implements BlockEntityRenderer {
	@Override
	public boolean shouldRender(BlockEntity blockEntity, Vec3 cameraPos) {
		return BannerDistance.visible(CakeFixes.config().bannerDrawDistance,
				Vec3.atCenterOf(blockEntity.getBlockPos()).distanceToSqr(cameraPos));
	}
}
