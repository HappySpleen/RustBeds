package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.storage.BrokenBedNotification;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.sql.SQLException;
import java.util.List;

public class BedsUtils {
    private static final String MAX_COUNT_PERMISSION_PREFIX = "rustbeds.maxcount.";
    private static final String LEGACY_MAX_COUNT_PERMISSION_PREFIX = "multiplebedspawn.maxcount.";

    static RustBeds plugin = RustBeds.getInstance();

    public static void removePlayerBed(String bedId, Player player) {
        try {
            plugin.getBedStorage().removeOwnership(bedId, player.getUniqueId());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not remove bed " + bedId + ": " + exception.getMessage());
        }
    }

    public static boolean checksIfBedExists(Location locBed, Player player, String bedId) {
        if (locBed == null || locBed.getWorld() == null) {
            return false;
        }

        World world = locBed.getWorld();
        Block bed = world.getBlockAt(locBed);
        bed = checkIfIsBed(bed);

        if (bed == null) {
            handleBrokenBed(world, locBed.getBlockX(), locBed.getBlockY(), locBed.getBlockZ());
            return false;
        }

        try {
            int bedX = bed.getX();
            int bedY = bed.getY();
            int bedZ = bed.getZ();
            return plugin.getBedStorage().findBedByLocation(world, bedX, bedY, bedZ)
                    .map(storedBed -> storedBed.getBedId().equalsIgnoreCase(bedId))
                    .orElse(false);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not verify bed " + bedId + ": " + exception.getMessage());
            return false;
        }
    }

    public static void handleBrokenBed(Block block) {
        Block bed = checkIfIsBed(block);
        if (bed == null) {
            return;
        }
        handleBrokenBed(bed.getWorld(), bed.getX(), bed.getY(), bed.getZ());
    }

    public static void handleBrokenBed(World world, int x, int y, int z) {
        try {
            List<BrokenBedNotification> notifications = plugin.getBedStorage().markBedBroken(world, x, y, z);
            notifyOwners(notifications);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not mark broken bed at " + world.getName() + " "
                    + x + " " + y + " " + z + ": " + exception.getMessage());
        }
    }

    public static void notifyOwners(List<BrokenBedNotification> notifications) {
        for (BrokenBedNotification notification : notifications) {
            Player player = Bukkit.getPlayer(notification.getPlayerUuid());
            if (player != null && player.isOnline()) {
                player.sendMessage(buildBrokenBedMessage(notification));
            }
        }
    }

    public static Block checkIfIsBed(Block block) {
        if (block != null && block.getBlockData() instanceof Bed bedPart) {
            if (bedPart.getPart() == Bed.Part.FOOT) {
                block = block.getRelative(bedPart.getFacing());
            }
            return block;
        }
        return null;
    }

    public static int getMaxNumberOfBeds(Player player) {
        int maxBeds = plugin.getConfig().getInt("max-beds");
        int maxBedsByPerms = 0;
        for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            if (!perm.getValue()) {
                continue;
            }

            Integer max = extractMaxBedsFromPermission(perm.getPermission(), player);
            if (max != null && max > maxBedsByPerms) {
                maxBedsByPerms = max;
            }
        }
        if (maxBeds > 53) {
            plugin.getLogger().warning("Max bed count cant be over 53! Value defaulted to 53.");
            plugin.getConfig().set("max-beds", 53);
            plugin.saveConfig();
            maxBeds = 53;
        }
        if (maxBedsByPerms > 0) {
            maxBeds = maxBedsByPerms;
        }
        return maxBeds;
    }

    private static Integer extractMaxBedsFromPermission(String permissionName, Player player) {
        String maxCount = null;
        if (permissionName.startsWith(MAX_COUNT_PERMISSION_PREFIX)) {
            maxCount = permissionName.substring(MAX_COUNT_PERMISSION_PREFIX.length()).trim();
        } else if (permissionName.startsWith(LEGACY_MAX_COUNT_PERMISSION_PREFIX)) {
            maxCount = permissionName.substring(LEGACY_MAX_COUNT_PERMISSION_PREFIX.length()).trim();
        }

        if (maxCount == null || maxCount.isBlank()) {
            return null;
        }

        try {
            int max = Integer.parseInt(maxCount);
            if (max > 53) {
                plugin.getLogger().warning("Permission " + permissionName
                        + " is invalid! Should be lower than 53. Value defaulted to 53, please remove this permission. Warning triggered by player "
                        + player.getName());
                return 53;
            }
            return max;
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Permission " + permissionName
                    + " is invalid! Should be a number after 'maxcount.'. Warning triggered by player "
                    + player.getName());
            return null;
        }
    }

    public static String buildBrokenBedMessage(BrokenBedNotification notification) {
        String bedName = notification.getBedName();
        if (bedName == null || bedName.isBlank()) {
            bedName = plugin.getMessages("broken-bed-default-name");
        }
        return plugin.getMessages("bed-broken-message")
                .replace("{bed}", bedName)
                .replace("{world}", notification.getWorldName())
                .replace("{x}", String.valueOf(notification.getX()))
                .replace("{y}", String.valueOf(notification.getY()))
                .replace("{z}", String.valueOf(notification.getZ()));
    }
}
