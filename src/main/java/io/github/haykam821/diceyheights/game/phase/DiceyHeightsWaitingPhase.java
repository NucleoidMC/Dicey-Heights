package io.github.haykam821.diceyheights.game.phase;

import java.util.Optional;

import io.github.haykam821.diceyheights.game.DiceyHeightsConfig;
import io.github.haykam821.diceyheights.game.map.DiceyHeightsMap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.common.team.TeamSelectionLobby;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class DiceyHeightsWaitingPhase implements GameActivityEvents.RequestStart, GameActivityEvents.Tick, GamePlayerEvents.Offer, PlayerDeathEvent {
	private final GameSpace gameSpace;
	private final ServerWorld world;
	private final DiceyHeightsMap map;
	private final DiceyHeightsConfig config;
	private final Optional<TeamSelectionLobby> teamSelection;

	public DiceyHeightsWaitingPhase(GameSpace gameSpace, ServerWorld world, DiceyHeightsMap map, DiceyHeightsConfig config, Optional<TeamSelectionLobby> teamSelection) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.map = map;
		this.config = config;
		this.teamSelection = teamSelection;
	}

	public static GameOpenProcedure open(GameOpenContext<DiceyHeightsConfig> context) {
		DiceyHeightsConfig config = context.config();

		Random random = Random.createLocal();
		DiceyHeightsMap map = new DiceyHeightsMap(config.mapConfig(), random);

		RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
			.setGenerator(map.createGenerator(context.server()));

		return context.openWithWorld(worldConfig, (activity, world) -> {
			Optional<TeamSelectionLobby> teamSelection = config.teams().map(teams -> TeamSelectionLobby.addTo(activity, teams));
			DiceyHeightsWaitingPhase phase = new DiceyHeightsWaitingPhase(activity.getGameSpace(), world, map, config, teamSelection);

			GameWaitingLobby.addTo(activity, config.playerConfig());

			DiceyHeightsActivePhase.setRules(activity, false);

			// Listeners
			activity.listen(GameActivityEvents.REQUEST_START, phase);
			activity.listen(GameActivityEvents.TICK, phase);
			activity.listen(GamePlayerEvents.OFFER, phase);
			activity.listen(PlayerDeathEvent.EVENT, phase);
		});
	}

	// Listeners

	@Override
	public GameResult onRequestStart() {
		DiceyHeightsActivePhase.open(this.gameSpace, this.world, this.map, this.config, this.teamSelection);
		return GameResult.ok();
	}

	@Override
	public void onTick() {
		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			if (this.map.isOutOfBounds(player)) {
				this.map.teleportToWaitingSpawn(player);
			}
		}
	}

	@Override
	public PlayerOfferResult onOfferPlayer(PlayerOffer offer) {
		return offer.accept(this.world, this.map.getWaitingSpawnPos()).and(() -> {
			offer.player().changeGameMode(GameMode.ADVENTURE);
		});
	}

	@Override
	public ActionResult onDeath(ServerPlayerEntity player, DamageSource source) {
		this.map.teleportToWaitingSpawn(player);
		return ActionResult.FAIL;
	}
}
