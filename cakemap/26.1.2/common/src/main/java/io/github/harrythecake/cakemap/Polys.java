package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.harrythecake.cakemap.mixin.GuiGraphicsExtractorAccessor;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jetbrains.annotations.Nullable;

/**
 * Triangles in one GUI pipeline (and texture), recorded into the frame's GUI render state with the
 * 2D pose they were made with (26.1: the GUI is drawn at the end of the frame). The GUI pipelines
 * draw quads, so each triangle is a quad with its last corner doubled; corners are put in the same
 * turning order as vanilla's quads so none is culled.
 */
final class Polys implements GuiElementRenderState {
	private final RenderPipeline pipeline;
	private final TextureSetup textureSetup;
	private final Matrix3x2f pose;
	private final boolean textured;
	/** Per triangle: 3 x (x, y, u, v) + colour. */
	private float[] data = new float[13 * 16];
	private int count;
	private float minX = Float.MAX_VALUE;
	private float minY = Float.MAX_VALUE;
	private float maxX = -Float.MAX_VALUE;
	private float maxY = -Float.MAX_VALUE;
	@Nullable
	private ScreenRectangle bounds;

	Polys(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, boolean textured) {
		this.pipeline = pipeline;
		this.textureSetup = textureSetup;
		this.pose = new Matrix3x2f(pose);
		this.textured = textured;
	}

	/** An RGB colour with an alpha of 0..1 as ARGB. */
	static int argb(int rgb, float alpha) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	void triangle(float x1, float y1, float u1, float v1, float x2, float y2, float u2, float v2, float x3, float y3, float u3, float v3, int argb) {
		// vanilla's quads (top left, bottom left, bottom right) have a negative area with y down
		if ((x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1) > 0) {
			float tx = x2, ty = y2, tu = u2, tv = v2;
			x2 = x3;
			y2 = y3;
			u2 = u3;
			v2 = v3;
			x3 = tx;
			y3 = ty;
			u3 = tu;
			v3 = tv;
		}
		if ((count + 1) * 13 > data.length) {
			data = Arrays.copyOf(data, data.length * 2);
		}
		int i = count++ * 13;
		data[i] = x1;
		data[i + 1] = y1;
		data[i + 2] = u1;
		data[i + 3] = v1;
		data[i + 4] = x2;
		data[i + 5] = y2;
		data[i + 6] = u2;
		data[i + 7] = v2;
		data[i + 8] = x3;
		data[i + 9] = y3;
		data[i + 10] = u3;
		data[i + 11] = v3;
		data[i + 12] = Float.intBitsToFloat(argb);
		grow(x1, y1);
		grow(x2, y2);
		grow(x3, y3);
	}

	private void grow(float x, float y) {
		Vector2f v = pose.transformPosition(x, y, new Vector2f());
		minX = Math.min(minX, v.x);
		minY = Math.min(minY, v.y);
		maxX = Math.max(maxX, v.x);
		maxY = Math.max(maxY, v.y);
	}

	void submit(GuiGraphicsExtractor g) {
		if (count == 0) {
			return;
		}
		int x = (int) Math.floor(minX);
		int y = (int) Math.floor(minY);
		bounds = new ScreenRectangle(x, y, Math.max(1, (int) Math.ceil(maxX) - x), Math.max(1, (int) Math.ceil(maxY) - y));
		((GuiGraphicsExtractorAccessor) g).cakemap$renderState().addGuiElement(this);
	}

	@Override
	public void buildVertices(VertexConsumer v) {
		for (int t = 0; t < count; t++) {
			int i = t * 13;
			int c = Float.floatToRawIntBits(data[i + 12]);
			for (int k : new int[] {0, 4, 8, 8}) {
				if (textured) {
					v.addVertexWith2DPose(pose, data[i + k], data[i + k + 1]).setUv(data[i + k + 2], data[i + k + 3]).setColor(c);
				} else {
					v.addVertexWith2DPose(pose, data[i + k], data[i + k + 1]).setColor(c);
				}
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
