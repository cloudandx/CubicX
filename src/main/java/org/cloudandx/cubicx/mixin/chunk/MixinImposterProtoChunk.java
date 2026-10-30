package org.cloudandx.cubicx.mixin.core.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
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
        return ((CubicColumnAccess) this.wrapped).cubicx$getCubeSectionIfPresent(cubeY);
    }

    @Override
    public LevelChunkSection cubicx$getOrCreateCubeSection(int cubeY) {
        return ((CubicColumnAccess) this.wrapped).cubicx$getOrCreateCubeSection(cubeY);
    }

    @Override
    public void cubicx$putCubeSection(int cubeY, LevelChunkSection section) {
        if (this.allowWrites) {
            ((CubicColumnAccess) this.wrapped).cubicx$putCubeSection(cubeY, section);
        }
    }

    @Override
    public Int2ObjectMap<LevelChunkSection> cubicx$getCubeSectionsMap() {
        return ((CubicColumnAccess) this.wrapped).cubicx$getCubeSectionsMap();
    }

    @Override
    public ShortList cubicx$getOrCreatePostProcessing(int cubeY) {
        return ((CubicColumnAccess) this.wrapped).cubicx$getOrCreatePostProcessing(cubeY);
    }

    @Override
    public Int2ObjectMap<ShortList> cubicx$getPostProcessingMap() {
        return ((CubicColumnAccess) this.wrapped).cubicx$getPostProcessingMap();
    }
}