package io.github.haykam821.diceyheights.game.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.api.game.level.generator.GameChunkGenerator;

import static io.github.haykam821.diceyheights.game.map.DiceyHeightsMap.START_Y;

public class DiceyHeightsMapGenerator extends GameChunkGenerator {
    private final DiceyHeightsMapConfig config;

    public DiceyHeightsMapGenerator(DiceyHeightsMapConfig config, MinecraftServer server) {
        super(createBiomeSource(server, Biomes.THE_VOID));
        this.config = config;
    }
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureAccessor) {
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = level.getRandom();
        int waitingPlatformY = START_Y + config.waitingPlatformHeight().sample(random);
        BlockBounds waitingPlatformBounds = BlockBounds.of(-5, waitingPlatformY, -5, 5, waitingPlatformY, 5);
        BlockPos min = new BlockPos(chunkPos.x() * 16, waitingPlatformY, chunkPos.z() * 16);
        BlockPos max = new BlockPos(chunkPos.x() * 16 + 15, waitingPlatformY, chunkPos.z() * 16 + 15);
        BlockBounds chunkBounds = new BlockBounds(min, max);
        if (chunkBounds.intersects(waitingPlatformBounds)) {
            for (BlockPos pos : chunkBounds) {
                if (waitingPlatformBounds.contains(pos)) {
                    BlockState state = this.config.waitingPlatformProvider().getState(level, random, pos);
                    chunk.setBlockState(pos, state);
                }
            }
        }
    }
}
