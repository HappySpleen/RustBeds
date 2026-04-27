package me.gabij.multiplebedspawn.listeners;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

import static me.gabij.multiplebedspawn.utils.BedsUtils.getMaxNumberOfBeds;
import static me.gabij.multiplebedspawn.utils.BedsUtils.getOrCreateRespawnPointUuid;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.getPlayerBedsCount;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.loadPlayerBedsData;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.savePlayerBedsData;

public class PlayerSetSpawnListener implements Listener {

    private final MultipleBedSpawn plugin;

    public PlayerSetSpawnListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getLocation() == null || event.getLocation().getWorld() == null) {
            return;
        }

        if (!plugin.isWorldEnabled(event.getLocation().getWorld().getName())) {
            return;
        }

        if (event.getCause() == PlayerSetSpawnEvent.Cause.BED) {
            // This plugin manages bed respawns itself, so skip the vanilla spawn update and chat message.
            event.setNotifyPlayer(false);
            event.setCancelled(true);
            return;
        }

        if (event.getCause() != PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR
                || !plugin.getConfig().getBoolean("respawn-anchors-enabled")) {
            return;
        }

        event.setNotifyPlayer(false);
        event.setCancelled(true);
        registerRespawnAnchor(event.getPlayer(), event.getLocation());
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

        playerBedsData.setNewRespawnPoint(player, anchor, savedUuid, me.gabij.multiplebedspawn.models.BedData.RespawnPointType.ANCHOR);
        savePlayerBedsData(player, playerBedsData);
        player.sendMessage(ChatColor.YELLOW + plugin.message("anchor-registered-successfully-message",
                "Respawn anchor registered successfully!"));
    }
}
