package io.github.haykam821.diceyheights.game.map;

import java.util.Set;

import io.github.haykam821.diceyheights.game.player.PlayerEntry;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.api.game.world.generator.TemplateChunkGenerator;

public class DiceyHeightsMap {
	private final DiceyHeightsMapConfig config;

	public static final int START_Y = 0;

	private final BlockBounds waitingPlatformBounds;
	private final int radius;

	private final MapTemplate template;

	public DiceyHeightsMap(DiceyHeightsMapConfig config, RandomSource random) {
		this.config = config;

		int waitingPlatformY = START_Y + config.waitingPlatformHeight().sample(random);
		this.waitingPlatformBounds = BlockBounds.of(-5, waitingPlatformY, -5, 5, waitingPlatformY, 5);

		this.radius = config.radius().sample(random);

		this.template = MapTemplate.createEmpty();
		this.placeWaitingPlatform(random);
	}

	private void placeWaitingPlatform(RandomSource random) {
		for (BlockPos pos : this.waitingPlatformBounds) {
			BlockState state = this.config.waitingPlatformProvider().getState(random, pos);
			this.template.setBlockState(pos, state);
		}
	}

	public void removeWaitingPlatform(ServerLevel world) {
		for (BlockPos pos : this.waitingPlatformBounds) {
			world.destroyBlock(pos, false);
		}
	}

	/**
	 * Determines the position of a player's pillar for a given angle.
	 * @param angle the angle of the pillar in radians
	 * @return the spawn position for players on top of the pillar
	 */
	public Vec3 getPillarPos(RandomSource random, float angle) {
		double x = 0.5 + Math.cos(angle) * this.radius;
		double z = 0.5 + Math.sin(angle) * this.radius;

		int height = this.config.pillarHeight().sample(random);

		return Vec3.atBottomCenterOf(BlockPos.containing(x, START_Y + height, z));
	}

	/**
	 * Places a pillar according to a player's pillar position.
	 */
	public void placePillar(ServerLevel world, RandomSource random, PlayerEntry player) {
		Vec3 pillarPos = player.getPillarPos();
		BlockPos bottomPos = BlockPos.containing(pillarPos.x(), START_Y, pillarPos.z());
		BlockPos.MutableBlockPos pos = bottomPos.mutable();

		while (pos.getY() < pillarPos.y()) {
			BlockState state = this.getPillarBlock(random, pos, player);
			world.setBlockAndUpdate(pos, state);

			pos.move(Direction.UP);
		}
	}

	private BlockState getPillarBlock(RandomSource random, BlockPos pos, PlayerEntry player) {
		if (pos.getY() == START_Y && player.getTeam() != null) {
			return player.getTeam().getBlock();
		}

		return this.config.pillarProvider().getState(random, pos);
	}

	public Vec3 getWaitingSpawnPos() {
		return this.waitingPlatformBounds.centerTop();
	}

	public ChunkGenerator createGenerator(MinecraftServer server) {
		return new TemplateChunkGenerator(server, this.template);
	}

	public void teleportToWaitingSpawn(ServerPlayer player) {
		player.teleportTo(player.level(), this.getWaitingSpawnPos().x(), this.getWaitingSpawnPos().y(), this.getWaitingSpawnPos().z(), Set.of(), 0, 0, true);
	}

	public boolean isOutOfBounds(ServerPlayer player) {
		return player.getY(1) < START_Y;
	}
}
