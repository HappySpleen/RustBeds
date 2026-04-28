package me.gabij.multiplebedspawn.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.listeners.AdminBedsMenuHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static me.gabij.multiplebedspawn.listeners.RespawnMenuHandler.openCommandMenu;

public class RespawnMenuCommand implements BasicCommand {
    public static final String LABEL = "beds";
    public static final String DESCRIPTION = "Opens the beds menu and plugin subcommands";

    private final MultipleBedSpawn plugin;

    public RespawnMenuCommand(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        CommandSender sender = commandSourceStack.getSender();
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openCommandMenu(player);
                return;
            }

            sender.sendMessage(ChatColor.RED + plugin.message("beds-command-usage",
                    "Usage: /beds, /beds admin, or /beds reload"));
            return;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "admin" -> handleAdminCommand(sender);
            case "reload" -> handleReloadCommand(sender);
            default -> sender.sendMessage(ChatColor.RED + plugin.message("beds-command-usage",
                    "Usage: /beds, /beds admin, or /beds reload"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (args.length > 1) {
            return List.of();
        }

        CommandSender sender = commandSourceStack.getSender();
        List<String> suggestions = new java.util.ArrayList<>();
        if (sender.hasPermission("multiplebedspawn.admin")) {
            suggestions.add("admin");
            suggestions.add("reload");
        }

        String input = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(option -> option.startsWith(input))
                .collect(Collectors.toList());
    }

    private void handleAdminCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + plugin.message("beds-player-only", "Only players can use this command."));
            return;
        }

        if (!player.hasPermission("multiplebedspawn.admin")) {
            player.sendMessage(ChatColor.RED + plugin.message("admin-beds-no-permission",
                    "You do not have permission to use this command."));
            return;
        }

        AdminBedsMenuHandler.openOwnerMenu(player, 0);
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("multiplebedspawn.admin")) {
            sender.sendMessage(ChatColor.RED + plugin.message("beds-reload-no-permission",
                    "You do not have permission to reload this plugin."));
            return;
        }

        plugin.reloadPluginSettings();
        sender.sendMessage(ChatColor.YELLOW + plugin.message("beds-reload-success",
                "MultipleBedSpawn settings reloaded from config.yml."));
    }
}
