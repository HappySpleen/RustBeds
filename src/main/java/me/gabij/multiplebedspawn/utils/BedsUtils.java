package me.gabij.multiplebedspawn.utils;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;

public class BedsUtils {
    static MultipleBedSpawn plugin = MultipleBedSpawn.getInstance();

    public static BedData removePlayerBed(String bedUUID, Player p) {
        return removePlayerBed(bedUUID, p, true);
    }

    public static BedData removePlayerBed(String bedUUID, Player p, boolean clearBlockUuid) {
        PersistentDataContainer playerData = p.getPersistentDataContainer();
        // checks to see if player has beds
        if (!playerData.has(PluginKeys.beds(), PluginKeys.bedsDataType())) {
            return null;
        }

        PlayerBedsData playerBedsData = PlayerUtils.loadPlayerBedsData(p);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return null;
        }

        HashMap<String, BedData> beds = playerBedsData.getPlayerBedData();
        if (!beds.containsKey(bedUUID)) {
            return null;
        }

        BedData bedData = beds.get(bedUUID);
        playerBedsData.removeBed(bedUUID);
        playerData.set(PluginKeys.beds(), PluginKeys.bedsDataType(), playerBedsData);
        plugin.getBedOwnershipStore().syncPlayerBeds(p);

        if (clearBlockUuid && !hasAnyRemainingOwner(bedUUID, p)) {
            clearRespawnPointUuid(bedData);
        }

        return bedData;
    }

    public static boolean checksIfBedExists(Location locBed, Player p, String bedUUID) {
        BedData savedRespawnPoint = null;
        PlayerBedsData playerBedsData = PlayerUtils.loadPlayerBedsData(p);
        if (playerBedsData != null && playerBedsData.getPlayerBedData() != null) {
            savedRespawnPoint = playerBedsData.getPlayerBedData().get(bedUUID);
        }

        if (savedRespawnPoint == null || !isRegisteredRespawnPointPresent(savedRespawnPoint, bedUUID)) {
            removePlayerBed(bedUUID, p, false);
            return false;
        }
        return true;
    }

    public static boolean isRegisteredRespawnPointPresent(BedData savedRespawnPoint, String uuid) {
        if (savedRespawnPoint == null) {
            return false;
        }

        Location blockLocation = savedRespawnPoint.getBedLocation();
        if (savedRespawnPoint.isRespawnAnchor()) {
            return isRegisteredAnchorPresent(blockLocation, uuid);
        }

        return isRegisteredBedPresent(blockLocation, uuid);
    }

    public static boolean isRegisteredBedPresent(Location locBed, String bedUUID) {
        World world = locBed.getWorld();
        if (world == null) {
            return false;
        }

        Block bed = world.getBlockAt(locBed);
        boolean isBed = false;
        if (bed.getBlockData() instanceof Bed bedPart) {
            // since the data is in the head we need to set the Block bed to its head
            if ("FOOT".equals(bedPart.getPart().toString())) {
                bed = (Block) bed.getRelative(bedPart.getFacing());
            }
            isBed = true;
        }

        if (!isBed) {
            return false;
        }

        BlockState blockState = bed.getState();
        if (blockState instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            String uuid = container.get(PluginKeys.uuid(), PersistentDataType.STRING);
            return uuid != null && uuid.equalsIgnoreCase(bedUUID);
        }

        return false;
    }

    public static boolean isRegisteredAnchorPresent(Location anchorLocation, String anchorUUID) {
        if (anchorLocation == null || anchorLocation.getWorld() == null) {
            return false;
        }

        Block anchor = anchorLocation.getWorld().getBlockAt(anchorLocation);
        if (anchor.getType() != Material.RESPAWN_ANCHOR) {
            return false;
        }

        return plugin.getRespawnAnchorStore().isAnchorRegistered(anchor.getLocation(), anchorUUID);
    }

    public static Block checkIfIsBed(Block block) {
        if (block != null && block.getBlockData() instanceof Bed bedPart) {
            // since the data is in the head we need to set the Block bed to its head
            if ("FOOT".equals(bedPart.getPart().toString())) {
                block = block.getRelative(bedPart.getFacing());
            }
            return block;
        }
        return null;
    }

    public static Block checkIfIsRespawnPoint(Block block) {
        if (block == null) {
            return null;
        }

        Block bed = checkIfIsBed(block);
        if (bed != null) {
            return bed;
        }

        return block.getBlockData() instanceof RespawnAnchor ? block : null;
    }

    public static String getOrCreateRespawnPointUuid(Block block, String generatedUuid) {
        Block normalizedBlock = checkIfIsRespawnPoint(block);
        if (normalizedBlock == null) {
            return null;
        }

        if (normalizedBlock.getType() == Material.RESPAWN_ANCHOR) {
            String existingUuid = plugin.getRespawnAnchorStore().getAnchorUuid(normalizedBlock.getLocation());
            if (existingUuid != null && !existingUuid.isBlank()) {
                return existingUuid;
            }

            plugin.getRespawnAnchorStore().bindAnchor(generatedUuid, normalizedBlock.getLocation());
            return generatedUuid;
        }

        BlockState blockState = normalizedBlock.getState();
        if (blockState instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            if (!container.has(PluginKeys.uuid(), PersistentDataType.STRING)) {
                container.set(PluginKeys.uuid(), PersistentDataType.STRING, generatedUuid);
                tileState.update();
                return generatedUuid;
            }

            String existingUuid = container.get(PluginKeys.uuid(), PersistentDataType.STRING);
            if (existingUuid != null && !existingUuid.isBlank()) {
                return existingUuid;
            }
        }

        return null;
    }

    public static String getRespawnPointUuid(Block block) {
        Block normalizedBlock = checkIfIsRespawnPoint(block);
        if (normalizedBlock == null) {
            return null;
        }

        if (normalizedBlock.getType() == Material.RESPAWN_ANCHOR) {
            return plugin.getRespawnAnchorStore().getAnchorUuid(normalizedBlock.getLocation());
        }

        BlockState blockState = normalizedBlock.getState();
        if (blockState instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            return container.get(PluginKeys.uuid(), PersistentDataType.STRING);
        }

        return null;
    }

    public static int getRespawnAnchorCharges(BedData savedRespawnPoint) {
        if (savedRespawnPoint == null || !savedRespawnPoint.isRespawnAnchor()) {
            return 0;
        }

        Location anchorLocation = savedRespawnPoint.getBedLocation();
        if (anchorLocation == null || anchorLocation.getWorld() == null) {
            return 0;
        }

        Block anchorBlock = anchorLocation.getWorld().getBlockAt(anchorLocation);
        if (!(anchorBlock.getBlockData() instanceof RespawnAnchor respawnAnchor)) {
            return 0;
        }

        return respawnAnchor.getCharges();
    }

    public static int getRespawnAnchorMaxCharges(BedData savedRespawnPoint) {
        if (savedRespawnPoint == null || !savedRespawnPoint.isRespawnAnchor()) {
            return 0;
        }

        Location anchorLocation = savedRespawnPoint.getBedLocation();
        if (anchorLocation == null || anchorLocation.getWorld() == null) {
            return 0;
        }

        Block anchorBlock = anchorLocation.getWorld().getBlockAt(anchorLocation);
        if (!(anchorBlock.getBlockData() instanceof RespawnAnchor respawnAnchor)) {
            return 0;
        }

        return respawnAnchor.getMaximumCharges();
    }

    public static boolean consumeRespawnAnchorCharge(BedData savedRespawnPoint) {
        if (savedRespawnPoint == null || !savedRespawnPoint.isRespawnAnchor()) {
            return false;
        }

        Location anchorLocation = savedRespawnPoint.getBedLocation();
        if (anchorLocation == null || anchorLocation.getWorld() == null) {
            return false;
        }

        Block anchorBlock = anchorLocation.getWorld().getBlockAt(anchorLocation);
        if (!(anchorBlock.getBlockData() instanceof RespawnAnchor respawnAnchor)) {
            return false;
        }
        if (!plugin.getRespawnAnchorStore().isAnchorRegistered(anchorBlock.getLocation(), getRespawnPointUuid(anchorBlock))) {
            return false;
        }
        if (respawnAnchor.getCharges() <= 0) {
            return false;
        }

        respawnAnchor.setCharges(respawnAnchor.getCharges() - 1);
        anchorBlock.setBlockData(respawnAnchor);
        return true;
    }

    public static int getMaxNumberOfBeds(Player player) {
        int maxBeds = plugin.getConfig().getInt("max-beds");
        int maxBedsByPerms = 0;
        if (player.hasPermission("multiplebedspawn.maxcount")) {
            for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
                String permName = perm.getPermission();
                if (permName.contains("multiplebedspawn.maxcount.") && perm.getValue()) {
                    String maxCount = (permName.split("multiplebedspawn.maxcount."))[1].trim();
                    try {
                        int max = Integer.parseInt(maxCount);
                        if (max > 53) {
                            plugin.getLogger().warning("Permission " + permName
                                    + " is invalid! Should be lower than 53. Value defaulted to 53, please remove this permission. Warning triggered by player "
                                    + player.getName());
                            max = 53;
                        }
                        if (max > maxBedsByPerms) {
                            maxBedsByPerms = max;
                        }
                    } catch (Exception err) {
                        plugin.getLogger().warning("Permission " + permName
                                + " is invalid! Should be a number after 'maxcount.'. Warning triggered by player "
                                + player.getName());
                    }
                }
            }
        }
        if (maxBeds > 53) {
            plugin.getLogger().warning("Max bed count cant be over 53! Value defaulted to 53.");
            plugin.getConfig().set("max-beds", 53);
            plugin.saveConfig();
            maxBeds = 53;
        }
        if (maxBedsByPerms > 0) {
            maxBeds = maxBedsByPerms;
        }
        return maxBeds;
    }

    private static boolean hasAnyRemainingOwner(String bedUUID, Player removedPlayer) {
        if (plugin.getBedOwnershipStore().hasAnyKnownOwner(bedUUID)) {
            return true;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(removedPlayer.getUniqueId())) {
                continue;
            }

            if (playerHasBed(onlinePlayer, bedUUID)) {
                return true;
            }
        }

        return false;
    }

    private static boolean playerHasBed(Player player, String bedUUID) {
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (!playerData.has(PluginKeys.beds(), PluginKeys.bedsDataType())) {
            return false;
        }

        PlayerBedsData playerBedsData = playerData.get(PluginKeys.beds(), PluginKeys.bedsDataType());
        return playerBedsData != null
                && playerBedsData.getPlayerBedData() != null
                && playerBedsData.hasBed(bedUUID);
    }

    private static void clearRespawnPointUuid(BedData bedData) {
        World world = Bukkit.getWorld(bedData.getBedWorld());
        if (world == null) {
            return;
        }

        Location locBed = bedData.getBedLocation();
        if (locBed == null) {
            return;
        }

        if (bedData.isRespawnAnchor()) {
            plugin.getRespawnAnchorStore().clearAnchor(locBed);
            return;
        }

        Block bed = checkIfIsBed(world.getBlockAt(locBed));
        if (bed == null) {
            return;
        }

        BlockState blockState = bed.getState();
        if (blockState instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            container.remove(PluginKeys.uuid());
            tileState.update();
        }
    }
}
