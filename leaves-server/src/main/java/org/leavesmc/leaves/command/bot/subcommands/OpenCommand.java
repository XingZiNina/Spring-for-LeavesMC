package org.leavesmc.leaves.command.bot.subcommands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.bot.BotSubcommand;
import org.leavesmc.leaves.command.arguments.BotArgumentType;

public class OpenCommand extends BotSubcommand {

    public OpenCommand() {
        super("open");
        children(BotArgument::new);
    }

    private static class BotArgument extends org.leavesmc.leaves.command.ArgumentNode<ServerBot> {
        private BotArgument() {
            super("bot", BotArgumentType.bot());
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
                return false;
            }

            ServerBot bot = context.getArgument(BotArgument.class);
            if (bot == null) {
                sender.sendMessage(Component.text("Fakeplayer not found", NamedTextColor.RED));
                return false;
            }

            bot.openInventory(player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer ? craftPlayer.getHandle() : null);
            return true;
        }
    }
}
