package org.cloudandx.cubicx.mixin.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.cloudandx.cubicx.api.CubicLightStorageAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkyLightSectionStorage.class)
public abstract class MixinSkyLightSectionStorage implements CubicLightStorageAccess {
    @Unique
    private static final int CUBICX_MAX_VERTICAL_SCAN = 64;

    @Shadow
    protected abstract int getTopSectionY(final long zeroNode);

    @Shadow
    protected abstract int getBottomSectionY();

    @Shadow
    protected abstract boolean hasLightDataAtOrBelow(final int sectionY);

    @Shadow
    private static DataLayer repeatFirstLayer(final DataLayer data) {
        return new DataLayer();
    }

    @Override
    public int cubicx$getTopSectionY(final long zeroNode) {
        return this.getTopSectionY(zeroNode);
    }

    @Override
    public int cubicx$getBottomSectionY() {
        return this.getBottomSectionY();
    }

    @Redirect(
        method = "onNodeRemoved",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;hasLightDataAtOrBelow(I)Z"
        )
    )
    private boolean cubicx$capOnNodeRemovedDownwardScan(
            SkyLightSectionStorage instance, int sectionY, long sectionNode
    ) {
        if ((long) SectionPos.y(sectionNode) - (long) sectionY > CUBICX_MAX_VERTICAL_SCAN) {
            return false;
        }
        return this.hasLightDataAtOrBelow(sectionY);
    }

    /**
     * @author cloudandx
     * @reason Cap upward skylight section search to 64 sections so high-altitude Y coordinates never stall across empty vertical space.
     */
    @Overwrite
    protected int getLightValue(long blockNode, final boolean updating) {
        long sectionNode = SectionPos.blockToSection(blockNode);
        int sectionY = SectionPos.y(sectionNode);
        int topSection = this.getTopSectionY(SectionPos.getZeroNode(sectionNode));
        int bottomSection = this.getBottomSectionY();
        if (topSection != bottomSection && sectionY < topSection) {
            DataLayer layer = this.cubicx$getDataLayer(sectionNode, updating);
            int localY = SectionPos.sectionRelative(BlockPos.getY(blockNode));
            if (layer == null) {
                localY = 0;
                int steps = 0;
                while (layer == null) {
                    if (++sectionY >= topSection || ++steps > CUBICX_MAX_VERTICAL_SCAN || sectionY > CubicMath.MAX_CUBE) {
                        return 15;
                    }
                    sectionNode = SectionPos.offset(sectionNode, Direction.UP);
                    layer = this.cubicx$getDataLayer(sectionNode, updating);
                }
            }
            return layer.get(
                SectionPos.sectionRelative(BlockPos.getX(blockNode)),
                localY,
                SectionPos.sectionRelative(BlockPos.getZ(blockNode))
            );
        }
        return updating && !this.cubicx$lightOnInSection(sectionNode) ? 0 : 15;
    }

    /**
     * @author cloudandx
     * @reason Initialize underground skylight sections to 0 (dark) when light is not yet enabled, and cap upward layer lookup to 64 sections.
     */
    @Overwrite
    protected DataLayer createDataLayer(final long sectionNode) {
        DataLayer queuedLayer = this.cubicx$getQueuedSection(sectionNode);
        if (queuedLayer != null) {
            return queuedLayer;
        }
        int sectionY = SectionPos.y(sectionNode);
        int topSection = this.getTopSectionY(SectionPos.getZeroNode(sectionNode));
        int bottomSection = this.getBottomSectionY();
        if (topSection != bottomSection && sectionY < topSection) {
            long aboveSection = SectionPos.offset(sectionNode, Direction.UP);
            int steps = 0;
            while (++steps <= CUBICX_MAX_VERTICAL_SCAN
                && SectionPos.y(aboveSection) < topSection
                && SectionPos.y(aboveSection) <= CubicMath.MAX_CUBE) {
                DataLayer aboveData = this.cubicx$getDataLayer(aboveSection, true);
                if (aboveData != null) {
                    return repeatFirstLayer(aboveData);
                }
                aboveSection = SectionPos.offset(aboveSection, Direction.UP);
            }
            return new DataLayer();
        }
        return this.cubicx$lightOnInSection(sectionNode) ? new DataLayer(15) : new DataLayer();
    }
}