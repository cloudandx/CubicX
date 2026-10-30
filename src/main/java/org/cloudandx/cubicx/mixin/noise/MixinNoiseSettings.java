package org.cloudandx.cubicx.mixin.noise;

import com.mojang.serialization.DataResult;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseSettings;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseSettings.class)
public abstract class MixinNoiseSettings {
    @Shadow
    @Final
    private int minY;

    @Shadow
    @Final
    private int height;

    /**
     * @author cloudandx
     * @reason Allow full 32-bit noise generation settings without 12-bit DimensionType.MAX_Y clamping.
     */
    @Overwrite
    private static DataResult<NoiseSettings> guardY(final NoiseSettings settings) {
        if (settings.height() % 16 != 0) {
            return DataResult.error(() -> "height has to be a multiple of 16");
        }
        if (settings.minY() % 16 != 0) {
            return DataResult.error(() -> "min_y has to be a multiple of 16");
        }
        return DataResult.success(settings);
    }

    /**
     * @author cloudandx
     * @reason Use 64-bit long arithmetic so heightAccessor.getMaxY() + 1 never overflows at Integer.MAX_VALUE.
     */
    @Overwrite
    public NoiseSettings clampToHeightAccessor(final LevelHeightAccessor heightAccessor) {
        int newMinY = Math.max(this.minY, heightAccessor.getMinY());
        long selfMaxExclusive = (long) this.minY + (long) this.height;
        long accessorMaxExclusive = (long) heightAccessor.getMaxY() + 1L;
        long clampedMaxExclusive = Math.min(selfMaxExclusive, accessorMaxExclusive);
        int newHeight = (int) Math.clamp(clampedMaxExclusive - (long) newMinY, 0L, CubicMath.MAX_BLOCK);
        return new NoiseSettings(newMinY, newHeight);
    }
}