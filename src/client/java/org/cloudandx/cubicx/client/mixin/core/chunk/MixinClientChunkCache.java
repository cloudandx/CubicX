package org.cloudandx.cubicx.client.mixin.core.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class MixinClientChunkCache {
    @Shadow
    public abstract void onSectionEmptinessChanged(final int sectionX, final int sectionY, final int sectionZ, final boolean empty);

    @Shadow
    public abstract @Nullable LevelChunk getChunk(final int x, final int z, final ChunkStatus targetStatus, final boolean loadOrGenerate);

    @Inject(method = "drop", at = @At("HEAD"))
    private void cubicx$onDropChunkCubes(ChunkPos pos, CallbackInfo ci) {
        LevelChunk chunk = this.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
        if (chunk != null) {
            Int2ObjectMap<LevelChunkSection> map = ((CubicColumnAccess) chunk).cubicx$getCubeSectionsMap();
            for (Int2ObjectMap.Entry<LevelChunkSection> entry : map.int2ObjectEntrySet()) {
                this.onSectionEmptinessChanged(pos.x(), entry.getIntKey(), pos.z(), true);
            }
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void cubicx$onPacketDataLoaded(
        int chunkX, int chunkZ, ClientboundLevelChunkPacketData chunkData, CallbackInfoReturnable<@Nullable LevelChunk> cir
    ) {
        LevelChunk chunk = cir.getReturnValue();
        if (chunk != null) {
            Int2ObjectMap<LevelChunkSection> map = ((CubicColumnAccess) chunk).cubicx$getCubeSectionsMap();
            for (Int2ObjectMap.Entry<LevelChunkSection> entry : map.int2ObjectEntrySet()) {
                LevelChunkSection section = entry.getValue();
                this.onSectionEmptinessChanged(chunkX, entry.getIntKey(), chunkZ, section == null || section.hasOnlyAir());
            }
        }
    }
}