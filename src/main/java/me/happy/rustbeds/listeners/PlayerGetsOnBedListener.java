package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.storage.RegisterBedResult;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.sql.SQLException;
import java.util.List;

import static me.happy.rustbeds.utils.BedsUtils.checkIfIsBed;
import static me.happy.rustbeds.utils.BedsUtils.getMaxNumberOfBeds;
import static me.happy.rustbeds.utils.PlayerUtils.ensureLegacyPlayerData;

public class PlayerGetsOnBedListener implements Listener {

    RustBeds plugin;

    public PlayerGetsOnBedListener(RustBeds plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerGetOnBed(PlayerBedEnterEvent e) {

        Player player = e.getPlayer();
        String world = player.getWorld().getName();
        List<String> denylist = plugin.getConfig().getStringList("denylist");
        List<String> allowlist = plugin.getConfig().getStringList("allowlist");
        boolean passLists = (!denylist.contains(world)) && (allowlist.contains(world) || allowlist.isEmpty());

        if (passLists) {
            ensureLegacyPlayerData(player);
            Block bed = checkIfIsBed(e.getBed());
            int maxBeds = getMaxNumberOfBeds(player);
            try {
                RegisterBedResult result = plugin.getBedStorage().registerBed(
                        player,
                        bed,
                        plugin.getConfig().getBoolean("exclusive-bed"),
                        maxBeds,
                        plugin.getConfig().getBoolean("link-worlds"));

                if (result == RegisterBedResult.CREATED || result == RegisterBedResult.ADDED_OWNER) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getMessages("bed-registered-successfully-message")));
                } else if (result == RegisterBedResult.ALREADY_REGISTERED) {
                    player.sendMessage(ChatColor.RED + plugin.getMessages("bed-already-registered-message"));
                } else if (result == RegisterBedResult.EXCLUSIVE_CONFLICT) {
                    player.sendMessage(ChatColor.RED + plugin.getMessages("bed-already-has-owner"));
                } else if (result == RegisterBedResult.MAX_BEDS_REACHED) {
                    player.sendMessage(ChatColor.RED + plugin.getMessages("max-beds-message"));
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not register bed for " + player.getName() + ": "
                        + exception.getMessage());
            }

            player.setBedSpawnLocation(null);
            e.setCancelled(plugin.getConfig().getBoolean("disable-sleeping"));
        }
    }

}
