package me.happy.rustbeds.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.listeners.AdminBedsMenuHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static me.happy.rustbeds.listeners.RespawnMenuHandler.openPendingRequestsMenu;
import static me.happy.rustbeds.listeners.RespawnMenuHandler.openCommandMenu;

public class RespawnMenuCommand implements BasicCommand {
    public static final String LABEL = "beds";
    public static final String DESCRIPTION = "Opens the beds menu, pending requests, and plugin subcommands";

    private final RustBeds plugin;

    public RespawnMenuCommand(RustBeds plugin) {
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
                    "Usage: /beds, /beds requests, /beds admin, or /beds reload"));
            return;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "admin" -> handleAdminCommand(sender);
            case "pending", "requests" -> handlePendingRequestsCommand(sender);
            case "reload" -> handleReloadCommand(sender);
            default -> sender.sendMessage(ChatColor.RED + plugin.message("beds-command-usage",
                    "Usage: /beds, /beds requests, /beds admin, or /beds reload"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (args.length > 1) {
            return List.of();
        }

        CommandSender sender = commandSourceStack.getSender();
        List<String> suggestions = new java.util.ArrayList<>();
        if (sender instanceof Player) {
            suggestions.add("requests");
        }
        if (plugin.hasAdminPermission(sender)) {
            suggestions.add("admin");
            suggestions.add("reload");
        }

        String input = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(option -> option.startsWith(input))
                .collect(Collectors.toList());
    }

    private void handlePendingRequestsCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + plugin.message("beds-player-only", "Only players can use this command."));
            return;
        }

        openPendingRequestsMenu(player);
    }

    private void handleAdminCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + plugin.message("beds-player-only", "Only players can use this command."));
            return;
        }

        if (!plugin.hasAdminPermission(player)) {
            player.sendMessage(ChatColor.RED + plugin.message("admin-beds-no-permission",
                    "You do not have permission to use this command."));
            return;
        }

        AdminBedsMenuHandler.openOwnerMenu(player, 0);
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!plugin.hasAdminPermission(sender)) {
            sender.sendMessage(ChatColor.RED + plugin.message("beds-reload-no-permission",
                    "You do not have permission to reload this plugin."));
            return;
        }

        plugin.reloadPluginSettings();
        sender.sendMessage(ChatColor.YELLOW + plugin.message("beds-reload-success",
                "RustBeds settings reloaded from config.yml."));
    }
}
