package org.cloudandx.cubicx.client.mixin.core.render;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSection.class)
public abstract class MixinRenderSection {
    @Shadow
    @Final
    public int index;

    @Shadow
    private AABB bb;

    @Shadow
    private volatile long sectionNode;

    @Shadow
    @Final
    private MutableBlockPos renderOrigin;

    @Shadow
    public abstract void reset();

    @Inject(method = "compileAsync", at = @At("HEAD"), cancellable = true)
    private void cubicx$ignoreNoopCompileAsync(RenderSectionRegion region, CallbackInfo ci) {
        if (this.index < 0) {
            ci.cancel();
        }
    }

    @Inject(method = "compileSync", at = @At("HEAD"), cancellable = true)
    private void cubicx$ignoreNoopCompileSync(RenderSectionRegion region, CallbackInfo ci) {
        if (this.index < 0) {
            ci.cancel();
        }
    }

    @Inject(method = "resortTransparency", at = @At("HEAD"), cancellable = true)
    private void cubicx$ignoreNoopResortTransparency(CallbackInfo ci) {
        if (this.index < 0) {
            ci.cancel();
        }
    }

    /**
     * @author cloudandx
     * @reason Compute 16x16x16 AABB bounds using 64-bit double arithmetic so y + 16 never overflows at Integer.MAX_VALUE.
     */
    @Overwrite
    public void setSectionNode(final long sectionNode) {
        this.reset();
        this.sectionNode = sectionNode;
        int x = SectionPos.sectionToBlockCoord(SectionPos.x(sectionNode));
        int y = SectionPos.sectionToBlockCoord(SectionPos.y(sectionNode));
        int z = SectionPos.sectionToBlockCoord(SectionPos.z(sectionNode));
        this.renderOrigin.set(x, y, z);
        this.bb = new AABB(x, y, z, (double) x + 16.0, (double) y + 16.0, (double) z + 16.0);
    }
}