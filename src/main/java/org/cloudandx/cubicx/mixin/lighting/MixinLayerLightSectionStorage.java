package org.cloudandx.cubicx.mixin.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.cloudandx.cubicx.api.CubicLightStorageAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LayerLightSectionStorage.class)
public abstract class MixinLayerLightSectionStorage<M extends DataLayerStorageMap<M>> implements CubicLightStorageAccess {
    @Shadow
    @Final
    protected Long2ByteMap sectionStates;

    @Shadow
    @Final
    protected M updatingSectionData;

    @Shadow
    @Final
    protected LongSet changedSections;

    @Shadow
    @Final
    protected LongSet sectionsAffectedByLightUpdates;

    @Shadow
    @Final
    protected Long2ObjectMap<DataLayer> queuedSections;

    @Shadow
    protected abstract void putSectionState(final long sectionNode, final byte state);

    @Shadow
    public abstract @Nullable DataLayer getDataLayerData(final long sectionNode);

    @Shadow
    protected abstract @Nullable DataLayer getDataLayer(final long sectionNode, final boolean updating);

    @Shadow
    protected abstract DataLayer createDataLayer(final long sectionNode);

    @Shadow
    protected abstract boolean lightOnInSection(final long sectionNode);

    @Override
    public @Nullable DataLayer cubicx$getDataLayerData(final long sectionNode) {
        return this.getDataLayerData(sectionNode);
    }

    @Override
    public @Nullable DataLayer cubicx$getDataLayer(final long sectionNode, final boolean updating) {
        return this.getDataLayer(sectionNode, updating);
    }

    @Override
    public @Nullable DataLayer cubicx$getQueuedSection(final long sectionNode) {
        return this.queuedSections.get(sectionNode);
    }

    @Override
    public boolean cubicx$lightOnInSection(final long sectionNode) {
        return this.lightOnInSection(sectionNode);
    }

    /**
     * @author cloudandx
     * @reason Guard against null DataLayer in concurrent 3D cubic light updates.
     */
    @Overwrite
    protected int getStoredLevel(final long blockNode) {
        long sectionNode = SectionPos.blockToSection(blockNode);
        DataLayer layer = this.getDataLayer(sectionNode, true);
        if (layer == null) {
            return 0;
        }
        return layer.get(
            SectionPos.sectionRelative(BlockPos.getX(blockNode)),
            SectionPos.sectionRelative(BlockPos.getY(blockNode)),
            SectionPos.sectionRelative(BlockPos.getZ(blockNode))
        );
    }

    /**
     * @author cloudandx
     * @reason Safely resolve or initialize DataLayer before writing light level to prevent NullPointerException.
     */
    @Overwrite
    protected void setStoredLevel(final long blockNode, final int level) {
        long sectionNode = SectionPos.blockToSection(blockNode);
        DataLayer layer = this.getDataLayer(sectionNode, true);
        if (layer == null) {
            layer = this.createDataLayer(sectionNode);
            this.updatingSectionData.setLayer(sectionNode, layer);
            this.changedSections.add(sectionNode);
        } else if (this.changedSections.add(sectionNode)) {
            layer = this.updatingSectionData.copyDataLayer(sectionNode);
        }
        layer.set(
            SectionPos.sectionRelative(BlockPos.getX(blockNode)),
            SectionPos.sectionRelative(BlockPos.getY(blockNode)),
            SectionPos.sectionRelative(BlockPos.getZ(blockNode)),
            level
        );
        SectionPos.aroundAndAtBlockPos(blockNode, this.sectionsAffectedByLightUpdates::add);
    }

    /**
     * @author cloudandx
     * @reason Guard 28-bit cube Y boundaries when marking 3x3x3 neighbor light sections as affected.
     */
    @Overwrite
    protected void markSectionAndNeighborsAsAffected(final long sectionNode) {
        int x = SectionPos.x(sectionNode);
        int y = SectionPos.y(sectionNode);
        int z = SectionPos.z(sectionNode);
        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    long ny = (long) y + (long) offsetY;
                    if (ny >= CubicMath.MIN_CUBE && ny <= CubicMath.MAX_CUBE) {
                        this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(x + offsetX, (int) ny, z + offsetZ));
                    }
                }
            }
        }
    }

    /**
     * @author cloudandx
     * @reason Guard 28-bit cube Y boundaries when updating neighbor section counts on section status change.
     */
    @Overwrite
    protected void updateSectionStatus(final long sectionNode, final boolean sectionEmpty) {
        byte state = this.sectionStates.get(sectionNode);
        byte newState = (byte) (!sectionEmpty ? state | 32 : state & -33);
        if (state != newState) {
            this.putSectionState(sectionNode, newState);
            int neighborIncrement = sectionEmpty ? -1 : 1;
            int baseY = SectionPos.y(sectionNode);
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    long targetY = (long) baseY + (long) offsetY;
                    if (targetY < CubicMath.MIN_CUBE || targetY > CubicMath.MAX_CUBE) {
                        continue;
                    }
                    for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
                            long neighborNode = SectionPos.offset(sectionNode, offsetX, offsetY, offsetZ);
                            byte neighborState = this.sectionStates.get(neighborNode);
                            int newCount = (neighborState & 31) + neighborIncrement;
                            if (newCount >= 0 && newCount <= 26) {
                                this.putSectionState(neighborNode, (byte) (neighborState & -32 | newCount & 31));
                            }
                        }
                    }
                }
            }
        }
    }
}