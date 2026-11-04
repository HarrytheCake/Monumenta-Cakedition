package io.github.harrythecake.cakeui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.harrythecake.cakeui.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jetbrains.annotations.Nullable;

/**
 * Float-precision quads for rects and images. Since 1.21.6 the GUI records render states and draws
 * them at the end of the frame, so each rect (or each image's batch of quads) is one
 * GuiElementRenderState with its own copy of the 2D pose.
 */
final class Paint {
	private Paint() {
	}

	static int withAlpha(int argb, double opacity) {
		int a = (int) Math.round(((argb >>> 24) & 255) * Math.max(0, Math.min(1, opacity)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** A filled rect; with c2 != c1 a gradient (vertical: c1 top → c2 bottom; else left → right). */
	static void rect(GuiGraphicsExtractor g, Matrix3x2f m, float x1, float y1, float x2, float y2, int c1, int c2, boolean vertical) {
		if (((c1 >>> 24) | (c2 >>> 24)) == 0 || x2 <= x1 || y2 <= y1) {
			return;
		}
		Quads q = new Quads(RenderPipelines.GUI, TextureSetup.noTexture(), m, false);
		q.add(x1, y1, x2, y2, 0, 0, 0, 0, c1, vertical ? c1 : c2, c2, vertical ? c2 : c1);
		q.submit(g);
	}

	static void outline(GuiGraphicsExtractor g, Matrix3x2f m, float x1, float y1, float x2, float y2, float t, int c) {
		rect(g, m, x1, y1, x2, y1 + t, c, c, true);
		rect(g, m, x1, y2 - t, x2, y2, c, c, true);
		rect(g, m, x1, y1 + t, x1 + t, y2 - t, c, c, true);
		rect(g, m, x2 - t, y1 + t, x2, y2 - t, c, c, true);
	}

	/** Starts a batch of textured quads for one texture (linear or nearest sampling). */
	static Quads beginTextured(AbstractTexture texture, Matrix3x2f m, boolean linear) {
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
		return new Quads(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(texture.getTextureView(), sampler), m, true);
	}

	static void texQuad(Quads b, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int argb) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		b.add(x1, y1, x2, y2, u1, v1, u2, v2, argb, argb, argb, argb);
	}

	static void end(GuiGraphicsExtractor g, Quads b) {
		b.submit(g);
	}

	/** Quads in one pipeline and texture, drawn with the pose they were recorded with. */
	static final class Quads implements GuiElementRenderState {
		private final RenderPipeline pipeline;
		private final TextureSetup textureSetup;
		private final Matrix3x2f pose;
		private final boolean textured;
		private float[] data = new float[16 * 8];
		private int count;
		private float minX = Float.MAX_VALUE;
		private float minY = Float.MAX_VALUE;
		private float maxX = -Float.MAX_VALUE;
		private float maxY = -Float.MAX_VALUE;
		@Nullable
		private ScreenRectangle bounds;

		Quads(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, boolean textured) {
			this.pipeline = pipeline;
			this.textureSetup = textureSetup;
			this.pose = new Matrix3x2f(pose);
			this.textured = textured;
		}

		/** Corners clockwise from the top-left: colours tl, tr, br, bl. */
		void add(float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int tl, int tr, int br, int bl) {
			if ((count + 1) * 12 > data.length) {
				data = java.util.Arrays.copyOf(data, data.length * 2);
			}
			int i = count++ * 12;
			data[i] = x1;
			data[i + 1] = y1;
			data[i + 2] = x2;
			data[i + 3] = y2;
			data[i + 4] = u1;
			data[i + 5] = v1;
			data[i + 6] = u2;
			data[i + 7] = v2;
			data[i + 8] = Float.intBitsToFloat(tl);
			data[i + 9] = Float.intBitsToFloat(tr);
			data[i + 10] = Float.intBitsToFloat(br);
			data[i + 11] = Float.intBitsToFloat(bl);
			for (float[] p : new float[][] {{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}}) {
				Vector2f v = pose.transformPosition(p[0], p[1], new Vector2f());
				minX = Math.min(minX, v.x);
				minY = Math.min(minY, v.y);
				maxX = Math.max(maxX, v.x);
				maxY = Math.max(maxY, v.y);
			}
		}

		void submit(GuiGraphicsExtractor g) {
			if (count == 0) {
				return;
			}
			int x = (int) Math.floor(minX);
			int y = (int) Math.floor(minY);
			bounds = new ScreenRectangle(x, y, Math.max(1, (int) Math.ceil(maxX) - x), Math.max(1, (int) Math.ceil(maxY) - y));
			((GuiGraphicsAccessor) g).cakeui$renderState().addGuiElement(this);
		}

		@Override
		public void buildVertices(VertexConsumer v) {
			for (int q = 0; q < count; q++) {
				int i = q * 12;
				float x1 = data[i], y1 = data[i + 1], x2 = data[i + 2], y2 = data[i + 3];
				int tl = Float.floatToRawIntBits(data[i + 8]);
				int tr = Float.floatToRawIntBits(data[i + 9]);
				int br = Float.floatToRawIntBits(data[i + 10]);
				int bl = Float.floatToRawIntBits(data[i + 11]);
				if (textured) {
					float u1 = data[i + 4], v1 = data[i + 5], u2 = data[i + 6], v2 = data[i + 7];
					v.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(tl);
					v.addVertexWith2DPose(pose, x1, y2).setUv(u1, v2).setColor(bl);
					v.addVertexWith2DPose(pose, x2, y2).setUv(u2, v2).setColor(br);
					v.addVertexWith2DPose(pose, x2, y1).setUv(u2, v1).setColor(tr);
				} else {
					v.addVertexWith2DPose(pose, x1, y1).setColor(tl);
					v.addVertexWith2DPose(pose, x1, y2).setColor(bl);
					v.addVertexWith2DPose(pose, x2, y2).setColor(br);
					v.addVertexWith2DPose(pose, x2, y1).setColor(tr);
				}
			}
		}

		@Override
		public RenderPipeline pipeline() {
			return pipeline;
		}

		@Override
		public TextureSetup textureSetup() {
			return textureSetup;
		}

		@Override
		@Nullable
		public ScreenRectangle scissorArea() {
			return null;
		}

		@Override
		@Nullable
		public ScreenRectangle bounds() {
			return bounds;
		}
	}

	static void sound(String id) {
		if (id == null || id.isEmpty()) {
			return;
		}
		Identifier ident = Identifier.tryParse(id);
		if (ident != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(ident), 1.0F));
		}
	}
}
