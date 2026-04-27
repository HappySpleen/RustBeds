package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.gui.AdminBedsMenuHolder;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import me.gabij.multiplebedspawn.utils.PluginKeys;
import me.gabij.multiplebedspawn.utils.TeleportUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.gabij.multiplebedspawn.utils.BedsUtils.isRegisteredBedPresent;
import static me.gabij.multiplebedspawn.utils.BedsUtils.removePlayerBed;

public class AdminBedsMenuHandler implements Listener {
    private static final int LIST_SIZE = 54;
    private static final int ACTION_SIZE = 27;
    private static final int PAGE_SIZE = 45;

    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int INFO_SLOT = 47;
    private static final int PRIMARY_ACTION_SLOT = 49;
    private static final int CLOSE_SLOT = 51;
    private static final int NEXT_PAGE_SLOT = 53;

    private static final int ACTION_RENAME_SLOT = 10;
    private static final int ACTION_TELEPORT_SELF_SLOT = 12;
    private static final int ACTION_PREVIEW_SLOT = 13;
    private static final int ACTION_TELEPORT_OTHER_SLOT = 14;
    private static final int ACTION_REMOVE_SLOT = 16;
    private static final int ACTION_BACK_SLOT = 18;

    private static MultipleBedSpawn plugin;

    public AdminBedsMenuHandler(MultipleBedSpawn plugin) {
        AdminBedsMenuHandler.plugin = plugin;
    }

    public static void openOwnerMenu(Player admin, int requestedPage) {
        if (!admin.hasPermission("multiplebedspawn.admin")) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-no-permission",
                    "You do not have permission to use this command."));
            admin.closeInventory();
            return;
        }

        List<Player> owners = getOwnerCandidates();
        int totalPages = Math.max(1, (int) Math.ceil(owners.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        AdminBedsMenuHolder holder = new AdminBedsMenuHolder(admin.getUniqueId(),
                AdminBedsMenuHolder.ViewType.OWNER_LIST, page, 0, null, null);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE, plugin.message("admin-beds-title", "Admin beds"));
        holder.setInventory(inventory);

        renderOwnerMenu(inventory, owners, page, totalPages);
        admin.openInventory(inventory);
    }

    public static void openOwnerBedsMenu(Player admin, UUID ownerId, int requestedPage) {
        Player owner = getOnlineOwner(ownerId);
        if (owner == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-owner-offline",
                    "That player is no longer online."));
            openOwnerMenu(admin, 0);
            return;
        }

        List<BedMenuEntry> entries = getOwnerBedEntries(owner);
        if (entries.isEmpty()) {
            admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-owner-no-beds",
                    "That player has no saved beds."));
            openOwnerMenu(admin, 0);
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        AdminBedsMenuHolder holder = new AdminBedsMenuHolder(admin.getUniqueId(), AdminBedsMenuHolder.ViewType.BED_LIST,
                page, 0, ownerId, null);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE,
                plugin.message("admin-beds-owner-title", "Player beds"));
        holder.setInventory(inventory);

        renderOwnerBedsMenu(inventory, owner, entries, page, totalPages);
        admin.openInventory(inventory);
    }

    public static void openActionMenu(Player admin, UUID ownerId, int returnPage, String bedUuid) {
        Player owner = getOnlineOwner(ownerId);
        if (owner == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-owner-offline",
                    "That player is no longer online."));
            openOwnerMenu(admin, 0);
            return;
        }

        BedMenuEntry entry = getOwnerBedEntry(owner, bedUuid);
        if (entry == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "That player no longer has that bed saved."));
            openOwnerBedsMenu(admin, ownerId, returnPage);
            return;
        }

        AdminBedsMenuHolder holder = new AdminBedsMenuHolder(admin.getUniqueId(), AdminBedsMenuHolder.ViewType.ACTIONS,
                returnPage, 0, ownerId, bedUuid);
        Inventory inventory = Bukkit.createInventory(holder, ACTION_SIZE,
                plugin.message("admin-beds-actions-title", "Admin bed actions"));
        holder.setInventory(inventory);

        renderActionMenu(inventory, owner, entry);
        admin.openInventory(inventory);
    }

    private static void openTeleportTargetMenu(Player admin, UUID ownerId, String bedUuid, int returnPage,
            int requestedPage) {
        Player owner = getOnlineOwner(ownerId);
        if (owner == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-owner-offline",
                    "That player is no longer online."));
            openOwnerMenu(admin, 0);
            return;
        }

        BedMenuEntry entry = getOwnerBedEntry(owner, bedUuid);
        if (entry == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "That player no longer has that bed saved."));
            openOwnerBedsMenu(admin, ownerId, returnPage);
            return;
        }

        List<Player> targets = getTeleportTargets(admin);
        int totalPages = Math.max(1, (int) Math.ceil(targets.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        AdminBedsMenuHolder holder = new AdminBedsMenuHolder(admin.getUniqueId(),
                AdminBedsMenuHolder.ViewType.TELEPORT_TARGET_LIST, page, returnPage, ownerId, bedUuid);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE,
                plugin.message("admin-beds-teleport-title", "Teleport player"));
        holder.setInventory(inventory);

        renderTeleportTargetMenu(inventory, owner, entry, targets, page, totalPages);
        admin.openInventory(inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminBedsMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        Player admin = (Player) event.getWhoClicked();
        if (!holder.getViewerId().equals(admin.getUniqueId())) {
            return;
        }

        switch (holder.getViewType()) {
            case OWNER_LIST -> handleOwnerListClick(admin, event.getSlot(), event.getCurrentItem(), holder);
            case BED_LIST -> handleBedListClick(admin, event.getSlot(), event.getCurrentItem(), holder);
            case ACTIONS -> handleActionMenuClick(admin, event.getSlot(), holder);
            case TELEPORT_TARGET_LIST ->
                    handleTeleportTargetMenuClick(admin, event.getSlot(), event.getCurrentItem(), holder);
        }
    }

    private static void handleOwnerListClick(Player admin, int slot, ItemStack clickedItem, AdminBedsMenuHolder holder) {
        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> openOwnerMenu(admin, holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT, CLOSE_SLOT -> admin.closeInventory();
            case NEXT_PAGE_SLOT -> openOwnerMenu(admin, holder.getPage() + 1);
            default -> {
                UUID ownerId = getUuidData(clickedItem, "admin-owner");
                if (ownerId == null) {
                    return;
                }

                openOwnerBedsMenu(admin, ownerId, 0);
            }
        }
    }

    private static void handleBedListClick(Player admin, int slot, ItemStack clickedItem, AdminBedsMenuHolder holder) {
        UUID ownerId = holder.getOwnerId();
        if (ownerId == null) {
            openOwnerMenu(admin, 0);
            return;
        }

        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> openOwnerBedsMenu(admin, ownerId, holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT -> openOwnerMenu(admin, 0);
            case CLOSE_SLOT -> admin.closeInventory();
            case NEXT_PAGE_SLOT -> openOwnerBedsMenu(admin, ownerId, holder.getPage() + 1);
            default -> {
                String bedUuid = getStringData(clickedItem, "admin-bed");
                if (bedUuid == null) {
                    return;
                }

                openActionMenu(admin, ownerId, holder.getPage(), bedUuid);
            }
        }
    }

    private static void handleActionMenuClick(Player admin, int slot, AdminBedsMenuHolder holder) {
        UUID ownerId = holder.getOwnerId();
        String bedUuid = holder.getBedUuid();
        if (ownerId == null || bedUuid == null) {
            openOwnerMenu(admin, 0);
            return;
        }

        Player owner = getOnlineOwner(ownerId);
        if (owner == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-owner-offline",
                    "That player is no longer online."));
            openOwnerMenu(admin, 0);
            return;
        }

        BedMenuEntry entry = getOwnerBedEntry(owner, bedUuid);
        if (entry == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "That player no longer has that bed saved."));
            openOwnerBedsMenu(admin, ownerId, holder.getPage());
            return;
        }

        switch (slot) {
            case ACTION_BACK_SLOT -> openOwnerBedsMenu(admin, ownerId, holder.getPage());
            case ACTION_RENAME_SLOT -> {
                if (entry.status() == BedStatus.MISSING) {
                    return;
                }

                AdminBedMenuInputListener.beginRenamePrompt(admin, ownerId, bedUuid, holder.getPage());
                admin.sendMessage(ChatColor.YELLOW + plugin.message("rename-prompt",
                        "Type the new bed name in chat. Type 'cancel' to abort."));
                admin.closeInventory();
            }
            case ACTION_TELEPORT_SELF_SLOT -> {
                if (entry.status() == BedStatus.MISSING) {
                    return;
                }

                if (teleportPlayerToBed(admin, admin, owner, entry)) {
                    admin.closeInventory();
                }
            }
            case ACTION_TELEPORT_OTHER_SLOT -> {
                if (entry.status() == BedStatus.MISSING) {
                    return;
                }

                openTeleportTargetMenu(admin, ownerId, bedUuid, holder.getPage(), 0);
            }
            case ACTION_REMOVE_SLOT -> {
                removePlayerBed(bedUuid, owner);
                admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-remove-success",
                        "Removed {1}'s saved bed.").replace("{1}", owner.getName()));
                openOwnerBedsMenu(admin, ownerId, holder.getPage());
            }
            default -> {
            }
        }
    }

    private static void handleTeleportTargetMenuClick(Player admin, int slot, ItemStack clickedItem,
            AdminBedsMenuHolder holder) {
        UUID ownerId = holder.getOwnerId();
        String bedUuid = holder.getBedUuid();
        if (ownerId == null || bedUuid == null) {
            openOwnerMenu(admin, 0);
            return;
        }

        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> openTeleportTargetMenu(admin, ownerId, bedUuid, holder.getContextPage(),
                    holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT -> openActionMenu(admin, ownerId, holder.getContextPage(), bedUuid);
            case CLOSE_SLOT -> admin.closeInventory();
            case NEXT_PAGE_SLOT -> openTeleportTargetMenu(admin, ownerId, bedUuid, holder.getContextPage(),
                    holder.getPage() + 1);
            default -> {
                UUID targetId = getUuidData(clickedItem, "admin-target");
                if (targetId == null) {
                    return;
                }

                Player owner = getOnlineOwner(ownerId);
                Player target = Bukkit.getPlayer(targetId);
                if (owner == null) {
                    admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-owner-offline",
                            "That player is no longer online."));
                    openOwnerMenu(admin, 0);
                    return;
                }
                if (target == null) {
                    admin.sendMessage(ChatColor.RED + plugin.message("player-not-found", "Player not found!"));
                    openTeleportTargetMenu(admin, ownerId, bedUuid, holder.getContextPage(), holder.getPage());
                    return;
                }

                BedMenuEntry entry = getOwnerBedEntry(owner, bedUuid);
                if (entry == null) {
                    admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                            "That player no longer has that bed saved."));
                    openOwnerBedsMenu(admin, ownerId, holder.getContextPage());
                    return;
                }

                if (teleportPlayerToBed(admin, target, owner, entry)) {
                    openActionMenu(admin, ownerId, holder.getContextPage(), bedUuid);
                }
            }
        }
    }

    private static void renderOwnerMenu(Inventory inventory, List<Player> owners, int page, int totalPages) {
        inventory.clear();
        fillBottomRow(inventory);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, owners.size());
        for (int slot = 0; slot + startIndex < endIndex; slot++) {
            inventory.setItem(slot, createOwnerItem(owners.get(startIndex + slot)));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-previous", "Previous page"), List.of()));
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + plugin.message("admin-beds-prompt", "Choose a player to manage."));
        if (owners.isEmpty()) {
            lore.add(ChatColor.RED + plugin.message("admin-beds-empty",
                    "No other online players have saved beds."));
        }
        inventory.setItem(INFO_SLOT, createControlItem(Material.PLAYER_HEAD,
                ChatColor.GOLD + plugin.message("admin-beds-page", "Players {1}/{2}")
                        .replace("{1}", Integer.toString(page + 1))
                        .replace("{2}", Integer.toString(totalPages)),
                lore));
        inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.BARRIER,
                ChatColor.RED + plugin.message("manage-menu-close", "Close menu"),
                List.of(ChatColor.GRAY + plugin.message("manage-menu-close-lore", "Close the beds management menu."))));
        inventory.setItem(CLOSE_SLOT, createControlItem(Material.BARRIER,
                ChatColor.RED + plugin.message("manage-menu-close", "Close menu"),
                List.of(ChatColor.GRAY + plugin.message("manage-menu-close-lore", "Close the beds management menu."))));

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-next", "Next page"), List.of()));
        }
    }

    private static void renderOwnerBedsMenu(Inventory inventory, Player owner, List<BedMenuEntry> entries, int page,
            int totalPages) {
        inventory.clear();
        fillBottomRow(inventory);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, entries.size());
        for (int slot = 0; slot + startIndex < endIndex; slot++) {
            inventory.setItem(slot, createBedListItem(entries.get(startIndex + slot)));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-previous", "Previous page"), List.of()));
        }

        inventory.setItem(INFO_SLOT, createControlItem(Material.BOOK,
                ChatColor.GOLD + plugin.message("admin-beds-owner-page", "{1}'s beds {2}/{3}")
                        .replace("{1}", owner.getName())
                        .replace("{2}", Integer.toString(page + 1))
                        .replace("{3}", Integer.toString(totalPages)),
                List.of(
                        ChatColor.GRAY + plugin.message("admin-beds-owner-prompt",
                                "Click a bed to rename, remove, or teleport to it."),
                        ChatColor.DARK_PURPLE + owner.getName())));
        inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("admin-beds-back-players", "Back to players"),
                List.of(ChatColor.GRAY + plugin.message("admin-beds-back-players-lore",
                        "Return to the player selection menu."))));
        inventory.setItem(CLOSE_SLOT, createControlItem(Material.BARRIER,
                ChatColor.RED + plugin.message("manage-menu-close", "Close menu"),
                List.of(ChatColor.GRAY + plugin.message("manage-menu-close-lore", "Close the beds management menu."))));

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-next", "Next page"), List.of()));
        }
    }

    private static void renderActionMenu(Inventory inventory, Player owner, BedMenuEntry entry) {
        fillInventory(inventory, Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        inventory.setItem(ACTION_PREVIEW_SLOT, createBedPreviewItem(owner, entry));
        inventory.setItem(ACTION_RENAME_SLOT, createActionItem(
                entry.status() != BedStatus.MISSING ? Material.NAME_TAG : Material.GRAY_DYE,
                entry.status() != BedStatus.MISSING,
                plugin.message("bed-action-rename", "Rename bed"),
                plugin.message("bed-action-rename-lore", "Rename this saved bed.")));
        inventory.setItem(ACTION_TELEPORT_SELF_SLOT, createActionItem(
                entry.status() != BedStatus.MISSING ? Material.ENDER_PEARL : Material.GRAY_DYE,
                entry.status() != BedStatus.MISSING,
                plugin.message("admin-beds-teleport-self", "Teleport to bed"),
                plugin.message("admin-beds-teleport-self-lore", "Teleport yourself to this saved bed.")));
        inventory.setItem(ACTION_TELEPORT_OTHER_SLOT, createActionItem(
                entry.status() != BedStatus.MISSING ? Material.PLAYER_HEAD : Material.GRAY_DYE,
                entry.status() != BedStatus.MISSING,
                plugin.message("admin-beds-teleport-other", "Teleport player to bed"),
                plugin.message("admin-beds-teleport-other-lore", "Choose another player to teleport to this bed.")));
        inventory.setItem(ACTION_REMOVE_SLOT, createActionItem(Material.BARRIER, true,
                plugin.message("bed-action-remove", "Remove bed"),
                plugin.message("bed-action-remove-lore", "Remove this bed from the saved list.")));
        inventory.setItem(ACTION_BACK_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to beds"), List.of()));
    }

    private static void renderTeleportTargetMenu(Inventory inventory, Player owner, BedMenuEntry entry,
            List<Player> targets, int page, int totalPages) {
        inventory.clear();
        fillBottomRow(inventory);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, targets.size());
        for (int slot = 0; slot + startIndex < endIndex; slot++) {
            inventory.setItem(slot, createTeleportTargetItem(targets.get(startIndex + slot)));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-previous", "Previous page"), List.of()));
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + plugin.message("admin-beds-target-prompt",
                "Choose a player to teleport to this bed."));
        lore.add(ChatColor.DARK_PURPLE + owner.getName() + ": " + entry.displayName());
        if (targets.isEmpty()) {
            lore.add(ChatColor.RED + plugin.message("admin-beds-target-empty",
                    "No other online players are available."));
        }
        inventory.setItem(INFO_SLOT, createControlItem(Material.PLAYER_HEAD,
                ChatColor.GOLD + plugin.message("admin-beds-target-page", "Teleport targets {1}/{2}")
                        .replace("{1}", Integer.toString(page + 1))
                        .replace("{2}", Integer.toString(totalPages)),
                lore));
        inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to beds"),
                List.of(ChatColor.GRAY + plugin.message("admin-beds-target-back-lore",
                        "Return to the selected bed actions."))));
        inventory.setItem(CLOSE_SLOT, createControlItem(Material.BARRIER,
                ChatColor.RED + plugin.message("manage-menu-close", "Close menu"),
                List.of(ChatColor.GRAY + plugin.message("manage-menu-close-lore", "Close the beds management menu."))));

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-next", "Next page"), List.of()));
        }
    }

    private static ItemStack createOwnerItem(Player owner) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.setDisplayName(ChatColor.GREEN + owner.getName());
        meta.setLore(List.of(
                ChatColor.GRAY + plugin.message("admin-beds-owner-count", "Beds saved: {1}")
                        .replace("{1}", Integer.toString(getSavedBedCount(owner))),
                ChatColor.YELLOW + plugin.message("admin-beds-owner-click",
                        "Click to manage this player's beds.")));
        meta.getPersistentDataContainer().set(PluginKeys.adminOwner(), PersistentDataType.STRING,
                owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createBedListItem(BedMenuEntry entry) {
        Material material = entry.status() == BedStatus.MISSING ? Material.BARRIER : entry.bedData().getBedMaterial();
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(switch (entry.status()) {
            case AVAILABLE -> ChatColor.GREEN + entry.displayName();
            case MISSING -> ChatColor.RED + entry.displayName();
        });
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(buildBedLore(entry));
        meta.getPersistentDataContainer().set(PluginKeys.adminBed(), PersistentDataType.STRING, entry.uuid());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createBedPreviewItem(Player owner, BedMenuEntry entry) {
        Material material = entry.status() == BedStatus.MISSING ? Material.BARRIER : entry.bedData().getBedMaterial();
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + entry.displayName());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + owner.getName());
        if (!plugin.getConfig().getBoolean("disable-bed-world-desc")) {
            lore.add(ChatColor.DARK_PURPLE + entry.bedData().getBedWorld().toUpperCase());
        }
        if (!plugin.getConfig().getBoolean("disable-bed-coords-desc")) {
            lore.add(ChatColor.GRAY + entry.bedData().formatCoords());
        }
        lore.add("");
        switch (entry.status()) {
            case AVAILABLE -> lore.add(ChatColor.GREEN + plugin.message("bed-action-ready", "Ready to manage."));
            case MISSING -> lore.add(ChatColor.RED
                    + plugin.message("respawn-menu-bed-missing", "This bed no longer exists."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createTeleportTargetItem(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName(ChatColor.GREEN + target.getName());
        meta.setLore(List.of(ChatColor.GRAY + plugin.message("admin-beds-target-click",
                "Click to teleport this player to the bed.")));
        meta.getPersistentDataContainer().set(PluginKeys.adminTarget(), PersistentDataType.STRING,
                target.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createActionItem(Material material, boolean enabled, String name, String loreLine) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.YELLOW : ChatColor.DARK_GRAY) + name);
        meta.setLore(List.of((enabled ? ChatColor.GRAY : ChatColor.RED) + loreLine));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createControlItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static boolean teleportPlayerToBed(Player admin, Player target, Player owner, BedMenuEntry entry) {
        Location teleportLocation = getSpawnLocation(entry.bedData());
        if (teleportLocation == null || teleportLocation.getWorld() == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("respawn-menu-bed-missing",
                    "This bed no longer exists."));
            return false;
        }

        if (!TeleportUtils.teleport(target, teleportLocation)) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-teleport-failed",
                    "Could not teleport the player to that bed."));
            return false;
        }

        if (target.getUniqueId().equals(admin.getUniqueId())) {
            admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-teleport-self-success",
                    "Teleported to {1}'s bed.").replace("{1}", owner.getName()));
        } else {
            admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-teleport-other-success",
                    "Teleported {1} to {2}'s bed.")
                    .replace("{1}", target.getName())
                    .replace("{2}", owner.getName()));
            target.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-teleport-target-message",
                    "An admin teleported you to {1}'s bed.").replace("{1}", owner.getName()));
        }

        return true;
    }

    private static Player getOnlineOwner(UUID ownerId) {
        return ownerId == null ? null : Bukkit.getPlayer(ownerId);
    }

    private static PlayerBedsData getPlayerBedsData(Player owner) {
        PersistentDataContainer ownerData = owner.getPersistentDataContainer();
        if (!ownerData.has(PluginKeys.beds(), PluginKeys.bedsDataType())) {
            return null;
        }
        return ownerData.get(PluginKeys.beds(), PluginKeys.bedsDataType());
    }

    private static int getSavedBedCount(Player owner) {
        PlayerBedsData playerBedsData = getPlayerBedsData(owner);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return 0;
        }
        return playerBedsData.getPlayerBedData().size();
    }

    private static List<Player> getOwnerCandidates() {
        List<Player> owners = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (getSavedBedCount(player) > 0) {
                owners.add(player);
            }
        });
        owners.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return owners;
    }

    private static List<Player> getTeleportTargets(Player admin) {
        List<Player> targets = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (!player.getUniqueId().equals(admin.getUniqueId())) {
                targets.add(player);
            }
        });
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    private static List<BedMenuEntry> getOwnerBedEntries(Player owner) {
        PlayerBedsData playerBedsData = getPlayerBedsData(owner);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return List.of();
        }

        HashMap<String, BedData> savedBeds = playerBedsData.getPlayerBedData();
        if (savedBeds.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<String, BedData>> sortedBeds = new ArrayList<>(savedBeds.entrySet());
        sortedBeds.sort(Comparator.comparing(entry -> entry.getValue().getSortKey(), String.CASE_INSENSITIVE_ORDER));

        List<BedMenuEntry> entries = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, BedData> entry : sortedBeds) {
            BedData bedData = entry.getValue();
            String displayName = bedData.hasCustomName()
                    ? bedData.getBedName()
                    : plugin.message("default-bed-name", "Bed {1}").replace("{1}", Integer.toString(index));
            BedStatus status = resolveStatus(entry.getKey(), bedData);
            entries.add(new BedMenuEntry(entry.getKey(), bedData, displayName, status));
            index++;
        }
        return entries;
    }

    private static BedMenuEntry getOwnerBedEntry(Player owner, String bedUuid) {
        for (BedMenuEntry entry : getOwnerBedEntries(owner)) {
            if (entry.uuid().equalsIgnoreCase(bedUuid)) {
                return entry;
            }
        }
        return null;
    }

    private static BedStatus resolveStatus(String uuid, BedData bedData) {
        Location bedLocation = bedData.getBedLocation();
        if (bedLocation == null || !isRegisteredBedPresent(bedLocation, uuid)) {
            return BedStatus.MISSING;
        }
        return BedStatus.AVAILABLE;
    }

    private static Location getSpawnLocation(BedData bedData) {
        return bedData.getSpawnLocation();
    }

    private static List<String> buildBedLore(BedMenuEntry entry) {
        List<String> lore = new ArrayList<>();
        boolean hasMetadata = false;
        if (!plugin.getConfig().getBoolean("disable-bed-world-desc")) {
            lore.add(ChatColor.DARK_PURPLE + entry.bedData().getBedWorld().toUpperCase());
            hasMetadata = true;
        }
        if (!plugin.getConfig().getBoolean("disable-bed-coords-desc")) {
            lore.add(ChatColor.GRAY + entry.bedData().formatCoords());
            hasMetadata = true;
        }
        if (hasMetadata) {
            lore.add("");
        }
        switch (entry.status()) {
            case AVAILABLE -> lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage",
                    "Click to manage this bed."));
            case MISSING -> lore.add(ChatColor.RED + plugin.message("respawn-menu-bed-missing",
                    "This bed no longer exists."));
        }
        return lore;
    }

    private static String getStringData(ItemStack clickedItem, String key) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return null;
        }
        return switch (key) {
            case "admin-owner" -> clickedItem.getItemMeta().getPersistentDataContainer()
                    .get(PluginKeys.adminOwner(), PersistentDataType.STRING);
            case "admin-bed" -> clickedItem.getItemMeta().getPersistentDataContainer()
                    .get(PluginKeys.adminBed(), PersistentDataType.STRING);
            case "admin-target" -> clickedItem.getItemMeta().getPersistentDataContainer()
                    .get(PluginKeys.adminTarget(), PersistentDataType.STRING);
            default -> null;
        };
    }

    private static UUID getUuidData(ItemStack clickedItem, String key) {
        String value = getStringData(clickedItem, key);
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void fillBottomRow(Inventory inventory) {
        for (int slot = PAGE_SIZE; slot < LIST_SIZE; slot++) {
            inventory.setItem(slot, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
        }
    }

    private static void fillInventory(Inventory inventory, Material material, String name) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, createFillerItem(material, name));
        }
    }

    private static ItemStack createFillerItem(Material material) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + " ");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createFillerItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private enum BedStatus {
        AVAILABLE,
        MISSING
    }

    private record BedMenuEntry(String uuid, BedData bedData, String displayName, BedStatus status) {
    }
}
