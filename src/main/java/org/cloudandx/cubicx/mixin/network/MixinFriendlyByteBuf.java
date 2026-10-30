package org.cloudandx.cubicx.mixin.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(FriendlyByteBuf.class)
public abstract class MixinFriendlyByteBuf {
    /**
     * @author cloudandx
     * @reason Read full 32-bit X, Y, Z integers from network buffers without 64-bit bit-packing truncation.
     */
    @Overwrite
    public static BlockPos readBlockPos(final ByteBuf input) {
        return new BlockPos(input.readInt(), input.readInt(), input.readInt());
    }

    /**
     * @author cloudandx
     * @reason Write full 32-bit X, Y, Z integers to network buffers without 64-bit bit-packing truncation.
     */
    @Overwrite
    public static void writeBlockPos(final ByteBuf output, final BlockPos pos) {
        output.writeInt(pos.getX());
        output.writeInt(pos.getY());
        output.writeInt(pos.getZ());
    }
}