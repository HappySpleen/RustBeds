package me.gabij.multiplebedspawn.models;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class PlayerBedsData implements Serializable {
    private static final long serialVersionUID = 6158573570409965948L;

    private HashMap<String, BedData> bedData = new HashMap<String, BedData>();

    public PlayerBedsData() {
    }

    public PlayerBedsData(Player p, Block bed, String bedUUID) {
        setNewRespawnPoint(p, bed, bedUUID);
    }

    public void setNewBed(Player p, Block bed, String bedUUID) {
        setNewRespawnPoint(p, bed, bedUUID, BedData.RespawnPointType.BED);
    }

    public void setNewRespawnPoint(Player p, Block block, String uuid) {
        setNewRespawnPoint(p, block, uuid, block.getType() == org.bukkit.Material.RESPAWN_ANCHOR
                ? BedData.RespawnPointType.ANCHOR
                : BedData.RespawnPointType.BED);
    }

    public void setNewRespawnPoint(Player p, Block block, String uuid, BedData.RespawnPointType respawnPointType) {
        normalizePrimaryBeds();
        BedData tempBedData = new BedData(block, p, respawnPointType);
        if (!hasPrimaryBed()) {
            tempBedData.setPrimary(true);
        }
        this.bedData.put(uuid, tempBedData);
    }

    public void shareBed(PlayerBedsData receiverPlayerBedsData, String bedUUID) {
        normalizePrimaryBeds();
        receiverPlayerBedsData.normalizePrimaryBeds();

        BedData bedToShare = bedData.remove(bedUUID);
        if (bedToShare == null) {
            return;
        }

        bedToShare.setPrimary(!receiverPlayerBedsData.hasPrimaryBed());
        receiverPlayerBedsData.bedData.put(bedUUID, bedToShare);
        assignPrimaryIfNeeded();
        receiverPlayerBedsData.assignPrimaryIfNeeded();
    }

    public void removeBed(String bedUUID) {
        normalizePrimaryBeds();
        bedData.remove(bedUUID);
        assignPrimaryIfNeeded();
    }

    public boolean hasBed(String bedUUID) {
        return bedData.containsKey(bedUUID);
    }

    public boolean setPrimaryBed(String bedUUID) {
        if (!bedData.containsKey(bedUUID)) {
            return false;
        }

        clearPrimaryBeds();
        BedData primaryBed = bedData.get(bedUUID);
        if (primaryBed == null) {
            return false;
        }

        primaryBed.setPrimary(true);
        return true;
    }

    public boolean normalizePrimaryBeds() {
        boolean primaryFound = false;
        boolean changed = false;
        for (BedData value : bedData.values()) {
            if (value == null || !value.isPrimary()) {
                continue;
            }

            if (!primaryFound) {
                primaryFound = true;
                continue;
            }

            value.setPrimary(false);
            changed = true;
        }

        return changed;
    }

    public boolean normalizePrimarySelection() {
        boolean changed = normalizePrimaryBeds();
        if (!hasPrimaryBed()) {
            assignPrimaryIfNeeded();
            changed = hasPrimaryBed() || changed;
        }
        return changed;
    }

    public HashMap<String, BedData> getPlayerBedData() {
        return bedData;
    }

    private boolean hasPrimaryBed() {
        for (BedData value : bedData.values()) {
            if (value != null && value.isPrimary()) {
                return true;
            }
        }
        return false;
    }

    private void clearPrimaryBeds() {
        for (BedData value : bedData.values()) {
            if (value != null) {
                value.setPrimary(false);
            }
        }
    }

    private void assignPrimaryIfNeeded() {
        if (bedData.isEmpty() || hasPrimaryBed()) {
            return;
        }

        Map.Entry<String, BedData> fallbackEntry = null;
        for (Map.Entry<String, BedData> entry : bedData.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }

            if (fallbackEntry == null
                    || entry.getValue().getSortKey().compareToIgnoreCase(fallbackEntry.getValue().getSortKey()) < 0) {
                fallbackEntry = entry;
            }
        }

        if (fallbackEntry != null && fallbackEntry.getValue() != null) {
            fallbackEntry.getValue().setPrimary(true);
        }
    }

}
