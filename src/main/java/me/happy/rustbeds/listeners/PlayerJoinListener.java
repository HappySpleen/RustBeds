package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.utils.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

import static me.happy.rustbeds.utils.PlayerUtils.stringToLocation;
import static me.happy.rustbeds.utils.PlayerUtils.undoPropPlayer;
import static me.happy.rustbeds.utils.TeleportUtils.teleport;

public class PlayerJoinListener implements Listener {
    RustBeds plugin;

    public PlayerJoinListener(RustBeds plugin) {
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
        List<String> pendingMessages = plugin.getPlayerBedStore().consumePendingMessages(p.getUniqueId());
        if (pendingMessages.isEmpty()) {
            return;
        }

        Runnable sendPendingMessages = () -> {
            if (!p.isOnline()) {
                return;
            }

            for (String message : pendingMessages) {
                p.sendMessage(message);
            }
        };

        long delayTicks = plugin.getOfflineRespawnPointDestroyedMessageDelayTicks();
        if (delayTicks <= 0L) {
            sendPendingMessages.run();
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, sendPendingMessages, delayTicks);
    }
}
