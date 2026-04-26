package me.happy.rustbeds.commands;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.storage.StoredBed;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Collectors;

import static me.happy.rustbeds.utils.PlayerUtils.ensureLegacyPlayerData;

public class AdminBedsCommand extends BukkitCommand {
    private static final String ADMIN_PERMISSION = "rustbeds.admin";
    private static final String LEGACY_ADMIN_PERMISSION = "multiplebedspawn.admin";

    private final RustBeds plugin;

    public AdminBedsCommand(RustBeds plugin, String name) {
        super(name);
        this.plugin = plugin;
        this.description = "Admin tools for viewing and editing stored beds";
        this.usageMessage = "/bedsadmin <list|rename|remove|tp> ...";
        this.setAliases(new ArrayList<>(List.of("rustbedsadmin")));
    }

    @Override
    public boolean execute(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION) && !sender.hasPermission(LEGACY_ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage beds.");
            return true;
        }

        if (args.length == 0) {
            return false;
        }

        try {
            return switch (args[0].toLowerCase()) {
                case "list" -> handleList(sender, args);
                case "rename" -> handleRename(sender, args);
                case "remove" -> handleRemove(sender, args);
                case "tp", "teleport" -> handleTeleport(sender, args);
                default -> false;
            };
        } catch (SQLException exception) {
            plugin.getLogger().warning("Admin command failed: " + exception.getMessage());
            sender.sendMessage(ChatColor.RED + "The bed store is currently unavailable.");
            return true;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (!sender.hasPermission(ADMIN_PERMISSION) && !sender.hasPermission(LEGACY_ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }

        try {
            if (args.length == 1) {
                return filterSuggestions(List.of("list", "rename", "remove", "tp", "teleport"), args[0]);
            }

            String subCommand = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2) {
                if (subCommand.equals("list") || subCommand.equals("rename")
                        || subCommand.equals("remove") || subCommand.equals("tp")
                        || subCommand.equals("teleport")) {
                    return filterSuggestions(getKnownPlayerNames(), args[1]);
                }
            }

            if (args.length == 3 && (subCommand.equals("rename") || subCommand.equals("remove"))) {
                return filterSuggestions(getPlayerBedIds(args[1]), args[2]);
            }

            if (args.length >= 3 && (subCommand.equals("tp") || subCommand.equals("teleport"))) {
                String selectorInput = String.join(" ", List.of(args).subList(2, args.length));
                return filterSuggestions(getPlayerBedSelectors(args[1]), selectorInput);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Admin tab completion failed: " + exception.getMessage());
        }

        return Collections.emptyList();
    }

    private boolean handleList(CommandSender sender, String[] args) throws SQLException {
        if (args.length != 2) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.isOnline() && target.getPlayer() != null) {
            ensureLegacyPlayerData(target.getPlayer());
        }

        List<StoredBed> beds = plugin.getBedStorage().getPlayerBeds(target.getUniqueId(), null, true);
        if (beds.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No stored beds found for " + target.getName() + ".");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "Beds for " + target.getName() + ":");
        for (StoredBed bed : beds) {
            String name = bed.getCustomName() == null || bed.getCustomName().isBlank()
                    ? plugin.getMessages("broken-bed-default-name")
                    : bed.getCustomName();
            String cooldown = bed.hasCooldown()
                    ? " cooldown=" + Math.max(0, (bed.getCooldownUntil() - System.currentTimeMillis()) / 1000) + "s"
                    : "";
            sender.sendMessage(ChatColor.GRAY + "- " + bed.getBedId()
                    + ChatColor.WHITE + " | " + name
                    + ChatColor.DARK_PURPLE + " | " + bed.getWorldName()
                    + ChatColor.GRAY + " | " + bed.locationText()
                    + ChatColor.GOLD + cooldown);
        }
        return true;
    }

    private boolean handleRename(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 4) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.isOnline() && target.getPlayer() != null) {
            ensureLegacyPlayerData(target.getPlayer());
        }

        String bedId = args[2];
        if (!plugin.getBedStorage().playerOwnsBed(target.getUniqueId(), bedId)) {
            sender.sendMessage(ChatColor.RED + "That player does not own bed " + bedId + ".");
            return true;
        }

        String newName = String.join(" ", List.of(args).subList(3, args.length)).trim();
        plugin.getBedStorage().renameBed(bedId, newName);
        sender.sendMessage(ChatColor.GREEN + "Renamed bed " + bedId + " for " + target.getName() + ".");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) throws SQLException {
        if (args.length != 3) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.isOnline() && target.getPlayer() != null) {
            ensureLegacyPlayerData(target.getPlayer());
        }

        String bedId = args[2];
        if (plugin.getBedStorage().removeOwnership(bedId, target.getUniqueId())) {
            sender.sendMessage(ChatColor.GREEN + "Removed bed " + bedId + " from " + target.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.RED + "That player does not own bed " + bedId + ".");
        }
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args) throws SQLException {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use teleport.");
            return true;
        }
        if (args.length < 3) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.isOnline() && target.getPlayer() != null) {
            ensureLegacyPlayerData(target.getPlayer());
        }

        String selector = String.join(" ", List.of(args).subList(2, args.length)).trim();
        Optional<StoredBed> bed = resolvePlayerBed(target, selector);
        if (bed.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Could not find a matching bed for " + target.getName() + ".");
            return true;
        }

        Location location = bed.get().getBedLocation();
        if (location == null) {
            sender.sendMessage(ChatColor.RED + "The bed's world is not currently loaded.");
            return true;
        }

        player.teleport(location.add(0.5, 1.0, 0.5));
        sender.sendMessage(ChatColor.GREEN + "Teleported to bed " + bed.get().getBedId() + ".");
        return true;
    }

    private List<String> getKnownPlayerNames() {
        List<String> playerNames = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerNames.add(player.getName());
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if ((player.hasPlayedBefore() || player.isOnline()) && player.getName() != null) {
                playerNames.add(player.getName());
            }
        }
        return playerNames.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
    }

    private List<String> getPlayerBedIds(String playerName) throws SQLException {
        return getStoredBedsForPlayer(playerName).stream()
                .map(StoredBed::getBedId)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> getPlayerBedSelectors(String playerName) throws SQLException {
        List<StoredBed> beds = getStoredBedsForPlayer(playerName);
        return beds.stream()
                .map(bed -> getPreferredBedSelector(bed, beds))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> filterSuggestions(List<String> suggestions, String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }

    private List<StoredBed> getStoredBedsForPlayer(String playerName) throws SQLException {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.isOnline() && target.getPlayer() != null) {
            ensureLegacyPlayerData(target.getPlayer());
        }
        return plugin.getBedStorage().getPlayerBeds(target.getUniqueId(), null, true);
    }

    private Optional<StoredBed> resolvePlayerBed(OfflinePlayer target, String selector) throws SQLException {
        if (selector == null || selector.isBlank()) {
            return Optional.empty();
        }

        List<StoredBed> beds = plugin.getBedStorage().getPlayerBeds(target.getUniqueId(), null, true);

        Optional<StoredBed> byUuid = beds.stream()
                .filter(bed -> bed.getBedId().equalsIgnoreCase(selector))
                .findFirst();
        if (byUuid.isPresent()) {
            return byUuid;
        }

        List<StoredBed> byPreferredName = beds.stream()
                .filter(bed -> getPreferredBedSelector(bed, beds).equalsIgnoreCase(selector))
                .toList();
        if (byPreferredName.size() == 1) {
            return Optional.of(byPreferredName.get(0));
        }

        List<StoredBed> byExactName = beds.stream()
                .filter(bed -> bed.getCustomName() != null && bed.getCustomName().equalsIgnoreCase(selector))
                .toList();
        if (byExactName.size() == 1) {
            return Optional.of(byExactName.get(0));
        }

        return Optional.empty();
    }

    private String getPreferredBedSelector(StoredBed bed, List<StoredBed> playerBeds) {
        String customName = bed.getCustomName();
        if (customName == null || customName.isBlank()) {
            return bed.getBedId();
        }

        long duplicates = playerBeds.stream()
                .filter(other -> other.getCustomName() != null)
                .filter(other -> other.getCustomName().equalsIgnoreCase(customName))
                .count();

        return duplicates == 1 ? customName : bed.getBedId();
    }
}
