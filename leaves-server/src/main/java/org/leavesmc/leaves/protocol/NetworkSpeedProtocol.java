package org.leavesmc.leaves.protocol;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.leavesmc.leaves.LeavesConfig;
import org.leavesmc.leaves.network.NetworkSpeedMonitor;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;

@LeavesProtocol.Register(namespace = "network_speed")
public class NetworkSpeedProtocol implements LeavesProtocol {

    @ProtocolHandler.Ticker
    public static void tick() {
        NetworkSpeedMonitor.tick();
        if (!LeavesConfig.mics.networkSpeed || !NetworkSpeedMonitor.visible()) {
            return;
        }
        for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            player.getBukkitEntity().sendActionBar(NetworkSpeedMonitor.gradientDisplayText());
        }
    }

    @Override
    public int tickerInterval(String tickerID) {
        return 20;
    }

    @Override
    public boolean isActive() {
        return LeavesConfig.mics.networkSpeed;
    }
}
