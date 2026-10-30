package org.cloudandx.cubicx.mixin.miscs;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.longs.LongSortedSets;
import java.util.stream.LongStream;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntitySectionStorage.class)
public abstract class MixinEntitySectionStorage<T extends EntityAccess> {
    @Shadow
    @Final
    private Class<T> entityClass;

    @Shadow
    @Final
    private Long2ObjectFunction<Visibility> intialSectionVisibility;

    @Shadow
    @Final
    private Long2ObjectMap<EntitySection<T>> sections;

    @Shadow
    @Final
    private LongSortedSet sectionIds;

    @Unique
    private final Long2ObjectMap<LongOpenHashSet> cubicx$chunkSections = new Long2ObjectOpenHashMap<>();

    @Shadow
    private static long getChunkKeyFromSectionKey(final long sectionPos) {
        return 0L;
    }

    /**
     * @author cloudandx
     * @reason Query entity sections using O(1) spatial hash lookups and column sets instead of LongAVLTreeSet.subSet which overflows at x = -1.
     */
    @Overwrite
    public void forEachAccessibleNonEmptySection(
        final AABB bb, final AbortableIterationConsumer<EntitySection<T>> output
    ) {
        if (this.sections.isEmpty()) {
            return;
        }
        int xMin = SectionPos.posToSectionCoord(bb.minX - 2.0);
        int yMin = SectionPos.posToSectionCoord(bb.minY - 4.0);
        int zMin = SectionPos.posToSectionCoord(bb.minZ - 2.0);
        int xMax = SectionPos.posToSectionCoord(bb.maxX + 2.0);
        int yMax = SectionPos.posToSectionCoord(bb.maxY + 0.0);
        int zMax = SectionPos.posToSectionCoord(bb.maxZ + 2.0);

        long xSpan = (long) xMax - (long) xMin + 1L;
        long ySpan = (long) yMax - (long) yMin + 1L;
        long zSpan = (long) zMax - (long) zMin + 1L;

        if (xSpan > 0 && ySpan > 0 && zSpan > 0 && xSpan <= 8 && ySpan <= 8 && zSpan <= 8) {
            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        EntitySection<T> entitySection = this.sections.get(SectionPos.asLong(x, y, z));
                        if (entitySection != null
                            && !entitySection.isEmpty()
                            && entitySection.getStatus().isAccessible()
                            && output.accept(entitySection).shouldAbort()) {
                            return;
                        }
                    }
                }
            }
            return;
        }

        if (xSpan > 0 && zSpan > 0 && xSpan * zSpan <= this.cubicx$chunkSections.size()) {
            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    LongOpenHashSet columnSet = this.cubicx$chunkSections.get(ChunkPos.pack(x, z));
                    if (columnSet != null && !columnSet.isEmpty()) {
                        LongIterator it = columnSet.iterator();
                        while (it.hasNext()) {
                            long sectionKey = it.nextLong();
                            int y = SectionPos.y(sectionKey);
                            if (y >= yMin && y <= yMax) {
                                EntitySection<T> entitySection = this.sections.get(sectionKey);
                                if (entitySection != null
                                    && !entitySection.isEmpty()
                                    && entitySection.getStatus().isAccessible()
                                    && output.accept(entitySection).shouldAbort()) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        for (Long2ObjectMap.Entry<EntitySection<T>> entry : this.sections.long2ObjectEntrySet()) {
            EntitySection<T> entitySection = entry.getValue();
            if (entitySection != null && !entitySection.isEmpty() && entitySection.getStatus().isAccessible()) {
                long sectionKey = entry.getLongKey();
                int x = SectionPos.x(sectionKey);
                int y = SectionPos.y(sectionKey);
                int z = SectionPos.z(sectionKey);
                if (x >= xMin && x <= xMax && y >= yMin && y <= yMax && z >= zMin && z <= zMax) {
                    if (output.accept(entitySection).shouldAbort()) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * @author cloudandx
     * @reason Retrieve existing section positions in a chunk column directly from O(1) column index without LongAVLTreeSet range overflow.
     */
    @Overwrite
    public LongStream getExistingSectionPositionsInChunk(final long chunkKey) {
        LongOpenHashSet set = this.cubicx$chunkSections.get(chunkKey);
        if (set == null || set.isEmpty()) {
            return LongStream.empty();
        }
        return LongStream.of(set.toLongArray());
    }

    /**
     * @author cloudandx
     * @reason Return chunk sections from column index without LongAVLTreeSet.subSet overflow.
     */
    @Overwrite
    private LongSortedSet getChunkSections(final int x, final int z) {
        LongOpenHashSet set = this.cubicx$chunkSections.get(ChunkPos.pack(x, z));
        if (set == null || set.isEmpty()) {
            return LongSortedSets.EMPTY_SET;
        }
        return new LongAVLTreeSet(set);
    }

    /**
     * @author cloudandx
     * @reason Register newly created entity section in both sectionIds and O(1) column map.
     */
    @Overwrite
    private EntitySection<T> createSection(final long sectionPos) {
        long chunkPos = getChunkKeyFromSectionKey(sectionPos);
        Visibility chunkStatus = this.intialSectionVisibility.get(chunkPos);
        this.sectionIds.add(sectionPos);
        LongOpenHashSet set = this.cubicx$chunkSections.get(chunkPos);
        if (set == null) {
            set = new LongOpenHashSet();
            this.cubicx$chunkSections.put(chunkPos, set);
        }
        set.add(sectionPos);
        return new EntitySection<>(this.entityClass, chunkStatus);
    }

    /**
     * @author cloudandx
     * @reason Remove entity section from sections, sectionIds, and O(1) column map.
     */
    @Overwrite
    public void remove(final long sectionKey) {
        this.sections.remove(sectionKey);
        this.sectionIds.remove(sectionKey);
        long chunkPos = getChunkKeyFromSectionKey(sectionKey);
        LongOpenHashSet set = this.cubicx$chunkSections.get(chunkPos);
        if (set != null) {
            set.remove(sectionKey);
            if (set.isEmpty()) {
                this.cubicx$chunkSections.remove(chunkPos);
            }
        }
    }
}