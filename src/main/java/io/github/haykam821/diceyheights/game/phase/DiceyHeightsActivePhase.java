package io.github.haykam821.diceyheights.game.phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.haykam821.diceyheights.game.DiceyHeightsConfig;
import io.github.haykam821.diceyheights.game.ItemSpawnStrategy;
import io.github.haykam821.diceyheights.game.map.DiceyHeightsMap;
import io.github.haykam821.diceyheights.game.player.PlayerEntry;
import io.github.haykam821.diceyheights.game.player.TeamEntry;
import io.github.haykam821.diceyheights.game.win.FreeForAllWinManager;
import io.github.haykam821.diceyheights.game.win.TeamWinManager;
import io.github.haykam821.diceyheights.game.win.WinManager;
import io.github.haykam821.diceyheights.game.win.WinResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.game.GameActivity;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamConfig;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamKey;
import xyz.nucleoid.plasmid.api.game.common.team.TeamChat;
import xyz.nucleoid.plasmid.api.game.common.team.TeamManager;
import xyz.nucleoid.plasmid.api.game.common.team.TeamSelectionLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class DiceyHeightsActivePhase implements GameActivityEvents.Enable, GameActivityEvents.Tick, GamePlayerEvents.Accept, GamePlayerEvents.Remove, PlayerDeathEvent {
	private final GameSpace gameSpace;
	private final RandomSource random;
	private final ServerLevel level;
	private final DiceyHeightsMap map;
	private final DiceyHeightsConfig config;

	private final List<PlayerEntry> players;
	private final boolean singleplayer;

	private final WinManager winManager;

	private final HolderSet<Item> items;

	private boolean beforeFirstItem = true;
	private int ticksUntilNextItem;
	private int ticksPerBeat;

	private int ticksUntilClose = -1;

	public DiceyHeightsActivePhase(GameSpace gameSpace, ServerLevel level, DiceyHeightsMap map, DiceyHeightsConfig config, Optional<TeamSelectionLobby> maybeTeamSelection, Optional<TeamManager> maybeTeamManager) {
		this.gameSpace = gameSpace;
		this.level = level;
		this.random = level.getRandom();
		this.map = map;
		this.config = config;

		PlayerSet participants = this.gameSpace.getPlayers().participants();

		List<ServerPlayer> shuffledPlayers = participants.stream().collect(Collectors.toCollection(ArrayList::new));
		Util.shuffle(shuffledPlayers, this.random);

		this.players = new ArrayList<>(shuffledPlayers.size());
		this.winManager = maybeTeamSelection.isPresent() ? new TeamWinManager(this) : new FreeForAllWinManager(this);

		int index = 0;

		Map<UUID, GameTeamKey> playersToTeams = new HashMap<>();
		Map<GameTeamKey, TeamEntry> keysToTeams = new HashMap<>();

		maybeTeamSelection.ifPresent(teamSelection -> {
			teamSelection.allocate(participants, (key, player) -> {
				playersToTeams.put(player.getUUID(), key);
				maybeTeamManager.get().addPlayerTo(player, key);
			});
		});

		for (ServerPlayer player : shuffledPlayers) {
			float angle = (index / (float) shuffledPlayers.size()) * (Mth.PI * 2);

			Vec3 pillarPos = this.map.getPillarPos(this.random, angle);
			float pillarYaw = angle * Mth.RAD_TO_DEG + 90;

			GameTeamKey key = playersToTeams.get(player.getUUID());

			TeamEntry team = key == null ? null : keysToTeams.computeIfAbsent(key, k -> new TeamEntry(this.config.teams().orElseThrow().byKey(k)));

			this.players.add(new PlayerEntry(player, team, pillarPos, pillarYaw));

			index += 1;
		}

		this.singleplayer = players.size() == 1;

		this.items = this.config.items().orElseGet(() -> {
			List<Holder.Reference<Item>> items = this.level.registryAccess()
				.lookupOrThrow(Registries.ITEM)
				.listElements()
				.filter(this::isItemEnabled)
				.toList();

			return HolderSet.direct(items);
		});

		this.resetTicksUntilNextItem(true, this.config.ticksUntilFirstItem().orElse(this.config.ticksUntilNextItem()));
	}

	public static void setRules(GameActivity activity, boolean pvp) {
		activity.deny(GameRuleType.PORTALS);
		activity.deny(GameRuleType.SATURATED_REGENERATION);

		if (pvp) {
			activity.allow(GameRuleType.PLAYER_PROJECTILE_KNOCKBACK);
		} else {
			activity.deny(GameRuleType.CRAFTING);
			activity.deny(GameRuleType.FALL_DAMAGE);
			activity.deny(GameRuleType.HUNGER);
			activity.deny(GameRuleType.MODIFY_ARMOR);
			activity.deny(GameRuleType.MODIFY_INVENTORY);
			activity.deny(GameRuleType.PICKUP_ITEMS);
			activity.deny(GameRuleType.PVP);
			activity.deny(GameRuleType.THROW_ITEMS);
		}
	}

	public static void open(GameSpace gameSpace, ServerLevel level, DiceyHeightsMap map, DiceyHeightsConfig config, Optional<TeamSelectionLobby> teamSelection) {
		gameSpace.setActivity(activity -> {
			Optional<TeamManager> maybeTeamManager = config.teams().map(teams -> {
				TeamManager teamManager = TeamManager.addTo(activity);
				TeamChat.addTo(activity, teamManager);

				for (GameTeam team : config.teams().get()) {
					GameTeamConfig teamConfig = GameTeamConfig.builder(team.config())
						.setFriendlyFire(false)
						.build();

					teamManager.addTeam(team.key(), teamConfig);
				}

				return teamManager;
			});

			DiceyHeightsActivePhase phase = new DiceyHeightsActivePhase(gameSpace, level, map, config, teamSelection, maybeTeamManager);

			DiceyHeightsActivePhase.setRules(activity, true);

			// Listeners
			activity.listen(GameActivityEvents.ENABLE, phase);
			activity.listen(GameActivityEvents.TICK, phase);
			activity.listen(GamePlayerEvents.ACCEPT, phase);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
			activity.listen(GamePlayerEvents.REMOVE, phase);
			activity.listen(PlayerDeathEvent.EVENT, phase);
		});
	}

	// Listeners

	@Override
	public void onEnable() {
		this.map.removeWaitingPlatform(this.level);

		for (PlayerEntry player : this.players) {
			player.spawn(this.map, this.level, this.random, this.ticksUntilNextItem);
		}

		for (ServerPlayer player : this.gameSpace.getPlayers().spectators()) {
			this.map.teleportToWaitingSpawn(player);
			player.setGameMode(GameType.SPECTATOR);
		}
	}

	@Override
	public void onTick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		for (PlayerEntry entry : this.players) {
			entry.tick(this.level, this.config.itemSpawnStrategy(), this.ticksUntilNextItem, this.beforeFirstItem);

			ServerPlayer player = entry.getAlivePlayer();

			if (player != null) {
				if (player.getY() > (DiceyHeightsMap.START_Y + this.config.mapConfig().maxHeight())) {
					player.hurtServer(this.level, this.level.damageSources().fellOutOfWorld(), 1);
				} else if (map.isOutOfBounds(player)) {
					this.eliminate(entry);
				}
			}
		}

		this.ticksUntilNextItem -= 1;

		if (this.ticksUntilNextItem <= 0) {
			this.resetTicksUntilNextItem(false, this.config.ticksUntilNextItem());

			this.gameSpace.getPlayers().playSound(SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 1, 1);
			this.giveRandomItems();
		} else if (this.ticksPerBeat > 0 && this.ticksUntilNextItem % this.ticksPerBeat == 0) {
			this.gameSpace.getPlayers().playSound(SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.PLAYERS, 1, 1f);
		}

		WinResult win = this.winManager.checkWin();

		if (win != null) {
			this.gameSpace.getPlayers().sendMessage(win.message());
			this.ticksUntilClose = this.config.ticksUntilClose().sample(this.random);
		}
	}

	@Override
	public JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.level, this.map.getWaitingSpawnPos()).thenRunForEach(player -> {
			player.setGameMode(GameType.SPECTATOR);
		});
	}

	@Override
	public void onRemovePlayer(ServerPlayer player) {
		this.eliminate(this.getPlayerEntry(player));
	}

	@Override
	public EventResult onDeath(ServerPlayer player, DamageSource source) {
		this.eliminate(this.getPlayerEntry(player));
		return EventResult.DENY;
	}

	// Utilities

	private boolean isItemEnabled(Holder<Item> entry) {
		if (entry.unwrapKey().isPresent() && !entry.unwrapKey().get().identifier().getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			return false;
		}

		Item item = entry.value();
		return !(item instanceof GameMasterBlockItem) && !(item instanceof AirItem) && item.isEnabled(this.level.enabledFeatures());
	}

	private void resetTicksUntilNextItem(boolean beforeFirstItem, IntProvider provider) {
		this.beforeFirstItem = beforeFirstItem;
		this.ticksUntilNextItem = provider.sample(this.random);
		this.ticksPerBeat = this.ticksUntilNextItem / this.config.beats().sample(this.random);
	}

	private void giveRandomItems() {
		ItemSpawnStrategy strategy = this.config.itemSpawnStrategy();
		int itemRolls = this.config.itemRolls().sample(this.random);

		for (int roll = 0; roll < itemRolls; roll += 1) {
			if (this.config.separate()) {
				for (PlayerEntry player : this.players) {
					player.giveItemStack(this.level, strategy, () -> {
						return this.getRandomItem()
							.map(entry -> {
								int count = this.config.itemCount().sample(this.random);
								return new ItemStack(entry, count);
							})
							.orElse(ItemStack.EMPTY);
					});
				}
			} else {
				this.getRandomItem().ifPresent(entry -> {
					for (PlayerEntry player : this.players) {
						player.giveItemStack(this.level, strategy, () -> {
							int count = this.config.itemCount().sample(this.random);
							return new ItemStack(entry, count);
						});
					}
				});
			}
		}
	}

	private Optional<Holder<Item>> getRandomItem() {
		return this.items.getRandomElement(this.random);
	}

	/**
	 * Attempts to eliminate a player.
	 */
	public void eliminate(PlayerEntry player) {
		if (this.isGameEnding()) return;

		if (player == null) return;
		if (player.getAlivePlayer() == null) return;

		// Send elimination message
		Component message = player.getEliminationMessage();
		this.gameSpace.getPlayers().sendMessage(message);

		// Perform removal operations
		player.reset(GameType.SPECTATOR);
		player.clearAlivePlayer();

	}

	public List<PlayerEntry> getPlayers() {
		return this.players;
	}

	public boolean isSingleplayer() {
		return this.singleplayer;
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	private PlayerEntry getPlayerEntry(ServerPlayer player) {
		if (player != null) {
			for (PlayerEntry entry : this.players) {
				if (player == entry.getAlivePlayer()) {
					return entry;
				}
			}
		}

		return null;
	}
}
