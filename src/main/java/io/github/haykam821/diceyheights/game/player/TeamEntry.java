package io.github.haykam821.diceyheights.game.player;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.util.ColoredBlocks;

public class TeamEntry {
	private final GameTeam team;

	public TeamEntry(GameTeam team) {
		this.team = team;
	}

	public BlockState getBlock() {
		return ColoredBlocks.concrete(this.team.config().blockDyeColor()).defaultBlockState();
	}

	public Component getWinMessage() {
		return Component.translatable("text.diceyheights.win.team", this.team.config().name()).withStyle(ChatFormatting.GOLD);
	}

	@Override
	public String toString() {
		return "TeamEntry{team=" + this.team + "}";
	}
}
