package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.models.PlayerBedsData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static me.happy.rustbeds.utils.BedsUtils.checksIfBedExists;

public class PlayerUtils {

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
        if (!playerData.has(PluginKeys.hasProp(), PersistentDataType.BOOLEAN)) {
            p.setInvulnerable(true);

            playerData.set(PluginKeys.isInvisible(), PersistentDataType.BOOLEAN, p.isInvisible());
            p.setInvisible(true);

            playerData.set(PluginKeys.canPickupItems(), PersistentDataType.BOOLEAN, p.getCanPickupItems());
            p.setCanPickupItems(false);

            if (plugin.getConfig().getBoolean("spawn-on-sky")) {
                playerData.set(PluginKeys.allowFly(), PersistentDataType.BOOLEAN, p.getAllowFlight());
                p.setAllowFlight(true);
                p.setFlying(true);
            }
            playerData.set(PluginKeys.lastWalkSpeed(), PersistentDataType.FLOAT, p.getWalkSpeed());
            p.setWalkSpeed(0);

            playerData.set(PluginKeys.hasProp(), PersistentDataType.BOOLEAN, true);
        }
    }

    public static void undoPropPlayer(Player p) {

        PersistentDataContainer playerData = p.getPersistentDataContainer();
        if (playerData.has(PluginKeys.hasProp(), PersistentDataType.BOOLEAN)) {
            playerData.remove(PluginKeys.hasProp());

            p.setInvulnerable(false);
            p.setInvisible(playerData.get(PluginKeys.isInvisible(), PersistentDataType.BOOLEAN));
            p.setCanPickupItems(playerData.get(PluginKeys.canPickupItems(), PersistentDataType.BOOLEAN));

            playerData.remove(PluginKeys.isInvisible());
            playerData.remove(PluginKeys.canPickupItems());

            p.setWalkSpeed(playerData.get(PluginKeys.lastWalkSpeed(), PersistentDataType.FLOAT));
            playerData.remove(PluginKeys.lastWalkSpeed());

            if (plugin.getConfig().getBoolean("spawn-on-sky")) {
                p.setAllowFlight(playerData.get(PluginKeys.allowFly(), PersistentDataType.BOOLEAN));
                p.setFlying(false);

                playerData.remove(PluginKeys.allowFly());
            }


            p.closeInventory();
        }

    }

    public static Location getPlayerRespawnLoc(Player p) {
        Location loc = p.getLocation();
        PersistentDataContainer playerData = p.getPersistentDataContainer();
        if (playerData.has(PluginKeys.spawnLoc(), PersistentDataType.STRING)) {
            Location playerRespawnLocation = stringToLocation(
                    playerData.get(PluginKeys.spawnLoc(), PersistentDataType.STRING));
            if (playerRespawnLocation != null) {
                loc = playerRespawnLocation;
            }
        }
        return loc;
    }

    public static PlayerBedsData loadPlayerBedsData(Player p) {
        return loadPlayerBedsData((OfflinePlayer) p);
    }

    public static PlayerBedsData loadPlayerBedsData(OfflinePlayer player) {
        if (player == null) {
            return null;
        }

        plugin.getPlayerBedStore().importLegacyBeds(player);
        return loadPlayerBedsData(player.getUniqueId());
    }

    public static PlayerBedsData loadPlayerBedsData(UUID playerId) {
        if (playerId == null) {
            return null;
        }

        PlayerBedsData playerBedsData = plugin.getPlayerBedStore().loadPlayerBeds(playerId);
        if (playerBedsData != null && playerBedsData.normalizePrimarySelection()) {
            plugin.getPlayerBedStore().savePlayerBeds(playerId, playerBedsData);
        }
        return playerBedsData;
    }

    public static void savePlayerBedsData(Player p, PlayerBedsData playerBedsData) {
        savePlayerBedsData(p.getUniqueId(), playerBedsData);
    }

    public static void savePlayerBedsData(OfflinePlayer player, PlayerBedsData playerBedsData) {
        if (player == null) {
            return;
        }

        savePlayerBedsData(player.getUniqueId(), playerBedsData);
    }

    public static void savePlayerBedsData(UUID playerId, PlayerBedsData playerBedsData) {
        if (playerId == null) {
            return;
        }

        plugin.getPlayerBedStore().savePlayerBeds(playerId, playerBedsData);
    }

    public static Integer getPlayerBedsCount(Player p) {
        AtomicInteger playerBedsCount = new AtomicInteger();
        playerBedsCount.set(0);
        PlayerBedsData playerBedsData = loadPlayerBedsData(p);
        if (playerBedsData != null && playerBedsData.getPlayerBedData() != null) {
            HashMap<String, BedData> beds = playerBedsData.getPlayerBedData();
            World world = getPlayerRespawnLoc(p).getWorld();
            String worldName = world.getName();
            if (!plugin.getConfig().getBoolean("link-worlds")) {
                HashMap<String, BedData> bedsT = (HashMap<String, BedData>) beds.clone();
                beds.forEach((uuid, bedData) -> {
                    // clear lists so beds are only from the world that player will respawn
                    if (!bedData.getBedWorld().equalsIgnoreCase(worldName)) {
                        bedsT.remove(uuid);
                    }
                });
                beds = bedsT;
            }
            playerBedsCount.set(beds.size());
            beds.forEach((uuid, bedData) -> {
                String[] location = bedData.getBedCoords().split(":");
                String bedWorld = bedData.getBedWorld();
                Location bedLoc = new Location(Bukkit.getWorld(bedWorld), Double.parseDouble(location[0]),
                        Double.parseDouble(location[1]), Double.parseDouble(location[2]));
                if (!checksIfBedExists(bedLoc, p, uuid)) {
                    playerBedsCount.addAndGet(-1);
                }
            });
        }
        return playerBedsCount.get();
    }

}
