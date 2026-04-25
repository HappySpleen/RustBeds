package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static me.gabij.multiplebedspawn.utils.KeyUtils.hasAny;
import static me.gabij.multiplebedspawn.utils.KeyUtils.getAny;
import static me.gabij.multiplebedspawn.utils.KeyUtils.removeAll;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.stringToLocation;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.undoPropPlayer;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.ensureLegacyPlayerData;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.deliverBrokenBedNotifications;

public class PlayerJoinListener implements Listener {
    MultipleBedSpawn plugin;

    public PlayerJoinListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        ensureLegacyPlayerData(p);
        deliverBrokenBedNotifications(p);
        PersistentDataContainer playerData = p.getPersistentDataContainer();
        if (plugin.getConfig().getBoolean("spawn-on-sky")
                && hasAny(playerData, plugin.getName(), "spawnLoc", PersistentDataType.STRING)) {
            Location location = stringToLocation(getAny(playerData, plugin.getName(), "spawnLoc",
                    PersistentDataType.STRING));
            removeAll(playerData, plugin.getName(), "spawnLoc");
            p.teleport(location);
        }
        undoPropPlayer(p);
    }
}
