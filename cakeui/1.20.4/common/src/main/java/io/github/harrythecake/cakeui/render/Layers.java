package io.github.harrythecake.cakeui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.harrythecake.cakeui.model.Geometry;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Offscreen buffers for 3D elements. A tilted element's content is drawn flat into a buffer the
 * size of the window, then the buffer is drawn as one quad through a real perspective projection,
 * so text, items and nested elements tilt correctly and the texture isn't warped.
 */
final class Layers {
	private final List<RenderTarget> pool = new ArrayList<>();
	private int depth;

	/** Clears and binds a buffer for one more level of nesting; content then draws at the origin. */
	RenderTarget push() {
		Minecraft client = Minecraft.getInstance();
		int w = client.getWindow().getWidth();
		int h = client.getWindow().getHeight();
		if (pool.size() <= depth) {
			pool.add(new TextureTarget(w, h, true, Minecraft.ON_OSX));
		}
		RenderTarget fb = pool.get(depth++);
		if (fb.width != w || fb.height != h) {
			fb.resize(w, h, Minecraft.ON_OSX);
		}
		fb.setClearColor(0, 0, 0, 0);
		fb.clear(Minecraft.ON_OSX); // leaves no buffer bound
		fb.bindWrite(true);
		return fb;
	}

	/** Binds the enclosing target again: the parent buffer, or the game's main framebuffer. */
	void pop() {
		depth--;
		bindCurrent();
	}

	void bindCurrent() {
		if (depth > 0) {
			pool.get(depth - 1).bindWrite(true);
		} else {
			Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
		}
	}

	/**
	 * Draws a buffer's top-left w×h GUI pixels as the quad `model` maps it to, seen by the camera
	 * (d, px, py) on a screen of sw×sh GUI pixels. The buffer holds premultiplied colour.
	 */
	void composite(RenderTarget fb, Geometry.Mat model, double w, double h, double sw, double sh, double d, double px, double py, double opacity) {
		Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorting savedSorting = RenderSystem.getVertexSorting();
		Matrix4f projection = new Matrix4f().set(Geometry.projection(sw, sh, d, px, py));
		RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
		PoseStack modelView = RenderSystem.getModelViewStack();
		modelView.pushPose();
		modelView.setIdentity();
		RenderSystem.applyModelViewMatrix();

		RenderSystem.setShaderTexture(0, fb.getColorTextureId());
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();

		float u = (float) (w / sw);
		float v = (float) (1 - h / sh);
		float a = (float) Math.max(0, Math.min(1, opacity));
		BufferBuilder b = Tesselator.getInstance().getBuilder();
		b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		vertex(b, model, 0, 0, 0, 1, a);
		vertex(b, model, 0, h, 0, v, a);
		vertex(b, model, w, h, u, v, a);
		vertex(b, model, w, 0, u, 1, a);
		BufferUploader.drawWithShader(b.end());

		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.defaultBlendFunc();
		modelView.popPose();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
	}

	private static void vertex(BufferBuilder b, Geometry.Mat model, double x, double y, float u, float v, float a) {
		double[] p = model.apply(x, y, 0);
		b.vertex(p[0], p[1], p[2]).uv(u, v).color(a, a, a, a).endVertex();
	}

	void close() {
		for (RenderTarget fb : pool) {
			fb.destroyBuffers();
		}
		pool.clear();
		depth = 0;
	}
}
