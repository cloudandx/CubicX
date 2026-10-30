package org.cloudandx.cubicx.api;

import net.minecraft.world.level.chunk.DataLayer;
import org.jspecify.annotations.Nullable;

public interface CubicLightStorageAccess {
    @Nullable DataLayer cubicx$getDataLayerData(long sectionNode);

    @Nullable DataLayer cubicx$getDataLayer(long sectionNode, boolean updating);

    @Nullable DataLayer cubicx$getQueuedSection(long sectionNode);

    boolean cubicx$lightOnInSection(long sectionNode);

    default int cubicx$getTopSectionY(long zeroNode) {
        return 0;
    }

    default int cubicx$getBottomSectionY() {
        return 0;
    }
}