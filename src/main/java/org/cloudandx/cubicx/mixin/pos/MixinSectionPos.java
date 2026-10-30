package org.cloudandx.cubicx.mixin.pos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import org.cloudandx.cubicx.util.CubicPosPacker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionPos.class)
public abstract class MixinSectionPos extends Vec3i {
    protected MixinSectionPos(int x, int y, int z) {
        super(x, y, z);
    }

    @Shadow
    public static int blockToSectionCoord(final int blockCoord) {
        return blockCoord >> 4;
    }

    /**
     * @author cloudandx
     * @reason Pack full 28-bit signed section coordinates without 22-bit overflow at Y=33554431.
     */
    @Overwrite
    public static long asLong(final int x, final int y, final int z) {
        return CubicPosPacker.packSection(x, y, z);
    }

    /**
     * @author cloudandx
     * @reason Pack BlockPos into full 28-bit section node without 22-bit Y overflow.
     */
    @Overwrite
    public static long asLong(final BlockPos pos) {
        return CubicPosPacker.packSection(
            blockToSectionCoord(pos.getX()),
            blockToSectionCoord(pos.getY()),
            blockToSectionCoord(pos.getZ())
        );
    }

    /**
     * @author cloudandx
     * @reason Unpack full section X coordinate from page-indexed 64-bit section node.
     */
    @Overwrite
    public static int x(final long sectionNode) {
        return CubicPosPacker.unpackSectionX(sectionNode);
    }

    /**
     * @author cloudandx
     * @reason Unpack full 28-bit section Y coordinate without sign-extension wrap at 2097152.
     */
    @Overwrite
    public static int y(final long sectionNode) {
        return CubicPosPacker.unpackSectionY(sectionNode);
    }

    /**
     * @author cloudandx
     * @reason Unpack full section Z coordinate from page-indexed 64-bit section node.
     */
    @Overwrite
    public static int z(final long sectionNode) {
        return CubicPosPacker.unpackSectionZ(sectionNode);
    }

    /**
     * @author cloudandx
     * @reason Reconstruct SectionPos from page-indexed 64-bit section node.
     */
    @Overwrite
    public static SectionPos of(final long sectionNode) {
        return SectionPos.of(x(sectionNode), y(sectionNode), z(sectionNode));
    }

    /**
     * @author cloudandx
     * @reason Offset page-indexed 64-bit section node safely across page boundaries.
     */
    @Overwrite
    public static long offset(final long sectionNode, final int dx, final int dy, final int dz) {
        return asLong(x(sectionNode) + dx, y(sectionNode) + dy, z(sectionNode) + dz);
    }

    /**
     * @author cloudandx
     * @reason Offset page-indexed 64-bit section node along Direction safely across page boundaries.
     */
    @Overwrite
    public static long offset(final long sectionNode, final Direction direction) {
        return offset(sectionNode, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    /**
     * @author cloudandx
     * @reason Convert packed 64-bit BlockPos to packed 64-bit SectionPos across full 32-bit Y range.
     */
    @Overwrite
    public static long blockToSection(final long blockNode) {
        return asLong(
            blockToSectionCoord(BlockPos.getX(blockNode)),
            blockToSectionCoord(BlockPos.getY(blockNode)),
            blockToSectionCoord(BlockPos.getZ(blockNode))
        );
    }

    /**
     * @author cloudandx
     * @reason Zero out section Y coordinate while preserving page-indexed X and Z coordinates.
     */
    @Overwrite
    public static long getZeroNode(final long sectionNode) {
        return asLong(x(sectionNode), 0, z(sectionNode));
    }

    /**
     * @author cloudandx
     * @reason Pack column zero-Y node using page-indexed SectionPos encoding.
     */
    @Overwrite
    public static long getZeroNode(final int x, final int z) {
        return asLong(x, 0, z);
    }

    /**
     * @author cloudandx
     * @reason Encode SectionPos instance using page-indexed 64-bit representation.
     */
    @Overwrite
    public long asLong() {
        return asLong(this.getX(), this.getY(), this.getZ());
    }
}