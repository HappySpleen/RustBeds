package me.gabij.multiplebedspawn.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.gabij.multiplebedspawn.listeners.AdminBedsMenuHandler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class AdminBedsCommand implements BasicCommand {
    public static final String LABEL = "adminbeds";
    public static final String DESCRIPTION = "Opens the admin beds menu";

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        if (!player.hasPermission("multiplebedspawn.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        AdminBedsMenuHandler.openOwnerMenu(player, 0);
    }
}
