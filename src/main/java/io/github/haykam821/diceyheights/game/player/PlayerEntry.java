package io.github.haykam821.diceyheights.game.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableSet;

import io.github.haykam821.diceyheights.DiceyHeights;
import io.github.haykam821.diceyheights.game.ItemSpawnStrategy;
import io.github.haykam821.diceyheights.game.map.DiceyHeightsMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Relative;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.util.InventoryUtil;

public class PlayerEntry {
	private static final Identifier FREEZE_ID = DiceyHeights.identifier("freeze");

	private ServerPlayer alivePlayer;

	private final TeamEntry team;

	private final Vec3 pillarPos;
	private final float pillarYaw;

	private final Set<Holder<Attribute>> attributes = new HashSet<>();

	public PlayerEntry(ServerPlayer player, TeamEntry team, Vec3 pillarPos, float pillarYaw) {
		this.alivePlayer = player;

		this.team = team;

		this.pillarPos = pillarPos;
		this.pillarYaw = pillarYaw;
	}

	/**
	 * {@return the player entity, or {@code null} if the player has been eliminated}
	 */
	public ServerPlayer getAlivePlayer() {
		return this.alivePlayer;
	}

	public void clearAlivePlayer() {
		this.alivePlayer = null;
	}

	public TeamEntry getTeam() {
		return this.team;
	}

	public Vec3 getPillarPos() {
		return this.pillarPos;
	}

	public void spawn(DiceyHeightsMap map, ServerLevel world, RandomSource random, int ticksUntilNextItem) {
		map.placePillar(world, random, this);

		this.reset(GameType.SURVIVAL);
		this.teleportToPillar(world, true);

		this.addSpawnAttributeModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(FREEZE_ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		this.addSpawnAttributeModifier(Attributes.JUMP_STRENGTH, new AttributeModifier(FREEZE_ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	public void tick(ServerLevel world, ItemSpawnStrategy strategy, int ticksUntilNextItem, boolean beforeFirstItem) {
		if (!beforeFirstItem) {
			this.clearSpawnAttributeModifiers();
		} else if (ticksUntilNextItem % 5 == 0) {
			this.teleportToPillar(world, false);
		}

		if (this.isPillarApplicable(strategy)) {
			spawnPillarItemSpawnParticles(world, this.pillarPos);
		}
	}

	public void giveItemStack(ServerLevel world, ItemSpawnStrategy strategy, Supplier<ItemStack> stackSupplier) {
		if (this.isPillarApplicable(strategy)) {
			ItemStack stack = stackSupplier.get();

			if (!stack.isEmpty()) {
				ItemEntity entity = new ItemEntity(world, this.pillarPos.x(), this.pillarPos.y(), this.pillarPos.z(), stack);
				world.addFreshEntity(entity);
			}
		} else if (strategy == ItemSpawnStrategy.DIRECT && this.alivePlayer != null) {
			this.alivePlayer.addItem(stackSupplier.get());
		}
	}

	private void teleportToPillar(ServerLevel world, boolean initial) {
		this.alivePlayer.fallDistance = 0;

		if (initial) {
			this.alivePlayer.setDeltaMovement(Vec3.ZERO);
			this.alivePlayer.connection.send(new ClientboundSetEntityMotionPacket(this.alivePlayer));

			this.alivePlayer.teleportTo(world, this.pillarPos.x(), this.pillarPos.y(), this.pillarPos.z(), Set.of(), this.pillarYaw, 0, true);
		} else {
			Set<Relative> flags = ImmutableSet.of(Relative.X_ROT, Relative.Y_ROT);
			this.alivePlayer.connection.teleport(new PositionMoveRotation(this.pillarPos, this.alivePlayer.getDeltaMovement(), 0, 0), flags);
		}
	}

	private void addSpawnAttributeModifier(Holder<Attribute> attribute, AttributeModifier modifier) {
		if (!modifier.is(FREEZE_ID)) {
			throw new IllegalArgumentException("Spawn attribute modifier has incorrect ID " + modifier.id());
		}

		this.alivePlayer.getAttribute(attribute).addTransientModifier(modifier);
		this.attributes.add(attribute);
	}

	private void clearSpawnAttributeModifiers() {
		for (Holder<Attribute> attribute : this.attributes) {
			this.alivePlayer.getAttribute(attribute).removeModifier(FREEZE_ID);
		}

		this.attributes.clear();
	}

	private boolean isPillarApplicable(ItemSpawnStrategy strategy) {
		if (strategy == ItemSpawnStrategy.AT_PILLAR) return true;
        return strategy == ItemSpawnStrategy.AT_PILLAR_WHEN_ALIVE && this.alivePlayer != null;
    }

	public Component getWinMessage() {
		return Component.translatable("text.diceyheights.win", this.alivePlayer.getDisplayName()).withStyle(ChatFormatting.GOLD);
	}

	public Component getEliminationMessage() {
		return Component.translatable("text.diceyheights.eliminated", this.alivePlayer.getDisplayName()).withStyle(ChatFormatting.RED);
	}

	public void reset(GameType gameMode) {
		this.alivePlayer.setGameMode(gameMode);

		this.alivePlayer.removeAllEffects();
		InventoryUtil.clear(this.alivePlayer);

		// https://bugs.mojang.com/browse/MC-99785
		List<Leashable> leashEntities = Leashable.leashableLeashedTo(this.alivePlayer);

		for (Leashable entity : leashEntities) {
			if (entity.isLeashed() && entity.getLeashHolder() == this.alivePlayer) {
				entity.dropLeash();
			}
		}
	}

	@Override
	public String toString() {
		return "PlayerEntry{alivePlayer=" + this.alivePlayer + ", pillarPos=" + this.pillarPos + ", pillarYaw=" + this.pillarYaw + "}";
	}

	private static void spawnPillarItemSpawnParticles(ServerLevel world, Vec3 pos) {
		// This effect could be improved
		ParticleOptions particle = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_WOOL.defaultBlockState());
		world.sendParticles(particle, pos.x(), pos.y(), pos.z(), 0, 0, 1, 0, 1);
	}
}
