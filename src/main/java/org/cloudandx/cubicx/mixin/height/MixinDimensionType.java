package org.cloudandx.cubicx.mixin.core.height;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.CardinalLighting.Type;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.DimensionType.MonsterSettings;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.cloudandx.cubicx.util.CubicMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DimensionType.class)
public abstract class MixinDimensionType {
    @Shadow
    @Final
    @Mutable
    public static int Y_SIZE;

    @Shadow
    @Final
    @Mutable
    public static int MAX_Y;

    @Shadow
    @Final
    @Mutable
    public static int MIN_Y;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void cubicx$unlockStaticBounds(CallbackInfo ci) {
        Y_SIZE = CubicMath.MAX_BLOCK;
        MAX_Y = CubicMath.MAX_BLOCK - 1;
        MIN_Y = CubicMath.MIN_BLOCK;
    }

    /**
     * @author cloudandx
     * @reason Unlock dimension codec intRange limits to full unbuffered 32-bit signed integer coordinates.
     */
    @Overwrite
    private static Codec<DimensionType> createDirectCodec(final Codec<EnvironmentAttributeMap> attributeMapCodec) {
        return ExtraCodecs.catchDecoderException(
            RecordCodecBuilder.create(
                i -> i.group(
                        Codec.BOOL.optionalFieldOf("has_fixed_time", false).forGetter(DimensionType::hasFixedTime),
                        Codec.BOOL.fieldOf("has_skylight").forGetter(DimensionType::hasSkyLight),
                        Codec.BOOL.fieldOf("has_ceiling").forGetter(DimensionType::hasCeiling),
                        Codec.BOOL.fieldOf("has_ender_dragon_fight").forGetter(DimensionType::hasEnderDragonFight),
                        Codec.doubleRange(1.0E-5F, 3.0E7).fieldOf("coordinate_scale").forGetter(DimensionType::coordinateScale),
                        Codec.intRange(CubicMath.MIN_BLOCK, CubicMath.MAX_BLOCK).fieldOf("min_y").forGetter(DimensionType::minY),
                        Codec.intRange(16, CubicMath.MAX_BLOCK).fieldOf("height").forGetter(DimensionType::height),
                        Codec.intRange(0, CubicMath.MAX_BLOCK).fieldOf("logical_height").forGetter(DimensionType::logicalHeight),
                        RegistryCodecs.holderSet(Registries.BLOCK).fieldOf("infiniburn").forGetter(DimensionType::infiniburn),
                        Codec.FLOAT.fieldOf("ambient_light").forGetter(DimensionType::ambientLight),
                        MonsterSettings.CODEC.forGetter(DimensionType::monsterSettings),
                        Skybox.CODEC.optionalFieldOf("skybox", Skybox.OVERWORLD).forGetter(DimensionType::skybox),
                        Type.CODEC.optionalFieldOf("cardinal_light", Type.DEFAULT).forGetter(DimensionType::cardinalLightType),
                        attributeMapCodec.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(DimensionType::attributes),
                        RegistryCodecs.holderSet(Registries.TIMELINE).optionalFieldOf("timelines", HolderSet.empty()).forGetter(DimensionType::timelines),
                        WorldClock.CODEC.optionalFieldOf("default_clock").forGetter(DimensionType::defaultClock)
                    )
                    .apply(i, DimensionType::new)
            )
        );
    }

    /**
     * @author cloudandx
     * @reason Unlock minimum dimension Y to exact Integer.MIN_VALUE without buffer zones.
     */
    @Overwrite
    public int minY() {
        return CubicMath.MIN_BLOCK;
    }

    /**
     * @author cloudandx
     * @reason Provide maximum 32-bit positive height for legacy callers.
     */
    @Overwrite
    public int height() {
        return CubicMath.MAX_BLOCK;
    }

    /**
     * @author cloudandx
     * @reason Provide maximum 32-bit positive logical height for legacy callers.
     */
    @Overwrite
    public int logicalHeight() {
        return CubicMath.MAX_BLOCK;
    }
}