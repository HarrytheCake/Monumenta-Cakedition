package io.github.harrythecake.cakemap;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.RegionShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Cave mode: a live cross-section of the 16 x 16 chunks around the player at the player's height:
 * walls dark, floors in their colours, lit by height. Never saved; a few chunks per tick.
 */
public final class CaveLayer {
	public static final int CHUNKS = 16;
	public static final int SIZE = CHUNKS * 16;
	private static final int PER_TICK = 4;
	private static final int DOWN = 40;
	private static final int WALL = 0xFF14161A;
	private static final int[][] ORDER = order();

	private static DynamicTexture texture;
	private static Identifier id;
	private static boolean active;
	private static int originChunkX = Integer.MIN_VALUE;
	private static int originChunkZ;
	private static int startY;
	private static final boolean[] DONE = new boolean[CHUNKS * CHUNKS];
	private static int cursor;
	private static boolean dirty;
	private static int checkTimer;
	private static boolean underground;

	private CaveLayer() {
	}

	/** Chunks sorted by distance from the middle: the player's surroundings come first. */
	private static int[][] order() {
		int[][] out = new int[CHUNKS * CHUNKS][];
		int k = 0;
		for (int z = 0; z < CHUNKS; z++) {
			for (int x = 0; x < CHUNKS; x++) {
				out[k++] = new int[] {x, z};
			}
		}
		java.util.Arrays.sort(out, (a, b) -> Double.compare(dist(a), dist(b)));
		return out;
	}

	private static double dist(int[] c) {
		double dx = c[0] - CHUNKS / 2.0 + 0.5;
		double dz = c[1] - CHUNKS / 2.0 + 0.5;
		return dx * dx + dz * dz;
	}

	public static boolean active() {
		return active && id != null;
	}

	public static Identifier id() {
		return active ? id : null;
	}

	public static int originX() {
		return originChunkX * 16;
	}

	public static int originZ() {
		return originChunkZ * 16;
	}

	public static void reset() {
		originChunkX = Integer.MIN_VALUE;
		active = false;
	}

	public static void tick(Minecraft mc) {
		ClientLevel world = mc.level;
		Player p = mc.player;
		if (world == null || p == null) {
			active = false;
			return;
		}
		MapSettings.Cave mode = CakeMap.settings().cave;
		if (++checkTimer >= 10) {
			checkTimer = 0;
			int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
			underground = top > p.getBlockY() + 3;
		}
		boolean want = mode == MapSettings.Cave.ON || (mode == MapSettings.Cave.AUTO && underground);
		if (!want) {
			active = false;
			return;
		}
		if (texture == null) {
			texture = new DynamicTexture("cakemap cave layer", SIZE, SIZE, true);
			id = Identifier.fromNamespaceAndPath(CakeMap.MOD_ID, "cave");
			mc.getTextureManager().register(id, texture);
		}
		if (!active) {
			active = true;
			originChunkX = Integer.MIN_VALUE;
		}
		int pcx = p.chunkPosition().x();
		int pcz = p.chunkPosition().z();
		int y = p.getBlockY();
		boolean recentre = originChunkX == Integer.MIN_VALUE || Math.abs(pcx - (originChunkX + CHUNKS / 2)) > 3
				|| Math.abs(pcz - (originChunkZ + CHUNKS / 2)) > 3;
		if (recentre || Math.abs(y - startY) >= 3) {
			if (recentre) {
				originChunkX = pcx - CHUNKS / 2;
				originChunkZ = pcz - CHUNKS / 2;
				texture.getPixels().fillRect(0, 0, SIZE, SIZE, 0);
			}
			startY = y;
			java.util.Arrays.fill(DONE, false);
			cursor = 0;
		}
		int n = 0;
		while (n < PER_TICK && cursor < ORDER.length) {
			int[] c = ORDER[cursor++];
			if (DONE[c[1] * CHUNKS + c[0]]) {
				continue;
			}
			LevelChunk chunk = world.getChunkSource().getChunk(originChunkX + c[0], originChunkZ + c[1], false);
			if (chunk == null || chunk.isEmpty()) {
				continue;
			}
			scan(world, chunk, c[0], c[1]);
			DONE[c[1] * CHUNKS + c[0]] = true;
			n++;
		}
		if (cursor >= ORDER.length) {
			// start over: blocks change, and chunks that weren't loaded may be now
			cursor = 0;
			java.util.Arrays.fill(DONE, false);
		}
		if (dirty) {
			dirty = false;
			texture.upload();
		}
	}

	private static void scan(ClientLevel world, LevelChunk chunk, int cx, int cz) {
		GameColors colors = GameColors.INSTANCE;
		NativeImage img = texture.getPixels();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bottom = world.getMinY();
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int wx = chunk.getPos().getMinBlockX() + lx;
				int wz = chunk.getPos().getMinBlockZ() + lz;
				int colour = 0;
				pos.set(wx, startY + 1, wz);
				BlockState head = chunk.getBlockState(pos);
				if (head.isSolidRender()) {
					colour = WALL;
				} else {
					for (int y = startY; y >= Math.max(bottom, startY - DOWN); y--) {
						pos.set(wx, y, wz);
						BlockState s = chunk.getBlockState(pos);
						boolean water = s.getFluidState().is(FluidTags.WATER);
						if (!water && (s.isAir() || s.getRenderShape() == RenderShape.INVISIBLE)) {
							continue;
						}
						String name = colors.name(s);
						int c = colors.base(name);
						int tint = colors.tint(name);
						if (tint == RegionShader.TINT_GRASS) {
							c = RegionShader.multiply(c, 0x7FB238);
						} else if (tint == RegionShader.TINT_FOLIAGE) {
							c = RegionShader.multiply(c, 0x48B518);
						} else if (tint == RegionShader.TINT_WATER || water) {
							c = 0x3F76E4;
						}
						double light = Math.max(0.45, Math.min(1.2, 1 + (y - startY) * 0.05));
						colour = 0xFF000000 | RegionShader.scale(c, light);
						break;
					}
				}
				int ix = cx * 16 + lx;
				int iz = cz * 16 + lz;
				// ARGB → ABGR
				img.setPixelABGR(ix, iz, (colour & 0xFF00FF00) | ((colour >> 16) & 0xFF) | ((colour & 0xFF) << 16));
			}
		}
		dirty = true;
	}
}
