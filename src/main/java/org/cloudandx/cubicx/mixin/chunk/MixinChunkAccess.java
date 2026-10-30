package org.cloudandx.cubicx.mixin.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortLists;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Continuation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.blockscan.BlockScanUtils;
import net.minecraft.world.level.blockscan.BlockStateConsumer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.cloudandx.cubicx.util.CubicMath;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class MixinChunkAccess implements LevelHeightAccessor, CubicColumnAccess {
    @Shadow
    @Final
    protected ChunkPos chunkPos;

    @Shadow
    @Final
    protected Map<Types, Heightmap> heightmaps;

    @Unique
    private Int2ObjectMap<LevelChunkSection> cubicx$cubeSections;

    @Unique
    private Int2ObjectMap<ShortList> cubicx$postProcessingMap;

    @Unique
    private PalettedContainerFactory cubicx$containerFactory;

    @Unique
    private LevelChunkSection cubicx$emptySection;

    @Unique
    private synchronized Int2ObjectMap<LevelChunkSection> cubicx$sectionsMap() {
        if (this.cubicx$cubeSections == null) {
            this.cubicx$cubeSections = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        }
        return this.cubicx$cubeSections;
    }

    @Unique
    private synchronized Int2ObjectMap<ShortList> cubicx$postMap() {
        if (this.cubicx$postProcessingMap == null) {
            this.cubicx$postProcessingMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        }
        return this.cubicx$postProcessingMap;
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelHeightAccessor;getSectionsCount()I"
        )
    )
    private int cubicx$preventMassiveArrayAllocation(LevelHeightAccessor instance) {
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cubicx$initCubicStorage(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        long inhabitedTime,
        LevelChunkSection @Nullable [] sections,
        @Nullable BlendingData blendingData,
        CallbackInfo ci
    ) {
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        this.cubicx$postMap();
        this.cubicx$containerFactory = containerFactory;
        this.cubicx$emptySection = new LevelChunkSection(containerFactory);
        if (sections != null) {
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section != null && !section.hasOnlyAir()) {
                    map.put(this.getSectionYFromSectionIndex(i), section);
                }
            }
        }
    }

    @Override
    public @Nullable LevelChunkSection cubicx$getCubeSectionIfPresent(int cubeY) {
        return this.cubicx$sectionsMap().get(cubeY);
    }

    @Override
    public LevelChunkSection cubicx$getOrCreateCubeSection(int cubeY) {
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        synchronized (map) {
            LevelChunkSection section = map.get(cubeY);
            if (section == null) {
                section = new LevelChunkSection(this.cubicx$containerFactory);
                map.put(cubeY, section);
            }
            return section;
        }
    }

    @Override
    public void cubicx$putCubeSection(int cubeY, LevelChunkSection section) {
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        if (section == null) {
            map.remove(cubeY);
        } else {
            map.put(cubeY, section);
        }
    }

    @Override
    public Int2ObjectMap<LevelChunkSection> cubicx$getCubeSectionsMap() {
        return this.cubicx$sectionsMap();
    }

    @Override
    public ShortList cubicx$getOrCreatePostProcessing(int cubeY) {
        Int2ObjectMap<ShortList> map = this.cubicx$postMap();
        synchronized (map) {
            ShortList list = map.get(cubeY);
            if (list == null) {
                list = ShortLists.synchronize(new ShortArrayList());
                map.put(cubeY, list);
            }
            return list;
        }
    }

    @Override
    public Int2ObjectMap<ShortList> cubicx$getPostProcessingMap() {
        return this.cubicx$postMap();
    }

    /**
     * @author cloudandx
     * @reason Prevent 32-bit signed integer underflow when subtracting 1 from Integer.MIN_VALUE on empty columns.
     */
    @Overwrite
    public int getHeight(final Types type, final int x, final int z) {
        Heightmap heightmap = this.heightmaps.get(type);
        if (heightmap == null) {
            Heightmap.primeHeightmaps((ChunkAccess) (Object) this, EnumSet.of(type));
            heightmap = this.heightmaps.get(type);
        }
        return CubicMath.clampAdd(heightmap.getFirstAvailable(x & 15, z & 15), -1);
    }

    /**
     * @author cloudandx
     * @reason Retrieve 16x16x16 cube section dynamically from sparse map using sectionIndex.
     */
    @Overwrite
    public LevelChunkSection getSection(final int sectionIndex) {
        int cubeY = this.getSectionYFromSectionIndex(sectionIndex);
        return this.cubicx$getOrCreateCubeSection(cubeY);
    }

    /**
     * @author cloudandx
     * @reason Provide bounded section view for vanilla client/biome callers while keeping sparse map backing.
     */
    @Overwrite
    public LevelChunkSection[] getSections() {
        int count = Math.clamp(this.getSectionsCount(), 0, 256);
        LevelChunkSection[] view = new LevelChunkSection[count];
        int minSecY = this.getMinSectionY();
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        for (int i = 0; i < count; i++) {
            LevelChunkSection sec = map.get(minSecY + i);
            view[i] = sec != null ? sec : this.cubicx$emptySection;
        }
        return view;
    }

    /**
     * @author cloudandx
     * @reason Find highest non-empty 16x16x16 cube section index in O(N) over active cubes only.
     */
    @Overwrite
    public int getHighestFilledSectionIndex() {
        int maxCubeY = Integer.MIN_VALUE;
        boolean found = false;
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        synchronized (map) {
            for (Int2ObjectMap.Entry<LevelChunkSection> entry : map.int2ObjectEntrySet()) {
                if (!entry.getValue().hasOnlyAir()) {
                    int cy = entry.getIntKey();
                    if (!found || cy > maxCubeY) {
                        maxCubeY = cy;
                        found = true;
                    }
                }
            }
        }
        return found ? this.getSectionIndexFromSectionY(maxCubeY) : -1;
    }

    /**
     * @author cloudandx
     * @reason Check vertical emptiness using sparse 16x16x16 cube lookup without iterating empty space.
     */
    @Overwrite
    public boolean isYSpaceEmpty(int yStartInclusive, int yEndInclusive) {
        int minCubeY = CubicMath.blockToCube(yStartInclusive);
        int maxCubeY = CubicMath.blockToCube(yEndInclusive);
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        synchronized (map) {
            for (Int2ObjectMap.Entry<LevelChunkSection> entry : map.int2ObjectEntrySet()) {
                int cubeY = entry.getIntKey();
                if (cubeY >= minCubeY && cubeY <= maxCubeY && !entry.getValue().hasOnlyAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @author cloudandx
     * @reason Store packed post-processing offsets in sparse map keyed by cubeY.
     */
    @Overwrite
    public void addPackedPostProcess(final ShortList packedOffsets, final int sectionIndex) {
        if (!packedOffsets.isEmpty()) {
            this.cubicx$getOrCreatePostProcessing(this.getSectionYFromSectionIndex(sectionIndex)).addAll(packedOffsets);
        }
    }

    /**
     * @author cloudandx
     * @reason Scan only loaded non-empty 16x16x16 cubes instead of iterating across 268 million vertical sections.
     */
    @Overwrite
    public void findBlocks(final Predicate<BlockState> predicate, final BiConsumer<BlockPos, BlockState> consumer) {
        BlockStateConsumer blockStateConsumer = (pos, state) -> {
            consumer.accept(pos, state);
            return Continuation.CONTINUE;
        };
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        IntArrayList cubeYs;
        synchronized (map) {
            cubeYs = new IntArrayList(map.keySet());
        }
        cubeYs.sort(IntComparators.NATURAL_COMPARATOR);
        for (int i = 0; i < cubeYs.size(); i++) {
            int sectionY = cubeYs.getInt(i);
            LevelChunkSection section = map.get(sectionY);
            if (section != null && section.maybeHas(predicate)) {
                BlockPos origin = SectionPos.of(this.chunkPos, sectionY).origin();
                BlockScanUtils.findBlocksInSection(section, origin, 0, 0, 0, 15, 15, 15, predicate, blockStateConsumer);
            }
        }
    }

    /**
     * @author cloudandx
     * @reason Collect biomes from active 16x16x16 cube sections in sparse map.
     */
    @Overwrite
    public void collectBiomesInPalette(final Set<Holder<Biome>> output) {
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        synchronized (map) {
            for (LevelChunkSection section : map.values()) {
                section.getBiomes().forEachInPalette(output::add);
            }
        }
    }

    /**
     * @author cloudandx
     * @reason Support full 32-bit vertical biome lookup without overflow or out-of-bounds array indexing.
     */
    @Overwrite
    public Holder<Biome> getNoiseBiome(final int quartX, final int quartY, final int quartZ) {
        try {
            int blockY = QuartPos.toBlock(quartY);
            int cubeY = CubicMath.blockToCube(blockY);
            LevelChunkSection section = this.cubicx$sectionsMap().get(cubeY);
            if (section == null) {
                section = this.cubicx$emptySection;
            }
            return section.getNoiseBiome(quartX & 3, quartY & 3, quartZ & 3);
        } catch (Throwable t) {
            CrashReport report = CrashReport.forThrowable(t, "Getting biome");
            CrashReportCategory category = report.addCategory("Biome being got");
            category.setDetail("Location", () -> CrashReportCategory.formatLocation(this, quartX, quartY, quartZ));
            throw new ReportedException(report);
        }
    }

    /**
     * @author cloudandx
     * @reason Fill biomes only for active 16x16x16 cubes to avoid 268-million-section loops.
     */
    @Overwrite
    public void fillBiomesFromNoise(final BiomeResolver biomeResolver) {
        int quartMinX = QuartPos.fromBlock(this.chunkPos.getMinBlockX());
        int quartMinZ = QuartPos.fromBlock(this.chunkPos.getMinBlockZ());
        Int2ObjectMap<LevelChunkSection> map = this.cubicx$sectionsMap();
        IntArrayList cubeYs;
        synchronized (map) {
            cubeYs = new IntArrayList(map.keySet());
        }
        for (int i = 0; i < cubeYs.size(); i++) {
            int sectionY = cubeYs.getInt(i);
            LevelChunkSection section = map.get(sectionY);
            if (section != null) {
                int quartMinY = QuartPos.fromSection(sectionY);
                section.fillBiomesFromNoise(biomeResolver, quartMinX, quartMinY, quartMinZ);
            }
        }
    }
}