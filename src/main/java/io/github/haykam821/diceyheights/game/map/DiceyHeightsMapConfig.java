package io.github.haykam821.diceyheights.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record DiceyHeightsMapConfig(
        IntProvider waitingPlatformSize,
        IntProvider waitingPlatformHeight,
        BlockStateProvider waitingPlatformProvider,
        IntProvider radius,
        IntProvider pillarHeight,
        BlockStateProvider pillarProvider,
        int maxHeight
) {
	public static final DiceyHeightsMapConfig DEFAULT = new DiceyHeightsMapConfig(
		ConstantInt.of(5),
		ConstantInt.of(24),
		BlockStateProvider.simple(Blocks.MAGENTA_CONCRETE),
		ConstantInt.of(12),
		ConstantInt.of(24),
		BlockStateProvider.simple(Blocks.BEDROCK),
		44
	);

	public static final Codec<DiceyHeightsMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("waiting_platform_size", DEFAULT.waitingPlatformSize()).forGetter(DiceyHeightsMapConfig::waitingPlatformSize),
        IntProvider.POSITIVE_CODEC.optionalFieldOf("waiting_platform_height", DEFAULT.waitingPlatformHeight()).forGetter(DiceyHeightsMapConfig::waitingPlatformHeight),
        BlockStateProvider.CODEC.optionalFieldOf("waiting_platform_provider", DEFAULT.waitingPlatformProvider()).forGetter(DiceyHeightsMapConfig::waitingPlatformProvider),
        IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("radius", DEFAULT.radius()).forGetter(DiceyHeightsMapConfig::radius),
        IntProvider.POSITIVE_CODEC.optionalFieldOf("pillar_height", DEFAULT.pillarHeight()).forGetter(DiceyHeightsMapConfig::pillarHeight),
        BlockStateProvider.CODEC.optionalFieldOf("pillar_provider", DEFAULT.pillarProvider()).forGetter(DiceyHeightsMapConfig::pillarProvider),
        Codec.INT.optionalFieldOf("max_height", DEFAULT.maxHeight()).forGetter(DiceyHeightsMapConfig::maxHeight)
    ).apply(instance, DiceyHeightsMapConfig::new));
}
