package me.happy.rustbeds.listeners;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.models.PlayerBedsData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSpawnChangeEvent;

import java.util.UUID;

import static me.happy.rustbeds.utils.BedsUtils.getMaxNumberOfBeds;
import static me.happy.rustbeds.utils.BedsUtils.getOrCreateRespawnPointUuid;
import static me.happy.rustbeds.utils.PlayerUtils.getPlayerBedsCount;
import static me.happy.rustbeds.utils.PlayerUtils.loadPlayerBedsData;
import static me.happy.rustbeds.utils.PlayerUtils.savePlayerBedsData;

public class PlayerSetSpawnListener implements Listener {

    private final RustBeds plugin;

    public PlayerSetSpawnListener(RustBeds plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSpawnChange(PlayerSpawnChangeEvent event) {
        // Paper calls this event before PlayerSetSpawnEvent.
        if (event.getCause() == PlayerSpawnChangeEvent.Cause.BED
                && plugin.isWorldEnabled(event.getPlayer().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSetBedSpawn(PlayerSetSpawnEvent event) {
        if (event.getCause() == PlayerSetSpawnEvent.Cause.BED
                && plugin.isWorldEnabled(event.getPlayer().getWorld().getName())) {
            event.setNotifyPlayer(false);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSetAnchorSpawn(PlayerSetSpawnEvent event) {
        if (event.getCause() != PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR
                || !plugin.getConfig().getBoolean("respawn-anchors-enabled")) {
            return;
        }

        Location location = event.getLocation();
        if (location == null || location.getWorld() == null
                || !plugin.isWorldEnabled(location.getWorld().getName())) {
            return;
        }

        event.setNotifyPlayer(false);
        event.setCancelled(true);
        registerRespawnAnchor(event.getPlayer(), location);
    }

    private void registerRespawnAnchor(Player player, Location respawnLocation) {
        Location anchorLocation = RespawnAnchorListener.consumePendingAnchor(player, respawnLocation);
        if (anchorLocation == null || anchorLocation.getWorld() == null) {
            return;
        }

        Block anchor = anchorLocation.getWorld().getBlockAt(anchorLocation);
        String savedUuid = getOrCreateRespawnPointUuid(anchor, UUID.randomUUID().toString());
        if (savedUuid == null) {
            return;
        }

        PlayerBedsData playerBedsData = loadPlayerBedsData(player);
        boolean alreadyRegisteredByPlayer = playerBedsData != null && playerBedsData.hasBed(savedUuid);
        if (!alreadyRegisteredByPlayer && getPlayerBedsCount(player) >= getMaxNumberOfBeds(player)) {
            player.sendMessage(ChatColor.RED + plugin.message("max-beds-message",
                    "You have reached the maximum amount of saved beds."));
            return;
        }

        if (!alreadyRegisteredByPlayer
                && plugin.getConfig().getBoolean("exclusive-bed")
                && plugin.getPlayerBedStore().hasOwnerOtherThan(savedUuid, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + plugin.message("anchor-already-has-owner",
                    "This respawn anchor already belongs to another player."));
            return;
        }

        if (alreadyRegisteredByPlayer) {
            return;
        }

        if (playerBedsData == null) {
            playerBedsData = new PlayerBedsData();
        }

        playerBedsData.setNewRespawnPoint(player, anchor, savedUuid, me.happy.rustbeds.models.BedData.RespawnPointType.ANCHOR);
        savePlayerBedsData(player, playerBedsData);
        player.sendMessage(ChatColor.YELLOW + plugin.registrationSuccessMessage(BedData.RespawnPointType.ANCHOR));
    }
}
