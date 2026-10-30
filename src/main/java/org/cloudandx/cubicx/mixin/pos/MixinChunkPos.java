package org.cloudandx.cubicx.mixin.pos;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkPos.class)
public abstract class MixinChunkPos {
    @Shadow
    @Final
    private int x;

    @Shadow
    @Final
    private int z;

    /**
     * @author cloudandx
     * @reason Use bitwise OR assembly for maximum region chunk coordinate without addition overflow.
     */
    @Overwrite
    public static ChunkPos maxFromRegion(final int regionX, final int regionZ) {
        return new ChunkPos((regionX << 5) | 31, (regionZ << 5) | 31);
    }

    /**
     * @author cloudandx
     * @reason Allow full chunk coordinate range without artificial pyramid coordinate clamping.
     */
    @Overwrite
    public static boolean isValid(final int x, final int z) {
        return true;
    }

    /**
     * @author cloudandx
     * @reason Prevent 32-bit signed integer overflow when computing squared chunk distances.
     */
    @Overwrite
    private int distanceSquared(final int x, final int z) {
        long deltaX = (long) x - (long) this.x;
        long deltaZ = (long) z - (long) this.z;
        long distSq = deltaX * deltaX + deltaZ * deltaZ;
        return distSq > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) distSq;
    }
}