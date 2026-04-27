package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import me.gabij.multiplebedspawn.utils.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static me.gabij.multiplebedspawn.utils.BedsUtils.checkIfIsRespawnPoint;
import static me.gabij.multiplebedspawn.utils.BedsUtils.getRespawnPointUuid;
import static me.gabij.multiplebedspawn.utils.BedsUtils.removePlayerBed;

public class BedDestroyedListener implements Listener {
    private final MultipleBedSpawn plugin;

    public BedDestroyedListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleDestroyedBlocks(Set.of(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        handleDestroyedBlocks(Set.of(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleDestroyedBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleDestroyedBlocks(event.blockList());
    }

    private void handleDestroyedBlocks(Collection<Block> blocks) {
        Set<String> destroyedBeds = new LinkedHashSet<>();
        for (Block block : blocks) {
            Block bed = checkIfIsRespawnPoint(block);
            if (bed == null) {
                continue;
            }

            String bedUuid = getBedUuid(bed);
            if (bedUuid == null || !destroyedBeds.add(bedUuid)) {
                continue;
            }

            notifyOwners(bed, bedUuid);
        }
    }

    private void notifyOwners(Block bed, String bedUuid) {
        Set<UUID> owners = new LinkedHashSet<>(plugin.getBedOwnershipStore().getOwners(bedUuid));
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (playerHasBed(player, bedUuid)) {
                owners.add(player.getUniqueId());
            }
        });

        if (owners.isEmpty()) {
            return;
        }

        for (UUID ownerId : owners) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                plugin.getBedOwnershipStore().queueDestroyedBed(ownerId, bedUuid);
                continue;
            }

            BedData removedBed = removePlayerBed(bedUuid, owner, false);
            if (removedBed != null) {
                owner.sendMessage(buildDestroyedMessage(removedBed, bed.getLocation()));
            }
        }

        plugin.getBedOwnershipStore().clearOwners(bedUuid);
        if (bed.getType() == org.bukkit.Material.RESPAWN_ANCHOR) {
            plugin.getRespawnAnchorStore().clearAnchor(bed.getLocation());
        }
    }

    private boolean playerHasBed(Player player, String bedUuid) {
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (!playerData.has(PluginKeys.beds(), PluginKeys.bedsDataType())) {
            return false;
        }

        PlayerBedsData playerBedsData = playerData.get(PluginKeys.beds(), PluginKeys.bedsDataType());
        return playerBedsData != null
                && playerBedsData.getPlayerBedData() != null
                && playerBedsData.hasBed(bedUuid);
    }

    private String getBedUuid(Block bed) {
        return getRespawnPointUuid(bed);
    }

    private String buildDestroyedMessage(BedData bedData, Location location) {
        if (bedData.hasCustomName()) {
            return ChatColor.RED + plugin.message(
                    bedData.isRespawnAnchor() ? "anchor-destroyed-message" : "bed-destroyed-message",
                    bedData.isRespawnAnchor()
                            ? "Your saved respawn anchor {1} was destroyed and removed."
                            : "Your saved bed {1} was destroyed and removed.")
                    .replace("{1}", bedData.getBedName());
        }

        return buildLocationDestroyedMessage(location, bedData.isRespawnAnchor());
    }

    private String buildLocationDestroyedMessage(Location location, boolean respawnAnchor) {
        String worldName = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return ChatColor.RED + plugin.message(
                respawnAnchor ? "anchor-destroyed-message-location" : "bed-destroyed-message-location",
                respawnAnchor
                        ? "Your saved respawn anchor at {1} in {2} was destroyed and removed."
                        : "Your saved bed at {1} in {2} was destroyed and removed.")
                .replace("{1}", formatCoords(location))
                .replace("{2}", worldName);
    }

    private String formatCoords(Location location) {
        return "X: " + location.getBlockX() + " Y: " + location.getBlockY() + " Z: " + location.getBlockZ();
    }

}
