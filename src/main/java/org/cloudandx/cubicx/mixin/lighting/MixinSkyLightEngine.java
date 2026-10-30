package org.cloudandx.cubicx.mixin.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.cloudandx.cubicx.api.CubicLightStorageAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkyLightEngine.class)
public abstract class MixinSkyLightEngine<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>>
    extends LightEngine<M, S> {

    @Unique
    private static final int CUBICX_MAX_COLUMN_SECTION_SPAN = 64;

    protected MixinSkyLightEngine(LightChunkGetter chunkSource, S storage) {
        super(chunkSource, storage);
    }

    @ModifyVariable(method = "removeSourcesBelow", at = @At("HEAD"), argsOnly = true, name = "worldBottomY")
    private int cubicx$capRemoveSourcesWorldBottomY(int worldBottomY, int x, int z, int lowestSourceY) {
        return Math.max(worldBottomY, CubicMath.clampAdd(lowestSourceY, -512));
    }

    @ModifyVariable(method = "addSourcesAbove", at = @At("HEAD"), argsOnly = true, name = "worldBottomY")
    private int cubicx$capAddSourcesWorldBottomY(int worldBottomY, int x, int z, int lowestSourceY) {
        return Math.max(worldBottomY, CubicMath.clampAdd(lowestSourceY, -512));
    }

    @Redirect(
        method = "removeSourcesBelow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;hasLightDataAtOrBelow(I)Z"
        )
    )
    private boolean cubicx$capRemoveSourcesSectionLoop(
        SkyLightSectionStorage storage, int sectionY, int x, int z, int lowestSourceY, int worldBottomY
    ) {
        int startSectionY = SectionPos.blockToSectionCoord(CubicMath.clampAdd(lowestSourceY, -1));
        if ((long) startSectionY - (long) sectionY > CUBICX_MAX_COLUMN_SECTION_SPAN) {
            return false;
        }
        return sectionY >= SectionPos.blockToSectionCoord(worldBottomY);
    }

    @Redirect(
        method = "addSourcesAbove",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;isAboveData(J)Z"
        )
    )
    private boolean cubicx$capAddSourcesSectionLoop(
        SkyLightSectionStorage storage, long sectionNode, int x, int z, int lowestSourceY, int worldBottomY
    ) {
        int startY = Math.max(lowestSourceY, worldBottomY);
        int startSectionY = SectionPos.blockToSectionCoord(startY);
        int currentSectionY = SectionPos.y(sectionNode);
        if ((long) currentSectionY - (long) startSectionY > CUBICX_MAX_COLUMN_SECTION_SPAN
            || currentSectionY >= CubicMath.MAX_CUBE) {
            return true;
        }
        CubicLightStorageAccess access = (CubicLightStorageAccess) storage;
        long zeroNode = SectionPos.getZeroNode(sectionNode);
        int topSection = access.cubicx$getTopSectionY(zeroNode);
        return topSection == access.cubicx$getBottomSectionY() || currentSectionY >= topSection;
    }

    @Redirect(
        method = "setLightEnabled",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;getBottomSectionY()I"
        )
    )
    private int cubicx$capSetLightEnabledBottomSection(SkyLightSectionStorage storage, ChunkPos pos, boolean enable) {
        CubicLightStorageAccess access = (CubicLightStorageAccess) storage;
        int topSectionY = access.cubicx$getTopSectionY(SectionPos.getZeroNode(pos.x(), pos.z()));
        return Math.max(access.cubicx$getBottomSectionY(), topSectionY - CUBICX_MAX_COLUMN_SECTION_SPAN);
    }

    @Redirect(
        method = "propagateLightSources",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;getBottomSectionY()I"
        )
    )
    private int cubicx$capPropagateLightSourcesBottomSection(SkyLightSectionStorage storage, ChunkPos pos) {
        CubicLightStorageAccess access = (CubicLightStorageAccess) storage;
        int topSectionY = access.cubicx$getTopSectionY(SectionPos.getZeroNode(pos.x(), pos.z()));
        return Math.max(access.cubicx$getBottomSectionY(), topSectionY - CUBICX_MAX_COLUMN_SECTION_SPAN);
    }

    /**
     * @author cloudandx
     * @reason Cap downward empty section scanning to 16 cubes so border skylight propagation is accurate without unbounded loops.
     */
    @Overwrite
    private int countEmptySectionsBelowIfAtBorder(final long blockNode) {
        int y = BlockPos.getY(blockNode);
        if (SectionPos.sectionRelative(y) != 0) {
            return 0;
        }
        int x = BlockPos.getX(blockNode);
        int z = BlockPos.getZ(blockNode);
        int localX = SectionPos.sectionRelative(x);
        int localZ = SectionPos.sectionRelative(z);
        if (localX != 0 && localX != 15 && localZ != 0 && localZ != 15) {
            return 0;
        }
        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionY = SectionPos.blockToSectionCoord(y);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        CubicLightStorageAccess storageAccess = (CubicLightStorageAccess) this.storage;
        int emptySectionsBelow = 0;
        while (emptySectionsBelow < 16) {
            long nextY = (long) sectionY - (long) emptySectionsBelow - 1L;
            if (nextY < CubicMath.MIN_CUBE) {
                break;
            }
            if (storageAccess.cubicx$getDataLayer(SectionPos.asLong(sectionX, (int) nextY, sectionZ), true) != null) {
                break;
            }
            emptySectionsBelow++;
        }
        return emptySectionsBelow;
    }
}