package org.cloudandx.cubicx.client.mixin.core.render;

import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionCopy.class)
public abstract class MixinSectionCopy {
    @Unique
    private static final LevelChunkSection[] CUBICX_EMPTY_SECTIONS = new LevelChunkSection[0];

    @Shadow
    @Final
    @Mutable
    private @Nullable PalettedContainer<BlockState> section;

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"
        )
    )
    private LevelChunkSection[] cubicx$bypassLinearArray(LevelChunk instance) {
        return CUBICX_EMPTY_SECTIONS;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cubicx$loadSparseCubeSection(LevelChunk levelChunk, int sectionIndex, CallbackInfo ci) {
        if (!(levelChunk instanceof EmptyLevelChunk)) {
            int cubeY = levelChunk.getSectionYFromSectionIndex(sectionIndex);
            LevelChunkSection cubeSection = ((CubicColumnAccess) levelChunk).cubicx$getCubeSectionIfPresent(cubeY);
            this.section = (cubeSection == null || cubeSection.hasOnlyAir()) ? null : cubeSection.getStates().copy();
        }
    }
}