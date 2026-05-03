package me.happy.rustbeds.gui;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.models.BedData;
import org.bukkit.Material;

import static me.happy.rustbeds.utils.BedsUtils.getRespawnAnchorCharges;
import static me.happy.rustbeds.utils.BedsUtils.getRespawnAnchorMaxCharges;

public record BedMenuEntry(String uuid, BedData bedData, String displayName, BedStatus status,
                           long remainingCooldownSeconds, int currentCharges, int maxCharges) {
    public static BedMenuEntry create(RustBeds plugin, String uuid, BedData bedData, int index, BedStatus status) {
        return create(plugin, uuid, bedData, index, status, 0L);
    }

    public static BedMenuEntry create(RustBeds plugin, String uuid, BedData bedData, int index, BedStatus status,
            long remainingCooldownSeconds) {
        int currentCharges = bedData.isRespawnAnchor() ? getRespawnAnchorCharges(bedData) : 0;
        int maxCharges = bedData.isRespawnAnchor() ? getRespawnAnchorMaxCharges(bedData) : 0;
        return new BedMenuEntry(uuid, bedData, displayName(plugin, bedData, index), status, remainingCooldownSeconds,
                currentCharges, maxCharges);
    }

    public Material displayMaterial() {
        return switch (status) {
            case COOLDOWN -> Material.CLOCK;
            case DEPLETED, DISABLED, MISSING, OBSTRUCTED -> Material.BARRIER;
            case AVAILABLE -> bedData.getBedMaterial();
        };
    }

    public int displayAmount() {
        if (bedData.isRespawnAnchor() && status == BedStatus.AVAILABLE) {
            return Math.max(1, Math.min(64, currentCharges));
        }

        return 1;
    }

    public boolean hasDynamicDisplay() {
        return status == BedStatus.COOLDOWN || status == BedStatus.OBSTRUCTED || bedData.isRespawnAnchor();
    }

    private static String displayName(RustBeds plugin, BedData bedData, int index) {
        if (bedData.hasCustomName()) {
            return bedData.getBedName();
        }

        return plugin.message(bedData.isRespawnAnchor() ? "default-anchor-name" : "default-bed-name",
                        bedData.isRespawnAnchor() ? "Respawn Anchor {1}" : "Bed {1}")
                .replace("{1}", Integer.toString(index));
    }
}
