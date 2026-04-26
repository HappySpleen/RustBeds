package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.storage.StoredBed;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

import static me.happy.rustbeds.listeners.RespawnMenuHandler.openRespawnMenu;
import static me.happy.rustbeds.utils.KeyUtils.key;
import static me.happy.rustbeds.utils.BedsUtils.checksIfBedExists;
import static me.happy.rustbeds.utils.PlayerUtils.locationToString;
import static me.happy.rustbeds.utils.PlayerUtils.ensureLegacyPlayerData;
import static me.happy.rustbeds.utils.PlayerUtils.getPlayerBeds;

public class PlayerRespawnListener implements Listener {
    static RustBeds plugin;

    public PlayerRespawnListener(RustBeds plugin) {
        PlayerRespawnListener.plugin = plugin;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {

        World world = e.getRespawnLocation().getWorld();
        if (world.getEnvironment() == Environment.NETHER || world.getEnvironment() == Environment.THE_END)
            return;
        if (e.getRespawnReason() == RespawnReason.PLUGIN)
            return;
        String worldName = world.getName();
        List<String> denylist = plugin.getConfig().getStringList("denylist");
        List<String> allowlist = plugin.getConfig().getStringList("allowlist");
        boolean passLists = (!denylist.contains(worldName)) && (allowlist.contains(worldName) || allowlist.isEmpty());

        if (passLists) {
            Player p = e.getPlayer();
            ensureLegacyPlayerData(p);
            List<StoredBed> beds = getPlayerBeds(p);
            for (StoredBed bed : beds) {
                checksIfBedExists(bed.getBedLocation(), p, bed.getBedId());
            }

            Location loc = e.getRespawnLocation();
            if (plugin.getConfig().getBoolean("spawn-on-sky")) {
                p.getPersistentDataContainer().set(key("spawnLoc"), PersistentDataType.STRING, locationToString(loc));
                loc.setY(loc.getY() + 300);
                e.setRespawnLocation(loc);
            }
            openRespawnMenu(p);
        }
    }
}
