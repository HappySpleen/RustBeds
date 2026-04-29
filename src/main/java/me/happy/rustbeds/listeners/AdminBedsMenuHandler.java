package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.gui.AdminBedsMenuHolder;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.models.PlayerBedsData;
import me.happy.rustbeds.utils.PluginKeys;
import me.happy.rustbeds.utils.TeleportUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import java.util.Set;
import java.util.UUID;

import static me.happy.rustbeds.utils.BedsUtils.getRespawnAnchorCharges;
import static me.happy.rustbeds.utils.BedsUtils.getRespawnAnchorMaxCharges;
import static me.happy.rustbeds.utils.BedsUtils.isRegisteredRespawnPointPresent;
import static me.happy.rustbeds.utils.BedsUtils.removePlayerBed;
import static me.happy.rustbeds.utils.PlayerUtils.loadPlayerBedsData;
import static me.happy.rustbeds.utils.PlayerUtils.savePlayerBedsData;

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
    private static final int ACTION_GRANT_SLOT = 15;
    private static final int ACTION_REMOVE_SLOT = 16;
    private static final int ACTION_BACK_SLOT = 18;

    private static RustBeds plugin;

    public AdminBedsMenuHandler(RustBeds plugin) {
        AdminBedsMenuHandler.plugin = plugin;
    }

    public static void openOwnerMenu(Player admin, int requestedPage) {
        if (!plugin.hasAdminPermission(admin)) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-no-permission",
                    "You do not have permission to use this command."));
            admin.closeInventory();
            return;
        }

        List<OfflinePlayer> owners = getOwnerCandidates();
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
        OfflinePlayer owner = getOwner(ownerId);
        if (owner == null) {
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
        OfflinePlayer owner = getOwner(ownerId);
        if (owner == null) {
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
        OfflinePlayer owner = getOwner(ownerId);
        if (owner == null) {
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

    private static void openGrantTargetMenu(Player admin, UUID ownerId, String bedUuid, int returnPage,
            int requestedPage) {
        OfflinePlayer owner = getOwner(ownerId);
        if (owner == null) {
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

        List<OfflinePlayer> targets = getGrantTargets(ownerId, bedUuid);
        int totalPages = Math.max(1, (int) Math.ceil(targets.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        AdminBedsMenuHolder holder = new AdminBedsMenuHolder(admin.getUniqueId(),
                AdminBedsMenuHolder.ViewType.GRANT_TARGET_LIST, page, returnPage, ownerId, bedUuid);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE,
                plugin.message("admin-beds-give-title", "Give respawn point"));
        holder.setInventory(inventory);

        renderGrantTargetMenu(inventory, owner, entry, targets, page, totalPages);
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
            case GRANT_TARGET_LIST ->
                    handleGrantTargetMenuClick(admin, event.getSlot(), event.getCurrentItem(), holder);
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

        OfflinePlayer owner = getOwner(ownerId);
        if (owner == null) {
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
            case ACTION_GRANT_SLOT -> {
                if (entry.status() == BedStatus.MISSING) {
                    return;
                }

                openGrantTargetMenu(admin, ownerId, bedUuid, holder.getPage(), 0);
            }
            case ACTION_REMOVE_SLOT -> {
                removePlayerBed(bedUuid, owner);
                admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-remove-success",
                        "Removed {1}'s saved bed.").replace("{1}", getOwnerName(owner)));
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

                OfflinePlayer owner = getOwner(ownerId);
                Player target = Bukkit.getPlayer(targetId);
                if (owner == null) {
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

    private static void handleGrantTargetMenuClick(Player admin, int slot, ItemStack clickedItem,
            AdminBedsMenuHolder holder) {
        UUID ownerId = holder.getOwnerId();
        String bedUuid = holder.getBedUuid();
        if (ownerId == null || bedUuid == null) {
            openOwnerMenu(admin, 0);
            return;
        }

        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> openGrantTargetMenu(admin, ownerId, bedUuid, holder.getContextPage(),
                    holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT -> openActionMenu(admin, ownerId, holder.getContextPage(), bedUuid);
            case CLOSE_SLOT -> admin.closeInventory();
            case NEXT_PAGE_SLOT -> openGrantTargetMenu(admin, ownerId, bedUuid, holder.getContextPage(),
                    holder.getPage() + 1);
            default -> {
                UUID targetId = getUuidData(clickedItem, "admin-target");
                if (targetId == null) {
                    return;
                }

                OfflinePlayer owner = getOwner(ownerId);
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
                if (owner == null) {
                    openOwnerMenu(admin, 0);
                    return;
                }

                if (grantRespawnPointToPlayer(admin, target, owner, bedUuid)) {
                    if (plugin.getConfig().getBoolean("exclusive-bed")) {
                        openOwnerBedsMenu(admin, ownerId, holder.getContextPage());
                    } else {
                        openActionMenu(admin, ownerId, holder.getContextPage(), bedUuid);
                    }
                } else {
                    openGrantTargetMenu(admin, ownerId, bedUuid, holder.getContextPage(), holder.getPage());
                }
            }
        }
    }

    private static void renderOwnerMenu(Inventory inventory, List<OfflinePlayer> owners, int page, int totalPages) {
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
                    "No players have saved beds."));
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

    private static void renderOwnerBedsMenu(Inventory inventory, OfflinePlayer owner, List<BedMenuEntry> entries, int page,
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
                        .replace("{1}", formatOwnerNameInline(owner, ChatColor.GOLD))
                        .replace("{2}", Integer.toString(page + 1))
                        .replace("{3}", Integer.toString(totalPages)),
                List.of(
                        ChatColor.GRAY + plugin.message("admin-beds-owner-prompt",
                                "Click a bed to rename, remove, or teleport to it."),
                        formatOwnerLoreName(owner))));
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

    private static void renderActionMenu(Inventory inventory, OfflinePlayer owner, BedMenuEntry entry) {
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
        inventory.setItem(ACTION_GRANT_SLOT, createActionItem(
                entry.status() != BedStatus.MISSING ? Material.CHEST : Material.GRAY_DYE,
                entry.status() != BedStatus.MISSING,
                plugin.message("admin-beds-give", "Give point"),
                plugin.message("admin-beds-give-lore", "Choose a player to receive this respawn point.")));
        inventory.setItem(ACTION_REMOVE_SLOT, createActionItem(Material.BARRIER, true,
                plugin.message("bed-action-remove", "Remove bed"),
                plugin.message("bed-action-remove-lore", "Remove this bed from the saved list.")));
        inventory.setItem(ACTION_BACK_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to beds"), List.of()));
    }

    private static void renderTeleportTargetMenu(Inventory inventory, OfflinePlayer owner, BedMenuEntry entry,
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
        lore.add(formatOwnerBedLabel(owner, entry));
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

    private static void renderGrantTargetMenu(Inventory inventory, OfflinePlayer owner, BedMenuEntry entry,
            List<OfflinePlayer> targets, int page, int totalPages) {
        inventory.clear();
        fillBottomRow(inventory);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, targets.size());
        for (int slot = 0; slot + startIndex < endIndex; slot++) {
            inventory.setItem(slot, createGrantTargetItem(targets.get(startIndex + slot)));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-previous", "Previous page"), List.of()));
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + plugin.message("admin-beds-give-prompt",
                "Choose a player to receive this respawn point."));
        lore.add(formatOwnerBedLabel(owner, entry));
        if (targets.isEmpty()) {
            lore.add(ChatColor.RED + plugin.message("admin-beds-give-empty",
                    "No known players can receive this respawn point."));
        }
        inventory.setItem(INFO_SLOT, createControlItem(Material.CHEST,
                ChatColor.GOLD + plugin.message("admin-beds-give-page", "Recipients {1}/{2}")
                        .replace("{1}", Integer.toString(page + 1))
                        .replace("{2}", Integer.toString(totalPages)),
                lore));
        inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to beds"),
                List.of(ChatColor.GRAY + plugin.message("admin-beds-give-back-lore",
                        "Return to the selected respawn point actions."))));
        inventory.setItem(CLOSE_SLOT, createControlItem(Material.BARRIER,
                ChatColor.RED + plugin.message("manage-menu-close", "Close menu"),
                List.of(ChatColor.GRAY + plugin.message("manage-menu-close-lore", "Close the beds management menu."))));

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-next", "Next page"), List.of()));
        }
    }

    private static ItemStack createOwnerItem(OfflinePlayer owner) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.setDisplayName((owner.isOnline() ? ChatColor.GREEN : ChatColor.RED) + getOwnerName(owner));
        hideMenuTooltipDetails(meta);
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
        Material material = entry.status() == BedStatus.AVAILABLE ? entry.bedData().getBedMaterial() : Material.BARRIER;
        ItemStack item = new ItemStack(material, getDisplayAmount(entry));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(switch (entry.status()) {
            case AVAILABLE -> ChatColor.GREEN + entry.displayName();
            case DEPLETED, MISSING -> ChatColor.RED + entry.displayName();
        });
        hideMenuTooltipDetails(meta);
        meta.setLore(buildBedLore(entry));
        meta.getPersistentDataContainer().set(PluginKeys.adminBed(), PersistentDataType.STRING, entry.uuid());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createBedPreviewItem(OfflinePlayer owner, BedMenuEntry entry) {
        Material material = entry.status() == BedStatus.AVAILABLE ? entry.bedData().getBedMaterial() : Material.BARRIER;
        ItemStack item = new ItemStack(material, getDisplayAmount(entry));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + entry.displayName());
        hideMenuTooltipDetails(meta);

        List<String> lore = new ArrayList<>();
        lore.add(formatOwnerLoreName(owner));
        appendSharedByLore(lore, entry);
        if (!plugin.getConfig().getBoolean("disable-bed-world-desc")) {
            lore.add(ChatColor.DARK_PURPLE + entry.bedData().getBedWorld().toUpperCase());
        }
        if (!plugin.getConfig().getBoolean("disable-bed-coords-desc")) {
            lore.add(ChatColor.GRAY + entry.bedData().formatCoords());
        }
        if (entry.bedData().isRespawnAnchor()) {
            lore.add(ChatColor.GOLD + plugin.message("respawn-anchor-charges", "Charges: {1}/{2}")
                    .replace("{1}", Integer.toString(entry.currentCharges()))
                    .replace("{2}", Integer.toString(entry.maxCharges())));
        }
        lore.add("");
        switch (entry.status()) {
            case AVAILABLE -> lore.add(ChatColor.GREEN + plugin.message("bed-action-ready", "Ready to manage."));
            case DEPLETED -> lore.add(ChatColor.RED + plugin.message("respawn-anchor-depleted",
                    "This respawn anchor has no charges."));
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
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of(ChatColor.GRAY + plugin.message("admin-beds-target-click",
                "Click to teleport this player to the bed.")));
        meta.getPersistentDataContainer().set(PluginKeys.adminTarget(), PersistentDataType.STRING,
                target.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createGrantTargetItem(OfflinePlayer target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName((target.isOnline() ? ChatColor.GREEN : ChatColor.RED) + getOwnerName(target));
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of(ChatColor.GRAY + plugin.message("admin-beds-give-target-click",
                "Click to give this respawn point to the player.")));
        meta.getPersistentDataContainer().set(PluginKeys.adminTarget(), PersistentDataType.STRING,
                target.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createActionItem(Material material, boolean enabled, String name, String loreLine) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.YELLOW : ChatColor.DARK_GRAY) + name);
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of((enabled ? ChatColor.GRAY : ChatColor.RED) + loreLine));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createControlItem(Material material, String name, List<String> lore) {
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

    private static boolean teleportPlayerToBed(Player admin, Player target, OfflinePlayer owner, BedMenuEntry entry) {
        String ownerName = getOwnerName(owner);
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
                    "Teleported to {1}'s bed.").replace("{1}", ownerName));
        } else {
            admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-teleport-other-success",
                    "Teleported {1} to {2}'s bed.")
                    .replace("{1}", target.getName())
                    .replace("{2}", ownerName));
            target.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-teleport-target-message",
                    "An admin teleported you to {1}'s bed.").replace("{1}", ownerName));
        }

        return true;
    }

    private static boolean grantRespawnPointToPlayer(Player admin, OfflinePlayer target, OfflinePlayer owner,
            String bedUuid) {
        String ownerName = getOwnerName(owner);
        String targetName = getOwnerName(target);
        PlayerBedsData ownerBedsData = getPlayerBedsData(owner);
        if (ownerBedsData == null || ownerBedsData.getPlayerBedData() == null || !ownerBedsData.hasBed(bedUuid)) {
            admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "That player no longer has that bed saved."));
            return false;
        }

        PlayerBedsData targetBedsData = getPlayerBedsData(target);
        if (targetBedsData == null) {
            targetBedsData = new PlayerBedsData();
        }
        if (targetBedsData.hasBed(bedUuid)) {
            admin.sendMessage(ChatColor.RED + plugin.message("admin-beds-give-already-has",
                    "{1} already has this respawn point.").replace("{1}", targetName));
            return false;
        }

        boolean transferOwnership = plugin.getConfig().getBoolean("exclusive-bed");
        if (!ownerBedsData.grantBed(targetBedsData, bedUuid, transferOwnership)) {
            admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "That player no longer has that bed saved."));
            return false;
        }

        savePlayerBedsData(target, targetBedsData);
        if (transferOwnership) {
            savePlayerBedsData(owner, ownerBedsData);
        }
        admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-give-success",
                "Gave {1}'s respawn point to {2}.")
                .replace("{1}", ownerName)
                .replace("{2}", targetName));

        String targetMessage = ChatColor.YELLOW + plugin.message("admin-beds-give-received",
                "An admin gave you {1}'s respawn point.").replace("{1}", ownerName);
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.sendMessage(targetMessage);
        } else {
            plugin.getPlayerBedStore().queuePendingMessage(target.getUniqueId(), targetMessage);
        }
        return true;
    }

    private static OfflinePlayer getOwner(UUID ownerId) {
        return ownerId == null ? null : Bukkit.getOfflinePlayer(ownerId);
    }

    private static PlayerBedsData getPlayerBedsData(OfflinePlayer owner) {
        return loadPlayerBedsData(owner);
    }

    private static int getSavedBedCount(OfflinePlayer owner) {
        PlayerBedsData playerBedsData = getPlayerBedsData(owner);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return 0;
        }
        return playerBedsData.getPlayerBedData().size();
    }

    private static List<OfflinePlayer> getOwnerCandidates() {
        List<OfflinePlayer> owners = new ArrayList<>();
        for (UUID ownerId : plugin.getPlayerBedStore().getPlayersWithBeds()) {
            owners.add(Bukkit.getOfflinePlayer(ownerId));
        }
        owners.sort(Comparator.comparing(AdminBedsMenuHandler::getOwnerSortName, String.CASE_INSENSITIVE_ORDER));
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

    private static List<OfflinePlayer> getGrantTargets(UUID ownerId, String bedUuid) {
        Map<UUID, OfflinePlayer> knownPlayers = new HashMap<>();
        Bukkit.getOnlinePlayers().forEach(player -> knownPlayers.put(player.getUniqueId(), player));
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player != null) {
                knownPlayers.put(player.getUniqueId(), player);
            }
        }

        Set<UUID> existingOwners = plugin.getPlayerBedStore().getOwners(bedUuid);
        List<OfflinePlayer> targets = new ArrayList<>();
        for (OfflinePlayer player : knownPlayers.values()) {
            if (player == null || player.getUniqueId().equals(ownerId) || existingOwners.contains(player.getUniqueId())) {
                continue;
            }

            targets.add(player);
        }
        targets.sort(Comparator.comparing(AdminBedsMenuHandler::getOwnerSortName, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    private static List<BedMenuEntry> getOwnerBedEntries(OfflinePlayer owner) {
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
                    : plugin.message(bedData.isRespawnAnchor() ? "default-anchor-name" : "default-bed-name",
                            bedData.isRespawnAnchor() ? "Respawn Anchor {1}" : "Bed {1}")
                    .replace("{1}", Integer.toString(index));
            BedStatus status = resolveStatus(entry.getKey(), bedData);
            int currentCharges = bedData.isRespawnAnchor() ? getRespawnAnchorCharges(bedData) : 0;
            int maxCharges = bedData.isRespawnAnchor() ? getRespawnAnchorMaxCharges(bedData) : 0;
            entries.add(new BedMenuEntry(entry.getKey(), bedData, displayName, status, currentCharges, maxCharges));
            index++;
        }
        return entries;
    }

    private static BedMenuEntry getOwnerBedEntry(OfflinePlayer owner, String bedUuid) {
        for (BedMenuEntry entry : getOwnerBedEntries(owner)) {
            if (entry.uuid().equalsIgnoreCase(bedUuid)) {
                return entry;
            }
        }
        return null;
    }

    private static BedStatus resolveStatus(String uuid, BedData bedData) {
        if (!isRegisteredRespawnPointPresent(bedData, uuid)) {
            return BedStatus.MISSING;
        }
        if (bedData.isRespawnAnchor() && getRespawnAnchorCharges(bedData) <= 0) {
            return BedStatus.DEPLETED;
        }
        return BedStatus.AVAILABLE;
    }

    private static Location getSpawnLocation(BedData bedData) {
        return bedData.getSpawnLocation();
    }

    private static List<String> buildBedLore(BedMenuEntry entry) {
        List<String> lore = new ArrayList<>();
        boolean hasMetadata = false;
        if (entry.bedData().hasSharedByName()) {
            lore.add(ChatColor.BLUE + plugin.message("bed-shared-by-label", "Shared By: {1}")
                    .replace("{1}", entry.bedData().getSharedByName()));
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
            lore.add(ChatColor.GOLD + plugin.message("respawn-anchor-charges", "Charges: {1}/{2}")
                    .replace("{1}", Integer.toString(entry.currentCharges()))
                    .replace("{2}", Integer.toString(entry.maxCharges())));
            hasMetadata = true;
        }
        if (hasMetadata) {
            lore.add("");
        }
        switch (entry.status()) {
            case AVAILABLE -> lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage",
                    "Click to manage this bed."));
            case DEPLETED -> {
                lore.add(ChatColor.RED + plugin.message("respawn-anchor-depleted",
                        "This respawn anchor has no charges."));
                lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage", "Click to manage this bed."));
            }
            case MISSING -> lore.add(ChatColor.RED + plugin.message("respawn-menu-bed-missing",
                    "This bed no longer exists."));
        }
        return lore;
    }

    private static int getDisplayAmount(BedMenuEntry entry) {
        if (entry.bedData().isRespawnAnchor() && entry.status() == BedStatus.AVAILABLE) {
            return Math.max(1, Math.min(64, entry.currentCharges()));
        }

        return 1;
    }

    private static String getOwnerName(OfflinePlayer owner) {
        if (owner == null) {
            return "Unknown player";
        }

        Player onlineOwner = owner.getPlayer();
        if (onlineOwner != null && onlineOwner.getName() != null && !onlineOwner.getName().isBlank()) {
            return onlineOwner.getName();
        }

        String ownerName = owner.getName();
        if (ownerName != null && !ownerName.isBlank()) {
            return ownerName;
        }

        return owner.getUniqueId().toString();
    }

    private static String getOwnerSortName(OfflinePlayer owner) {
        return getOwnerName(owner);
    }

    private static String formatOwnerNameInline(OfflinePlayer owner, ChatColor surroundingColor) {
        String ownerName = getOwnerName(owner);
        if (owner != null && !owner.isOnline()) {
            return ChatColor.RED + ownerName + surroundingColor;
        }

        return ownerName;
    }

    private static String formatOwnerLoreName(OfflinePlayer owner) {
        return (owner != null && !owner.isOnline() ? ChatColor.RED : ChatColor.DARK_PURPLE) + getOwnerName(owner);
    }

    private static String formatOwnerBedLabel(OfflinePlayer owner, BedMenuEntry entry) {
        return formatOwnerLoreName(owner) + ChatColor.GRAY + ": " + ChatColor.YELLOW + entry.displayName();
    }

    private static void appendSharedByLore(List<String> lore, BedMenuEntry entry) {
        if (!entry.bedData().hasSharedByName()) {
            return;
        }

        lore.add(ChatColor.BLUE + plugin.message("bed-shared-by-label", "Shared By: {1}")
                .replace("{1}", entry.bedData().getSharedByName()));
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
        hideMenuTooltipDetails(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createFillerItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        hideMenuTooltipDetails(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static void hideMenuTooltipDetails(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    private enum BedStatus {
        AVAILABLE,
        DEPLETED,
        MISSING
    }

    private record BedMenuEntry(String uuid, BedData bedData, String displayName, BedStatus status,
                                int currentCharges, int maxCharges) {
    }
}
