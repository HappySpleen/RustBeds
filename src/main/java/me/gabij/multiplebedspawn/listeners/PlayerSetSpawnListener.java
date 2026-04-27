package me.gabij.multiplebedspawn.listeners;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerSetSpawnListener implements Listener {

    private final MultipleBedSpawn plugin;

    public PlayerSetSpawnListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getCause() != PlayerSetSpawnEvent.Cause.BED
                || event.getLocation() == null
                || event.getLocation().getWorld() == null) {
            return;
        }

        if (!plugin.isWorldEnabled(event.getLocation().getWorld().getName())) {
            return;
        }

        // This plugin manages bed respawns itself, so skip the vanilla spawn update and chat message.
        event.setNotifyPlayer(false);
        event.setCancelled(true);
    }
}
