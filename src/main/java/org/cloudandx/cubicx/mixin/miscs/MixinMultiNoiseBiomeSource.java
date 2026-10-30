package org.cloudandx.cubicx.mixin.miscs;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.Climate.TargetPoint;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MixinMultiNoiseBiomeSource {
    @Shadow
    public abstract BiomeResolver createResolver(final Sampler sampler);

    @Shadow
    public abstract Holder<Biome> getNoiseBiome(final TargetPoint target);

    /**
     * @author cloudandx
     * @reason Fallback to zero-allocation point resolver when quartSizeY exceeds batch threshold and clamp volume indices.
     */
    @Overwrite
    public BiomeResolver createResolverForChunk(
        final Sampler sampler,
        final int minQuartX,
        final int minQuartY,
        final int minQuartZ,
        final int quartSizeX,
        final int quartSizeY,
        final int quartSizeZ
    ) {
        if (quartSizeY <= 0 || quartSizeY > 512) {
            return this.createResolver(sampler);
        }
        DensityVolume volume = new DensityVolume(
            quartSizeX,
            quartSizeY,
            quartSizeZ,
            QuartPos.toBlock(minQuartX),
            QuartPos.toBlock(minQuartY),
            QuartPos.toBlock(minQuartZ),
            4,
            4,
            4
        );
        DensityBuffer temperature = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer vegetation = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer continents = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer erosion = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer depth = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer ridges = DensityBuffer.createUnpooled(volume.size());
        sampler.temperature().sampleVolume(temperature, volume);
        sampler.humidity().sampleVolume(vegetation, volume);
        sampler.continentalness().sampleVolume(continents, volume);
        sampler.erosion().sampleVolume(erosion, volume);
        sampler.depth().sampleVolume(depth, volume);
        sampler.weirdness().sampleVolume(ridges, volume);
        return (quartX, quartY, quartZ) -> {
            if (quartY < minQuartY || quartY >= minQuartY + quartSizeY) {
                return this.getNoiseBiome(sampler.sample(quartX, quartY, quartZ));
            }
            int relX = Mth.clamp(quartX - minQuartX, 0, quartSizeX - 1);
            int relY = Mth.clamp(quartY - minQuartY, 0, quartSizeY - 1);
            int relZ = Mth.clamp(quartZ - minQuartZ, 0, quartSizeZ - 1);
            int index = volume.indexUnchecked(relX, relY, relZ);
            return this.getNoiseBiome(
                Climate.target(
                    temperature.get(index),
                    vegetation.get(index),
                    continents.get(index),
                    erosion.get(index),
                    depth.get(index),
                    ridges.get(index)
                )
            );
        };
    }
}