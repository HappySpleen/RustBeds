package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.storage.StoredBed;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static me.happy.rustbeds.utils.BedsUtils.checksIfBedExists;
import static me.happy.rustbeds.utils.KeyUtils.key;
import static me.happy.rustbeds.utils.KeyUtils.removeAll;
import static me.happy.rustbeds.utils.PlayerUtils.*;
import static me.happy.rustbeds.utils.RunCommandUtils.runCommandOnSpawn;

@SuppressWarnings("deprecation")
public class RespawnMenuHandler implements Listener {

    static RustBeds plugin;

    public RespawnMenuHandler(RustBeds plugin) {
        RespawnMenuHandler.plugin = plugin;
    }

    public static void updateItens(Inventory gui, Player player) {
        if (gui.getViewers().isEmpty()) {
            return;
        }

        boolean hasActiveCooldown = false;
        for (ItemStack item : gui.getContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }

            ItemMeta itemMeta = item.getItemMeta();
            PersistentDataContainer data = itemMeta.getPersistentDataContainer();
            if (!data.has(key("bedId"), PersistentDataType.STRING)) {
                continue;
            }

            String bedId = data.get(key("bedId"), PersistentDataType.STRING);
            Optional<StoredBed> bed = findBed(bedId);
            if (bed.isEmpty()) {
                continue;
            }

            updateBedItem(item, itemMeta, bed.get(), null);
            if (bed.get().hasCooldown()) {
                hasActiveCooldown = true;
            }
        }

        if (hasActiveCooldown) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateItens(gui, player), 10L);
        }
    }

    public static void openRespawnMenu(Player player) {
        ensureLegacyPlayerData(player);
        List<StoredBed> beds = new ArrayList<>(getPlayerBeds(player));
        beds.removeIf(bed -> !checksIfBedExists(bed.getBedLocation(), player, bed.getBedId()));

        if (beds.isEmpty()) {
            PersistentDataContainer playerData = player.getPersistentDataContainer();
            if (playerData.has(key("spawnLoc"), PersistentDataType.STRING)) {
                Location location = getPlayerRespawnLoc(player);
                removeAll(playerData, plugin.getName(), "spawnLoc");
                undoPropPlayer(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(location), 1L);
            }
            return;
        }

        setPropPlayer(player);
        Inventory gui = Bukkit.createInventory(player, getInventorySize(beds.size() + 1),
                ChatColor.translateAlternateColorCodes('&', plugin.getMessages("menu-title")));

        AtomicBoolean hasCooldown = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(1);
        for (StoredBed bed : beds) {
            ItemStack item = new ItemStack(bed.getMaterial(), 1);
            ItemMeta itemMeta = item.getItemMeta();
            updateBedItem(item, itemMeta, bed, counter.getAndIncrement());
            if (bed.hasCooldown()) {
                hasCooldown.set(true);
            }
            gui.addItem(item);
        }

        ItemStack spawnItem = new ItemStack(Material.GRASS_BLOCK, 1);
        ItemMeta spawnMeta = spawnItem.getItemMeta();
        spawnMeta.setDisplayName(ChatColor.YELLOW + "SPAWN");
        spawnItem.setItemMeta(spawnMeta);
        gui.setItem(gui.getSize() - 1, spawnItem);

        if (hasCooldown.get()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateItens(gui, player), 10L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> player.openInventory(gui), 0L);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equalsIgnoreCase(plugin.getMessages("menu-title"))) {
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
            Optional<StoredBed> bed = findBed(bedId);

            if (bed.isPresent() && checksIfBedExists(bed.get().getBedLocation(), player, bedId)) {
                if (bed.get().hasCooldown()) {
                    updateItens(event.getClickedInventory(), player);
                    return;
                }
                teleportPlayer(player, bed.get());
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, (Runnable) player::closeInventory, 0L);
            }
            return;
        }

        if (event.getSlot() == event.getInventory().getSize() - 1) {
            undoPropPlayer(player);
            Location location = getPlayerRespawnLoc(player);
            removeAll(player.getPersistentDataContainer(), plugin.getName(), "spawnLoc");
            player.teleport(location);
            runCommandOnSpawn(player);
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equalsIgnoreCase(plugin.getMessages("menu-title"))) {
            Player player = (Player) event.getPlayer();
            if (!player.getCanPickupItems()) {
                openRespawnMenu(player);
            }
        }
    }

    private static void updateBedItem(ItemStack item, ItemMeta itemMeta, StoredBed bed, Integer defaultNumber) {
        String displayName = bed.getCustomName();
        if (displayName == null || displayName.isBlank()) {
            String index = defaultNumber == null ? "?" : String.valueOf(defaultNumber);
            displayName = plugin.getMessages("default-bed-name").replace("{1}", index);
        }

        item.setType(bed.getMaterial());
        itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        itemMeta.getPersistentDataContainer().set(key("bedId"), PersistentDataType.STRING, bed.getBedId());

        List<String> lore = new ArrayList<>();
        if (!plugin.getConfig().getBoolean("disable-bed-world-desc")) {
            lore.add(ChatColor.DARK_PURPLE + bed.getWorldName().toUpperCase());
        }
        if (!plugin.getConfig().getBoolean("disable-bed-coords-desc")) {
            lore.add(ChatColor.GRAY + bed.locationText());
        }
        if (bed.hasCooldown()) {
            long seconds = Math.max(0, (bed.getCooldownUntil() - System.currentTimeMillis()) / 1000);
            lore.add(ChatColor.GOLD + "" + ChatColor.BOLD
                    + plugin.getMessages("cooldown-text").replace("{1}", String.valueOf(seconds)));
        }

        itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
    }

    private static int getInventorySize(int bedCount) {
        return 9 * ((int) Math.ceil(bedCount / 9.0));
    }

    private static Optional<StoredBed> findBed(String bedId) {
        try {
            return plugin.getBedStorage().findBedById(bedId);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load bed " + bedId + ": " + exception.getMessage());
            return Optional.empty();
        }
    }
}
