package org.cloudandx.cubicx.mixin.height;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    /**
     * @author cloudandx
     * @reason Allow player movement packets across the full 32-bit vertical Y range without clamping at 20,000,000.
     */
    @Overwrite
    private static double clampVertical(final double value) {
        return Mth.clamp(value, CubicMath.MIN_BLOCK,  CubicMath.MAX_BLOCK);
    }
}