package org.leavesmc.leaves.bytebuf.internal;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.leavesmc.leaves.LeavesConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketProtector extends ChannelDuplexHandler {

    static final String handlerName = "leaves-packet-protector";
    private static final String guardName = "leaves-chunk-buf-guard";

    private final ConcurrentLinkedQueue<int[]> recentChunks = new ConcurrentLinkedQueue<>();
    private boolean guardInjected = false;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!LeavesConfig.mics.packetProtector) {
            super.write(ctx, msg, promise);
            return;
        }

        if (!guardInjected) {
            injectGuard(ctx);
            guardInjected = true;
        }

        super.write(ctx, msg, promise);
    }

    void trackChunk(int x, int z) {
        recentChunks.add(new int[]{x, z});
        while (recentChunks.size() > 128) {
            recentChunks.poll();
        }
    }

    int[] pollRecentChunk() {
        return recentChunks.poll();
    }

    private void injectGuard(ChannelHandlerContext ctx) {
        try {
            if (ctx.pipeline().get(guardName) != null) {
                return;
            }

            io.netty.channel.ChannelPipeline pipeline = ctx.pipeline();
            Map<String, io.netty.channel.ChannelHandler> handlers = pipeline.toMap();

            String insertAfter = null;
            for (Map.Entry<String, io.netty.channel.ChannelHandler> entry : handlers.entrySet()) {
                String className = entry.getValue().getClass().getName();
                if (className.contains("craftengine") && className.contains("Encoder")) {
                    insertAfter = entry.getKey();
                    break;
                }
            }

            if (insertAfter == null) {
                for (Map.Entry<String, io.netty.channel.ChannelHandler> entry : handlers.entrySet()) {
                    String className = entry.getValue().getClass().getName();
                    if (className.contains("craftengine")) {
                        insertAfter = entry.getKey();
                    }
                }
            }

            ChunkBufGuard guard = new ChunkBufGuard();
            if (insertAfter != null) {
                pipeline.addAfter(insertAfter, guardName, guard);
                MinecraftServer.LOGGER.info(
                    "[PacketProtector] ByteBuf guard injected after {}",
                    insertAfter
                );
            } else {
                pipeline.addBefore("prepender", guardName, guard);
                MinecraftServer.LOGGER.info("[PacketProtector] ByteBuf guard injected before prepender");
            }
        } catch (Exception e) {
            MinecraftServer.LOGGER.warn("[PacketProtector] Failed to inject ByteBuf guard", e);
        }
    }

    private class ChunkBufGuard extends ChannelDuplexHandler {

        @Override
        public void exceptionCaught(ChannelHandlerContext guardCtx, Throwable cause) throws Exception {
            if (isChunkRelatedException(cause)) {
                MinecraftServer.LOGGER.warn(
                    "[PacketProtector] Suppressed chunk pipeline error: {}",
                    cause.getClass().getSimpleName() + ": " + cause.getMessage()
                );
                int[] coords = pollRecentChunk();
                if (coords != null) {
                    retryChunk(guardCtx, coords[0], coords[1]);
                }
                return;
            }
            guardCtx.fireExceptionCaught(cause);
        }

        private boolean isChunkRelatedException(Throwable cause) {
            if (cause instanceof IndexOutOfBoundsException) {
                return true;
            }
            if (cause.getStackTrace().length > 0) {
                String cls = cause.getStackTrace()[0].getClassName();
                if (cls.contains("craftengine") || cls.contains("MCSection")
                    || cls.contains("chunk.packet") || cls.contains("FriendlyByteBuf")) {
                    return true;
                }
            }
            String msg = cause.getMessage();
            return msg != null && msg.contains("LevelChunkWithLight");
        }

        private void retryChunk(ChannelHandlerContext guardCtx, int cx, int cz) {
            ServerPlayer player = resolvePlayer(guardCtx);
            if (player == null || !player.isAlive()) {
                return;
            }

            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    return;
                }

                if (!player.isAlive() || !guardCtx.channel().isActive()) {
                    return;
                }

                ServerLevel level = player.level();
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return;
                }

                MinecraftServer.LOGGER.info(
                    "[PacketProtector] Retrying chunk [{}, {}] for {}",
                    cx, cz, player.getScoreboardName()
                );

                guardCtx.channel().eventLoop().execute(() -> {
                    if (!guardCtx.channel().isActive()) {
                        return;
                    }
                    try {
                        boolean shouldModify = level.chunkPacketBlockController.shouldModify(player, chunk);
                        ClientboundLevelChunkWithLightPacket safePacket = new ClientboundLevelChunkWithLightPacket(
                            chunk, level.getLightEngine(), null, null, shouldModify
                        );
                        Connection conn = (Connection) guardCtx.pipeline().get("packet_handler");
                        if (conn != null) {
                            conn.send(safePacket);
                        }
                    } catch (Exception e) {
                        MinecraftServer.LOGGER.error(
                            "[PacketProtector] Retry failed for [{}, {}]", cx, cz, e
                        );
                    }
                });
            });
        }

        private ServerPlayer resolvePlayer(ChannelHandlerContext ctx) {
            try {
                Connection connection = (Connection) ctx.pipeline().get("packet_handler");
                if (connection != null) {
                    return connection.getPlayer();
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}
