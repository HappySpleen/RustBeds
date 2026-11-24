package me.happy.rustbeds.gui;

import me.happy.rustbeds.RustBeds;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.List;

public final class RespawnPointMenuLore {
    private RespawnPointMenuLore() {
    }

    public static boolean appendMetadataLore(List<String> lore, RustBeds plugin, BedMenuEntry entry,
            boolean includePrimary) {
        boolean hasMetadata = false;
        if (includePrimary && entry.bedData().isPrimary()) {
            lore.add(ChatColor.AQUA + plugin.message("bed-primary-label", "Primary bed"));
            hasMetadata = true;
        }
        if (appendSharedByLore(lore, plugin, entry)) {
            hasMetadata = true;
        }
        if (!plugin.getConfig().getBoolean("disable-bed-world-desc")) {
            lore.add(ChatColor.DARK_PURPLE + entry.bedData().getBedWorld().toUpperCase());
            hasMetadata = true;
        }
        if (!plugin.getConfig().getBoolean("disable-bed-coords-desc")) {
            lore.add(ChatColor.GRAY + entry.bedData().formatCoords());
            hasMetadata = true;
        }
        if (entry.bedData().isRespawnAnchor()) {
            lore.add(anchorChargesLine(plugin, entry));
            hasMetadata = true;
        }
        return hasMetadata;
    }

    public static boolean appendSharedByLore(List<String> lore, RustBeds plugin, BedMenuEntry entry) {
        if (!entry.bedData().hasSharedByName()) {
            return false;
        }

        lore.add(ChatColor.BLUE + sharedByLabel(plugin, entry.bedData().getSharedByName()));
        return true;
    }

    public static String deathDistanceLine(RustBeds plugin, BedMenuEntry entry, Location deathLocation) {
        if (!deathLocation.isWorldLoaded()) {
            return ChatColor.GRAY + plugin.message("respawn-menu-death-distance-unavailable", "Death distance unavailable");
        }

        Location bedLocation = entry.bedData().getBedLocation();
        if (bedLocation == null || !deathLocation.getWorld().equals(bedLocation.getWorld())) {
            return ChatColor.GRAY + plugin.message("respawn-menu-death-distance-other-world",
                    "Death was in a different world");
        }

        long blocks = Math.round(deathLocation.distance(bedLocation));
        return ChatColor.GRAY + (blocks == 1
                ? plugin.message("respawn-menu-death-distance-one", "1 block from death")
                : plugin.message("respawn-menu-death-distance", "{1} blocks from death")
                        .replace("{1}", Long.toString(blocks)));
    }

    public static String sharedByLabel(RustBeds plugin, String playerName) {
        return plugin.sharingModeMessage("bed-shared-by-label", "Shared By: {1}",
                        "bed-transferred-by-label", "Transferred By: {1}")
                .replace("{1}", playerName);
    }

    public static String anchorChargesLine(RustBeds plugin, BedMenuEntry entry) {
        return anchorChargesLine(plugin, entry.currentCharges(), entry.maxCharges());
    }

    static String anchorChargesLine(RustBeds plugin, int charges, int maxCharges) {
        return ChatColor.GOLD + plugin.message("respawn-anchor-charges", "Charges: {1}/{2}")
                .replace("{1}", Integer.toString(charges))
                .replace("{2}", Integer.toString(maxCharges));
    }
}
