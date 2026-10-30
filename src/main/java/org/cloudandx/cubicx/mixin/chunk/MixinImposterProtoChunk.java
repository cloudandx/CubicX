package org.cloudandx.cubicx.mixin.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortLists;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
public abstract class MixinImposterProtoChunk implements CubicColumnAccess {
    @Shadow
    @Final
    private LevelChunk wrapped;

    @Shadow
    @Final
    private boolean allowWrites;

    @Override
    public @Nullable LevelChunkSection cubicx$getCubeSectionIfPresent(int cubeY) {
        return this.wrapped != null ? ((CubicColumnAccess) this.wrapped).cubicx$getCubeSectionIfPresent(cubeY) : null;
    }

    @Override
    public LevelChunkSection cubicx$getOrCreateCubeSection(int cubeY) {
        return ((CubicColumnAccess) this.wrapped).cubicx$getOrCreateCubeSection(cubeY);
    }

    @Override
    public void cubicx$putCubeSection(int cubeY, LevelChunkSection section) {
        if (this.allowWrites && this.wrapped != null) {
            ((CubicColumnAccess) this.wrapped).cubicx$putCubeSection(cubeY, section);
        }
    }

    @Override
    public Int2ObjectMap<LevelChunkSection> cubicx$getCubeSectionsMap() {
        return this.wrapped != null
            ? ((CubicColumnAccess) this.wrapped).cubicx$getCubeSectionsMap()
            : Int2ObjectMaps.emptyMap();
    }

    @Override
    public ShortList cubicx$getOrCreatePostProcessing(int cubeY) {
        return this.wrapped != null
            ? ((CubicColumnAccess) this.wrapped).cubicx$getOrCreatePostProcessing(cubeY)
            : ShortLists.emptyList();
    }

    @Override
    public Int2ObjectMap<ShortList> cubicx$getPostProcessingMap() {
        return this.wrapped != null
            ? ((CubicColumnAccess) this.wrapped).cubicx$getPostProcessingMap()
            : Int2ObjectMaps.emptyMap();
    }
}