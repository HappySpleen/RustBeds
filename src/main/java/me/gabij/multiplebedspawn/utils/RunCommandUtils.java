package me.gabij.multiplebedspawn.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.gabij.multiplebedspawn.MultipleBedSpawn;

public class RunCommandUtils {
    static MultipleBedSpawn plugin = MultipleBedSpawn.getInstance();

    public static void runCommandOnSpawn(Player p) {
        String commandString = plugin.getConfig().getString("command-on-spawn");
        if (commandString == null || commandString.isBlank()) {
            return;
        }

        try {
            CommandSender commandSender;
            if (plugin.getConfig().getBoolean("run-command-as-player")) {
                commandSender = p;
            } else {
                commandSender = Bukkit.getServer().getConsoleSender();
            }
            Bukkit.dispatchCommand(commandSender, commandString);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not run command-on-spawn: " + e.getMessage());
        }
    }
}
