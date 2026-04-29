package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.utils.PluginKeys;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static me.happy.rustbeds.listeners.RespawnMenuHandler.beginRespawnMenu;
import static me.happy.rustbeds.utils.PlayerUtils.locationToString;

public class PlayerRespawnListener implements Listener {
    static RustBeds plugin;

    public PlayerRespawnListener(RustBeds plugin) {
        PlayerRespawnListener.plugin = plugin;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        World world = e.getRespawnLocation().getWorld();
        if (world.getEnvironment() == Environment.THE_END) {
            return;
        }
        if (world.getEnvironment() == Environment.NETHER
                && !plugin.getConfig().getBoolean("respawn-anchors-enabled")) {
            return;
        }
        if (e.getRespawnReason() == RespawnReason.PLUGIN) {
            return;
        }

        if (!plugin.isWorldEnabled(world.getName())) {
            return;
        }

        Player player = e.getPlayer();
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        Location defaultRespawn = e.getRespawnLocation().clone();
        playerData.set(PluginKeys.spawnLoc(), PersistentDataType.STRING, locationToString(defaultRespawn));

        if (plugin.getConfig().getBoolean("spawn-on-sky") && world.getEnvironment() == Environment.NORMAL) {
            Location skyRespawn = defaultRespawn.clone();
            skyRespawn.setY(Math.min(world.getMaxHeight() - 1, defaultRespawn.getY() + 300));
            e.setRespawnLocation(skyRespawn);
        }

        beginRespawnMenu(player, plugin.getConfig().getLong("respawn-menu-open-delay-ticks"));
    }
}
