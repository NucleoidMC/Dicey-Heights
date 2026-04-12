package io.github.haykam821.diceyheights.game.phase;

import java.util.Optional;

import io.github.haykam821.diceyheights.game.DiceyHeightsConfig;
import io.github.haykam821.diceyheights.game.map.DiceyHeightsMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.common.team.TeamSelectionLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class DiceyHeightsWaitingPhase implements GameActivityEvents.RequestStart, GameActivityEvents.Tick, GamePlayerEvents.Accept, PlayerDeathEvent {
	private final GameSpace gameSpace;
	private final ServerLevel world;
	private final DiceyHeightsMap map;
	private final DiceyHeightsConfig config;
	private final Optional<TeamSelectionLobby> teamSelection;

	public DiceyHeightsWaitingPhase(GameSpace gameSpace, ServerLevel world, DiceyHeightsMap map, DiceyHeightsConfig config, Optional<TeamSelectionLobby> teamSelection) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.map = map;
		this.config = config;
		this.teamSelection = teamSelection;
	}

	public static GameOpenProcedure open(GameOpenContext<DiceyHeightsConfig> context) {
		DiceyHeightsConfig config = context.config();

		RandomSource random = RandomSource.createNewThreadLocalInstance();
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
			activity.listen(GamePlayerEvents.ACCEPT, phase);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
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
		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			if (this.map.isOutOfBounds(player)) {
				this.map.teleportToWaitingSpawn(player);
			}
		}
	}

	@Override
	public JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.world, this.map.getWaitingSpawnPos()).thenRunForEach(player -> {
			player.setGameMode(GameType.ADVENTURE);
		});
	}

	@Override
	public EventResult onDeath(ServerPlayer player, DamageSource source) {
		this.map.teleportToWaitingSpawn(player);
		return EventResult.DENY;
	}
}
