package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static me.happy.rustbeds.utils.KeyUtils.hasAny;
import static me.happy.rustbeds.utils.KeyUtils.getAny;
import static me.happy.rustbeds.utils.KeyUtils.removeAll;
import static me.happy.rustbeds.utils.PlayerUtils.stringToLocation;
import static me.happy.rustbeds.utils.PlayerUtils.undoPropPlayer;
import static me.happy.rustbeds.utils.PlayerUtils.ensureLegacyPlayerData;
import static me.happy.rustbeds.utils.PlayerUtils.deliverBrokenBedNotifications;

public class PlayerJoinListener implements Listener {
    RustBeds plugin;

    public PlayerJoinListener(RustBeds plugin) {
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
