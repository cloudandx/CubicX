package org.cloudandx.cubicx.client.mixin.core.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class MixinClientboundLevelChunkPacketData {
    /**
     * @author cloudandx
     * @reason Calculate exact packet buffer size for sparse 16x16x16 cubes plus 32-bit cubeY headers and 32-bit block entity Y coordinates.
     */
    @Overwrite
    private static int calculateChunkSize(final LevelChunk chunk) {
        Int2ObjectMap<LevelChunkSection> map = ((CubicColumnAccess) chunk).cubicx$getCubeSectionsMap();
        int total = 4;
        for (Int2ObjectMap.Entry<LevelChunkSection> entry : map.int2ObjectEntrySet()) {
            LevelChunkSection section = entry.getValue();
            if (section != null && !section.hasOnlyAir()) {
                total += 4 + section.getSerializedSize();
            }
        }
        total += 4 + (chunk.getBlockEntities().size() * 4);
        return total;
    }

    /**
     * @author cloudandx
     * @reason Serialize active 16x16x16 cubes with 32-bit cubeY coordinates and full 32-bit block entity Y coordinates.
     */
    @Overwrite
    public static void extractChunkData(final FriendlyByteBuf buffer, final LevelChunk chunk) {
        Int2ObjectMap<LevelChunkSection> map = ((CubicColumnAccess) chunk).cubicx$getCubeSectionsMap();
        int count = 0;
        for (Int2ObjectMap.Entry<LevelChunkSection> entry : map.int2ObjectEntrySet()) {
            LevelChunkSection section = entry.getValue();
            if (section != null && !section.hasOnlyAir()) {
                count++;
            }
        }
        buffer.writeInt(count);
        for (Int2ObjectMap.Entry<LevelChunkSection> entry : map.int2ObjectEntrySet()) {
            LevelChunkSection section = entry.getValue();
            if (section != null && !section.hasOnlyAir()) {
                buffer.writeInt(entry.getIntKey());
                section.write(buffer);
            }
        }
        buffer.writeInt(chunk.getBlockEntities().size());
        for (Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            buffer.writeInt(entry.getValue().getBlockPos().getY());
        }
        if (buffer.writerIndex() != buffer.capacity()) {
            throw new IllegalStateException("Didn't fill chunk buffer: expected " + buffer.capacity() + " bytes, got " + buffer.writerIndex());
        }
    }
}