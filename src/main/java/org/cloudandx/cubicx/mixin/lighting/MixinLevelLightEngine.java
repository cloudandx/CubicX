package org.cloudandx.cubicx.mixin.lighting;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelLightEngine.class)
public abstract class MixinLevelLightEngine {
    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    /**
     * @author cloudandx
     * @reason Use active dimension section count for legacy light packet loops to prevent 268-million-iteration packet stalls.
     */
    @Overwrite
    public int getLightSectionCount() {
        return this.levelHeightAccessor.getSectionsCount() + 2;
    }

    /**
     * @author cloudandx
     * @reason Use active dimension minimum section Y for legacy light packet loops.
     */
    @Overwrite
    public int getMinLightSection() {
        return this.levelHeightAccessor.getMinSectionY() - 1;
    }

    /**
     * @author cloudandx
     * @reason Use active dimension maximum light section Y for legacy light packet loops.
     */
    @Overwrite
    public int getMaxLightSection() {
        return this.getMinLightSection() + this.getLightSectionCount();
    }
}