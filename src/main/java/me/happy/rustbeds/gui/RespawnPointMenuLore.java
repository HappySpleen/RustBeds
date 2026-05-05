package me.happy.rustbeds.gui;

import me.happy.rustbeds.RustBeds;
import org.bukkit.ChatColor;

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

    public static String sharedByLabel(RustBeds plugin, String playerName) {
        return plugin.sharingModeMessage("bed-shared-by-label", "Shared By: {1}",
                        "bed-transferred-by-label", "Transferred By: {1}")
                .replace("{1}", playerName);
    }

    public static String anchorChargesLine(RustBeds plugin, BedMenuEntry entry) {
        return ChatColor.GOLD + plugin.message("respawn-anchor-charges", "Charges: {1}/{2}")
                .replace("{1}", Integer.toString(entry.currentCharges()))
                .replace("{2}", Integer.toString(entry.maxCharges()));
    }
}
