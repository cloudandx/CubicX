package org.cloudandx.cubicx.mixin.core.height;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LevelHeightAccessor.class)
public interface MixinLevelHeightAccessor {
    /**
     * @author cloudandx
     * @reason Unlock full 32-bit maximum Y coordinate (Integer.MAX_VALUE) without 32-bit addition overflow.
     */
    @Overwrite
    default int getMaxY() {
        return CubicMath.MAX_BLOCK;
    }

    /**
     * @author cloudandx
     * @reason Return total 16x16x16 cube count across the entire unbuffered 32-bit signed vertical range.
     */
    @Overwrite
    default int getSectionsCount() {
        return CubicMath.TOTAL_CUBE_COUNT;
    }

    /**
     * @author cloudandx
     * @reason Return minimum 28-bit signed cube Y coordinate (-134,217,728).
     */
    @Overwrite
    default int getMinSectionY() {
        return CubicMath.MIN_CUBE;
    }

    /**
     * @author cloudandx
     * @reason Return maximum 28-bit signed cube Y coordinate (134,217,727).
     */
    @Overwrite
    default int getMaxSectionY() {
        return CubicMath.MAX_CUBE;
    }

    /**
     * @author cloudandx
     * @reason All 32-bit signed Y coordinates are valid build heights.
     */
    @Overwrite
    default boolean isInsideBuildHeight(final BlockPos pos) {
        return true;
    }

    /**
     * @author cloudandx
     * @reason All 32-bit signed Y coordinates are valid build heights.
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
     * @reason Map full 32-bit blockY directly to zero-based cube section index.
     */
    @Overwrite
    default int getSectionIndex(final int blockY) {
        return CubicMath.cubeToSectionIndex(CubicMath.blockToCube(blockY));
    }

    /**
     * @author cloudandx
     * @reason Map 28-bit signed sectionY to zero-based cube section index without overflow.
     */
    @Overwrite
    default int getSectionIndexFromSectionY(final int sectionY) {
        return CubicMath.cubeToSectionIndex(sectionY);
    }

    /**
     * @author cloudandx
     * @reason Map zero-based cube section index back to 28-bit signed sectionY.
     */
    @Overwrite
    default int getSectionYFromSectionIndex(final int sectionIndex) {
        return CubicMath.sectionIndexToCube(sectionIndex);
    }
}