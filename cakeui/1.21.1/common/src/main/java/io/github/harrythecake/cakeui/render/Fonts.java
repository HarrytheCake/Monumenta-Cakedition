package io.github.harrythecake.cakeui.render;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import io.github.harrythecake.cakeui.mixin.FontStorageAccessor;
import io.github.harrythecake.cakeui.mixin.TextRendererAccessor;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Problems;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.font.GlyphProvider.Conditional;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.gui.font.FontOption;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;

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
	private final Map<String, Font> renderers = new HashMap<>();
	private final List<FontSet> storages = new ArrayList<>();
	private final Set<String> failed = new HashSet<>();
	private FontSet vanillaDefault;

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
	Font get(String name, double pixelScale) {
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
		Font r = renderers.get(key);
		if (r != null) {
			return r;
		}
		try {
			FontSet storage = new FontSet(Minecraft.getInstance().getTextureManager(), ResourceLocation.fromNamespaceAndPath("cakeui", "font/" + id + "/" + name.toLowerCase().replaceAll("[^a-z0-9_]", "_") + "_" + step));
			List<Conditional> fonts = new ArrayList<>();
			fonts.add(new Conditional(load(spec, step), FontOption.Filter.ALWAYS_PASS));
			for (GlyphProvider f : vanillaFonts()) {
				fonts.add(new Conditional(new Borrowed(f), FontOption.Filter.ALWAYS_PASS));
			}
			storage.reload(fonts, Set.of());
			storages.add(storage);
			r = new Font(id -> storage, false);
			renderers.put(key, r);
			return r;
		} catch (Exception e) {
			failed.add(name);
			problems.add("window.json5", 0, "font '" + name + "' could not be loaded: " + e.getMessage());
			return null;
		}
	}

	/** Like vanilla's TrueTypeGlyphProviderDefinition (FreeType since 1.21), but from the layout's assets. */
	private GlyphProvider load(Layout.FontSpec spec, int oversample) throws Exception {
		byte[] bytes = Files.readAllBytes(layout.root.resolve("assets").resolve(spec.file()).normalize());
		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		buffer.put(bytes).flip();
		FT_Face face = null;
		try {
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer pointer = stack.mallocPointer(1);
					FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), buffer, 0L, pointer), "Initializing font face");
					face = FT_Face.create(pointer.get());
				}
				FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
				return new TrueTypeGlyphProvider(buffer, face, (float) spec.size(), oversample, (float) spec.shiftX(), (float) spec.shiftY(), "");
			}
		} catch (Exception e) {
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				if (face != null) {
					FreeType.FT_Done_Face(face);
				}
			}
			MemoryUtil.memFree(buffer);
			throw new IllegalArgumentException("not a TTF/OTF font (" + e.getMessage() + ")", e);
		}
	}

	private List<GlyphProvider> vanillaFonts() {
		return vanillaDefault == null ? List.of() : ((FontStorageAccessor) vanillaDefault).cakeui$fonts();
	}

	/** Minecraft rebuilds its fonts on a resource reload; ours borrow them, so they are rebuilt too. */
	private void checkVanilla() {
		Font tr = Minecraft.getInstance().font;
		FontSet now = ((TextRendererAccessor) tr).cakeui$storage().apply(Minecraft.DEFAULT_FONT);
		if (now != vanillaDefault) {
			closeStorages();
			vanillaDefault = now;
		}
	}

	private void closeStorages() {
		for (FontSet s : storages) {
			s.close();
		}
		storages.clear();
		renderers.clear();
	}

	void close() {
		closeStorages();
	}

	/** One of Minecraft's fonts used as a fallback: our storage must never close it. */
	private record Borrowed(GlyphProvider font) implements GlyphProvider {
		@Nullable
		@Override
		public GlyphInfo getGlyph(int codePoint) {
			return font.getGlyph(codePoint);
		}

		@Override
		public IntSet getSupportedGlyphs() {
			return font.getSupportedGlyphs();
		}

		@Override
		public void close() {
		}
	}
}
