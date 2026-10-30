package io.github.harrythecake.cakeui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import io.github.harrythecake.cakeui.model.Geometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Offscreen buffers for 3D elements. A tilted element's content is drawn flat into a buffer the
 * size of the window, then the buffer is drawn as one quad through a real perspective projection,
 * so text, items and nested elements tilt correctly and the texture isn't warped.
 */
final class Layers {
	private final List<Framebuffer> pool = new ArrayList<>();
	private int depth;

	/** Clears and binds a buffer for one more level of nesting; content then draws at the origin. */
	Framebuffer push() {
		MinecraftClient client = MinecraftClient.getInstance();
		int w = client.getWindow().getFramebufferWidth();
		int h = client.getWindow().getFramebufferHeight();
		if (pool.size() <= depth) {
			pool.add(new SimpleFramebuffer(w, h, true, MinecraftClient.IS_SYSTEM_MAC));
		}
		Framebuffer fb = pool.get(depth++);
		if (fb.textureWidth != w || fb.textureHeight != h) {
			fb.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
		}
		fb.setClearColor(0, 0, 0, 0);
		fb.clear(MinecraftClient.IS_SYSTEM_MAC); // leaves no buffer bound
		fb.beginWrite(true);
		return fb;
	}

	/** Binds the enclosing target again: the parent buffer, or the game's main framebuffer. */
	void pop() {
		depth--;
		bindCurrent();
	}

	void bindCurrent() {
		if (depth > 0) {
			pool.get(depth - 1).beginWrite(true);
		} else {
			MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
		}
	}

	/**
	 * Draws a buffer's top-left w×h GUI pixels as the quad `model` maps it to, seen by the camera
	 * (d, px, py) on a screen of sw×sh GUI pixels. The buffer holds premultiplied colour.
	 */
	void composite(Framebuffer fb, Geometry.Mat model, double w, double h, double sw, double sh, double d, double px, double py, double opacity) {
		Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorter savedSorting = RenderSystem.getVertexSorting();
		Matrix4f projection = new Matrix4f().set(Geometry.projection(sw, sh, d, px, py));
		RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);
		MatrixStack modelView = RenderSystem.getModelViewStack();
		modelView.push();
		modelView.loadIdentity();
		RenderSystem.applyModelViewMatrix();

		RenderSystem.setShaderTexture(0, fb.getColorAttachment());
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();

		float u = (float) (w / sw);
		float v = (float) (1 - h / sh);
		float a = (float) Math.max(0, Math.min(1, opacity));
		BufferBuilder b = Tessellator.getInstance().getBuffer();
		b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		vertex(b, model, 0, 0, 0, 1, a);
		vertex(b, model, 0, h, 0, v, a);
		vertex(b, model, w, h, u, v, a);
		vertex(b, model, w, 0, u, 1, a);
		BufferRenderer.drawWithGlobalProgram(b.end());

		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.defaultBlendFunc();
		modelView.pop();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
	}

	private static void vertex(BufferBuilder b, Geometry.Mat model, double x, double y, float u, float v, float a) {
		double[] p = model.apply(x, y, 0);
		b.vertex(p[0], p[1], p[2]).texture(u, v).color(a, a, a, a).next();
	}

	void close() {
		for (Framebuffer fb : pool) {
			fb.delete();
		}
		pool.clear();
		depth = 0;
	}
}
