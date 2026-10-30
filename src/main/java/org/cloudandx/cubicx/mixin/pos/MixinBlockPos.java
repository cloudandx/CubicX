package org.cloudandx.cubicx.mixin.pos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.cloudandx.cubicx.util.CubicPosPacker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockPos.class)
public abstract class MixinBlockPos extends Vec3i {
    protected MixinBlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    /**
     * @author cloudandx
     * @reason Pack full 32-bit X, Y, Z coordinates without 26-bit Y overflow at 33554431.
     */
    @Overwrite
    public static long asLong(final int x, final int y, final int z) {
        return CubicPosPacker.packBlock(x, y, z);
    }

    /**
     * @author cloudandx
     * @reason Unpack full 32-bit block X from page-indexed 64-bit block node.
     */
    @Overwrite
    public static int getX(final long blockNode) {
        return CubicPosPacker.unpackBlockX(blockNode);
    }

    /**
     * @author cloudandx
     * @reason Unpack full 32-bit block Y without sign-extension wrap at 33554432.
     */
    @Overwrite
    public static int getY(final long blockNode) {
        return CubicPosPacker.unpackBlockY(blockNode);
    }

    /**
     * @author cloudandx
     * @reason Unpack full 32-bit block Z from page-indexed 64-bit block node.
     */
    @Overwrite
    public static int getZ(final long blockNode) {
        return CubicPosPacker.unpackBlockZ(blockNode);
    }

    /**
     * @author cloudandx
     * @reason Reconstruct BlockPos from page-indexed 64-bit block node.
     */
    @Overwrite
    public static BlockPos of(final long blockNode) {
        return new BlockPos(getX(blockNode), getY(blockNode), getZ(blockNode));
    }

    /**
     * @author cloudandx
     * @reason Offset page-indexed 64-bit block node safely across full 32-bit coordinates.
     */
    @Overwrite
    public static long offset(final long blockNode, final int dx, final int dy, final int dz) {
        return asLong(getX(blockNode) + dx, getY(blockNode) + dy, getZ(blockNode) + dz);
    }

    /**
     * @author cloudandx
     * @reason Offset page-indexed 64-bit block node along Direction safely.
     */
    @Overwrite
    public static long offset(final long blockNode, final Direction direction) {
        return offset(blockNode, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    /**
     * @author cloudandx
     * @reason Zero out block Y coordinate while preserving page-indexed X and Z coordinates.
     */
    @Overwrite
    public static long getFlatIndex(final long blockNode) {
        return asLong(getX(blockNode), 0, getZ(blockNode));
    }

    /**
     * @author cloudandx
     * @reason Encode BlockPos instance into page-indexed 64-bit representation.
     */
    @Overwrite
    public long asLong() {
        return asLong(this.getX(), this.getY(), this.getZ());
    }
}