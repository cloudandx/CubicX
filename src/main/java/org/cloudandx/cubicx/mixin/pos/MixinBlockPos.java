package org.cloudandx.cubicx.mixin.core.pos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockPos.class)
public abstract class MixinBlockPos extends Vec3i {
    protected MixinBlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    /**
     * @author cloudandx
     * @reason Encode full 32-bit X, Y, Z coordinates using two-tier reversible XXH3 spatial encoding.
     */
    @Overwrite
    public static long asLong(final int x, final int y, final int z) {
        return CubicMath.packBlockPos(x, y, z);
    }

    /**
     * @author cloudandx
     * @reason Decode full 32-bit X coordinate from reversible spatial node.
     */
    @Overwrite
    public static int getX(final long blockNode) {
        return CubicMath.unpackBlockX(blockNode);
    }

    /**
     * @author cloudandx
     * @reason Decode full 32-bit Y coordinate from reversible spatial node.
     */
    @Overwrite
    public static int getY(final long blockNode) {
        return CubicMath.unpackBlockY(blockNode);
    }

    /**
     * @author cloudandx
     * @reason Decode full 32-bit Z coordinate from reversible spatial node.
     */
    @Overwrite
    public static int getZ(final long blockNode) {
        return CubicMath.unpackBlockZ(blockNode);
    }

    /**
     * @author cloudandx
     * @reason Offset encoded 64-bit blockNode safely across 32-bit boundaries without overflow.
     */
    @Overwrite
    public static long offset(final long blockNode, final int stepX, final int stepY, final int stepZ) {
        return asLong(
            CubicMath.clampAdd(getX(blockNode), stepX),
            CubicMath.clampAdd(getY(blockNode), stepY),
            CubicMath.clampAdd(getZ(blockNode), stepZ)
        );
    }

    /**
     * @author cloudandx
     * @reason Prevent 32-bit signed integer overflow at exact Integer.MIN_VALUE and Integer.MAX_VALUE boundaries.
     */
    @Overwrite
    public @NonNull BlockPos offset(final int x, final int y, final int z) {
        if (x == 0 && y == 0 && z == 0) {
            return (BlockPos) (Object) this;
        }
        return new BlockPos(
            CubicMath.clampAdd(this.getX(), x),
            CubicMath.clampAdd(this.getY(), y),
            CubicMath.clampAdd(this.getZ(), z)
        );
    }

    /**
     * @author cloudandx
     * @reason Prevent 32-bit signed integer overflow when stepping in any direction at 32-bit coordinate limits.
     */
    @Overwrite
    public @NonNull BlockPos relative(final Direction direction) {
        return new BlockPos(
            CubicMath.clampAdd(this.getX(), direction.getStepX()),
            CubicMath.clampAdd(this.getY(), direction.getStepY()),
            CubicMath.clampAdd(this.getZ(), direction.getStepZ())
        );
    }

    /**
     * @author cloudandx
     * @reason Prevent 32-bit signed integer overflow on multi-step directional offsets at 32-bit limits.
     */
    @Overwrite
    public @NonNull BlockPos relative(final @NonNull Direction direction, final int steps) {
        if (steps == 0) {
            return (BlockPos) (Object) this;
        }
        long dx = (long) direction.getStepX() * (long) steps;
        long dy = (long) direction.getStepY() * (long) steps;
        long dz = (long) direction.getStepZ() * (long) steps;
        return new BlockPos(
            Math.clamp((long) this.getX() + dx, CubicMath.MIN_BLOCK, CubicMath.MAX_BLOCK),
            Math.clamp((long) this.getY() + dy, CubicMath.MIN_BLOCK, CubicMath.MAX_BLOCK),
            Math.clamp((long) this.getZ() + dz, CubicMath.MIN_BLOCK, CubicMath.MAX_BLOCK)
        );
    }
}