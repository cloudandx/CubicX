package org.cloudandx.cubicx.client.mixin.core.render;

import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.SectionPos;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public abstract class MixinViewArea {
    @Shadow
    @Final
    private RotatingSectionStorage<RenderSection> sections;

    @Unique
    private RenderSection cubicx$noopSection;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cubicx$initNoopSection(
        SectionRenderDispatcher sectionRenderDispatcher,
        int minY,
        int maxY,
        int minSectionY,
        int maxSectionY,
        int renderDistance,
        SectionOcclusionGraph sectionOcclusionGraph,
        CallbackInfo ci
    ) {
        this.cubicx$noopSection = sectionRenderDispatcher.new RenderSection(-1, SectionPos.asLong(0, CubicMath.MIN_CUBE, 0));
    }

    /**
     * @author cloudandx
     * @reason Return non-null sentinel RenderSection when sectionNode is outside the 3D view window so LevelRenderer.compileSections never throws NPE.
     */
    @Overwrite
    protected RenderSection getRenderSection(final long sectionNode) {
        RenderSection section = this.sections.getValue(sectionNode);
        return section != null ? section : this.cubicx$noopSection;
    }

    /**
     * @author cloudandx
     * @reason Return dynamic minimum block Y of the active 3D cubic view window for Octree and occlusion raycasting.
     */
    @Overwrite
    public int minY() {
        return CubicMath.cubeToMinBlock(this.sections.minY());
    }

    /**
     * @author cloudandx
     * @reason Return dynamic maximum block Y of the active 3D cubic view window without integer addition overflow at Integer.MAX_VALUE.
     */
    @Overwrite
    public int maxY() {
        return CubicMath.cubeToMaxBlock(this.sections.maxY());
    }
}