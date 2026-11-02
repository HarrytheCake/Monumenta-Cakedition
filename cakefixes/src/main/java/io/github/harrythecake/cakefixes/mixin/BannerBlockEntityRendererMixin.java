package io.github.harrythecake.cakefixes.mixin;

import io.github.harrythecake.cakefixes.BannerDistance;
import io.github.harrythecake.cakefixes.CakeFixes;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;

/**
 * bannerDrawDistance: see {@link BannerDistance}. BannerBlockEntityRenderer doesn't override the interface's default
 * isInRenderDistance, so this adds the override to that one class; BlockEntityRenderDispatcher asks it before every
 * draw. Raw BlockEntityRenderer so the method has the interface's erased signature (no bridge method needed); the
 * jar remap renames it with the interface method (intermediary method_33892).
 */
@SuppressWarnings({"rawtypes", "unused"})
@Mixin(BannerBlockEntityRenderer.class)
abstract class BannerBlockEntityRendererMixin implements BlockEntityRenderer {
	@Override
	public boolean isInRenderDistance(BlockEntity blockEntity, Vec3d cameraPos) {
		return BannerDistance.visible(CakeFixes.config().bannerDrawDistance,
				Vec3d.ofCenter(blockEntity.getPos()).squaredDistanceTo(cameraPos));
	}
}
