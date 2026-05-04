package org.leavesmc.leaves.world;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class NatureSpawnChunkMap {

    private final Long2ReferenceOpenHashMap<Entry> entries = new Long2ReferenceOpenHashMap<>();

    public void addPlayer(ServerPlayer player) {
        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;
        byte range = (byte) (player.playerNaturallySpawnedEvent == null ? 8 : player.playerNaturallySpawnedEvent.getSpawnRadius());
        range = (byte) Math.min(range, 8);
        int minX = chunkX - range;
        int maxX = chunkX + range;
        int minZ = chunkZ - range;
        int maxZ = chunkZ + range;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long key = ChunkPos.asLong(x, z);
                entries.computeIfAbsent(key, k -> new Entry()).count++;
            }
        }
    }

    public void build(ca.spottedleaf.moonrise.common.list.ReferenceList<LevelChunk> tickChunks, java.util.List<LevelChunk> out) {
        out.clear();
        LevelChunk[] raw = tickChunks.getRawDataUnchecked();
        int size = tickChunks.size();
        for (int i = 0; i < size; i++) {
            LevelChunk chunk = raw[i];
            if (entries.containsKey(chunk.coordinateKey)) {
                out.add(chunk);
            }
        }
    }

    public void clear() {
        entries.clear();
    }

    private static final class Entry {
        int count;
    }
}
