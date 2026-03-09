package net.kronoz.odyssey.block.custom;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

final class ClientBlockScanHelper {
    private ClientBlockScanHelper() {
    }

    static void scanChunk(ClientWorld world,
                          ChunkPos chunkPos,
                          Predicate<BlockState> predicate,
                          BiConsumer<BlockPos, BlockState> consumer) {
        if (world == null || chunkPos == null || predicate == null || consumer == null) {
            return;
        }

        WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return;
        }

        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSection = world.getBottomSectionCoord();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) {
                continue;
            }
            int worldYBase = (bottomSection + sectionIndex) << 4;
            for (int y = 0; y < 16; y++) {
                int worldY = worldYBase + y;
                for (int x = 0; x < 16; x++) {
                    int worldX = startX + x;
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!predicate.test(state)) {
                            continue;
                        }
                        mutable.set(worldX, worldY, startZ + z);
                        consumer.accept(mutable.toImmutable(), state);
                    }
                }
            }
        }
    }
}
