package org.leavesmc.leaves.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import no.jckf.dhsupport.core.DhSupport;
import no.jckf.dhsupport.core.configuration.Configuration;
import no.jckf.dhsupport.core.configuration.DhsConfig;
import no.jckf.dhsupport.core.message.plugin.PluginMessageSender;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.LeavesConfig;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.distanthorizons.LeavesDistantHorizonsScheduler;
import org.leavesmc.leaves.protocol.distanthorizons.LeavesDistantHorizonsWorldInterface;

import java.util.UUID;
import java.util.logging.Logger;

@LeavesProtocol.Register(namespace = DistantHorizonsProtocol.PROTOCOL_ID)
public class DistantHorizonsProtocol implements LeavesProtocol {

    public static final String PROTOCOL_ID = "distant_horizons";
    private static final String MESSAGE_CHANNEL = "msg";
    private static DhSupport dhSupport;

    @Override
    public boolean isActive() {
        return LeavesConfig.protocol.distantHorizonsProtocol;
    }

    @Contract("_ -> new")
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PROTOCOL_ID, path);
    }

    @ProtocolHandler.Init
    public static void init() {
        reload();
    }

    @ProtocolHandler.ReloadServer
    public static void reload() {
        if (dhSupport != null) {
            dhSupport.onDisable();
        }
        dhSupport = null;
        if (!LeavesConfig.protocol.distantHorizonsProtocol) {
            return;
        }
        DhSupport support = new DhSupport(MinecraftServer.getServer().getServerVersion(), MinecraftServer.getServer().getServerVersion());
        support.setLogger(Logger.getLogger("DistantHorizons"));
        support.setDataDirectory("distant-horizons-server");
        support.setPluginMessageSender(new LeavesDistantHorizonsSender());
        loadConfig(support.getConfig());
        support.setScheduler(new LeavesDistantHorizonsScheduler(support));
        dhSupport = support;
        registerWorlds();
        support.onEnable();
    }

    @ProtocolHandler.Ticker
    public static void tick() {
        if (dhSupport == null || !LeavesConfig.protocol.distantHorizonsProtocol) {
            return;
        }
        registerWorlds();
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(@NotNull ServerPlayer player) {
        if (dhSupport == null || !LeavesConfig.protocol.distantHorizonsProtocol) {
            return;
        }
        registerWorld(player.getBukkitEntity().getWorld());
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLeave(@NotNull ServerPlayer player) {
        if (dhSupport != null) {
            dhSupport.clearPlayerConfiguration(player.getUUID());
        }
    }

    @ProtocolHandler.BytebufReceiver(key = MESSAGE_CHANNEL)
    public static void onMessage(@NotNull ServerPlayer player, FriendlyByteBuf buf) {
        if (dhSupport == null || !LeavesConfig.protocol.distantHorizonsProtocol) {
            return;
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        dhSupport.getPluginMessageHandler().onPluginMessageReceived(PROTOCOL_ID + ":" + MESSAGE_CHANNEL, player.getUUID(), data);
    }

    public static DhSupport getDhSupport() {
        return dhSupport;
    }

    private static void registerWorlds() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (org.bukkit.World world : server.server.getWorlds()) {
            registerWorld(world);
        }
    }

    private static void registerWorld(org.bukkit.World world) {
        if (dhSupport == null || world == null || dhSupport.getWorldInterface(world.getUID()) != null) {
            return;
        }
        LeavesDistantHorizonsWorldInterface worldInterface = new LeavesDistantHorizonsWorldInterface(dhSupport, world, dhSupport.getConfig());
        worldInterface.setLogger(dhSupport.getLogger());
        dhSupport.setWorldInterface(world.getUID(), worldInterface);
    }

    private static void loadConfig(Configuration config) {
        config.clear();
        config.set(DhsConfig.CONFIG_VERSION, 1);
        config.set(DhsConfig.DEBUG, LeavesConfig.protocol.distantHorizonsDebug);
        config.set(DhsConfig.DATABASE_PATH, "{datadir}/database.sqlite");
        config.set(DhsConfig.CHECK_FOR_UPDATES, false);
        config.set(DhsConfig.RENDER_DISTANCE, LeavesConfig.protocol.distantHorizonsRenderDistance);
        config.set(DhsConfig.DISTANT_GENERATION_ENABLED, LeavesConfig.protocol.distantHorizonsDistantGeneration);
        config.set(DhsConfig.FULL_DATA_REQUEST_CONCURRENCY_LIMIT, LeavesConfig.protocol.distantHorizonsRequestConcurrencyLimit);
        config.set(DhsConfig.REAL_TIME_UPDATES_ENABLED, LeavesConfig.protocol.distantHorizonsRealTimeUpdates);
        config.set(DhsConfig.REAL_TIME_UPDATE_RADIUS, LeavesConfig.protocol.distantHorizonsRealTimeUpdateRadius);
        config.set(DhsConfig.LOGIN_DATA_SYNC_ENABLED, LeavesConfig.protocol.distantHorizonsLoginDataSync);
        config.set(DhsConfig.LOGIN_DATA_SYNC_RADIUS, LeavesConfig.protocol.distantHorizonsLoginDataSyncRadius);
        config.set(DhsConfig.LOGIN_DATA_SYNC_RC_LIMIT, LeavesConfig.protocol.distantHorizonsLoginDataSyncRequestConcurrencyLimit);
        config.set(DhsConfig.MAX_DATA_TRANSFER_SPEED, LeavesConfig.protocol.distantHorizonsMaxDataTransferSpeed);
        config.set(DhsConfig.SCHEDULER_THREADS, LeavesConfig.protocol.distantHorizonsSchedulerThreads);
        config.set(DhsConfig.SERVER_KEY, "");
        config.set(DhsConfig.LEVEL_KEY_PREFIX, null);
        config.set(DhsConfig.BORDER_CENTER_X, null);
        config.set(DhsConfig.BORDER_CENTER_Z, null);
        config.set(DhsConfig.BORDER_RADIUS, null);
        config.set(DhsConfig.USE_VANILLA_WORLD_BORDER, LeavesConfig.protocol.distantHorizonsUseVanillaWorldBorder);
        config.set(DhsConfig.VANILLA_WORLD_BORDER_EXPANSION, "auto");
        config.set(DhsConfig.LOD_REFRESH_INTERVAL, LeavesConfig.protocol.distantHorizonsLodRefreshInterval);
        config.set(DhsConfig.GENERATE_NEW_CHUNKS, LeavesConfig.protocol.distantHorizonsGenerateNewChunks);
        config.set(DhsConfig.GENERATE_NEW_CHUNKS_WARNING, false);
        config.set(DhsConfig.BUILDER_TYPE, LeavesConfig.protocol.distantHorizonsBuilderType);
        config.set(DhsConfig.TRUST_HEIGHT_MAP, LeavesConfig.protocol.distantHorizonsTrustHeightMap);
        config.set(DhsConfig.BUILDER_RESOLUTION, LeavesConfig.protocol.distantHorizonsBuilderResolution);
        config.set(DhsConfig.SCAN_TO_SEA_LEVEL, LeavesConfig.protocol.distantHorizonsScanToSeaLevel);
        config.set(DhsConfig.FAST_UNDERFILL, LeavesConfig.protocol.distantHorizonsFastUnderfill);
        config.set(DhsConfig.INCLUDE_NON_COLLIDING_TOP_LAYER, LeavesConfig.protocol.distantHorizonsIncludeNonCollidingTopLayer);
        config.set(DhsConfig.PERFORM_UNDERGLOW_HACK, LeavesConfig.protocol.distantHorizonsPerformUnderglowHack);
        config.set(DhsConfig.SAMPLE_BIOMES_3D, LeavesConfig.protocol.distantHorizonsSampleBiomes3d);
        config.set(DhsConfig.UPDATE_EVENTS, false);
        config.set(DhsConfig.ENABLE_DH_COMMAND_HELP_TEXT, false);
        config.set(DhsConfig.DUMMY_CHUNK, java.util.List.of());
        config.set(DhsConfig.AD_MESSAGE, java.util.List.of());
        config.set(DhsConfig.LIGHT_OFFSET_HACK, 0);
    }

    private static final class LeavesDistantHorizonsSender implements PluginMessageSender {
        @Override
        public void sendPluginMessage(UUID recipientUuid, String channel, byte[] message) {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(recipientUuid);
            if (player != null && channel.equals(PROTOCOL_ID + ":" + MESSAGE_CHANNEL)) {
                org.leavesmc.leaves.protocol.core.ProtocolUtils.sendBytebufPacket(player, id(MESSAGE_CHANNEL), buf -> buf.writeBytes(message));
            }
        }
    }
}
