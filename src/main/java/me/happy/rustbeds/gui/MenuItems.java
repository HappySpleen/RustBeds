package me.happy.rustbeds.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class MenuItems {
    private static final String FILLER_NAME = ChatColor.DARK_GRAY + " ";

    private MenuItems() {
    }

    public static ItemStack createActionItem(Material material, boolean enabled, String name, String loreLine) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.YELLOW : ChatColor.DARK_GRAY) + name);
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of((enabled ? ChatColor.GRAY : ChatColor.RED) + loreLine));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createControlItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        hideMenuTooltipDetails(meta);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static void fillBottomRow(Inventory inventory) {
        int bottomRowStart = Math.max(0, inventory.getSize() - 9);
        for (int slot = bottomRowStart; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
        }
    }

    public static void fillInventory(Inventory inventory, Material material, String name) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, createFillerItem(material, name));
        }
    }

    public static void hideMenuTooltipDetails(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    private static ItemStack createFillerItem(Material material) {
        return createFillerItem(material, FILLER_NAME);
    }

    private static ItemStack createFillerItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        hideMenuTooltipDetails(meta);
        item.setItemMeta(meta);
        return item;
    }
}
