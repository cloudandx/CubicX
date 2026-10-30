package org.cloudandx.cubicx.mixin.core.pos;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import org.cloudandx.cubicx.util.CubicMath;
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
        return 0;
    }

    /**
     * @author cloudandx
     * @reason Use bitwise OR assembly so maxBlock coordinates reach exact Integer.MAX_VALUE without addition overflow.
     */
    @Overwrite
    public static int sectionToBlockCoord(final int sectionCoord, final int offset) {
        return (sectionCoord << 4) | (offset & 15);
    }

    /**
     * @author cloudandx
     * @reason Encode full 28-bit signed cube coordinates using two-tier reversible XXH3 spatial encoding.
     */
    @Overwrite
    public static long asLong(final int x, final int y, final int z) {
        return CubicMath.packCubePos(x, y, z);
    }

    /**
     * @author cloudandx
     * @reason Decode full 28-bit signed cube X coordinate from reversible sectionNode.
     */
    @Overwrite
    public static int x(final long sectionNode) {
        return CubicMath.unpackCubeX(sectionNode);
    }

    /**
     * @author cloudandx
     * @reason Decode full 28-bit signed cube Y coordinate from reversible sectionNode.
     */
    @Overwrite
    public static int y(final long sectionNode) {
        return CubicMath.unpackCubeY(sectionNode);
    }

    /**
     * @author cloudandx
     * @reason Decode full 28-bit signed cube Z coordinate from reversible sectionNode.
     */
    @Overwrite
    public static int z(final long sectionNode) {
        return CubicMath.unpackCubeZ(sectionNode);
    }

    /**
     * @author cloudandx
     * @reason Zero out cube Y coordinate while preserving full reversible cube X and Z coordinates.
     */
    @Overwrite
    public static long getZeroNode(final long sectionNode) {
        return asLong(x(sectionNode), 0, z(sectionNode));
    }

    /**
     * @author cloudandx
     * @reason Prevent integer overflow at Integer.MIN_VALUE and Integer.MAX_VALUE when scanning neighbor sections.
     */
    @Overwrite
    public static void aroundAndAtBlockPos(final int blockX, final int blockY, final int blockZ, final LongConsumer sectionConsumer) {
        int minSectionX = blockToSectionCoord(CubicMath.clampAdd(blockX, -1));
        int maxSectionX = blockToSectionCoord(CubicMath.clampAdd(blockX, 1));
        int minSectionY = blockToSectionCoord(CubicMath.clampAdd(blockY, -1));
        int maxSectionY = blockToSectionCoord(CubicMath.clampAdd(blockY, 1));
        int minSectionZ = blockToSectionCoord(CubicMath.clampAdd(blockZ, -1));
        int maxSectionZ = blockToSectionCoord(CubicMath.clampAdd(blockZ, 1));
        if (minSectionX == maxSectionX && minSectionY == maxSectionY && minSectionZ == maxSectionZ) {
            sectionConsumer.accept(asLong(minSectionX, minSectionY, minSectionZ));
        } else {
            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                        sectionConsumer.accept(asLong(sectionX, sectionY, sectionZ));
                    }
                }
            }
        }
    }
}