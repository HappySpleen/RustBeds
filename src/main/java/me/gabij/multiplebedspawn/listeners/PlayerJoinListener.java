package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.utils.BedOwnershipStore.PendingBedUpdates;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static me.gabij.multiplebedspawn.utils.BedsUtils.removePlayerBed;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.stringToLocation;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.undoPropPlayer;
import static me.gabij.multiplebedspawn.utils.TeleportUtils.teleport;

public class PlayerJoinListener implements Listener {
    MultipleBedSpawn plugin;

    public PlayerJoinListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PersistentDataContainer playerData = p.getPersistentDataContainer();
        NamespacedKey spawnLocName = new NamespacedKey(plugin, "spawnLoc");
        if (playerData.has(spawnLocName, PersistentDataType.STRING)) {
            Location location = stringToLocation(playerData.get(spawnLocName, PersistentDataType.STRING));
            playerData.remove(spawnLocName);
            if (location != null) {
                teleport(p, location);
            }
        }
        undoPropPlayer(p);

        PendingBedUpdates pendingUpdates = plugin.getBedOwnershipStore().consumePendingUpdates(p.getUniqueId());
        for (String bedUuid : pendingUpdates.bedUuids()) {
            BedData removedBed = removePlayerBed(bedUuid, p, false);
            if (removedBed != null) {
                p.sendMessage(buildDestroyedMessage(removedBed));
            }
        }
        for (String message : pendingUpdates.messages()) {
            p.sendMessage(message);
        }
        plugin.getBedOwnershipStore().syncPlayerBeds(p);
    }

    private String buildDestroyedMessage(BedData bedData) {
        if (bedData.getBedName() != null && !bedData.getBedName().isBlank()) {
            return ChatColor.RED + message("bed-destroyed-message",
                    "Your saved bed {1} was destroyed and removed.").replace("{1}", bedData.getBedName());
        }

        return ChatColor.RED + message("bed-destroyed-message-location",
                "Your saved bed at {1} in {2} was destroyed and removed.")
                .replace("{1}", formatCoords(bedData))
                .replace("{2}", bedData.getBedWorld());
    }

    private String formatCoords(BedData bedData) {
        String[] coords = bedData.getBedCoords().split(":");
        return "X: " + formatCoord(coords[0]) + " Y: " + formatCoord(coords[1]) + " Z: " + formatCoord(coords[2]);
    }

    private String formatCoord(String coordinate) {
        return Integer.toString((int) Math.floor(Double.parseDouble(coordinate)));
    }

    private String message(String key, String fallback) {
        String value = plugin.getMessages(key);
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
