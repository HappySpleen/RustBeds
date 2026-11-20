package me.happy.rustbeds.gui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class MenuItems {
    private static final String FILLER_NAME = ChatColor.DARK_GRAY + " ";
    private static final LegacyComponentSerializer LEGACY_TEXT = LegacyComponentSerializer.legacySection();

    private MenuItems() {
    }

    public static ItemStack createActionItem(Material material, boolean enabled, String name, String loreLine) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        setName(meta, (enabled ? ChatColor.YELLOW : ChatColor.DARK_GRAY) + name);
        setLore(meta, List.of((enabled ? ChatColor.GRAY : ChatColor.RED) + loreLine));
        applyMenuMeta(item, meta);
        return item;
    }

    public static ItemStack createControlItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        setName(meta, name);
        if (!lore.isEmpty()) {
            setLore(meta, lore);
        }
        applyMenuMeta(item, meta);
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

    public static Component text(String text) {
        return LEGACY_TEXT.deserialize(text);
    }

    public static Inventory createInventory(InventoryHolder holder, int size, String title) {
        return Bukkit.createInventory(holder, size, text(title));
    }

    public static void setName(ItemMeta meta, String name) {
        meta.displayName(text(name));
    }

    public static void setLore(ItemMeta meta, List<String> lore) {
        meta.lore(lore.stream().map(MenuItems::text).toList());
    }

    public static void applyMenuMeta(ItemStack item, ItemMeta meta) {
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.BANNER_PATTERNS)
                .build());
    }

    private static ItemStack createFillerItem(Material material) {
        return createFillerItem(material, FILLER_NAME);
    }

    private static ItemStack createFillerItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        setName(meta, name);
        applyMenuMeta(item, meta);
        return item;
    }
}
