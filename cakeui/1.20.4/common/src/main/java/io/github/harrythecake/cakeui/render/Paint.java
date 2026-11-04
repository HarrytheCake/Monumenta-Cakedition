package io.github.harrythecake.cakeui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix4f;

/** Float-precision quads for rects and images, drawn at once (no batching across elements). */
final class Paint {
	private Paint() {
	}

	static int withAlpha(int argb, double opacity) {
		int a = (int) Math.round(((argb >>> 24) & 255) * Math.max(0, Math.min(1, opacity)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static void color(BufferBuilder b, int argb) {
		b.color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
	}

	/** A filled rect; with c2 != c1 a gradient (vertical: c1 top → c2 bottom; else left → right). */
	static void rect(Matrix4f m, float x1, float y1, float x2, float y2, int c1, int c2, boolean vertical) {
		if (((c1 >>> 24) | (c2 >>> 24)) == 0) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder b = Tesselator.getInstance().getBuilder();
		b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		int tl = c1;
		int tr = vertical ? c1 : c2;
		int br = c2;
		int bl = vertical ? c2 : c1;
		b.vertex(m, x1, y1, 0);
		color(b, tl);
		b.endVertex();
		b.vertex(m, x1, y2, 0);
		color(b, bl);
		b.endVertex();
		b.vertex(m, x2, y2, 0);
		color(b, br);
		b.endVertex();
		b.vertex(m, x2, y1, 0);
		color(b, tr);
		b.endVertex();
		BufferUploader.drawWithShader(b.end());
	}

	static void outline(Matrix4f m, float x1, float y1, float x2, float y2, float t, int c) {
		rect(m, x1, y1, x2, y1 + t, c, c, true);
		rect(m, x1, y2 - t, x2, y2, c, c, true);
		rect(m, x1, y1 + t, x1 + t, y2 - t, c, c, true);
		rect(m, x2 - t, y1 + t, x2, y2 - t, c, c, true);
	}

	/** Starts a batch of textured quads for one texture. */
	static BufferBuilder beginTextured(ResourceLocation texture) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
		BufferBuilder b = Tesselator.getInstance().getBuilder();
		b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		return b;
	}

	static void texQuad(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int argb) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		b.vertex(m, x1, y1, 0);
		color(b, argb);
		b.uv(u1, v1).endVertex();
		b.vertex(m, x1, y2, 0);
		color(b, argb);
		b.uv(u1, v2).endVertex();
		b.vertex(m, x2, y2, 0);
		color(b, argb);
		b.uv(u2, v2).endVertex();
		b.vertex(m, x2, y1, 0);
		color(b, argb);
		b.uv(u2, v1).endVertex();
	}

	static void end(BufferBuilder b) {
		BufferUploader.drawWithShader(b.end());
	}

	static void sound(String id) {
		if (id == null || id.isEmpty()) {
			return;
		}
		ResourceLocation ident = ResourceLocation.tryParse(id);
		if (ident != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(ident), 1.0F));
		}
	}
}
