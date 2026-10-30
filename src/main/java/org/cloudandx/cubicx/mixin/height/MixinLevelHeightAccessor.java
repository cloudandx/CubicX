package org.cloudandx.cubicx.mixin.height;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelHeightAccessor.class)
public interface MixinLevelHeightAccessor {
    @Shadow
    int getMinY();

    @Shadow
    int getHeight();

    /**
     * @author cloudandx
     * @reason Unlock full 32-bit maximum build height so ServerGamePacketListenerImpl never blocks building at Y=319.
     */
    @Overwrite
    default int getMaxY() {
        return CubicMath.MAX_BLOCK;
    }

    /**
     * @author cloudandx
     * @reason Return minimum section Y from dimension minY.
     */
    @Overwrite
    default int getMinSectionY() {
        return SectionPos.blockToSectionCoord(this.getMinY());
    }

    /**
     * @author cloudandx
     * @reason Keep legacy section count bounded to base dimension height so packet and light loops never iterate 268 million sections.
     */
    @Overwrite
    default int getMaxSectionY() {
        return SectionPos.blockToSectionCoord(this.getMinY() + this.getHeight() - 1);
    }

    /**
     * @author cloudandx
     * @reason All 32-bit signed Y coordinates (Integer.MIN_VALUE to Integer.MAX_VALUE) are valid build heights.
     */
    @Overwrite
    default boolean isInsideBuildHeight(final BlockPos pos) {
        return true;
    }

    /**
     * @author cloudandx
     * @reason All 32-bit signed Y coordinates (Integer.MIN_VALUE to Integer.MAX_VALUE) are valid build heights.
     */
    @Overwrite
    default boolean isInsideBuildHeight(final int blockY) {
        return true;
    }

    /**
     * @author cloudandx
     * @reason No 32-bit signed Y coordinate is outside build height.
     */
    @Overwrite
    default boolean isOutsideBuildHeight(final BlockPos pos) {
        return false;
    }

    /**
     * @author cloudandx
     * @reason No 32-bit signed Y coordinate is outside build height.
     */
    @Overwrite
    default boolean isOutsideBuildHeight(final int blockY) {
        return false;
    }

    /**
     * @author cloudandx
     * @reason Map full 32-bit blockY to section index relative to minSectionY.
     */
    @Overwrite
    default int getSectionIndex(final int blockY) {
        return this.getSectionIndexFromSectionY(SectionPos.blockToSectionCoord(blockY));
    }

    /**
     * @author cloudandx
     * @reason Map 28-bit signed sectionY to section index relative to minSectionY so SurfaceSystem never loops 2.14 billion times.
     */
    @Overwrite
    default int getSectionIndexFromSectionY(final int sectionY) {
        return sectionY - this.getMinSectionY();
    }

    /**
     * @author cloudandx
     * @reason Map section index back to 28-bit signed sectionY.
     */
    @Overwrite
    default int getSectionYFromSectionIndex(final int sectionIndex) {
        return sectionIndex + this.getMinSectionY();
    }
}