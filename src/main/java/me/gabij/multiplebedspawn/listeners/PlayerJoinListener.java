package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.utils.BedOwnershipStore.PendingBedUpdates;
import me.gabij.multiplebedspawn.utils.PluginKeys;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
        if (playerData.has(PluginKeys.spawnLoc(), PersistentDataType.STRING)) {
            Location location = stringToLocation(playerData.get(PluginKeys.spawnLoc(), PersistentDataType.STRING));
            playerData.remove(PluginKeys.spawnLoc());
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
        if (bedData.hasCustomName()) {
            return ChatColor.RED + plugin.message(
                    bedData.isRespawnAnchor() ? "anchor-destroyed-message" : "bed-destroyed-message",
                    bedData.isRespawnAnchor()
                            ? "Your saved respawn anchor {1} was destroyed and removed."
                            : "Your saved bed {1} was destroyed and removed.")
                    .replace("{1}", bedData.getBedName());
        }

        return ChatColor.RED + plugin.message(
                bedData.isRespawnAnchor() ? "anchor-destroyed-message-location" : "bed-destroyed-message-location",
                bedData.isRespawnAnchor()
                        ? "Your saved respawn anchor at {1} in {2} was destroyed and removed."
                        : "Your saved bed at {1} in {2} was destroyed and removed.")
                .replace("{1}", bedData.formatCoords())
                .replace("{2}", bedData.getBedWorld());
    }
}
