package org.cloudandx.cubicx.mixin.miscs;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldGenerationContext.class)
public abstract class MixinWorldGenerationContext {
    @Mutable
    @Final
    @Shadow
    private int minY;

    @Mutable
    @Final
    @Shadow
    private int height;

    @Redirect(
        method = "<init>*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelHeightAccessor;getMinY()I"
        )
    )
    private int cubicx$safeInitMinY(LevelHeightAccessor instance, ChunkGenerator generator) {
        return generator.getMinY();
    }

    @Redirect(
        method = "<init>*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelHeightAccessor;getHeight()I"
        )
    )
    private int cubicx$safeInitHeight(LevelHeightAccessor instance, ChunkGenerator generator) {
        return generator.getGenDepth();
    }

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void cubicx$assignGeneratorBounds(CallbackInfo ci) {
        this.minY = getMinGenY();
        this.height = getGenDepth();
    }

    /**
     * @author cloudandx
     * @reason Return generator minimum Y without 32-bit addition overflow.
     */
    @Overwrite
    public int getMinGenY() {
        return this.minY;
    }

    /**
     * @author cloudandx
     * @reason Return generator depth without 32-bit addition overflow.
     */
    @Overwrite
    public int getGenDepth() {
        return this.height;
    }
}