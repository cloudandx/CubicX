package org.cloudandx.cubicx.mixin.lighting;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSkyLightSources.class)
public abstract class MixinChunkSkyLightSources {
    @Unique
    private static final int CUBICX_EMPTY_SOURCE_Y = CubicMath.MIN_BLOCK + 1;

    @Shadow
    @Final
    private MutableBlockPos mutablePos1;

    @Shadow
    @Final
    private MutableBlockPos mutablePos2;

    @Unique
    private final int[] cubicx$sources = new int[256];

    @Shadow
    private static boolean isEdgeOccluded(final BlockState topState, final BlockState bottomState) {
        return false;
    }

    @Shadow
    private static int index(final int x, final int z) {
        return 0;
    }

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/SimpleBitStorage;<init>(II)V"
        ),
        index = 0
    )
    private int cubicx$forceValidBitStorageBits(int bits) {
        return 1;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cubicx$initSourcesArray(LevelHeightAccessor level, CallbackInfo ci) {
        Arrays.fill(this.cubicx$sources, CUBICX_EMPTY_SOURCE_Y);
    }

    /**
     * @author cloudandx
     * @reason Populate 32-bit skylight sources by scanning only active 16x16x16 cubes from top to bottom.
     */
    @Overwrite
    public void fillFrom(final ChunkAccess chunk) {
        Int2ObjectMap<LevelChunkSection> cubeMap = ((CubicColumnAccess) chunk).cubicx$getCubeSectionsMap();
        IntArrayList sortedCubeYs;
        synchronized (cubeMap) {
            if (cubeMap.isEmpty()) {
                this.fill(CUBICX_EMPTY_SOURCE_Y);
                return;
            }
            sortedCubeYs = new IntArrayList(cubeMap.keySet());
        }
        sortedCubeYs.sort(IntComparators.OPPOSITE_COMPARATOR);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int lowestSource = this.cubicx$scanSparseColumn(
                    cubeMap, sortedCubeYs, x, z, CubicMath.MAX_BLOCK, Blocks.AIR.defaultBlockState()
                );
                this.set(index(x, z), lowestSource);
            }
        }
    }

    /**
     * @author cloudandx
     * @reason Update 32-bit skylight source edge safely without integer overflow at 32-bit limits.
     */
    @Overwrite
    public boolean update(final BlockGetter level, final int x, final int y, final int z) {
        int upperEdgeY = CubicMath.clampAdd(y, 1);
        int idx = index(x, z);
        int currentLowestSourceY = this.get(idx);
        if (upperEdgeY < currentLowestSourceY) {
            return false;
        }
        BlockPos topPos = this.mutablePos1.set(x, upperEdgeY, z);
        BlockState topState = level.getBlockState(topPos);
        BlockPos middlePos = this.mutablePos2.set(x, y, z);
        BlockState middleState = level.getBlockState(middlePos);
        if (this.updateEdge(level, idx, currentLowestSourceY, topPos, topState, middlePos, middleState)) {
            return true;
        }
        BlockPos bottomPos = this.mutablePos1.set(x, CubicMath.clampAdd(y, -1), z);
        BlockState bottomState = level.getBlockState(bottomPos);
        return this.updateEdge(level, idx, currentLowestSourceY, middlePos, middleState, bottomPos, bottomState);
    }

    /**
     * @author cloudandx
     * @reason Evaluate skylight edge occlusion and scan sparse cubes below when unoccluded.
     */
    @Overwrite
    private boolean updateEdge(
        final BlockGetter level,
        final int index,
        final int oldTopEdgeY,
        final BlockPos topPos,
        final BlockState topState,
        final BlockPos bottomPos,
        final BlockState bottomState
    ) {
        int checkedEdgeY = topPos.getY();
        if (isEdgeOccluded(topState, bottomState)) {
            if (checkedEdgeY > oldTopEdgeY) {
                this.set(index, checkedEdgeY);
                return true;
            }
        } else if (checkedEdgeY == oldTopEdgeY) {
            this.set(index, this.findLowestSourceBelow(level, bottomPos, bottomState));
            return true;
        }
        return false;
    }

    /**
     * @author cloudandx
     * @reason Find next lowest skylight source below startPos by jumping across populated 16x16x16 cubes instead of looping to Integer.MIN_VALUE.
     */
    @Overwrite
    private int findLowestSourceBelow(final BlockGetter level, final BlockPos startPos, final BlockState startState) {
        int x = startPos.getX() & 15;
        int z = startPos.getZ() & 15;
        int firstBottomY = CubicMath.clampAdd(startPos.getY(), -1);
        if (level instanceof CubicColumnAccess cubicChunk) {
            Int2ObjectMap<LevelChunkSection> cubeMap = cubicChunk.cubicx$getCubeSectionsMap();
            IntArrayList sortedCubeYs;
            synchronized (cubeMap) {
                if (cubeMap.isEmpty()) {
                    return CUBICX_EMPTY_SOURCE_Y;
                }
                sortedCubeYs = new IntArrayList(cubeMap.keySet());
            }
            sortedCubeYs.sort(IntComparators.OPPOSITE_COMPARATOR);
            return this.cubicx$scanSparseColumn(cubeMap, sortedCubeYs, x, z, firstBottomY, startState);
        }
        MutableBlockPos topPos = this.mutablePos1.set(startPos);
        MutableBlockPos bottomPos = this.mutablePos2.setWithOffset(startPos, Direction.DOWN);
        BlockState topState = startState;
        int steps = 0;
        while (bottomPos.getY() > CUBICX_EMPTY_SOURCE_Y && ++steps <= 512) {
            BlockState bottomState = level.getBlockState(bottomPos);
            if (isEdgeOccluded(topState, bottomState)) {
                return topPos.getY();
            }
            topState = bottomState;
            topPos.set(bottomPos);
            bottomPos.move(Direction.DOWN);
        }
        return CUBICX_EMPTY_SOURCE_Y;
    }

    /**
     * @author cloudandx
     * @reason Return full 32-bit lowest skylight source Y for column (x, z).
     */
    @Overwrite
    public int getLowestSourceY(final int x, final int z) {
        return this.get(index(x, z));
    }

    /**
     * @author cloudandx
     * @reason Return highest 32-bit lowest skylight source Y across all 256 columns in the chunk.
     */
    @Overwrite
    public int getHighestLowestSourceY() {
        int maxValue = CUBICX_EMPTY_SOURCE_Y;
        for (int i = 0; i < 256; i++) {
            int value = this.cubicx$sources[i];
            if (value > maxValue) {
                maxValue = value;
            }
        }
        return maxValue;
    }

    /**
     * @author cloudandx
     * @reason Fill all 256 columns with the given 32-bit lowestSourceY.
     */
    @Overwrite
    private void fill(final int lowestSourceY) {
        Arrays.fill(this.cubicx$sources, lowestSourceY);
    }

    /**
     * @author cloudandx
     * @reason Store full 32-bit signed Y directly into int[256] array.
     */
    @Overwrite
    private void set(final int index, final int value) {
        this.cubicx$sources[index] = value;
    }

    /**
     * @author cloudandx
     * @reason Read full 32-bit signed Y directly from int[256] array.
     */
    @Overwrite
    private int get(final int index) {
        return this.cubicx$sources[index];
    }

    @Unique
    private int cubicx$scanSparseColumn(
        final Int2ObjectMap<LevelChunkSection> cubeMap,
        final IntArrayList sortedCubeYs,
        final int x,
        final int z,
        final int firstBottomY,
        final BlockState initialTopState
    ) {
        int startCubeY = CubicMath.blockToCube(firstBottomY);
        BlockState topState = initialTopState;
        int lastCheckedY = CubicMath.clampAdd(firstBottomY, 1);
        for (int i = 0; i < sortedCubeYs.size(); i++) {
            int cubeY = sortedCubeYs.getInt(i);
            if (cubeY > startCubeY) {
                continue;
            }
            LevelChunkSection section = cubeMap.get(cubeY);
            if (section == null || section.hasOnlyAir()) {
                topState = Blocks.AIR.defaultBlockState();
                lastCheckedY = CubicMath.cubeToMinBlock(cubeY);
                continue;
            }
            int maxLocalY = (cubeY == startCubeY) ? (firstBottomY & 15) : 15;
            if (cubeY < startCubeY && CubicMath.cubeToMaxBlock(cubeY) < lastCheckedY - 1) {
                topState = Blocks.AIR.defaultBlockState();
            }
            for (int localY = maxLocalY; localY >= 0; localY--) {
                int worldY = CubicMath.cubeToBlock(cubeY, localY);
                BlockState bottomState = section.getBlockState(x, localY, z);
                if (isEdgeOccluded(topState, bottomState)) {
                    return CubicMath.clampAdd(worldY, 1);
                }
                topState = bottomState;
                lastCheckedY = worldY;
            }
        }
        return CUBICX_EMPTY_SOURCE_Y;
    }
}