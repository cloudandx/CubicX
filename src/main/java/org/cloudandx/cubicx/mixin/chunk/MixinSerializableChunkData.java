package org.cloudandx.cubicx.mixin.core.chunk;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Optionull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkAccess.PackedTicks;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunk.PostLoadProcessor;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.ChunkReadException;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.blending.BlendingData.Packed;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.cloudandx.cubicx.api.CubicColumnAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SerializableChunkData.class)
public abstract class MixinSerializableChunkData {
    @Shadow
    @Final
    private static Codec<List<SavedTick<Block>>> BLOCK_TICKS_CODEC;

    @Shadow
    @Final
    private static Codec<List<SavedTick<Fluid>>> FLUID_TICKS_CODEC;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private PalettedContainerFactory containerFactory;

    @Shadow
    @Final
    private ChunkPos chunkPos;

    @Shadow
    @Final
    private int minSectionY;

    @Shadow
    @Final
    private long lastUpdateTime;

    @Shadow
    @Final
    private long inhabitedTime;

    @Shadow
    @Final
    private ChunkStatus chunkStatus;

    @Shadow
    @Final
    private @Nullable Packed blendingData;

    @Shadow
    @Final
    private @Nullable BelowZeroRetrogen belowZeroRetrogen;

    @Shadow
    @Final
    private UpgradeData upgradeData;

    @Shadow
    @Final
    private Map<Types, long[]> heightmaps;

    @Shadow
    @Final
    private PackedTicks packedTicks;

    @Shadow
    @Final
    private @Nullable ShortList[] postProcessingSections;

    @Shadow
    @Final
    private boolean lightCorrect;

    @Shadow
    @Final
    private List<SectionData> sectionData;

    @Shadow
    @Final
    private List<CompoundTag> entities;

    @Shadow
    @Final
    private List<CompoundTag> blockEntities;

    @Shadow
    @Final
    private CompoundTag structureData;

    @Shadow
    private static void logErrors(final ChunkPos pos, final int sectionY, final String message) {
    }

    @Shadow
    private static @Nullable PostLoadProcessor postLoadChunk(final ServerLevel level, final List<CompoundTag> entities, final List<CompoundTag> blockEntities) {
        return null;
    }

    @Shadow
    private static CompoundTag packStructureData(
        final StructurePieceSerializationContext context,
        final ChunkPos pos,
        final Map<Structure, StructureStart> starts,
        final Map<Structure, LongSet> references
    ) {
        return new CompoundTag();
    }

    @Shadow
    private static Map<Structure, StructureStart> unpackStructureStart(final StructurePieceSerializationContext context, final CompoundTag tag, final long seed) {
        return Map.of();
    }

    @Shadow
    private static Map<Structure, LongSet> unpackStructureReferences(final RegistryAccess registryAccess, final ChunkPos pos, final CompoundTag tag) {
        return Map.of();
    }

    @Shadow
    private static void saveTicks(final CompoundTag levelData, final PackedTicks ticksForSerialization) {
    }

    @Shadow
    private static ListTag packOffsets(final @Nullable ShortList[] sections) {
        return new ListTag();
    }

    /**
     * @author cloudandx
     * @reason Parse full 32-bit integer section Y coordinates instead of truncating to 8-bit signed byte.
     */
    @Overwrite
    public static @Nullable SerializableChunkData parse(
        final LevelHeightAccessor levelHeight, final PalettedContainerFactory containerFactory, final CompoundTag chunkData
    ) {
        if (chunkData.getString("Status").isEmpty()) {
            return null;
        }
        ChunkPos chunkPos = new ChunkPos(chunkData.getIntOr("xPos", 0), chunkData.getIntOr("zPos", 0));
        long lastUpdateTime = chunkData.getLongOr("LastUpdate", 0L);
        long inhabitedTime = chunkData.getLongOr("InhabitedTime", 0L);
        ChunkStatus status = chunkData.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY);
        UpgradeData upgradeData = chunkData.getCompound("UpgradeData")
            .map(tag -> new UpgradeData(tag, levelHeight))
            .orElse(UpgradeData.EMPTY);
        boolean lightCorrect = chunkData.getBooleanOr("isLightOn", false);
        Packed blendingData = chunkData.read("blending_data", Packed.CODEC).orElse(null);
        BelowZeroRetrogen belowZeroRetrogen = chunkData.read("below_zero_retrogen", BelowZeroRetrogen.CODEC).orElse(null);
        Map<Types, long[]> heightmaps = new EnumMap<>(Types.class);
        chunkData.getCompound("Heightmaps").ifPresent(heightmapsTag -> {
            for (Types type : Types.values()) {
                heightmapsTag.getLongArray(type.getSerializationKey()).ifPresent(longs -> heightmaps.put(type, longs));
            }
        });
        List<SavedTick<Block>> blockTicks = SavedTick.filterTickListForChunk(
            chunkData.read("block_ticks", BLOCK_TICKS_CODEC).orElse(List.of()), chunkPos
        );
        List<SavedTick<Fluid>> fluidTicks = SavedTick.filterTickListForChunk(
            chunkData.read("fluid_ticks", FLUID_TICKS_CODEC).orElse(List.of()), chunkPos
        );
        PackedTicks packedTicks = new PackedTicks(blockTicks, fluidTicks);
        ListTag postProcessTags = chunkData.getListOrEmpty("PostProcessing");
        ShortList[] postProcessingSections = new ShortList[postProcessTags.size()];
        for (int sectionIndex = 0; sectionIndex < postProcessTags.size(); sectionIndex++) {
            ListTag offsetsTag = postProcessTags.getList(sectionIndex).orElse(null);
            if (offsetsTag != null && !offsetsTag.isEmpty()) {
                ShortList packedOffsets = new ShortArrayList(offsetsTag.size());
                for (int i = 0; i < offsetsTag.size(); i++) {
                    packedOffsets.add(offsetsTag.getShortOr(i, (short) 0));
                }
                postProcessingSections[sectionIndex] = packedOffsets;
            }
        }
        List<CompoundTag> entities = chunkData.getList("entities").stream().flatMap(ListTag::compoundStream).toList();
        List<CompoundTag> blockEntities = chunkData.getList("block_entities").stream().flatMap(ListTag::compoundStream).toList();
        CompoundTag structureData = chunkData.getCompoundOrEmpty("structures");
        ListTag sectionTags = chunkData.getListOrEmpty("sections");
        List<SectionData> sectionData = new ArrayList<>(sectionTags.size());
        Codec<PalettedContainerRO<Holder<Biome>>> biomesCodec = containerFactory.biomeContainerCodec();
        Codec<PalettedContainer<BlockState>> blockStatesCodec = containerFactory.blockStatesContainerCodec();
        for (int i = 0; i < sectionTags.size(); i++) {
            Optional<CompoundTag> maybeSectionTag = sectionTags.getCompound(i);
            if (maybeSectionTag.isPresent()) {
                CompoundTag sectionTag = maybeSectionTag.get();
                int y = sectionTag.getIntOr("Y", 0);
                LevelChunkSection section;
                if (y >= levelHeight.getMinSectionY() && y <= levelHeight.getMaxSectionY()) {
                    PalettedContainer<BlockState> blocks = sectionTag.getCompound("block_states")
                        .map(
                            container -> blockStatesCodec.parse(NbtOps.INSTANCE, container)
                                .promotePartial(msg -> logErrors(chunkPos, y, msg))
                                .getOrThrow(ChunkReadException::new)
                        )
                        .orElseGet(containerFactory::createForBlockStates);
                    PalettedContainerRO<Holder<Biome>> biomes = sectionTag.getCompound("biomes")
                        .map(
                            container -> biomesCodec.parse(NbtOps.INSTANCE, container)
                                .promotePartial(msg -> logErrors(chunkPos, y, msg))
                                .getOrThrow(ChunkReadException::new)
                        )
                        .orElseGet(containerFactory::createForBiomes);
                    section = new LevelChunkSection(blocks, biomes);
                } else {
                    section = null;
                }
                DataLayer blockLight = sectionTag.getByteArray("BlockLight").map(DataLayer::new).orElse(null);
                DataLayer skyLight = sectionTag.getByteArray("SkyLight").map(DataLayer::new).orElse(null);
                sectionData.add(new SectionData(y, section, blockLight, skyLight));
            }
        }
        return new SerializableChunkData(
            containerFactory,
            chunkPos,
            chunkData.getIntOr("yPos", levelHeight.getMinSectionY()),
            lastUpdateTime,
            inhabitedTime,
            status,
            blendingData,
            belowZeroRetrogen,
            upgradeData,
            heightmaps,
            packedTicks,
            postProcessingSections,
            lightCorrect,
            sectionData,
            entities,
            blockEntities,
            structureData
        );
    }

    /**
     * @author cloudandx
     * @reason Populate sparse 16x16x16 cube sections directly without allocating a 268-million-element section array.
     */
    @Overwrite
    public ProtoChunk read(final ServerLevel level, final PoiManager poiManager, final RegionStorageInfo regionInfo, final ChunkPos pos) {
        if (!Objects.equals(pos, this.chunkPos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", pos, pos, this.chunkPos);
            level.getServer().reportMisplacedChunk(this.chunkPos, pos, regionInfo);
        }
        boolean skyLight = level.dimensionType().hasSkyLight();
        ChunkSource chunkSource = level.getChunkSource();
        LevelLightEngine lightEngine = chunkSource.getLightEngine();
        PalettedContainerFactory factory = level.palettedContainerFactory();
        ChunkType chunkType = this.chunkStatus.getChunkType();
        ChunkAccess chunk;
        if (chunkType == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> blockTicks = new LevelChunkTicks<>(this.packedTicks.blocks());
            LevelChunkTicks<Fluid> fluidTicks = new LevelChunkTicks<>(this.packedTicks.fluids());
            chunk = new LevelChunk(
                level.getLevel(),
                pos,
                this.upgradeData,
                blockTicks,
                fluidTicks,
                this.inhabitedTime,
                null,
                postLoadChunk(level, this.entities, this.blockEntities),
                BlendingData.unpack(this.blendingData)
            );
        } else {
            ProtoChunkTicks<Block> blockTicks = ProtoChunkTicks.load(this.packedTicks.blocks());
            ProtoChunkTicks<Fluid> fluidTicks = ProtoChunkTicks.load(this.packedTicks.fluids());
            ProtoChunk protoChunk = new ProtoChunk(
                pos, this.upgradeData, null, blockTicks, fluidTicks, level, factory, BlendingData.unpack(this.blendingData)
            );
            chunk = protoChunk;
            chunk.setInhabitedTime(this.inhabitedTime);
            if (this.belowZeroRetrogen != null) {
                protoChunk.setBelowZeroRetrogen(this.belowZeroRetrogen);
            }
            protoChunk.setPersistedStatus(this.chunkStatus);
            if (this.chunkStatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protoChunk.setLightEngine(lightEngine);
            }
        }
        CubicColumnAccess cubicChunk = (CubicColumnAccess) chunk;
        boolean loadedAnyLight = false;
        for (SectionData section : this.sectionData) {
            SectionPos sectionPos = SectionPos.of(pos, section.y());
            LevelChunkSection chunkSection = section.chunkSection();
            if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                cubicChunk.cubicx$putCubeSection(section.y(), chunkSection);
                poiManager.checkConsistencyWithBlocks(sectionPos, chunkSection);
            }
            DataLayer blockLight = section.blockLight();
            DataLayer skyLightLayer = section.skyLight();
            boolean hasBlockLight = blockLight != null;
            boolean hasSkyLight = skyLight && skyLightLayer != null;
            if (hasBlockLight || hasSkyLight) {
                if (!loadedAnyLight) {
                    lightEngine.retainData(pos, true);
                    loadedAnyLight = true;
                }
                if (hasBlockLight) {
                    lightEngine.queueSectionData(LightLayer.BLOCK, sectionPos, blockLight);
                }
                if (hasSkyLight) {
                    lightEngine.queueSectionData(LightLayer.SKY, sectionPos, skyLightLayer);
                }
            }
        }
        chunk.setLightCorrect(this.lightCorrect);
        Set<Types> toPrime = EnumSet.copyOf(chunk.getPersistedStatus().heightmapsAfter());
        for (Entry<Types, long[]> entry : this.heightmaps.entrySet()) {
            chunk.setHeightmap(entry.getKey(), entry.getValue());
            toPrime.remove(entry.getKey());
        }
        Heightmap.primeHeightmaps(chunk, toPrime);
        chunk.setAllStarts(unpackStructureStart(StructurePieceSerializationContext.fromLevel(level), this.structureData, level.getSeed()));
        chunk.setAllReferences(unpackStructureReferences(level.registryAccess(), pos, this.structureData));
        if (this.postProcessingSections != null) {
            for (int sectionIndex = 0; sectionIndex < this.postProcessingSections.length; sectionIndex++) {
                ShortList postProcessingSection = this.postProcessingSections[sectionIndex];
                if (postProcessingSection != null && !postProcessingSection.isEmpty()) {
                    cubicChunk.cubicx$getOrCreatePostProcessing(this.minSectionY + sectionIndex).addAll(postProcessingSection);
                }
            }
        }
        if (chunkType == ChunkType.LEVELCHUNK) {
            return new ImposterProtoChunk((LevelChunk) chunk, false);
        }
        ProtoChunk protoChunk = (ProtoChunk) chunk;
        for (CompoundTag entity : this.entities) {
            protoChunk.addEntity(entity);
        }
        for (CompoundTag blockEntity : this.blockEntities) {
            protoChunk.setBlockEntityNbt(blockEntity);
        }
        return protoChunk;
    }

    /**
     * @author cloudandx
     * @reason Copy only populated 16x16x16 cube sections from sparse map for O(K) serialization performance.
     */
    @Overwrite
    public static SerializableChunkData copyOf(final ServerLevel level, final ChunkAccess chunk) {
        if (!chunk.canBeSerialized()) {
            throw new IllegalArgumentException("Chunk can't be serialized: " + chunk);
        }
        ChunkPos pos = chunk.getPos();
        CubicColumnAccess cubicChunk = (CubicColumnAccess) chunk;
        Int2ObjectMap<LevelChunkSection> cubeMap = cubicChunk.cubicx$getCubeSectionsMap();
        IntArrayList sortedYs = new IntArrayList(cubeMap.keySet());
        sortedYs.sort(IntComparators.NATURAL_COMPARATOR);
        List<SectionData> sectionData = new ArrayList<>(sortedYs.size());
        LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        for (int i = 0; i < sortedYs.size(); i++) {
            int sectionY = sortedYs.getInt(i);
            LevelChunkSection section = cubeMap.get(sectionY);
            SectionPos sectionPos = SectionPos.of(pos, sectionY);
            DataLayer sourceBlockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
            DataLayer sourceSkyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
            DataLayer blockLight = sourceBlockLight != null && !sourceBlockLight.isEmpty() ? sourceBlockLight.copy() : null;
            DataLayer skyLight = sourceSkyLight != null && !sourceSkyLight.isEmpty() ? sourceSkyLight.copy() : null;
            boolean hasBlocks = section != null && !section.hasOnlyAir();
            if (hasBlocks || blockLight != null || skyLight != null) {
                sectionData.add(new SectionData(sectionY, hasBlocks ? section.copy() : null, blockLight, skyLight));
            }
        }
        List<CompoundTag> blockEntities = new ArrayList<>(chunk.getBlockEntitiesPos().size());
        for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag blockEntityTag = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
            if (blockEntityTag != null) {
                blockEntities.add(blockEntityTag);
            }
        }
        List<CompoundTag> entities = new ArrayList<>();
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protoChunk = (ProtoChunk) chunk;
            entities.addAll(protoChunk.getEntities());
        }
        Map<Types, long[]> heightmaps = new EnumMap<>(Types.class);
        for (Entry<Types, Heightmap> entry : chunk.getHeightmaps()) {
            long[] data = entry.getValue().getRawData();
            heightmaps.put(entry.getKey(), data.clone());
        }
        PackedTicks ticksForSerialization = chunk.getTicksForSerialization(level.getGameTime());
        ShortList[] postProcessingSections = new ShortList[0];
        CompoundTag structureData = packStructureData(
            StructurePieceSerializationContext.fromLevel(level), pos, chunk.getAllStarts(), chunk.getAllReferences()
        );
        int minSavedSectionY = sortedYs.isEmpty() ? 0 : sortedYs.getInt(0);
        return new SerializableChunkData(
            level.palettedContainerFactory(),
            pos,
            minSavedSectionY,
            level.getGameTime(),
            chunk.getInhabitedTime(),
            chunk.getPersistedStatus(),
            Optionull.map(chunk.getBlendingData(), BlendingData::pack),
            chunk.getBelowZeroRetrogen(),
            chunk.getUpgradeData().copy(),
            heightmaps,
            ticksForSerialization,
            postProcessingSections,
            chunk.isLightCorrect(),
            sectionData,
            entities,
            blockEntities,
            structureData
        );
    }

    /**
     * @author cloudandx
     * @reason Write full 32-bit integer section Y coordinate to NBT instead of casting to 8-bit byte.
     */
    @Overwrite
    public CompoundTag write() {
        CompoundTag tag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        tag.putInt("xPos", this.chunkPos.x());
        tag.putInt("yPos", this.minSectionY);
        tag.putInt("zPos", this.chunkPos.z());
        tag.putLong("LastUpdate", this.lastUpdateTime);
        tag.putLong("InhabitedTime", this.inhabitedTime);
        tag.putString("Status", Objects.requireNonNull(BuiltInRegistries.CHUNK_STATUS.getKey(this.chunkStatus)).toString());
        tag.storeNullable("blending_data", Packed.CODEC, this.blendingData);
        tag.storeNullable("below_zero_retrogen", BelowZeroRetrogen.CODEC, this.belowZeroRetrogen);
        if (!this.upgradeData.isEmpty()) {
            tag.put("UpgradeData", this.upgradeData.write());
        }
        ListTag sectionTags = new ListTag();
        Codec<PalettedContainer<BlockState>> blockStatesCodec = this.containerFactory.blockStatesContainerCodec();
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = this.containerFactory.biomeContainerCodec();
        for (SectionData section : this.sectionData) {
            CompoundTag sectionTag = new CompoundTag();
            LevelChunkSection chunkSection = section.chunkSection();
            if (chunkSection != null) {
                sectionTag.store("block_states", blockStatesCodec, chunkSection.getStates());
                sectionTag.store("biomes", biomeCodec, chunkSection.getBiomes());
            }
            DataLayer blockLight = section.blockLight();
            if (blockLight != null) {
                sectionTag.putByteArray("BlockLight", blockLight.getData());
            }
            DataLayer skyLight = section.skyLight();
            if (skyLight != null) {
                sectionTag.putByteArray("SkyLight", skyLight.getData());
            }
            if (!sectionTag.isEmpty()) {
                sectionTag.putInt("Y", section.y());
                sectionTags.add(sectionTag);
            }
        }
        tag.put("sections", sectionTags);
        if (this.lightCorrect) {
            tag.putBoolean("isLightOn", true);
        }
        ListTag blockEntityTags = new ListTag();
        blockEntityTags.addAll(this.blockEntities);
        tag.put("block_entities", blockEntityTags);
        if (this.chunkStatus.getChunkType() == ChunkType.PROTOCHUNK) {
            ListTag entityTags = new ListTag();
            entityTags.addAll(this.entities);
            tag.put("entities", entityTags);
        }
        saveTicks(tag, this.packedTicks);
        tag.put("PostProcessing", packOffsets(this.postProcessingSections));
        CompoundTag heightmapsTag = new CompoundTag();
        this.heightmaps.forEach((type, data) -> heightmapsTag.put(type.getSerializationKey(), new LongArrayTag(data)));
        tag.put("Heightmaps", heightmapsTag);
        tag.put("structures", this.structureData);
        return tag;
    }
}