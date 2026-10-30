package org.cloudandx.cubicx.mixin.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunk.EntityCreationType;
import net.minecraft.world.level.chunk.LevelChunk.PostLoadProcessor;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@SuppressWarnings({"deprecation", "WhileLoopReplaceableByForEach"})
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk extends ChunkAccess {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private Level level;

    protected MixinLevelChunk(
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
    public abstract @Nullable BlockEntity getBlockEntity(BlockPos pos, EntityCreationType creationType);

    @Shadow
    public abstract @Nullable BlockEntity getBlockEntity(@NonNull BlockPos pos);

    @Shadow
    public abstract void addAndRegisterBlockEntity(BlockEntity blockEntity);

    @Shadow
    protected abstract <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity);

    @Shadow
    public abstract void clearAllBlockEntities();

    @Inject(
        method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
        at = @At("RETURN")
    )
    private void cubicx$copyProtoChunkCubes(ServerLevel level, ProtoChunk protoChunk, @Nullable PostLoadProcessor postLoad, CallbackInfo ci) {
        CubicColumnAccess source = (CubicColumnAccess) protoChunk;
        CubicColumnAccess self = (CubicColumnAccess) this;
        for (Int2ObjectMap.Entry<LevelChunkSection> entry : source.cubicx$getCubeSectionsMap().int2ObjectEntrySet()) {
            self.cubicx$putCubeSection(entry.getIntKey(), entry.getValue());
        }
        for (Int2ObjectMap.Entry<ShortList> entry : source.cubicx$getPostProcessingMap().int2ObjectEntrySet()) {
            self.cubicx$getOrCreatePostProcessing(entry.getIntKey()).addAll(entry.getValue());
        }
    }

    /**
     * @author cloudandx
     * @reason Query 16x16x16 sparse cube map for full 32-bit Y coordinates with zero allocation on air cubes.
     */
    @Overwrite
    public @NonNull BlockState getBlockState(final BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (this.level.isDebug()) {
            BlockState blockState = null;
            if (y == 60) {
                blockState = Blocks.BARRIER.defaultBlockState();
            }
            if (y == 70) {
                blockState = DebugLevelSource.getBlockStateFor(x, z);
            }
            return blockState == null ? Blocks.AIR.defaultBlockState() : blockState;
        }
        try {
            LevelChunkSection currentSection = ((CubicColumnAccess) this).cubicx$getCubeSectionIfPresent(CubicMath.blockToCube(y));
            if (currentSection != null && !currentSection.hasOnlyAir()) {
                return currentSection.getBlockState(x & 15, y & 15, z & 15);
            }
            return Blocks.AIR.defaultBlockState();
        } catch (Throwable t) {
            CrashReport report = CrashReport.forThrowable(t, "Getting block state");
            CrashReportCategory category = report.addCategory("Block being got");
            category.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
            throw new ReportedException(report);
        }
    }

    /**
     * @author cloudandx
     * @reason Query 16x16x16 sparse cube map for fluid state across full 32-bit Y coordinates.
     */
    @Overwrite
    public FluidState getFluidState(final int x, final int y, final int z) {
        try {
            LevelChunkSection currentSection = ((CubicColumnAccess) this).cubicx$getCubeSectionIfPresent(CubicMath.blockToCube(y));
            if (currentSection != null && !currentSection.hasOnlyAir()) {
                return currentSection.getFluidState(x & 15, y & 15, z & 15);
            }
            return Fluids.EMPTY.defaultFluidState();
        } catch (Throwable t) {
            CrashReport report = CrashReport.forThrowable(t, "Getting fluid state");
            CrashReportCategory category = report.addCategory("Block being got");
            category.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
            throw new ReportedException(report);
        }
    }

    /**
     * @author cloudandx
     * @reason Mutate block state in sparse 16x16x16 cube storage across full 32-bit Y range.
     */
    @Overwrite
    public @Nullable BlockState setBlockState(final BlockPos pos, final @NonNull BlockState state, final int flags) {
        int y = pos.getY();
        int cubeY = CubicMath.blockToCube(y);
        CubicColumnAccess column = (CubicColumnAccess) this;
        LevelChunkSection existing = column.cubicx$getCubeSectionIfPresent(cubeY);
        if ((existing == null || existing.hasOnlyAir()) && state.isAir()) {
            return null;
        }
        LevelChunkSection section = column.cubicx$getOrCreateCubeSection(cubeY);
        boolean wasEmpty = section.hasOnlyAir();
        int localX = pos.getX() & 15;
        int localY = y & 15;
        int localZ = pos.getZ() & 15;
        BlockState oldState = section.setBlockState(localX, localY, localZ, state);
        if (oldState == state) {
            return null;
        }
        Block newBlock = state.getBlock();
        this.heightmaps.get(Types.MOTION_BLOCKING).update(localX, y, localZ, state);
        this.heightmaps.get(Types.MOTION_BLOCKING_NO_LEAVES).update(localX, y, localZ, state);
        this.heightmaps.get(Types.OCEAN_FLOOR).update(localX, y, localZ, state);
        this.heightmaps.get(Types.WORLD_SURFACE).update(localX, y, localZ, state);
        boolean isEmpty = section.hasOnlyAir();
        if (wasEmpty != isEmpty) {
            this.level.getChunkSource().getLightEngine().updateSectionStatus(pos, isEmpty);
            this.level.getChunkSource().onSectionEmptinessChanged(this.chunkPos.x(), SectionPos.blockToSectionCoord(y), this.chunkPos.z(), isEmpty);
        }
        if (LightEngine.hasDifferentLightProperties(oldState, state)) {
            ProfilerFiller profiler = Profiler.get();
            profiler.push("updateSkyLightSources");
            this.skyLightSources.update(this, localX, y, localZ);
            profiler.popPush("queueCheckLight");
            this.level.getChunkSource().getLightEngine().checkBlock(pos);
            profiler.pop();
        }
        boolean blockChanged = !oldState.is(newBlock);
        boolean movedByPiston = (flags & 64) != 0;
        boolean sideEffects = (flags & 256) == 0;
        if (blockChanged && oldState.hasBlockEntity() && !state.shouldChangedStateKeepBlockEntity(oldState)) {
            if (!this.level.isClientSide() && sideEffects) {
                BlockEntity blockEntity = this.level.getBlockEntity(pos);
                if (blockEntity != null) {
                    blockEntity.preRemoveSideEffects(pos, oldState);
                }
            }
            this.removeBlockEntity(pos);
        }
        if ((blockChanged || newBlock instanceof BaseRailBlock) && this.level instanceof ServerLevel serverLevel && ((flags & 1) != 0 || movedByPiston)) {
            oldState.affectNeighborsAfterRemoval(serverLevel, pos, movedByPiston);
        }
        if (!section.getBlockState(localX, localY, localZ).is(newBlock)) {
            return null;
        }
        if (!this.level.isClientSide() && (flags & 512) == 0) {
            state.onPlace(this.level, pos, oldState, movedByPiston);
        }
        if (state.hasBlockEntity() && section.getBlockState(localX, localY, localZ).is(newBlock)) {
            BlockEntity blockEntity = this.getBlockEntity(pos, EntityCreationType.CHECK);
            if (blockEntity != null && !blockEntity.isValidBlockState(state)) {
                LOGGER.warn("Found mismatched block entity @ {}: type = {}, state = {}", pos, blockEntity.typeHolder().getRegisteredName(), state);
                this.removeBlockEntity(pos);
                blockEntity = null;
            }
            if (blockEntity == null) {
                blockEntity = ((EntityBlock) newBlock).newBlockEntity(pos, state);
                if (blockEntity != null) {
                    this.addAndRegisterBlockEntity(blockEntity);
                }
            } else {
                blockEntity.setBlockState(state);
                this.updateBlockEntityTicker(blockEntity);
            }
        }
        this.markUnsaved();
        return oldState;
    }

    /**
     * @author cloudandx
     * @reason Process post-generation offsets from sparse 16x16x16 cube map instead of fixed array.
     */
    @Overwrite
    public void postProcessGeneration(final ServerLevel level) {
        ChunkPos chunkPos = this.getPos();
        Int2ObjectMap<ShortList> postMap = ((CubicColumnAccess) this).cubicx$getPostProcessingMap();
        for (Int2ObjectMap.Entry<ShortList> entry : postMap.int2ObjectEntrySet()) {
            int sectionY = entry.getIntKey();
            ShortList postProcessingSection = entry.getValue();
            if (postProcessingSection != null) {
                ShortListIterator iterator = postProcessingSection.iterator();
                while (iterator.hasNext()) {
                    Short packedOffset = iterator.next();
                    BlockPos blockPos = ProtoChunk.unpackOffsetCoordinates(packedOffset, sectionY, chunkPos);
                    BlockState blockState = this.getBlockState(blockPos);
                    FluidState fluidState = blockState.getFluidState();
                    if (!fluidState.isEmpty()) {
                        fluidState.tick(level, blockPos, blockState);
                    }
                    if (blockState.getBlock() instanceof LiquidBlock) {
                        blockState.tick(level, blockPos, level.getRandom());
                    } else {
                        BlockState blockStateNew = Block.updateFromNeighbourShapes(blockState, level, blockPos);
                        if (blockStateNew != blockState) {
                            level.setBlock(blockPos, blockStateNew, 276);
                        }
                    }
                }
                postProcessingSection.clear();
            }
        }
        postMap.clear();
        UnmodifiableIterator<BlockPos> pendingIter = ImmutableList.copyOf(this.pendingBlockEntities.keySet()).iterator();
        while (pendingIter.hasNext()) {
            BlockPos pos = pendingIter.next();
            this.getBlockEntity(pos);
        }
        this.pendingBlockEntities.clear();
        this.upgradeData.upgrade((LevelChunk) (Object) this);
    }

    /**
     * @author cloudandx
     * @reason Read sparse 16x16x16 cubes and full 32-bit block entity Y coordinates from packet buffer on the client.
     */
    @Overwrite
    public void replaceWithPacketData(final int chunkX, final int chunkZ, final ClientboundLevelChunkPacketData chunkData) {
        FriendlyByteBuf buffer = chunkData.getReadBuffer();
        Map<Types, long[]> packetHeightmaps = chunkData.getHeightmaps();
        this.clearAllBlockEntities();
        CubicColumnAccess cubicSelf = (CubicColumnAccess) this;
        cubicSelf.cubicx$getCubeSectionsMap().clear();
        if (buffer.isReadable(4)) {
            int cubeCount = buffer.readInt();
            for (int i = 0; i < cubeCount; i++) {
                int cubeY = buffer.readInt();
                LevelChunkSection section = cubicSelf.cubicx$getOrCreateCubeSection(cubeY);
                section.read(buffer);
            }
        }
        int beCount = buffer.isReadable(4) ? buffer.readInt() : 0;
        int[] fullBeYs = new int[beCount];
        for (int i = 0; i < beCount; i++) {
            fullBeYs[i] = buffer.readInt();
        }
        packetHeightmaps.forEach(this::setHeightmap);
        this.initializeLightSources();
        final int[] idx = new int[]{0};
        try ( net.minecraft.util.ProblemReporter.ScopedCollector reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            chunkData.forEachBlockEntityTag(chunkX, chunkZ, (pos, type, tag) -> {
                int currentIdx = idx[0]++;
                BlockPos actualPos = currentIdx < fullBeYs.length ? new BlockPos(pos.getX(), fullBeYs[currentIdx], pos.getZ()) : pos;
                BlockEntity blockEntity = this.getBlockEntity(actualPos, EntityCreationType.IMMEDIATE);
                if (blockEntity != null && tag != null && blockEntity.getType() == type) {
                    blockEntity.loadWithComponents(
                            net.minecraft.world.level.storage.TagValueInput.create(
                                    reporter.forChild(blockEntity.problemPath()), this.level.registryAccess(), tag
                            )
                    );
                }
            });
        }
    }

    /**
     * @author cloudandx
     * @reason Read biome palettes only for active 16x16x16 cube sections on the client.
     */
    @Overwrite
    public void replaceBiomes(final FriendlyByteBuf buffer) {
        for (LevelChunkSection section : ((CubicColumnAccess) this).cubicx$getCubeSectionsMap().values()) {
            if (buffer.isReadable()) {
                section.readBiomes(buffer);
            }
        }
    }
}