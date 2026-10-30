package org.cloudandx.cubicx.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

public interface CubicColumnAccess {
    @Nullable LevelChunkSection cubicx$getCubeSectionIfPresent(int cubeY);

    LevelChunkSection cubicx$getOrCreateCubeSection(int cubeY);

    void cubicx$putCubeSection(int cubeY, LevelChunkSection section);

    Int2ObjectMap<LevelChunkSection> cubicx$getCubeSectionsMap();

    ShortList cubicx$getOrCreatePostProcessing(int cubeY);

    Int2ObjectMap<ShortList> cubicx$getPostProcessingMap();
}