package org.leavesmc.leaves.async;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.leavesmc.leaves.async.chunk.AsyncChunkLoadManager;
import org.leavesmc.leaves.async.chunk.AsyncChunkSend;
import org.leavesmc.leaves.async.path.AsyncPathProcessor;

@NullMarked
public final class ShutdownExecutors {

    public static void shutdown(MinecraftServer server) {
        if (org.leavesmc.leaves.LeavesConfig.async.asyncMobSpawning) {
            server.mobSpawnExecutor.shutdown();
        }
        if (org.leavesmc.leaves.LeavesConfig.async.asyncChunkSend) {
            AsyncChunkSend.POOL.shutdown();
        }
        if (org.leavesmc.leaves.LeavesConfig.async.asyncPathfinding) {
            AsyncPathProcessor.PATH_EXECUTOR.shutdown();
        }
        if (org.leavesmc.leaves.LeavesConfig.async.asyncChunkLoading) {
            AsyncChunkLoadManager.getLastSnapshot();
        }
    }
}

