package org.cloudandx.cubicx.mixin.noise;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator {
    @Shadow
    @Final
    protected BiomeSource biomeSource;

    @Shadow
    public abstract int getMinY();

    @Shadow
    public abstract int getGenDepth();

    @Shadow
    protected abstract BiomeResolver decorateBiomeResolver(
            final Blender blender, final ChunkAccess protoChunk, final BiomeResolver biomeResolver
    );

    /**
     * @author cloudandx
     * @reason Sample biomes only for active 16x16x16 cubes or active generator range instead of allocating an 8.5-billion-element volume.
     */
    @Overwrite
    private void doCreateBiomes(final Blender blender, final RandomState randomState, final ChunkAccess protoChunk) {
        DensityBufferPool bufferPool = randomState.acquireDensityBufferPool();
        try {
            Sampler climateSampler = randomState.createClimateSampler(
                    SamplerContext.builder().enableCaches().useBufferArena(bufferPool).build()
            );
            ChunkPos pos = protoChunk.getPos();
            int genMinY = this.getMinY();
            int genHeight = Math.max(16, this.getGenDepth());
            CubicColumnAccess cubicChunk = (CubicColumnAccess) protoChunk;
            Int2ObjectMap<LevelChunkSection> cubeMap = cubicChunk.cubicx$getCubeSectionsMap();
            if (cubeMap.isEmpty()) {
                int minCubeY = CubicMath.blockToCube(genMinY);
                int maxCubeY = CubicMath.blockToCube(CubicMath.clampAdd(genMinY, genHeight - 1));
                for (int cy = minCubeY; cy <= maxCubeY; cy++) {
                    cubicChunk.cubicx$getOrCreateCubeSection(cy);
                }
            }
            BiomeResolver biomeResolver = this.biomeSource.createResolverForChunk(
                    climateSampler,
                    QuartPos.fromBlock(pos.getMinBlockX()),
                    QuartPos.fromBlock(genMinY),
                    QuartPos.fromBlock(pos.getMinBlockZ()),
                    QuartPos.fromBlock(16),
                    QuartPos.fromBlock(genHeight),
                    QuartPos.fromBlock(16)
            );
            protoChunk.fillBiomesFromNoise(this.decorateBiomeResolver(blender, protoChunk, biomeResolver));
        } finally {
            randomState.releaseDensityBufferPool(bufferPool);
        }
    }

    /**
     * @author cloudandx
     * @reason Unlock full unbuffered 32-bit BoundingBox (Integer.MIN_VALUE to Integer.MAX_VALUE) without adding +1 buffer to minY.
     */
    @Overwrite
    private static BoundingBox getWritableArea(final ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int targetBlockX = chunkPos.getMinBlockX();
        int targetBlockZ = chunkPos.getMinBlockZ();
        LevelHeightAccessor heightAccessor = chunk.getHeightAccessorForGeneration();
        return new BoundingBox(
                targetBlockX,
                heightAccessor.getMinY(),
                targetBlockZ,
                targetBlockX | 15,
                heightAccessor.getMaxY(),
                targetBlockZ | 15
        );
    }
}