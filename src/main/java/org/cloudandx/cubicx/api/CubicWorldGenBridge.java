package org.cloudandx.cubicx.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jspecify.annotations.Nullable;

public final class CubicWorldGenBridge {
    private static volatile @Nullable CustomCubicBackend activeBackend;

    private CubicWorldGenBridge() {
    }

    public static void registerBackend(final @Nullable CustomCubicBackend backend) {
        activeBackend = backend;
    }

    public static @Nullable CustomCubicBackend getActiveBackend() {
        return activeBackend;
    }

    public interface CustomCubicBackend {
        boolean isEnabled();

        CompletableFuture<ChunkAccess> buildCustomCubicTerrain(
            ChunkAccess chunk,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            BiomeManager biomeManager,
            @Nullable WorldGenRegion carverBiomeRegion,
            Set<Holder<Biome>> possibleBiomes
        );
    }
}