package me.happy.rustbeds.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.happy.rustbeds.RustBeds;

public class RunCommandUtils {
    static RustBeds plugin = RustBeds.getInstance();

    public static void runCommandOnSpawn(Player p) {
        String commandString = plugin.getConfig().getString("command-on-spawn");
        if (commandString.length() > 0) {
            try {
                CommandSender commandSender;
                if (plugin.getConfig().getBoolean("run-command-as-player")) {
                    commandSender = p;
                } else {
                    commandSender = Bukkit.getServer().getConsoleSender();
                }
                Bukkit.dispatchCommand(commandSender, commandString);
            } catch (Error e) {
                plugin.getLogger().info(e.getMessage());
            }
        }
    };
}
