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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static me.happy.rustbeds.utils.BedsUtils.checksIfBedExists;

public class PlayerUtils {

    private static final int RESPAWN_MENU_DARKNESS_TICKS = 20 * 60 * 60;

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
            applyRespawnMenuDarkness(p);
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
            clearRespawnMenuDarkness(p);

            if (plugin.getConfig().getBoolean("spawn-on-sky")) {
                p.setAllowFlight(playerData.get(PluginKeys.allowFly(), PersistentDataType.BOOLEAN));
                p.setFlying(false);

                playerData.remove(PluginKeys.allowFly());
            }


            p.closeInventory();
        }

    }

    private static void applyRespawnMenuDarkness(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, RESPAWN_MENU_DARKNESS_TICKS, 1,
                false, false, false), true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, RESPAWN_MENU_DARKNESS_TICKS, 1,
                false, false, false), true);
    }

    private static void clearRespawnMenuDarkness(Player p) {
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
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
        PlayerBedsData playerBedsData = loadPlayerBedsData(p);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return 0;
        }

        World world = getPlayerRespawnLoc(p).getWorld();
        String worldName = world == null ? null : world.getName();
        boolean linkWorlds = plugin.getConfig().getBoolean("link-worlds");
        int playerBedsCount = 0;
        for (Map.Entry<String, BedData> entry : playerBedsData.getPlayerBedData().entrySet()) {
            BedData bedData = entry.getValue();
            if (bedData == null || (!linkWorlds && !bedData.getBedWorld().equalsIgnoreCase(worldName))) {
                continue;
            }

            if (checksIfBedExists(bedData.getBedLocation(), p, entry.getKey())) {
                playerBedsCount++;
            }
        }
        return playerBedsCount;
    }

    public static String getOfflinePlayerName(OfflinePlayer player) {
        if (player == null) {
            return "Unknown player";
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer != null && onlinePlayer.getName() != null && !onlinePlayer.getName().isBlank()) {
            return onlinePlayer.getName();
        }

        String playerName = player.getName();
        if (playerName != null && !playerName.isBlank()) {
            return playerName;
        }

        return player.getUniqueId().toString();
    }

    public static Comparator<OfflinePlayer> offlinePlayerListComparator() {
        return Comparator.comparing((OfflinePlayer player) -> !isOnlinePlayer(player))
                .thenComparing(PlayerUtils::getOfflinePlayerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(player -> player == null ? "" : player.getUniqueId().toString());
    }

    public static boolean isOnlinePlayer(OfflinePlayer player) {
        return player != null && player.isOnline();
    }

}
