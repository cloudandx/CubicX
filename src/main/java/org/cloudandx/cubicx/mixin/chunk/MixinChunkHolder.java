package org.cloudandx.cubicx.mixin.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkHolder.PlayerProvider;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkHolder.class)
public abstract class MixinChunkHolder extends GenerationChunkHolder {
    @Shadow
    private boolean hasChangedSections;

    @Shadow
    @Final
    private BitSet blockChangedLightSectionFilter;

    @Shadow
    @Final
    private BitSet skyChangedLightSectionFilter;

    @Shadow
    @Final
    private LevelLightEngine lightEngine;

    @Shadow
    @Final
    private PlayerProvider playerProvider;

    @Unique
    private final Int2ObjectMap<ShortSet> cubicx$changedBlocksPerCube = new Int2ObjectOpenHashMap<>();

    protected MixinChunkHolder(ChunkPos pos) {
        super(pos);
    }

    @Shadow
    public abstract @Nullable LevelChunk getTickingChunk();

    @Shadow
    public abstract boolean hasChangesToBroadcast();

    @Shadow
    protected abstract void broadcast(final List<ServerPlayer> players, final Packet<?> packet);

    @Shadow
    protected abstract void broadcastBlockEntityIfNeeded(
        final List<ServerPlayer> players, final Level level, final BlockPos pos, final BlockState state
    );

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelHeightAccessor;getSectionsCount()I"
        )
    )
    private int cubicx$preventMassiveShortSetArray(LevelHeightAccessor instance) {
        return 0;
    }

    /**
     * @author cloudandx
     * @reason Track block changes in sparse 16x16x16 cube map keyed by 28-bit cubeY across full 32-bit Y range.
     */
    @Overwrite
    public boolean blockChanged(final BlockPos pos) {
        LevelChunk chunk = this.getTickingChunk();
        if (chunk == null) {
            return false;
        }
        boolean hadChangedSections = this.hasChangedSections;
        int cubeY = CubicMath.blockToCube(pos.getY());
        ShortSet changedBlocksInSection = this.cubicx$changedBlocksPerCube.get(cubeY);
        if (changedBlocksInSection == null) {
            this.hasChangedSections = true;
            changedBlocksInSection = new ShortOpenHashSet();
            this.cubicx$changedBlocksPerCube.put(cubeY, changedBlocksInSection);
        }
        changedBlocksInSection.add(SectionPos.sectionRelativePos(pos));
        return !hadChangedSections;
    }

    /**
     * @author cloudandx
     * @reason Broadcast light, block, and section updates from sparse 16x16x16 cube map.
     */
    @Overwrite
    public void broadcastChanges(final LevelChunk chunk) {
        if (!this.hasChangesToBroadcast()) {
            return;
        }
        Level level = chunk.getLevel();
        if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
            List<ServerPlayer> borderPlayers = this.playerProvider.getPlayers(this.pos, true);
            if (!borderPlayers.isEmpty()) {
                ClientboundLightUpdatePacket lightPacket = new ClientboundLightUpdatePacket(
                    chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter
                );
                this.broadcast(borderPlayers, lightPacket);
            }
            this.skyChangedLightSectionFilter.clear();
            this.blockChangedLightSectionFilter.clear();
        }
        if (this.hasChangedSections) {
            List<ServerPlayer> players = this.playerProvider.getPlayers(this.pos, false);
            if (!players.isEmpty()) {
                CubicColumnAccess cubicChunk = (CubicColumnAccess) chunk;
                for (Int2ObjectMap.Entry<ShortSet> entry : this.cubicx$changedBlocksPerCube.int2ObjectEntrySet()) {
                    int sectionY = entry.getIntKey();
                    ShortSet changedBlocks = entry.getValue();
                    if (changedBlocks != null && !changedBlocks.isEmpty()) {
                        SectionPos sectionPos = SectionPos.of(chunk.getPos(), sectionY);
                        if (changedBlocks.size() == 1) {
                            BlockPos pos = sectionPos.relativeToBlockPos(changedBlocks.iterator().nextShort());
                            BlockState state = level.getBlockState(pos);
                            this.broadcast(players, new ClientboundBlockUpdatePacket(pos, state));
                            this.broadcastBlockEntityIfNeeded(players, level, pos, state);
                        } else {
                            LevelChunkSection section = cubicChunk.cubicx$getOrCreateCubeSection(sectionY);
                            ClientboundSectionBlocksUpdatePacket packet =
                                new ClientboundSectionBlocksUpdatePacket(sectionPos, changedBlocks, section);
                            this.broadcast(players, packet);
                            packet.runUpdates((pos, state) -> this.broadcastBlockEntityIfNeeded(players, level, pos, state));
                        }
                    }
                }
            }
            this.cubicx$changedBlocksPerCube.clear();
            this.hasChangedSections = false;
        }
    }
}