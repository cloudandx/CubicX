package org.cloudandx.cubicx.mixin.density;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DensityVolume.class)
public abstract class MixinDensityVolume {
    @Shadow
    @Final
    private int sizeX;

    @Shadow
    @Final
    private int sizeY;

    @Shadow
    @Final
    private int sizeZ;

    @Shadow
    @Final
    private int minBlockX;

    @Shadow
    @Final
    private int minBlockY;

    @Shadow
    @Final
    private int minBlockZ;

    @Shadow
    @Final
    private int stepBlockX;

    @Shadow
    @Final
    private int stepBlockY;

    @Shadow
    @Final
    private int stepBlockZ;

    @Shadow
    public abstract int indexUnchecked(final int indexX, final int indexY, final int indexZ);

    /**
     * @author cloudandx
     * @reason Compute maximum X block coordinate using 64-bit long arithmetic to prevent overflow at Integer.MAX_VALUE.
     */
    @Overwrite
    public int maxBlockX() {
        long max = (long) this.minBlockX + (long) this.sizeX * (long) this.stepBlockX - 1L;
        return Math.clamp(max, CubicMath.MIN_BLOCK, CubicMath.MAX_BLOCK);
    }

    /**
     * @author cloudandx
     * @reason Compute maximum Y block coordinate using 64-bit long arithmetic to prevent overflow at Integer.MAX_VALUE.
     */
    @Overwrite
    public int maxBlockY() {
        long max = (long) this.minBlockY + (long) this.sizeY * (long) this.stepBlockY - 1L;
        return Math.clamp(max, CubicMath.MIN_BLOCK, CubicMath.MAX_BLOCK);
    }

    /**
     * @author cloudandx
     * @reason Compute maximum Z block coordinate using 64-bit long arithmetic to prevent overflow at Integer.MAX_VALUE.
     */
    @Overwrite
    public int maxBlockZ() {
        long max = (long) this.minBlockZ + (long) this.sizeZ * (long) this.stepBlockZ - 1L;
        return Math.clamp(max, CubicMath.MIN_BLOCK, CubicMath.MAX_BLOCK);
    }

    /**
     * @author cloudandx
     * @reason Compute relative block index using 64-bit long deltas to avoid signed 32-bit subtraction overflow.
     */
    @Overwrite
    public int indexOfBlock(final int blockX, final int blockY, final int blockZ) {
        long relativeX = (long) blockX - (long) this.minBlockX;
        long relativeY = (long) blockY - (long) this.minBlockY;
        long relativeZ = (long) blockZ - (long) this.minBlockZ;
        if (relativeX < 0L || relativeY < 0L || relativeZ < 0L) {
            return -1;
        }
        long spanX = (long) this.sizeX * (long) this.stepBlockX;
        long spanY = (long) this.sizeY * (long) this.stepBlockY;
        long spanZ = (long) this.sizeZ * (long) this.stepBlockZ;
        if (relativeX >= spanX || relativeY >= spanY || relativeZ >= spanZ) {
            return -1;
        }
        if (this.stepBlockX == 1 && this.stepBlockY == 1 && this.stepBlockZ == 1) {
            return this.indexUnchecked((int) relativeX, (int) relativeY, (int) relativeZ);
        }
        if (Math.floorMod(relativeX, this.stepBlockX) == 0
            && Math.floorMod(relativeY, this.stepBlockY) == 0
            && Math.floorMod(relativeZ, this.stepBlockZ) == 0) {
            return this.indexUnchecked(
                Mth.floorDiv((int) relativeX, this.stepBlockX),
                Mth.floorDiv((int) relativeY, this.stepBlockY),
                Mth.floorDiv((int) relativeZ, this.stepBlockZ)
            );
        }
        return -1;
    }
}