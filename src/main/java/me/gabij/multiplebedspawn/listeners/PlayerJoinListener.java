package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
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

        plugin.getPlayerBedStore().importLegacyBeds(p);
        for (String message : plugin.getPlayerBedStore().consumePendingMessages(p.getUniqueId())) {
            p.sendMessage(message);
        }
    }
}
