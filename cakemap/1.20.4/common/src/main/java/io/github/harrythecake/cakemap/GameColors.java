package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.harrythecake.cakemap.core.BlockStates;
import io.github.harrythecake.cakemap.core.RegionShader;
import io.github.harrythecake.cakemap.mixin.SpriteContentsAccessor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;

/**
 * Block and biome colours for the map: the average colour of each block's top texture (from the
 * loaded resource packs), the biome tint where the block takes one, and Minecraft's map colour as a
 * fallback. Colours are worked out on the render thread and cached; {@link #snapshot} gives worker
 * threads a frozen copy. Cleared when resources reload.
 */
public final class GameColors implements RegionShader.Colors {
	public static final GameColors INSTANCE = new GameColors();
	private static final int GRASS_DEFAULT = 0x7FB238;
	private static final int FOLIAGE_DEFAULT = 0x48B518;
	private static final int WATER_DEFAULT = 0x3F76E4;

	private final Map<String, Integer> base = new ConcurrentHashMap<>();
	private final Map<String, Integer> tint = new ConcurrentHashMap<>();
	private final Map<String, int[]> biomes = new ConcurrentHashMap<>();
	private final Map<String, BlockState> parsed = new ConcurrentHashMap<>();
	private final Map<BlockState, String> names = new ConcurrentHashMap<>();
	private HolderLookup.Provider builtin;
	/** Bumped when colours change (resource reload): textures built from older colours are rebuilt. */
	public volatile int generation;

	private GameColors() {
	}

	public void clear() {
		base.clear();
		tint.clear();
		biomes.clear();
		generation++;
	}

	/** CakeMap's text for a block state (sorted properties). */
	public String name(BlockState state) {
		String n = names.get(state);
		if (n == null) {
			Map<String, String> props = new TreeMap<>();
			for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
				props.put(e.getKey().getName(), nameOf(e.getKey(), e.getValue()));
			}
			n = BlockStates.join(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), props);
			names.put(state, n);
		}
		return n;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> String nameOf(Property<T> p, Comparable<?> v) {
		return p.getName((T) v);
	}

	public BlockState state(String s) {
		return parsed.computeIfAbsent(s, k -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), k, false).blockState();
			} catch (Exception e) {
				// unknown block (another mod's, a renamed one): its default if the id is known
				ResourceLocation id = ResourceLocation.tryParse(BlockStates.blockName(k));
				Block b = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
				return b == null ? Blocks.AIR.defaultBlockState() : b.defaultBlockState();
			}
		});
	}

	@Override
	public int base(String state) {
		Integer c = base.get(state);
		if (c == null) {
			compute(state);
			c = base.getOrDefault(state, 0x808080);
		}
		return c;
	}

	@Override
	public int tint(String state) {
		Integer t = tint.get(state);
		if (t == null) {
			compute(state);
			t = tint.getOrDefault(state, RegionShader.TINT_NONE);
		}
		return t;
	}

	/** Must run on the render thread the first time a state is seen (textures and models). */
	private void compute(String s) {
		BlockState state = state(s);
		int colour;
		int t = RegionShader.TINT_NONE;
		if (BlockStates.isWater(s) || state.is(Blocks.WATER)) {
			colour = 0xFFFFFF;
			t = RegionShader.TINT_WATER;
		} else {
			int[] tex = textureColour(state);
			if (tex != null) {
				colour = tex[0];
				if (tex[1] >= 0) {
					t = tintKind(state, tex[1]);
					if (t == -1) {
						// a fixed tint (spruce and birch leaves, lily pads …)
						int fixed = Minecraft.getInstance().getBlockColors().getColor(state, null, null, tex[1]);
						colour = fixed == -1 ? colour : RegionShader.multiply(colour, fixed & 0xFFFFFF);
						t = RegionShader.TINT_NONE;
					}
				}
			} else {
				MapColor mc = state.getMapColor(null, null);
				colour = mc == null || mc == MapColor.NONE ? 0x808080 : mc.col;
			}
		}
		base.put(s, colour);
		tint.put(s, t);
	}

	/** GRASS / FOLIAGE when the block's colour provider gives the plain default, -1 for a fixed tint. */
	private static int tintKind(BlockState state, int index) {
		int c;
		try {
			c = Minecraft.getInstance().getBlockColors().getColor(state, null, null, index);
		} catch (RuntimeException e) {
			return RegionShader.TINT_GRASS; // providers that insist on a world are biome tints
		}
		if (c == -1) {
			return RegionShader.TINT_NONE;
		}
		c &= 0xFFFFFF;
		if (c == (GrassColor.getDefaultColor() & 0xFFFFFF) || c == (GrassColor.get(0.5, 1.0) & 0xFFFFFF)) {
			return RegionShader.TINT_GRASS;
		}
		if (c == (FoliageColor.getDefaultColor() & 0xFFFFFF) || c == (FoliageColor.getMangroveColor() & 0xFFFFFF)) {
			return RegionShader.TINT_FOLIAGE;
		}
		return -1;
	}

	/** {average RGB of the top face's sprite, tint index or -1}, or null without a model. */
	private static int[] textureColour(BlockState state) {
		Minecraft mc = Minecraft.getInstance();
		BakedModel model = mc.getBlockRenderer().getBlockModel(state);
		if (model == null) {
			return null;
		}
		RandomSource random = RandomSource.create(42);
		List<BakedQuad> quads = model.getQuads(state, Direction.UP, random);
		if (quads.isEmpty()) {
			quads = model.getQuads(state, null, random);
		}
		TextureAtlasSprite sprite;
		int tintIndex = -1;
		if (!quads.isEmpty()) {
			BakedQuad q = quads.get(0);
			sprite = q.getSprite();
			tintIndex = q.isTinted() ? q.getTintIndex() : -1;
		} else {
			sprite = model.getParticleIcon();
		}
		if (sprite == null) {
			return null;
		}
		NativeImage img = ((SpriteContentsAccessor) sprite.contents()).cakemap$image();
		if (img == null) {
			return null;
		}
		int w = sprite.contents().width();
		int h = sprite.contents().height();
		long r = 0;
		long g = 0;
		long b = 0;
		long a = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int abgr = img.getPixelRGBA(x, y);
				int alpha = (abgr >>> 24) & 0xFF;
				if (alpha < 16) {
					continue;
				}
				r += (long) (abgr & 0xFF) * alpha;
				g += (long) ((abgr >> 8) & 0xFF) * alpha;
				b += (long) ((abgr >> 16) & 0xFF) * alpha;
				a += alpha;
			}
		}
		if (a == 0) {
			return null;
		}
		int rgb = ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
		return new int[] {rgb, tintIndex};
	}

	// ---- biomes ----

	@Override
	public int grass(String biome) {
		return biome(biome)[0];
	}

	@Override
	public int foliage(String biome) {
		return biome(biome)[1];
	}

	@Override
	public int water(String biome) {
		return biome(biome)[2];
	}

	private int[] biome(String name) {
		return biomes.computeIfAbsent(name, n -> {
			Biome b = findBiome(n);
			if (b == null) {
				return new int[] {GRASS_DEFAULT, FOLIAGE_DEFAULT, WATER_DEFAULT};
			}
			return new int[] {b.getGrassColor(0, 0) & 0xFFFFFF, b.getFoliageColor() & 0xFFFFFF, b.getWaterColor() & 0xFFFFFF};
		});
	}

	private Biome findBiome(String name) {
		ResourceLocation id = ResourceLocation.tryParse(name);
		if (id == null || name.isEmpty()) {
			return null;
		}
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			Optional<Biome> b = mc.level.registryAccess().registryOrThrow(Registries.BIOME).getOptional(key);
			if (b.isPresent()) {
				return b.get();
			}
		}
		try {
			if (builtin == null) {
				builtin = VanillaRegistries.createLookup();
			}
			return builtin.lookupOrThrow(Registries.BIOME).get(key).map(e -> e.value()).orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Works out every state's colour now (render thread), so a worker can shade the region with {@link #snapshot}. */
	public RegionShader.Colors prepare(List<String> states, List<String> biomeNames) {
		for (String s : states) {
			if (!s.isEmpty()) {
				base(s);
				tint(s);
			}
		}
		for (String b : biomeNames) {
			biome(b);
		}
		return snapshot();
	}

	/** Reads only what is cached (unknown states are grey): safe on worker threads. */
	public RegionShader.Colors snapshot() {
		return new RegionShader.Colors() {
			@Override
			public int base(String state) {
				return GameColors.this.base.getOrDefault(state, 0x808080);
			}

			@Override
			public int tint(String state) {
				return GameColors.this.tint.getOrDefault(state, RegionShader.TINT_NONE);
			}

			@Override
			public int grass(String biome) {
				int[] b = biomes.get(biome);
				return b == null ? GRASS_DEFAULT : b[0];
			}

			@Override
			public int foliage(String biome) {
				int[] b = biomes.get(biome);
				return b == null ? FOLIAGE_DEFAULT : b[1];
			}

			@Override
			public int water(String biome) {
				int[] b = biomes.get(biome);
				return b == null ? WATER_DEFAULT : b[2];
			}
		};
	}
}
