package org.cloudandx.cubicx.mixin.noise;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler.Bound;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.api.CubicWorldGenBridge;
import org.cloudandx.cubicx.api.CubicWorldGenBridge.CustomCubicBackend;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class MixinNoiseBasedChunkGenerator {
    @Shadow
    @Final
    private static BlockState AIR;

    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    protected abstract NoiseChunk createNoiseChunk(
        final ChunkAccess chunk,
        final StructureManager structureManager,
        final Blender blender,
        final RandomState randomState,
        final NoiseSettings noiseSettings
    );

    @Shadow
    protected abstract void buildSurface(
        final ChunkAccess protoChunk,
        final NoiseChunk noiseChunk,
        final RandomState randomState,
        final BiomeManager biomeManager,
        final Set<Holder<Biome>> possibleBiomes,
        final MaterialRule materialRule
    );

    @Shadow
    protected abstract void generateCarvers(
        final ChunkAccess chunk,
        final Blender blender,
        final NoiseChunk noiseChunk,
        final RandomState randomState,
        final BiomeManager biomeManager,
        final @Nullable WorldGenRegion carverBiomeRegion,
        final MaterialRule materialRule
    );

    @Shadow
    protected abstract BlockState debugPreliminarySurfaceLevel(
        final NoiseChunk noiseChunk, final int posX, final int posY, final int posZ, final BlockState state
    );

    /**
     * @author cloudandx
     * @reason Route terrain generation to CubicWorldGenBridge when enabled and avoid pre-allocating empty 16x16x16 air cubes.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> buildTerrain(
        final ChunkAccess chunk,
        final Blender blender,
        final RandomState randomState,
        final StructureManager structureManager,
        final BiomeManager biomeManager,
        final @Nullable WorldGenRegion carverBiomeRegion,
        final Set<Holder<Biome>> possibleBiomes
    ) {
        CustomCubicBackend customBackend = CubicWorldGenBridge.getActiveBackend();
        if (customBackend != null && customBackend.isEnabled()) {
            return customBackend.buildCustomCubicTerrain(
                chunk, blender, randomState, structureManager, biomeManager, carverBiomeRegion, possibleBiomes
            );
        }
        NoiseSettings noiseSettings = this.settings.value().noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        if (noiseSettings.height() <= 0 || SharedConstants.debugVoidTerrain(chunk.getPos())) {
            return CompletableFuture.completedFuture(chunk);
        }
        return CompletableFuture.supplyAsync(() -> {
            ProfilerFiller profiler = Profiler.get();
            try (NoiseChunk noiseChunk = this.createNoiseChunk(chunk, structureManager, blender, randomState, noiseSettings)) {
                try (Zone ignored = profiler.zone("doFill")) {
                    this.doFill(noiseChunk, chunk);
                }
                MaterialRule materialRule = this.settings.value().materialRule().value();
                try (Zone ignored = profiler.zone("buildSurface")) {
                    this.buildSurface(chunk, noiseChunk, randomState, biomeManager, possibleBiomes, materialRule);
                }
                try (Zone ignored = profiler.zone("generateCarvers")) {
                    this.generateCarvers(chunk, blender, noiseChunk, randomState, biomeManager, carverBiomeRegion, materialRule);
                }
                return chunk;
            }
        }, Util.backgroundExecutor().forName("buildTerrain"));
    }

    /**
     * @author cloudandx
     * @reason Populate sparse 16x16x16 cube sections on demand only when non-air blocks are generated.
     */
    @Overwrite
    private void doFill(final NoiseChunk noiseChunk, final ChunkAccess chunk) {
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
        Aquifer aquifer = noiseChunk.aquifer();
        MutableBlockPos blockPos = new MutableBlockPos();
        DensityVolume volume = noiseChunk.volume();
        Bound finalDensity = noiseChunk.cachingSamplers().get(this.settings.value().noiseRouter().finalDensity());
        CubicColumnAccess cubicChunk = (CubicColumnAccess) chunk;
        Int2ObjectOpenHashMap<LevelChunkSection> lockedSections = new Int2ObjectOpenHashMap<>();
        BlockState defaultBlock = this.settings.value().defaultBlock();
        try (ScopedDensityBuffer densityBuffer = finalDensity.sampleVolume(volume)) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    for (int y = volume.sizeY() - 1; y >= 0; y--) {
                        int blockY = volume.blockY(y);
                        float density = densityBuffer.get(volume.indexUnchecked(x, y, z));
                        BlockState state = aquifer.computeSubstance(blockX, blockY, blockZ, density);
                        if (state == null) {
                            state = defaultBlock;
                        }
                        BlockState finalState = this.debugPreliminarySurfaceLevel(noiseChunk, blockX, blockY, blockZ, state);
                        if (finalState != AIR) {
                            int cubeY = CubicMath.blockToCube(blockY);
                            LevelChunkSection section = lockedSections.get(cubeY);
                            if (section == null) {
                                section = cubicChunk.cubicx$getOrCreateCubeSection(cubeY);
                                section.acquire();
                                lockedSections.put(cubeY, section);
                            }
                            section.setBlockState(x, SectionPos.sectionRelative(blockY), z, finalState, false);
                            oceanFloor.update(x, blockY, z, finalState);
                            worldSurface.update(x, blockY, z, finalState);
                            if (aquifer.shouldScheduleFluidUpdate() && !finalState.getFluidState().isEmpty()) {
                                blockPos.set(blockX, blockY, blockZ);
                                chunk.markPosForPostProcessing(blockPos);
                            }
                        }
                    }
                }
            }
        } finally {
            for (Int2ObjectMap.Entry<LevelChunkSection> entry : lockedSections.int2ObjectEntrySet()) {
                entry.getValue().release();
            }
        }
    }
}