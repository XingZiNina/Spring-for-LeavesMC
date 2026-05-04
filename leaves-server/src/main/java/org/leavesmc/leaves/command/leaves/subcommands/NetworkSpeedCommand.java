package org.leavesmc.leaves.command.leaves.subcommands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.LiteralNode;
import org.leavesmc.leaves.command.leaves.LeavesSubcommand;
import org.leavesmc.leaves.network.NetworkSpeedMonitor;

import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.spaces;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

public class NetworkSpeedCommand extends LeavesSubcommand {

    public NetworkSpeedCommand() {
        super("networkspeed");
        children(
            OpenNode::new,
            CloseNode::new
        );
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        CommandSender sender = context.getSender();
        sender.sendMessage(text("Network speed", AQUA));
        sender.sendMessage(join(spaces(),
            text("Status:", GRAY),
            text(NetworkSpeedMonitor.visible() ? "open" : "close", AQUA)
        ));
        sender.sendMessage(join(spaces(),
            text("Total download:", GRAY),
            text(NetworkSpeedMonitor.formatBytes(NetworkSpeedMonitor.totalDownloadBytes()), AQUA)
        ));
        sender.sendMessage(join(spaces(),
            text("Total upload:", GRAY),
            text(NetworkSpeedMonitor.formatBytes(NetworkSpeedMonitor.totalUploadBytes()), AQUA)
        ));
        sender.sendMessage(join(noSeparators(),
            text("Current download: ", GRAY),
            text(NetworkSpeedMonitor.formatBytes(NetworkSpeedMonitor.currentDownloadBytesPerSecond()), AQUA),
            text("/s", GRAY)
        ));
        sender.sendMessage(join(noSeparators(),
            text("Current upload: ", GRAY),
            text(NetworkSpeedMonitor.formatBytes(NetworkSpeedMonitor.currentUploadBytesPerSecond()), AQUA),
            text("/s", GRAY)
        ));
        sender.sendMessage(NetworkSpeedMonitor.gradientDisplayText());
        return true;
    }

    private static class OpenNode extends LiteralNode {
        private OpenNode() {
            super("open");
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) {
            NetworkSpeedMonitor.setVisible(true);
            context.getSender().sendMessage(text("Network speed is open", AQUA));
            return true;
        }
    }

    private static class CloseNode extends LiteralNode {
        private CloseNode() {
            super("close");
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) {
            NetworkSpeedMonitor.setVisible(false);
            context.getSender().sendMessage(text("Network speed is close", AQUA));
            return true;
        }
    }
}
