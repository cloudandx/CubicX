package org.cloudandx.cubicx.mixin.chunk;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionStorage.class)
public abstract class MixinSectionStorage<R, P> {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private SimpleRegionStorage simpleRegionStorage;

    @Shadow
    @Final
    private Long2ObjectMap<Optional<R>> storage;

    @Shadow
    @Final
    private LongLinkedOpenHashSet dirtyChunks;

    @Shadow
    @Final
    private Codec<P> codec;

    @Shadow
    @Final
    private Function<R, P> packer;

    @Shadow
    @Final
    private BiFunction<P, Runnable, R> unpacker;

    @Shadow
    @Final
    private RegistryAccess registryAccess;

    @Shadow
    @Final
    private ChunkIOErrorReporter errorReporter;

    @Shadow
    @Final
    private LongSet loadedChunks;

    @Unique
    private final Object cubicx$lock = new Object();

    @Unique
    private final Long2ObjectMap<CompletableFuture<Int2ObjectMap<P>>> cubicx$pendingLoads = new Long2ObjectOpenHashMap<>();

    @Shadow
    protected abstract boolean outsideStoredRange(final long sectionPos);

    @Shadow
    protected abstract void onSectionLoad(final long sectionPos);

    @Shadow
    protected abstract void setDirty(final long sectionPos);

    @Shadow
    private void writeChunk(final ChunkPos chunkPos) {
    }

    @Shadow
    private static long getKey(final ChunkPos chunkPos, final int sectionY) {
        return 0L;
    }

    /**
     * @author cloudandx
     * @reason Tick dirty chunks and unpack sparse 32-bit POI sections without 268-million-section loops.
     */
    @Overwrite
    protected void tick(final BooleanSupplier haveTime) {
        LongIterator iterator = this.dirtyChunks.iterator();
        while (iterator.hasNext() && haveTime.getAsBoolean()) {
            ChunkPos chunkPos = ChunkPos.unpack(iterator.nextLong());
            iterator.remove();
            this.writeChunk(chunkPos);
        }
        this.cubicx$unpackPendingLoads();
    }

    /**
     * @author cloudandx
     * @reason Lazily resolve sparse 32-bit section entries without pre-filling 268 million Optional.empty() instances.
     */
    @Overwrite
    protected Optional<R> getOrLoad(final long sectionPos) {
        if (this.outsideStoredRange(sectionPos)) {
            return Optional.empty();
        }
        if (this.storage.containsKey(sectionPos)) {
            return this.storage.get(sectionPos);
        }
        this.cubicx$loadChunkSync(SectionPos.of(sectionPos).chunk());
        if (this.storage.containsKey(sectionPos)) {
            return this.storage.get(sectionPos);
        }
        Optional<R> empty = Optional.empty();
        this.storage.put(sectionPos, empty);
        return empty;
    }

    /**
     * @author cloudandx
     * @reason Prefetch sparse POI sections directly from NBT map keys without iterating full 32-bit section range.
     */
    @Overwrite
    public CompletableFuture<?> prefetch(final ChunkPos chunkPos) {
        synchronized (this.cubicx$lock) {
            long chunkKey = chunkPos.pack();
            if (this.loadedChunks.contains(chunkKey)) {
                return CompletableFuture.completedFuture(null);
            }
            return this.cubicx$pendingLoads.computeIfAbsent(chunkKey, this::cubicx$tryReadSparse);
        }
    }

    /**
     * @author cloudandx
     * @reason Serialize only populated 32-bit sections belonging to the target chunkPos.
     */
    @Overwrite
    private <T> Dynamic<T> writeChunk(final ChunkPos chunkPos, final DynamicOps<T> ops) {
        Map<T, T> sections = Maps.newHashMap();
        int targetX = chunkPos.x();
        int targetZ = chunkPos.z();
        for (Long2ObjectMap.Entry<Optional<R>> entry : this.storage.long2ObjectEntrySet()) {
            Optional<R> r = entry.getValue();
            if (r.isPresent()) {
                long key = entry.getLongKey();
                if (SectionPos.x(key) == targetX && SectionPos.z(key) == targetZ) {
                    int sectionY = SectionPos.y(key);
                    DataResult<T> serializedSection = this.codec.encodeStart(ops, this.packer.apply(r.get()));
                    String yName = Integer.toString(sectionY);
                    serializedSection.resultOrPartial(LOGGER::error).ifPresent(s -> sections.put(ops.createString(yName), s));
                }
            }
        }
        return new Dynamic<>(
            ops,
            ops.createMap(
                ImmutableMap.of(
                    ops.createString("Sections"),
                    ops.createMap(sections),
                    ops.createString("DataVersion"),
                    ops.createInt(SharedConstants.getCurrentVersion().dataVersion().version())
                )
            )
        );
    }

    @Unique
    private void cubicx$unpackPendingLoads() {
        synchronized (this.cubicx$lock) {
            Iterator<Long2ObjectMap.Entry<CompletableFuture<Int2ObjectMap<P>>>> iterator =
                Long2ObjectMaps.fastIterator(this.cubicx$pendingLoads);
            while (iterator.hasNext()) {
                Long2ObjectMap.Entry<CompletableFuture<Int2ObjectMap<P>>> entry = iterator.next();
                CompletableFuture<Int2ObjectMap<P>> future = entry.getValue();
                if (future.isDone()) {
                    long chunkKey = entry.getLongKey();
                    this.cubicx$applySparseChunk(ChunkPos.unpack(chunkKey), future.join());
                    iterator.remove();
                    this.loadedChunks.add(chunkKey);
                }
            }
        }
    }

    @Unique
    private void cubicx$loadChunkSync(final ChunkPos chunkPos) {
        long chunkKey = chunkPos.pack();
        CompletableFuture<Int2ObjectMap<P>> future;
        synchronized (this.cubicx$lock) {
            if (!this.loadedChunks.add(chunkKey)) {
                return;
            }
            future = this.cubicx$pendingLoads.computeIfAbsent(chunkKey, this::cubicx$tryReadSparse);
        }
        this.cubicx$applySparseChunk(chunkPos, future.join());
        synchronized (this.cubicx$lock) {
            this.cubicx$pendingLoads.remove(chunkKey);
        }
    }

    @Unique
    private void cubicx$applySparseChunk(final ChunkPos pos, final Int2ObjectMap<P> sectionsByY) {
        for (Int2ObjectMap.Entry<P> entry : sectionsByY.int2ObjectEntrySet()) {
            int sectionY = entry.getIntKey();
            long key = getKey(pos, sectionY);
            R unpacked = this.unpacker.apply(entry.getValue(), () -> this.setDirty(key));
            this.storage.put(key, Optional.ofNullable(unpacked));
            if (unpacked != null) {
                this.onSectionLoad(key);
            }
        }
    }

    @Unique
    private CompletableFuture<Int2ObjectMap<P>> cubicx$tryReadSparse(final long chunkKey) {
        ChunkPos chunkPos = ChunkPos.unpack(chunkKey);
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        return this.simpleRegionStorage
            .read(chunkPos)
            .thenApplyAsync(
                result -> result.map(tag -> {
                    Dynamic<Tag> originalTag = new Dynamic<>(registryOps, tag);
                    Dynamic<Tag> fixedTag = this.simpleRegionStorage.upgradeChunkTag(originalTag, 1945);
                    OptionalDynamic<Tag> sections = fixedTag.get("Sections");
                    Int2ObjectMap<P> sectionsByY = new Int2ObjectOpenHashMap<>();
                    sections.result().ifPresent(secDynamic -> {
                        if (secDynamic.getValue() instanceof CompoundTag compound) {
                            for (String keyStr : compound.keySet()) {
                                try {
                                    int sectionY = Integer.parseInt(keyStr);
                                    sections.get(keyStr)
                                        .result()
                                        .flatMap(sectionData -> this.codec.parse(sectionData).resultOrPartial(LOGGER::error))
                                        .ifPresent(parsed -> sectionsByY.put(sectionY, parsed));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    });
                    return sectionsByY;
                }).orElse(Int2ObjectMaps.emptyMap()),
                Util.backgroundExecutor().forName("parseSection")
            )
            .exceptionally(throwable -> {
                Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                if (cause instanceof IOException e) {
                    LOGGER.error("Error reading chunk {} data from disk", chunkPos, e);
                    this.errorReporter.reportChunkLoadFailure(e, this.simpleRegionStorage.storageInfo(), chunkPos);
                    return Int2ObjectMaps.emptyMap();
                }
                throw new CompletionException(cause);
            });
    }
}