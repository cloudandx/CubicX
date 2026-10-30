package org.cloudandx.cubicx.mixin.lighting;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class MixinThreadedLevelLightEngine extends LevelLightEngine {
    @Shadow
    @Final
    private ObjectList<Pair<Object, Runnable>> lightTasks;

    @Unique
    private final Object cubicx$lock = new Object();

    @Unique
    private final Long2ObjectMap<IntSet> cubicx$activeLightSections = new Long2ObjectOpenHashMap<>();

    protected MixinThreadedLevelLightEngine(LightChunkGetter chunkSource, boolean hasBlockLight, boolean hasSkyLight) {
        super(chunkSource, hasBlockLight, hasSkyLight);
    }

    @Shadow
    public abstract void tryScheduleUpdate();

    @Shadow
    public abstract CompletableFuture<?> waitForPendingTasks(final int chunkX, final int chunkZ);

    @Unique
    private void cubicx$recordActiveSection(final SectionPos pos) {
        long key = ChunkPos.pack(pos.x(), pos.z());
        synchronized (this.cubicx$lock) {
            IntSet set = this.cubicx$activeLightSections.get(key);
            if (set == null) {
                set = new IntOpenHashSet();
                this.cubicx$activeLightSections.put(key, set);
            }
            set.add(pos.y());
        }
    }

    @ModifyArg(
        method = "addTask(IILjava/util/function/IntSupplier;Lnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkTaskDispatcher;submit(Ljava/lang/Runnable;JLjava/util/function/IntSupplier;)V"
        ),
        index = 0
    )
    private Runnable cubicx$scheduleUpdateAfterEnqueue(Runnable originalTask) {
        return () -> {
            originalTask.run();
            this.tryScheduleUpdate();
        };
    }

    @Inject(method = "runUpdate", at = @At("RETURN"))
    private void cubicx$rescheduleIfTasksRemain(CallbackInfo ci) {
        if (!this.lightTasks.isEmpty() || super.hasLightWork()) {
            this.tryScheduleUpdate();
        }
    }

    @Inject(method = "updateSectionStatus(Lnet/minecraft/core/SectionPos;Z)V", at = @At("HEAD"))
    private void cubicx$trackSectionStatus(SectionPos pos, boolean sectionEmpty, CallbackInfo ci) {
        if (!sectionEmpty) {
            this.cubicx$recordActiveSection(pos);
        }
    }

    @Inject(method = "queueSectionData", at = @At("HEAD"))
    private void cubicx$trackQueuedData(LightLayer layer, SectionPos pos, @Nullable DataLayer data, CallbackInfo ci) {
        if (data != null) {
            this.cubicx$recordActiveSection(pos);
        }
    }

    /**
     * @author cloudandx
     * @reason Unload only active sparse 16x16x16 light sections without 268-million-section loops.
     */
    @Overwrite
    protected void updateChunkStatus(final ChunkPos pos) {
        long chunkKey = pos.pack();
        IntSet snapshot = null;
        synchronized (this.cubicx$lock) {
            IntSet tracked = this.cubicx$activeLightSections.remove(chunkKey);
            if (tracked != null && !tracked.isEmpty()) {
                snapshot = new IntOpenHashSet(tracked);
            }
        }
        if (snapshot != null) {
            IntIterator iter = snapshot.iterator();
            while (iter.hasNext()) {
                int sectionY = iter.nextInt();
                SectionPos sectionPos = SectionPos.of(pos, sectionY);
                this.queueSectionData(LightLayer.BLOCK, sectionPos, null);
                this.queueSectionData(LightLayer.SKY, sectionPos, null);
                this.updateSectionStatus(sectionPos, true);
            }
        }
        this.retainData(pos, false);
        this.setLightEnabled(pos, false);
    }

    /**
     * @author cloudandx
     * @reason Initialize light status for sparse 16x16x16 cubes and complete future after POST_UPDATE light pass finishes.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> initializeLight(final ChunkAccess chunk, final boolean lighted) {
        ChunkPos pos = chunk.getPos();
        Int2ObjectMap<LevelChunkSection> cubeMap = ((CubicColumnAccess) chunk).cubicx$getCubeSectionsMap();
        IntArrayList nonEmptyCubes = new IntArrayList();
        synchronized (cubeMap) {
            for (Int2ObjectMap.Entry<LevelChunkSection> entry : cubeMap.int2ObjectEntrySet()) {
                LevelChunkSection section = entry.getValue();
                if (section != null && !section.hasOnlyAir()) {
                    nonEmptyCubes.add(entry.getIntKey());
                }
            }
        }
        for (int i = 0; i < nonEmptyCubes.size(); i++) {
            this.updateSectionStatus(SectionPos.of(pos, nonEmptyCubes.getInt(i)), false);
        }
        this.setLightEnabled(pos, lighted);
        this.retainData(pos, false);
        return this.waitForPendingTasks(pos.x(), pos.z()).thenApply(ignored -> chunk);
    }
}