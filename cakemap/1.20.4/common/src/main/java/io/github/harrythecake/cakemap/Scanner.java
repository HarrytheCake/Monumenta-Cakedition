package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.BlockStates;
import io.github.harrythecake.cakemap.core.RegionData;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Reads the chunks the client has into the dynamic map: per column the top visible block, its
 * biome and height, and water above it. Queued chunks are scanned a few per tick; the chunks
 * around the player are scanned again every two seconds so building shows up.
 */
public final class Scanner {
	private static final int PER_TICK = 6;
	private static final Set<Block> SEE_THROUGH_IN_WATER = Set.of(Blocks.WATER, Blocks.BUBBLE_COLUMN, Blocks.KELP, Blocks.KELP_PLANT, Blocks.SEAGRASS,
			Blocks.TALL_SEAGRASS);
	private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();
	private static int nearTimer;

	private Scanner() {
	}

	public static void queue(ChunkPos pos) {
		long k = pos.toLong();
		if (QUEUED.add(k)) {
			QUEUE.add(k);
		}
	}

	public static void clear() {
		QUEUE.clear();
		QUEUED.clear();
	}

	public static void tick(Minecraft mc, MapSession session) {
		ClientLevel world = mc.level;
		if (world == null || mc.player == null || session == null) {
			return;
		}
		if (++nearTimer >= 40) {
			nearTimer = 0;
			ChunkPos p = mc.player.chunkPosition();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					queue(new ChunkPos(p.x + dx, p.z + dz));
				}
			}
		}
		int done = 0;
		int tries = QUEUE.size();
		while (done < PER_TICK && tries-- > 0 && !QUEUE.isEmpty()) {
			long k = QUEUE.poll();
			ChunkPos cp = new ChunkPos(k);
			LevelChunk chunk = world.getChunkSource().getChunk(cp.x, cp.z, false);
			if (chunk == null || chunk.isEmpty()) {
				QUEUED.remove(k);
				continue;
			}
			RegionData r = session.store().forWrite(cp.x >> 5, cp.z >> 5);
			if (r == null) {
				QUEUE.add(k); // its region is still loading
				continue;
			}
			QUEUED.remove(k);
			if (scan(world, chunk, r, session)) {
				session.store().markDirty(cp.x >> 5, cp.z >> 5);
			}
			done++;
		}
	}

	private static boolean scan(ClientLevel world, LevelChunk chunk, RegionData r, MapSession session) {
		GameColors colors = GameColors.INSTANCE;
		ChunkPos cp = chunk.getPos();
		int bottom = world.getMinBuildHeight();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int wx = cp.getMinBlockX() + lx;
				int wz = cp.getMinBlockZ() + lz;
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
				String topState = null;
				String over = null;
				int surface = 0;
				int h = bottom;
				for (int y = top; y >= bottom; y--) {
					pos.set(wx, y, wz);
					BlockState s = chunk.getBlockState(pos);
					FluidState f = s.getFluidState();
					if (!f.isEmpty() && f.is(FluidTags.WATER)) {
						if (over == null) {
							over = BlockStates.WATER;
							surface = y;
						}
						if (SEE_THROUGH_IN_WATER.contains(s.getBlock())) {
							continue; // look through water and water plants to the floor
						}
					} else if (s.isAir() || s.getRenderShape() == RenderShape.INVISIBLE) {
						continue; // air, barriers, light blocks
					}
					topState = colors.name(s);
					h = y;
					break;
				}
				int depth = over == null ? 0 : surface - h;
				if (topState == null) {
					if (over == null) {
						continue;
					}
					topState = over; // water to the bottom of the world
					h = surface;
					depth = 0;
				}
				if (depth <= 0) {
					over = null;
					depth = 0;
				}
				pos.set(wx, h, wz);
				Holder<Biome> biome = chunk.getNoiseBiome(lx >> 2, h >> 2, lz >> 2);
				String biomeName = biome.unwrapKey().map(k -> k.location().toString()).orElse("");
				int ix = Math.floorMod(wx, RegionData.SIZE);
				int iz = Math.floorMod(wz, RegionData.SIZE);
				changed |= r.set(ix, iz, topState, biomeName, h, over, depth);
				if ((lx & 7) == 4 && (lz & 7) == 4) {
					session.sampleSeen(wx, wz, h);
				}
			}
		}
		return changed;
	}
}
