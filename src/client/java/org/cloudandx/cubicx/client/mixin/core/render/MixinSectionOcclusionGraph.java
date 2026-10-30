package org.cloudandx.cubicx.client.mixin.core.render;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionOcclusionGraph.class)
public abstract class MixinSectionOcclusionGraph {
    @Shadow
    private @Nullable ViewArea viewArea;

    @Inject(method = "schedulePropagationFrom", at = @At("HEAD"), cancellable = true)
    private void cubicx$ignoreNoopPropagation(final RenderSection section, final CallbackInfo ci) {
        if (section == null || section.index < 0) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "initializeQueueForFullUpdate", at = @At("STORE"))
    private @Nullable RenderSection cubicx$filterNoopInFullUpdate(final @Nullable RenderSection section) {
        return (section != null && section.index < 0) ? null : section;
    }

    @Inject(method = "getRelativeFrom", at = @At("HEAD"), cancellable = true)
    private void cubicx$guardVerticalBoundsAndOverflow(
        final long cameraSectionNode,
        final RenderSection renderSection,
        final Direction direction,
        final CallbackInfoReturnable<@Nullable RenderSection> cir
    ) {
        ViewArea area = this.viewArea;
        if (area == null || renderSection.index < 0) {
            cir.setReturnValue(null);
            return;
        }
        long currentNode = renderSection.getSectionNode();
        int currentY = SectionPos.y(currentNode);
        if ((direction == Direction.UP && currentY >= CubicMath.MAX_CUBE)
            || (direction == Direction.DOWN && currentY <= CubicMath.MIN_CUBE)) {
            cir.setReturnValue(null);
            return;
        }
        long targetY = (long) currentY + (long) direction.getStepY();
        long dy = Math.abs((long) SectionPos.y(cameraSectionNode) - targetY);
        int verticalRadius = Math.max(area.getViewDistance(), area.sectionCount() / 2);
        if (dy > verticalRadius) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getRelativeFrom", at = @At("RETURN"), cancellable = true)
    private void cubicx$filterNoopRelativeReturn(
        final long cameraSectionNode,
        final RenderSection renderSection,
        final Direction direction,
        final CallbackInfoReturnable<@Nullable RenderSection> cir
    ) {
        RenderSection result = cir.getReturnValue();
        if (result != null && result.index < 0) {
            cir.setReturnValue(null);
        }
    }
}