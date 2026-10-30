package org.cloudandx.cubicx.mixin.chunk;

import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ProtoChunk.class)
public abstract class MixinProtoChunk extends ChunkAccess {
    @Shadow
    private volatile @Nullable LevelLightEngine lightEngine;

    @Shadow
    private volatile ChunkStatus status;

    protected MixinProtoChunk(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        long inhabitedTime,
        LevelChunkSection @Nullable [] sections,
        @Nullable BlendingData blendingData
    ) {
        super(chunkPos, upgradeData, levelHeightAccessor, containerFactory, inhabitedTime, sections, blendingData);
    }

    @Shadow
    public static short packOffsetCoordinates(final BlockPos pos) {
        return 0;
    }

    /**
     * @author cloudandx
     * @reason Read block state from sparse 16x16x16 cube storage without allocating empty sections.
     */
    @Overwrite
    public @NonNull BlockState getBlockState(final BlockPos pos) {
        int y = pos.getY();
        LevelChunkSection section = ((CubicColumnAccess) this).cubicx$getCubeSectionIfPresent(CubicMath.blockToCube(y));
        return section == null || section.hasOnlyAir()
            ? Blocks.AIR.defaultBlockState()
            : section.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    /**
     * @author cloudandx
     * @reason Read fluid state from sparse 16x16x16 cube storage without allocating empty sections.
     */
    @Overwrite
    public @NonNull FluidState getFluidState(final BlockPos pos) {
        int y = pos.getY();
        LevelChunkSection section = ((CubicColumnAccess) this).cubicx$getCubeSectionIfPresent(CubicMath.blockToCube(y));
        return section == null || section.hasOnlyAir()
            ? Fluids.EMPTY.defaultFluidState()
            : section.getFluidState(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    /**
     * @author cloudandx
     * @reason Write block state into sparse 16x16x16 cube storage across full 32-bit Y range.
     */
    @Overwrite
    public @Nullable BlockState setBlockState(final BlockPos pos, final @NonNull BlockState state, final int flags) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int cubeY = CubicMath.blockToCube(y);
        CubicColumnAccess column = (CubicColumnAccess) this;
        LevelChunkSection existing = column.cubicx$getCubeSectionIfPresent(cubeY);
        if ((existing == null || existing.hasOnlyAir()) && state.is(Blocks.AIR)) {
            return state;
        }
        LevelChunkSection section = column.cubicx$getOrCreateCubeSection(cubeY);
        boolean wasEmpty = section.hasOnlyAir();
        int localX = SectionPos.sectionRelative(x);
        int localY = SectionPos.sectionRelative(y);
        int localZ = SectionPos.sectionRelative(z);
        BlockState oldState = section.setBlockState(localX, localY, localZ, state);
        LevelLightEngine engine = this.lightEngine;
        if (this.status.isOrAfter(ChunkStatus.INITIALIZE_LIGHT) && engine != null) {
            boolean isEmpty = section.hasOnlyAir();
            if (isEmpty != wasEmpty) {
                engine.updateSectionStatus(pos, isEmpty);
            }
            if (LightEngine.hasDifferentLightProperties(oldState, state)) {
                this.skyLightSources.update(this, localX, y, localZ);
                engine.checkBlock(pos);
            }
        }
        EnumSet<Types> heightmapsAfter = this.getPersistedStatus().heightmapsAfter();
        EnumSet<Types> toPrime = null;
        for (Types type : heightmapsAfter) {
            Heightmap heightmap = this.heightmaps.get(type);
            if (heightmap == null) {
                if (toPrime == null) {
                    toPrime = EnumSet.noneOf(Types.class);
                }
                toPrime.add(type);
            } else {
                heightmap.update(localX, y, localZ, state);
            }
        }
        if (toPrime != null) {
            Heightmap.primeHeightmaps(this, toPrime);
        }
        return oldState;
    }

    /**
     * @author cloudandx
     * @reason Record post-processing offsets in sparse cube map without indexing into fixed array.
     */
    @Overwrite
    public void markPosForPostProcessing(final BlockPos blockPos) {
        int cubeY = CubicMath.blockToCube(blockPos.getY());
        ((CubicColumnAccess) this).cubicx$getOrCreatePostProcessing(cubeY).add(packOffsetCoordinates(blockPos));
    }

    /**
     * @author cloudandx
     * @reason Add packed post-processing offsets into sparse cube map.
     */
    @Overwrite
    public void addPackedPostProcess(final ShortList packedOffsets, final int sectionIndex) {
        if (!packedOffsets.isEmpty()) {
            ((CubicColumnAccess) this).cubicx$getOrCreatePostProcessing(CubicMath.sectionIndexToCube(sectionIndex)).addAll(packedOffsets);
        }
    }
}