package io.github.harrythecake.cakeui.render;

import io.github.harrythecake.cakeui.mixin.FontStorageAccessor;
import io.github.harrythecake.cakeui.mixin.TextRendererAccessor;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Problems;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TrueTypeFont;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TTF/OTF fonts from a layout's assets. Each font is built per sharpness step (oversample 1, 2, 4,
 * 8, 16) the first time text needs it; characters the font lacks fall back to Minecraft's default
 * font, so Chinese still shows with a Latin-only font.
 */
final class Fonts {
	private static int generation;
	private final Layout layout;
	private final Problems problems;
	private final int id = generation++;
	private final Map<String, TextRenderer> renderers = new HashMap<>();
	private final List<FontStorage> storages = new ArrayList<>();
	private final Set<String> failed = new HashSet<>();
	private FontStorage vanillaDefault;

	Fonts(Layout layout, Problems problems) {
		this.layout = layout;
		this.problems = problems;
	}

	/** Base size (the height text is drawn at with scale 1) of a layout font. */
	double baseSize(String name) {
		Layout.FontSpec spec = layout.fonts.get(name);
		return spec == null ? 9 : spec.size();
	}

	boolean has(String name) {
		return layout.fonts.containsKey(name);
	}

	/** A text renderer for the layout font sharp enough for `pixelScale` screen pixels per font unit. */
	@Nullable
	TextRenderer get(String name, double pixelScale) {
		Layout.FontSpec spec = layout.fonts.get(name);
		if (spec == null || failed.contains(name)) {
			return null;
		}
		checkVanilla();
		int step = 1;
		double want = spec.oversample() > 0 ? spec.oversample() : pixelScale;
		while (step < want && step < 16) {
			step *= 2;
		}
		String key = name + "@" + step;
		TextRenderer r = renderers.get(key);
		if (r != null) {
			return r;
		}
		try {
			FontStorage storage = new FontStorage(MinecraftClient.getInstance().getTextureManager(), new Identifier("cakeui", "font/" + id + "/" + name.toLowerCase().replaceAll("[^a-z0-9_]", "_") + "_" + step));
			List<Font> fonts = new ArrayList<>();
			fonts.add(load(spec, step));
			for (Font f : vanillaFonts()) {
				fonts.add(new Borrowed(f));
			}
			storage.setFonts(fonts);
			storages.add(storage);
			r = new TextRenderer(id -> storage, false);
			renderers.put(key, r);
			return r;
		} catch (Exception e) {
			failed.add(name);
			problems.add("window.json5", 0, "font '" + name + "' could not be loaded: " + e.getMessage());
			return null;
		}
	}

	private Font load(Layout.FontSpec spec, int oversample) throws Exception {
		byte[] bytes = Files.readAllBytes(layout.root.resolve("assets").resolve(spec.file()).normalize());
		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		buffer.put(bytes).flip();
		STBTTFontinfo info = STBTTFontinfo.malloc();
		if (!STBTruetype.stbtt_InitFont(info, buffer)) {
			info.free();
			MemoryUtil.memFree(buffer);
			throw new IllegalArgumentException("not a TTF/OTF font");
		}
		return new TrueTypeFont(buffer, info, (float) spec.size(), oversample, (float) spec.shiftX(), (float) spec.shiftY(), "");
	}

	private List<Font> vanillaFonts() {
		return vanillaDefault == null ? List.of() : ((FontStorageAccessor) vanillaDefault).cakeui$fonts();
	}

	/** Minecraft rebuilds its fonts on a resource reload; ours borrow them, so they are rebuilt too. */
	private void checkVanilla() {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		FontStorage now = ((TextRendererAccessor) tr).cakeui$storage().apply(MinecraftClient.DEFAULT_FONT_ID);
		if (now != vanillaDefault) {
			closeStorages();
			vanillaDefault = now;
		}
	}

	private void closeStorages() {
		for (FontStorage s : storages) {
			s.close();
		}
		storages.clear();
		renderers.clear();
	}

	void close() {
		closeStorages();
	}

	/** One of Minecraft's fonts used as a fallback: our storage must never close it. */
	private record Borrowed(Font font) implements Font {
		@Nullable
		@Override
		public Glyph getGlyph(int codePoint) {
			return font.getGlyph(codePoint);
		}

		@Override
		public IntSet getProvidedGlyphs() {
			return font.getProvidedGlyphs();
		}

		@Override
		public void close() {
		}
	}
}
