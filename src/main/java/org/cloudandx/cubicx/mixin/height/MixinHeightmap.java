package org.cloudandx.cubicx.mixin.core.height;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Heightmap.class)
public abstract class MixinHeightmap {
    @Shadow
    @Final
    private Predicate<BlockState> isOpaque;

    @Shadow
    @Final
    private ChunkAccess chunk;

    @Unique
    private final int[] cubicx$heights = new int[256];

    @Shadow
    public abstract int getFirstAvailable(final int x, final int z);

    @Shadow
    private static int getIndex(final int x, final int z) {
        return 0;
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getHeight()I"
        )
    )
    private int cubicx$preventBitStorageOverflow(ChunkAccess instance) {
        return 15;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cubicx$initHeights(ChunkAccess chunk, Types heightmapType, CallbackInfo ci) {
        Arrays.fill(this.cubicx$heights, CubicMath.MIN_BLOCK);
    }

    /**
     * @author cloudandx
     * @reason Prime 32-bit heightmaps by scanning only populated 16x16x16 cubes from top to bottom without empty space loops.
     */
    @Overwrite
    public static void primeHeightmaps(final ChunkAccess chunk, final Set<Types> types) {
        if (types.isEmpty()) {
            return;
        }
        int size = types.size();
        List<Heightmap> allHeightmaps = new ObjectArrayList<>(size);
        for (Types type : types) {
            allHeightmaps.add(chunk.getOrCreateHeightmapUnprimed(type));
        }
        Int2ObjectMap<LevelChunkSection> cubeMap = ((CubicColumnAccess) chunk).cubicx$getCubeSectionsMap();
        if (cubeMap.isEmpty()) {
            return;
        }
        IntArrayList sortedCubeYs = new IntArrayList(cubeMap.keySet());
        sortedCubeYs.sort(IntComparators.OPPOSITE_COMPARATOR);
        List<Heightmap> remainingHeightmaps = new ObjectArrayList<>(size);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                remainingHeightmaps.clear();
                remainingHeightmaps.addAll(allHeightmaps);
                for (int cIdx = 0; cIdx < sortedCubeYs.size() && !remainingHeightmaps.isEmpty(); cIdx++) {
                    int cubeY = sortedCubeYs.getInt(cIdx);
                    LevelChunkSection section = cubeMap.get(cubeY);
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    for (int localY = 15; localY >= 0 && !remainingHeightmaps.isEmpty(); localY--) {
                        BlockState state = section.getBlockState(x, localY, z);
                        if (!state.is(Blocks.AIR)) {
                            int worldY = CubicMath.cubeToBlock(cubeY, localY);
                            for (int i = remainingHeightmaps.size() - 1; i >= 0; i--) {
                                Heightmap hm = remainingHeightmaps.get(i);
                                if (hm.update(x, worldY, z, state)) {
                                    remainingHeightmaps.remove(i);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @author cloudandx
     * @reason Update 32-bit heightmap by jumping across populated 16x16x16 cubes instead of iterating down to Integer.MIN_VALUE.
     */
    @Overwrite
    public boolean update(final int localX, final int localY, final int localZ, final BlockState state) {
        int firstAvailable = this.getFirstAvailable(localX, localZ);
        if ((long) localY < (long) firstAvailable - 1L) {
            return false;
        }
        if (this.isOpaque.test(state)) {
            if (localY >= firstAvailable) {
                this.setHeight(localX, localZ, CubicMath.clampAdd(localY, 1));
                return true;
            }
        } else if ((long) firstAvailable - 1L == (long) localY) {
            Int2ObjectMap<LevelChunkSection> cubeMap = ((CubicColumnAccess) this.chunk).cubicx$getCubeSectionsMap();
            int startCubeY = CubicMath.blockToCube(localY);
            int startInCubeY = (localY & 15) - 1;
            LevelChunkSection startSection = cubeMap.get(startCubeY);
            if (startSection != null && !startSection.hasOnlyAir()) {
                for (int cy = startInCubeY; cy >= 0; cy--) {
                    if (this.isOpaque.test(startSection.getBlockState(localX, cy, localZ))) {
                        this.setHeight(localX, localZ, CubicMath.clampAdd(CubicMath.cubeToBlock(startCubeY, cy), 1));
                        return true;
                    }
                }
            }
            IntArrayList belowCubeYs = new IntArrayList();
            for (int cyKey : cubeMap.keySet()) {
                if (cyKey < startCubeY) {
                    belowCubeYs.add(cyKey);
                }
            }
            belowCubeYs.sort(IntComparators.OPPOSITE_COMPARATOR);
            for (int i = 0; i < belowCubeYs.size(); i++) {
                int cubeY = belowCubeYs.getInt(i);
                LevelChunkSection section = cubeMap.get(cubeY);
                if (section != null && !section.hasOnlyAir()) {
                    for (int cy = 15; cy >= 0; cy--) {
                        if (this.isOpaque.test(section.getBlockState(localX, cy, localZ))) {
                            this.setHeight(localX, localZ, CubicMath.clampAdd(CubicMath.cubeToBlock(cubeY, cy), 1));
                            return true;
                        }
                    }
                }
            }
            this.setHeight(localX, localZ, CubicMath.MIN_BLOCK);
            return true;
        }
        return false;
    }

    /**
     * @author cloudandx
     * @reason Return full 32-bit signed height value directly from int array.
     */
    @Overwrite
    private int getFirstAvailable(final int index) {
        return this.cubicx$heights[index];
    }

    /**
     * @author cloudandx
     * @reason Store full 32-bit signed height value directly into int array.
     */
    @Overwrite
    private void setHeight(final int x, final int z, final int height) {
        this.cubicx$heights[getIndex(x, z)] = height;
    }

    /**
     * @author cloudandx
     * @reason Pack 256 32-bit signed heights into 128 64-bit longs for network and disk serialization.
     */
    @Overwrite
    public long[] getRawData() {
        long[] raw = new long[128];
        for (int i = 0; i < 128; i++) {
            long low = (long) this.cubicx$heights[i << 1] & 0xFFFFFFFFL;
            long high = ((long) this.cubicx$heights[(i << 1) | 1] & 0xFFFFFFFFL) << 32;
            raw[i] = low | high;
        }
        return raw;
    }

    /**
     * @author cloudandx
     * @reason Unpack 128 64-bit longs back into 256 32-bit signed height values.
     */
    @Overwrite
    public void setRawData(final ChunkAccess chunk, final Types type, final long[] data) {
        if (data.length == 128) {
            for (int i = 0; i < 128; i++) {
                long packed = data[i];
                this.cubicx$heights[i << 1] = (int) (packed & 0xFFFFFFFFL);
                this.cubicx$heights[(i << 1) | 1] = (int) (packed >>> 32);
            }
        } else {
            primeHeightmaps(chunk, EnumSet.of(type));
        }
    }
}