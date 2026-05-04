package org.leavesmc.leaves.protocol.bladeren;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.LeavesConfig;
import org.leavesmc.leaves.async.chunk.AsyncChunkLoadManager;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;

import java.util.ArrayList;
import java.util.List;

@LeavesProtocol.Register(namespace = "bladeren")
public class AsyncChunkLoadMetricsProtocol implements LeavesProtocol {

    public static final String PROTOCOL_ID = "bladeren";
    private static final Identifier ASYNC_CHUNK_LOAD = id("async_chunk_load");
    private static final List<ServerPlayer> players = new ArrayList<>();

    @Contract("_ -> new")
    public static Identifier id(String path) {
        return Identifier.tryBuild(PROTOCOL_ID, path);
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLeave(@NotNull ServerPlayer player) {
        players.remove(player);
    }

    @ProtocolHandler.Ticker
    public static void tick() {
        if (players.isEmpty() || !LeavesConfig.async.asyncChunkLoadingMetricsProtocol) {
            return;
        }
        AsyncChunkLoadManager.Snapshot snapshot = AsyncChunkLoadManager.getLastSnapshot();
        players.forEach(player -> ProtocolUtils.sendBytebufPacket(player, ASYNC_CHUNK_LOAD, buf -> {
            buf.writeDouble(snapshot.averageMspt);
            buf.writeDouble(snapshot.tps);
            buf.writeVarInt(snapshot.inFlight);
            buf.writeVarInt(snapshot.completionQueueSize);
            buf.writeVarInt(snapshot.activeAsyncThreads);
            buf.writeLong(snapshot.usedHeap);
            buf.writeLong(snapshot.committedHeap);
            buf.writeLong(snapshot.gcCollections);
            buf.writeLong(snapshot.gcCollectionTime);
            buf.writeLong(snapshot.completed);
            buf.writeLong(snapshot.failed);
            buf.writeLong(snapshot.fallbacks);
            buf.writeLong(snapshot.rejected);
            buf.writeDouble(snapshot.averageChunkLoadMs());
            buf.writeLong(snapshot.averageHeapDeltaBytes());
            buf.writeBoolean(snapshot.degraded);
        }));
    }

    public static void subscribe(@NotNull ServerPlayer player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public static void unsubscribe(@NotNull ServerPlayer player) {
        players.remove(player);
    }

    @Override
    public int tickerInterval(String tickerID) {
        return Math.max(1, LeavesConfig.async.asyncChunkLoadingMonitorInterval);
    }

    @Override
    public boolean isActive() {
        return LeavesConfig.async.asyncChunkLoadingMetricsProtocol;
    }
}
