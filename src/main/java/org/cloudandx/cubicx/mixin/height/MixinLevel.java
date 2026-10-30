package org.cloudandx.cubicx.mixin.height;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Level.class)
public abstract class MixinLevel {
    @Shadow
    private static boolean isInWorldBoundsHorizontal(final BlockPos pos) {
        return false;
    }

    /**
     * @author cloudandx
     * @reason Expand spawnable and teleportable Y bounds to full 32-bit CubicMath limits.
     */
    @Overwrite
    public static boolean isInSpawnableBounds(final BlockPos pos) {
        return !isOutsideSpawnableHeight(pos.getY()) && isInWorldBoundsHorizontal(pos);
    }

    /**
     * @author cloudandx
     * @reason Remove vanilla 20,000,000 Y limit so /tp and block placement work across full 32-bit Y coordinates.
     */
    @Overwrite
    private static boolean isOutsideSpawnableHeight(final int y) {
        return y < CubicMath.MIN_BLOCK || y > CubicMath.MAX_BLOCK;
    }
}