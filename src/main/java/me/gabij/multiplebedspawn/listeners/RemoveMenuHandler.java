package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.storage.StoredBed;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static me.gabij.multiplebedspawn.utils.BedsUtils.checksIfBedExists;
import static me.gabij.multiplebedspawn.utils.BedsUtils.removePlayerBed;
import static me.gabij.multiplebedspawn.utils.KeyUtils.key;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.ensureLegacyPlayerData;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.getPlayerBeds;

public class RemoveMenuHandler implements Listener {
    static MultipleBedSpawn plugin;

    public RemoveMenuHandler(MultipleBedSpawn plugin) {
        RemoveMenuHandler.plugin = plugin;
    }

    public static void openRemoveMenu(Player player) {
        ensureLegacyPlayerData(player);
        List<StoredBed> beds = new ArrayList<>(getPlayerBeds(player));
        beds.removeIf(bed -> !checksIfBedExists(bed.getBedLocation(), player, bed.getBedId()));
        if (beds.isEmpty()) {
            return;
        }

        Inventory gui = Bukkit.createInventory(player, getInventorySize(beds.size() + 1),
                ChatColor.translateAlternateColorCodes('&', plugin.getMessages("remove-menu-title")));
        populateBeds(gui, beds);
        gui.setItem(gui.getSize() - 1, closeItem());

        Bukkit.getScheduler().runTaskLater(plugin, () -> player.openInventory(gui), 0L);
    }

    public static void updateItens(Inventory gui, Player player) {
        if (gui.getViewers().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, (Runnable) player::closeInventory, 0L);
            return;
        }

        List<StoredBed> beds = new ArrayList<>(getPlayerBeds(player));
        beds.removeIf(bed -> !checksIfBedExists(bed.getBedLocation(), player, bed.getBedId()));
        if (beds.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, (Runnable) player::closeInventory, 0L);
            return;
        }

        gui.clear();
        populateBeds(gui, beds);
        gui.setItem(gui.getSize() - 1, closeItem());
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equalsIgnoreCase(plugin.getMessages("remove-menu-title"))) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null
                || !event.getCurrentItem().hasItemMeta()) {
            return;
        }

        ItemMeta itemMeta = event.getCurrentItem().getItemMeta();
        PersistentDataContainer data = itemMeta.getPersistentDataContainer();

        if (data.has(key("bedId"), PersistentDataType.STRING)) {
            String bedId = data.get(key("bedId"), PersistentDataType.STRING);
            removePlayerBed(bedId, player);
            updateItens(event.getClickedInventory(), player);
            return;
        }

        if (event.getCurrentItem().getType() == Material.BARRIER) {
            Bukkit.getScheduler().runTaskLater(plugin, (Runnable) player::closeInventory, 0L);
        }
    }

    private static void populateBeds(Inventory gui, List<StoredBed> beds) {
        AtomicInteger counter = new AtomicInteger(1);
        for (StoredBed bed : beds) {
            ItemStack item = new ItemStack(bed.getMaterial(), 1);
            ItemMeta itemMeta = item.getItemMeta();
            String defaultName = plugin.getMessages("default-bed-name").replace("{1}",
                    String.valueOf(counter.getAndIncrement()));
            itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    bed.getCustomName() == null ? defaultName : bed.getCustomName()));

            List<String> lore = new ArrayList<>();
            if (!plugin.getConfig().getBoolean("disable-bed-world-desc")) {
                lore.add(ChatColor.DARK_PURPLE + bed.getWorldName().toUpperCase());
            }
            if (!plugin.getConfig().getBoolean("disable-bed-coords-desc")) {
                lore.add(ChatColor.GRAY + bed.locationText());
            }
            itemMeta.setLore(lore);
            itemMeta.getPersistentDataContainer().set(key("bedId"), PersistentDataType.STRING, bed.getBedId());
            item.setItemMeta(itemMeta);
            gui.addItem(item);
        }
    }

    private static int getInventorySize(int bedCount) {
        return 9 * ((int) Math.ceil(bedCount / 9.0));
    }

    private static ItemStack closeItem() {
        ItemStack item = new ItemStack(Material.BARRIER, 1);
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(ChatColor.YELLOW + plugin.getMessages("close-menu"));
        item.setItemMeta(itemMeta);
        return item;
    }
}
