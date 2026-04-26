package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.storage.BrokenBedNotification;
import me.happy.rustbeds.storage.StoredBed;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static me.happy.rustbeds.utils.BedsUtils.checksIfBedExists;

public class PlayerUtils {
    private static final String SKIP_COOLDOWN_PERMISSION = "rustbeds.skipcooldown";
    private static final String LEGACY_SKIP_COOLDOWN_PERMISSION = "multiplebedspawn.skipcooldown";

    static RustBeds plugin = RustBeds.getInstance();

    public static String locationToString(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ();
    }

    public static Location stringToLocation(String locString) {
        String[] loc = locString.split(":");
        return new Location(Bukkit.getWorld(loc[0]), Double.parseDouble(loc[1]), Double.parseDouble(loc[2]),
                Double.parseDouble(loc[3]));
    }

    public static void setPropPlayer(Player p) {

        PersistentDataContainer playerData = p.getPersistentDataContainer();
        if (!KeyUtils.hasAny(playerData, plugin.getName(), "hasProp", PersistentDataType.BOOLEAN)) {
            p.setInvulnerable(true);

            playerData.set(KeyUtils.key("isInvisible"), PersistentDataType.BOOLEAN, p.isInvisible());
            p.setInvisible(true);

            playerData.set(KeyUtils.key("canPickupItems"), PersistentDataType.BOOLEAN,
                    p.getCanPickupItems());
            p.setCanPickupItems(false);

            if (plugin.getConfig().getBoolean("spawn-on-sky")) {
                playerData.set(KeyUtils.key("allowFly"), PersistentDataType.BOOLEAN, p.getAllowFlight());
                p.setAllowFlight(true);
                p.setFlying(true);
            }
            playerData.set(KeyUtils.key("lastWalkspeed"), PersistentDataType.FLOAT, p.getWalkSpeed());
            p.setWalkSpeed(0);

            playerData.set(KeyUtils.key("hasProp"), PersistentDataType.BOOLEAN, true);
        }
    }

    public static void undoPropPlayer(Player p) {

        PersistentDataContainer playerData = p.getPersistentDataContainer();
        if (KeyUtils.hasAny(playerData, plugin.getName(), "hasProp", PersistentDataType.BOOLEAN)) {
            KeyUtils.removeAll(playerData, plugin.getName(), "hasProp");

            p.setInvulnerable(false);
            Boolean wasInvisible = KeyUtils.getAny(playerData, plugin.getName(), "isInvisible", PersistentDataType.BOOLEAN);
            p.setInvisible(Boolean.TRUE.equals(wasInvisible));
            p.setCanPickupItems(
                    Boolean.TRUE.equals(KeyUtils.getAny(playerData, plugin.getName(), "canPickupItems",
                            PersistentDataType.BOOLEAN)));

            KeyUtils.removeAll(playerData, plugin.getName(), "isInvisible");
            KeyUtils.removeAll(playerData, plugin.getName(), "canPickupItems");

            Float walkSpeed = KeyUtils.getAny(playerData, plugin.getName(), "lastWalkspeed", PersistentDataType.FLOAT);
            if (walkSpeed != null) {
                p.setWalkSpeed(walkSpeed);
            }
            KeyUtils.removeAll(playerData, plugin.getName(), "lastWalkspeed");

            if (plugin.getConfig().getBoolean("spawn-on-sky")) {
                Boolean allowFly = KeyUtils.getAny(playerData, plugin.getName(), "allowFly", PersistentDataType.BOOLEAN);
                p.setAllowFlight(Boolean.TRUE.equals(allowFly));
                p.setFlying(false);

                KeyUtils.removeAll(playerData, plugin.getName(), "allowFly");
                KeyUtils.removeAll(playerData, plugin.getName(), "isFlying");
            }


            p.closeInventory();
        }

    }

    public static void teleportPlayer(Player player, StoredBed bed) {
        if (bed == null || bed.hasCooldown()) {
            return;
        }

        Location spawnLocation = bed.getSpawnLocation();
        if (spawnLocation == null) {
            return;
        }

        undoPropPlayer(player);
        if (!hasSkipCooldownPermission(player)) {
            try {
                plugin.getBedStorage().setBedCooldown(
                        bed.getBedId(),
                        System.currentTimeMillis() + (plugin.getConfig().getLong("bed-cooldown") * 1000));
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not update cooldown for bed " + bed.getBedId() + ": "
                        + exception.getMessage());
            }
        }
        KeyUtils.removeAll(player.getPersistentDataContainer(), plugin.getName(), "spawnLoc");
        player.teleport(spawnLocation);
    }

    public static Location getPlayerRespawnLoc(Player p) {
        Location loc = p.getLocation();
        PersistentDataContainer playerData = p.getPersistentDataContainer();
        if (KeyUtils.hasAny(playerData, plugin.getName(), "spawnLoc", PersistentDataType.STRING)) {
            Location playerRespawnLocation = stringToLocation(
                    KeyUtils.getAny(playerData, plugin.getName(), "spawnLoc", PersistentDataType.STRING));
            if (playerRespawnLocation != null) {
                loc = playerRespawnLocation;
            }
        }
        return loc;
    }

    public static Integer getPlayerBedsCount(Player p) {
        return getPlayerBeds(p).size();
    }

    public static List<StoredBed> getPlayerBeds(Player player) {
        try {
            ensureLegacyPlayerData(player);
            World world = getPlayerRespawnLoc(player).getWorld();
            List<StoredBed> beds = new ArrayList<>(plugin.getBedStorage().getPlayerBeds(player.getUniqueId(), world,
                    plugin.getConfig().getBoolean("link-worlds")));
            boolean changed = false;
            for (StoredBed bed : beds) {
                if (!checksIfBedExists(bed.getBedLocation(), player, bed.getBedId())) {
                    changed = true;
                }
            }
            if (changed) {
                beds = new ArrayList<>(plugin.getBedStorage().getPlayerBeds(player.getUniqueId(), world,
                        plugin.getConfig().getBoolean("link-worlds")));
            }
            return beds;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load beds for " + player.getName() + ": " + exception.getMessage());
            return List.of();
        }
    }

    private static boolean hasSkipCooldownPermission(Player player) {
        return player.hasPermission(SKIP_COOLDOWN_PERMISSION)
                || player.hasPermission(LEGACY_SKIP_COOLDOWN_PERMISSION);
    }

    public static void ensureLegacyPlayerData(Player player) {
        try {
            plugin.getBedStorage().importLegacyPlayerData(player);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not import legacy data for " + player.getName() + ": "
                    + exception.getMessage());
        }
    }

    public static void deliverBrokenBedNotifications(Player player) {
        try {
            List<BrokenBedNotification> notifications = plugin.getBedStorage()
                    .consumeBrokenBedNotifications(player.getUniqueId());
            for (BrokenBedNotification notification : notifications) {
                player.sendMessage(BedsUtils.buildBrokenBedMessage(notification));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not deliver broken bed notifications to " + player.getName() + ": "
                    + exception.getMessage());
        }
    }

}
