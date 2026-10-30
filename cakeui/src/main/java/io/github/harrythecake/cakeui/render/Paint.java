package io.github.harrythecake.cakeui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
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
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder b = Tessellator.getInstance().getBuffer();
		b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		int tl = c1;
		int tr = vertical ? c1 : c2;
		int br = c2;
		int bl = vertical ? c2 : c1;
		b.vertex(m, x1, y1, 0);
		color(b, tl);
		b.next();
		b.vertex(m, x1, y2, 0);
		color(b, bl);
		b.next();
		b.vertex(m, x2, y2, 0);
		color(b, br);
		b.next();
		b.vertex(m, x2, y1, 0);
		color(b, tr);
		b.next();
		BufferRenderer.drawWithGlobalProgram(b.end());
	}

	static void outline(Matrix4f m, float x1, float y1, float x2, float y2, float t, int c) {
		rect(m, x1, y1, x2, y1 + t, c, c, true);
		rect(m, x1, y2 - t, x2, y2, c, c, true);
		rect(m, x1, y1 + t, x1 + t, y2 - t, c, c, true);
		rect(m, x2 - t, y1 + t, x2, y2 - t, c, c, true);
	}

	/** Starts a batch of textured quads for one texture. */
	static BufferBuilder beginTextured(Identifier texture) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
		BufferBuilder b = Tessellator.getInstance().getBuffer();
		b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
		return b;
	}

	static void texQuad(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int argb) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		b.vertex(m, x1, y1, 0);
		color(b, argb);
		b.texture(u1, v1).next();
		b.vertex(m, x1, y2, 0);
		color(b, argb);
		b.texture(u1, v2).next();
		b.vertex(m, x2, y2, 0);
		color(b, argb);
		b.texture(u2, v2).next();
		b.vertex(m, x2, y1, 0);
		color(b, argb);
		b.texture(u2, v1).next();
	}

	static void end(BufferBuilder b) {
		BufferRenderer.drawWithGlobalProgram(b.end());
	}

	static void sound(String id) {
		if (id == null || id.isEmpty()) {
			return;
		}
		Identifier ident = Identifier.tryParse(id);
		if (ident != null) {
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(ident), 1.0F));
		}
	}
}
