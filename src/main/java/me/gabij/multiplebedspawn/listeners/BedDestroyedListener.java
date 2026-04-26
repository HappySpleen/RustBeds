package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.BedsDataType;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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

import static me.gabij.multiplebedspawn.utils.BedsUtils.checkIfIsBed;
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
            Block bed = checkIfIsBed(block);
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
    }

    private boolean playerHasBed(Player player, String bedUuid) {
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (!playerData.has(new NamespacedKey(plugin, "beds"), new BedsDataType())) {
            return false;
        }

        PlayerBedsData playerBedsData = playerData.get(new NamespacedKey(plugin, "beds"), new BedsDataType());
        return playerBedsData != null
                && playerBedsData.getPlayerBedData() != null
                && playerBedsData.hasBed(bedUuid);
    }

    private String getBedUuid(Block bed) {
        BlockState blockState = bed.getState();
        if (!(blockState instanceof TileState tileState)) {
            return null;
        }

        PersistentDataContainer container = tileState.getPersistentDataContainer();
        return container.get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING);
    }

    private String buildDestroyedMessage(BedData bedData, Location location) {
        if (bedData.getBedName() != null && !bedData.getBedName().isBlank()) {
            return ChatColor.RED + message("bed-destroyed-message",
                    "Your saved bed {1} was destroyed and removed.").replace("{1}", bedData.getBedName());
        }

        return buildLocationDestroyedMessage(location);
    }

    private String buildLocationDestroyedMessage(Location location) {
        String worldName = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return ChatColor.RED + message("bed-destroyed-message-location",
                "Your saved bed at {1} in {2} was destroyed and removed.")
                .replace("{1}", formatCoords(location))
                .replace("{2}", worldName);
    }

    private String formatCoords(Location location) {
        return "X: " + location.getBlockX() + " Y: " + location.getBlockY() + " Z: " + location.getBlockZ();
    }

    private String message(String key, String fallback) {
        String value = plugin.getMessages(key);
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
