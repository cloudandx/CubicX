package org.cloudandx.cubicx.mixin.miscs;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Structure.GenerationContext.class)
public abstract class MixinStructureGenerationContext {
    @Shadow
    @Final
    private ChunkGenerator chunkGenerator;

    @Shadow
    @Final
    private BiomeSource biomeSource;

    @Shadow
    @Final
    private Sampler climateSampler;

    @Shadow
    @Final
    private Predicate<Holder<Biome>> validBiome;

    /**
     * @author cloudandx
     * @reason Bound column biome check to active generator terrain depth to prevent 2GB DensityBuffer allocation.
     */
    @Overwrite
    public boolean couldStructureExistInColumn(
        final int blockX, final int blockZ, final int minBlockY, final int maxBlockY
    ) {
        int genMinY = this.chunkGenerator.getMinY();
        int genMaxY = CubicMath.clampAdd(genMinY, Math.max(16, this.chunkGenerator.getGenDepth()) - 1);
        int boundedMinY = Math.max(minBlockY, genMinY);
        int boundedMaxY = Math.min(maxBlockY, genMaxY);
        if (boundedMaxY < boundedMinY) {
            boundedMinY = genMinY;
            boundedMaxY = genMaxY;
        }
        int quartX = QuartPos.fromBlock(blockX);
        int quartZ = QuartPos.fromBlock(blockZ);
        int minQuartY = QuartPos.fromBlock(boundedMinY);
        int maxQuartY = QuartPos.fromBlock(boundedMaxY);
        int quartSizeY = Math.clamp((long) maxQuartY - (long) minQuartY + 1L, 1, 512);
        maxQuartY = minQuartY + quartSizeY - 1;
        BiomeResolver columnResolver = this.biomeSource.createResolverForChunk(
            this.climateSampler, quartX, minQuartY, quartZ, 1, quartSizeY, 1
        );
        for (int quartY = minQuartY; quartY <= maxQuartY; quartY++) {
            if (this.validBiome.test(columnResolver.getNoiseBiome(quartX, quartY, quartZ))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @author cloudandx
     * @reason Use generator minY and genDepth instead of subtracting 1 from Integer.MIN_VALUE.
     */
    @Overwrite
    public boolean couldValidBiomeExistInTerrainColumn(final int blockX, final int blockZ) {
        int genMinY = this.chunkGenerator.getMinY();
        int genMaxY = CubicMath.clampAdd(genMinY, Math.max(16, this.chunkGenerator.getGenDepth()) - 1);
        return this.couldStructureExistInColumn(blockX, blockZ, genMinY, genMaxY);
    }
}