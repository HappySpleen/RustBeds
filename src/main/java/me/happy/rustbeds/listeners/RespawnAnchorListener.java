package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RespawnAnchorListener implements Listener {
    private static final long MAX_PENDING_AGE_MILLIS = 5000L;
    private static final int SEARCH_RADIUS = 2;
    private static final Map<UUID, PendingAnchorSelection> PENDING_SELECTIONS = new HashMap<>();

    private final RustBeds plugin;

    public RespawnAnchorListener(RustBeds plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("respawn-anchors-enabled")) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        if (!plugin.isWorldEnabled(clickedBlock.getWorld().getName())) {
            return;
        }

        PENDING_SELECTIONS.put(event.getPlayer().getUniqueId(),
                new PendingAnchorSelection(clickedBlock.getLocation().clone(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PENDING_SELECTIONS.remove(event.getPlayer().getUniqueId());
    }

    public static Location consumePendingAnchor(Player player, Location respawnLocation) {
        PendingAnchorSelection pendingSelection = PENDING_SELECTIONS.remove(player.getUniqueId());
        if (pendingSelection != null
                && System.currentTimeMillis() - pendingSelection.recordedAtMillis() <= MAX_PENDING_AGE_MILLIS) {
            return pendingSelection.location();
        }

        return findNearbyAnchor(respawnLocation);
    }

    private static Location findNearbyAnchor(Location respawnLocation) {
        if (respawnLocation == null || respawnLocation.getWorld() == null) {
            return null;
        }

        Block bestMatch = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    Block block = respawnLocation.getWorld().getBlockAt(
                            respawnLocation.getBlockX() + x,
                            respawnLocation.getBlockY() + y,
                            respawnLocation.getBlockZ() + z);
                    if (block.getType() != Material.RESPAWN_ANCHOR) {
                        continue;
                    }

                    double distanceSquared = block.getLocation().distanceSquared(respawnLocation);
                    if (distanceSquared < bestDistanceSquared) {
                        bestMatch = block;
                        bestDistanceSquared = distanceSquared;
                    }
                }
            }
        }

        return bestMatch == null ? null : bestMatch.getLocation();
    }

    private record PendingAnchorSelection(Location location, long recordedAtMillis) {
    }
}
