package io.github.haykam821.diceyheights.game;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.diceyheights.game.map.DiceyHeightsMapConfig;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.Item;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderSet;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamList;

public record DiceyHeightsConfig(
        WaitingLobbyConfig playerConfig,
        DiceyHeightsMapConfig mapConfig,
        Optional<GameTeamList> teams,
        Optional<IntProvider> ticksUntilFirstItem,
        IntProvider ticksUntilNextItem,
        IntProvider beats,
        Optional<HolderSet<Item>> items,
        IntProvider itemRolls,
        IntProvider itemCount,
        ItemSpawnStrategy itemSpawnStrategy,
        boolean separate,
        IntProvider ticksUntilClose
) {
	public static final MapCodec<DiceyHeightsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(DiceyHeightsConfig::playerConfig),
        DiceyHeightsMapConfig.CODEC.optionalFieldOf("map", DiceyHeightsMapConfig.DEFAULT).forGetter(DiceyHeightsConfig::mapConfig),
        GameTeamList.CODEC.optionalFieldOf("teams").forGetter(DiceyHeightsConfig::teams),
        IntProvider.POSITIVE_CODEC.optionalFieldOf("ticks_until_first_item").forGetter(DiceyHeightsConfig::ticksUntilFirstItem),
        IntProvider.POSITIVE_CODEC.optionalFieldOf("ticks_until_next_item", ConstantInt.of(SharedConstants.TICKS_PER_SECOND * 3)).forGetter(DiceyHeightsConfig::ticksUntilNextItem),
        IntProvider.POSITIVE_CODEC.optionalFieldOf("beats", ConstantInt.of(3)).forGetter(DiceyHeightsConfig::beats),
        RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(DiceyHeightsConfig::items),
        IntProvider.POSITIVE_CODEC.optionalFieldOf("item_rolls", ConstantInt.of(1)).forGetter(DiceyHeightsConfig::itemRolls),
        IntProvider.POSITIVE_CODEC.optionalFieldOf("item_count", ConstantInt.of(1)).forGetter(DiceyHeightsConfig::itemCount),
        ItemSpawnStrategy.CODEC.optionalFieldOf("item_spawn_strategy", ItemSpawnStrategy.DIRECT).forGetter(DiceyHeightsConfig::itemSpawnStrategy),
        Codec.BOOL.optionalFieldOf("separate", false).forGetter(DiceyHeightsConfig::separate),
        IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("ticks_until_close", ConstantInt.of(SharedConstants.TICKS_PER_SECOND * 5)).forGetter(DiceyHeightsConfig::ticksUntilClose)
    ).apply(instance, DiceyHeightsConfig::new));
}
