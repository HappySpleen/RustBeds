package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.gui.BedMenuEntry;
import me.happy.rustbeds.gui.BedStatus;
import me.happy.rustbeds.gui.RespawnMenuHolder;
import me.happy.rustbeds.gui.RespawnPointMenuLore;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.models.PlayerBedsData;
import me.happy.rustbeds.models.ShareInvite;
import me.happy.rustbeds.utils.PlayerUtils;
import me.happy.rustbeds.utils.PluginKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
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

import static me.happy.rustbeds.gui.MenuItems.createActionItem;
import static me.happy.rustbeds.gui.MenuItems.createControlItem;
import static me.happy.rustbeds.gui.MenuItems.fillBottomRow;
import static me.happy.rustbeds.gui.MenuItems.fillInventory;
import static me.happy.rustbeds.gui.MenuItems.hideMenuTooltipDetails;
import static me.happy.rustbeds.utils.BedsUtils.consumeRespawnAnchorCharge;
import static me.happy.rustbeds.utils.BedsUtils.getRespawnAnchorCharges;
import static me.happy.rustbeds.utils.BedsUtils.isRegisteredRespawnPointPresent;
import static me.happy.rustbeds.utils.BedsUtils.removePlayerBed;
import static me.happy.rustbeds.utils.PlayerUtils.getPlayerRespawnLoc;
import static me.happy.rustbeds.utils.PlayerUtils.getOfflinePlayerName;
import static me.happy.rustbeds.utils.PlayerUtils.loadPlayerBedsData;
import static me.happy.rustbeds.utils.PlayerUtils.savePlayerBedsData;
import static me.happy.rustbeds.utils.PlayerUtils.setPropPlayer;
import static me.happy.rustbeds.utils.PlayerUtils.undoPropPlayer;
import static me.happy.rustbeds.utils.RespawnSafetyUtils.findSafeRespawnLocation;
import static me.happy.rustbeds.utils.RespawnSafetyUtils.hasSafeRespawnLocation;
import static me.happy.rustbeds.utils.RunCommandUtils.runCommandOnSpawn;
import static me.happy.rustbeds.utils.TeleportUtils.teleport;

public class RespawnMenuHandler implements Listener {
    public static final String PENDING_REQUESTS_COMMAND = "/beds requests";

    private static final int LIST_SIZE = 54;
    private static final int ACTION_SIZE = 27;
    private static final int PAGE_SIZE = 45;

    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int INFO_SLOT = 47;
    private static final int PRIMARY_ACTION_SLOT = 49;
    private static final int CLOSE_SLOT = 51;
    private static final int NEXT_PAGE_SLOT = 53;

    private static final int ACTION_RENAME_SLOT = 10;
    private static final int ACTION_PRIMARY_SLOT = 12;
    private static final int ACTION_PREVIEW_SLOT = 13;
    private static final int ACTION_SHARE_SLOT = 16;
    private static final int ACTION_BACK_SLOT = 18;
    private static final int ACTION_REMOVE_SLOT = 26;

    private static final int INVITE_ACCEPT_SLOT = 11;
    private static final int INVITE_PREVIEW_SLOT = 13;
    private static final int INVITE_DENY_SLOT = 15;
    private static final int INVITE_BACK_SLOT = 18;

    private static final Map<UUID, BedMenuSession> ACTIVE_SESSIONS = new HashMap<>();

    private static RustBeds plugin;

    public RespawnMenuHandler(RustBeds plugin) {
        RespawnMenuHandler.plugin = plugin;
    }

    public static void openCommandMenu(Player player) {
        if (isRespawnProtected(player)) {
            beginRespawnMenu(player, 0L);
            return;
        }

        openManageMenu(player, 0);
    }

    public static void openPendingRequestsMenu(Player player) {
        showShareInviteListMenu(player, 0);
    }

    public static void beginRespawnMenu(Player player, long openDelayTicks) {
        UUID playerId = player.getUniqueId();
        BedMenuSession existingSession = ACTIVE_SESSIONS.get(playerId);
        if (existingSession != null && existingSession.getMode() == SessionMode.RESPAWN) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> showListMenu(player, existingSession.getListPage()),
                    Math.max(0L, openDelayTicks));
            return;
        }

        cancelSession(playerId);

        BedMenuSession session = new BedMenuSession(SessionMode.RESPAWN);
        ACTIVE_SESSIONS.put(playerId, session);
        scheduleRespawnTimeout(player, session);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ACTIVE_SESSIONS.get(playerId) != session) {
                return;
            }

            List<BedMenuEntry> entries = getMenuEntries(player);
            if (entries.isEmpty()) {
                sendPlayerToDefaultRespawn(player, false);
                return;
            }

            setPropPlayer(player);
            player.sendActionBar(Component.text(stripColors(plugin.message("respawn-menu-prompt", "Choose a respawn bed")))
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
            showListMenu(player, 0);
        }, Math.max(0L, openDelayTicks));
    }

    public static void openManageMenu(Player player, int page) {
        UUID playerId = player.getUniqueId();
        BedMenuSession session = ACTIVE_SESSIONS.get(playerId);
        if (session == null || session.getMode() != SessionMode.MANAGE) {
            cancelSession(playerId);
            session = new BedMenuSession(SessionMode.MANAGE);
            ACTIVE_SESSIONS.put(playerId, session);
        }

        showListMenu(player, page);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RespawnMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (!holder.getPlayerId().equals(player.getUniqueId())) {
            return;
        }

        if (!ACTIVE_SESSIONS.containsKey(player.getUniqueId())) {
            return;
        }

        switch (holder.getViewType()) {
            case RESPAWN_LIST -> handleRespawnListClick(player, event.getSlot(), event.getCurrentItem(), holder);
            case MANAGE_LIST -> handleManageListClick(player, event.getSlot(), event.getCurrentItem(), holder);
            case ACTIONS -> handleActionMenuClick(player, event.getSlot(), holder);
            case SHARE_LIST -> handleShareMenuClick(player, event.getSlot(), event.getCurrentItem(), holder);
            case SHARE_INVITE_LIST -> handleShareInviteListClick(player, event.getSlot(), event.getCurrentItem(), holder);
            case SHARE_INVITE_ACTIONS -> handleShareInviteActionClick(player, event.getSlot(), holder);
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RespawnMenuHolder holder)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (!holder.getPlayerId().equals(player.getUniqueId())) {
            return;
        }

        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.consumeSuppressedClose()) {
            return;
        }

        if (session.getMode() == SessionMode.RESPAWN) {
            Bukkit.getScheduler().runTask(plugin, () -> sendPlayerToDefaultRespawn(player, false));
            return;
        }

        cancelSession(player.getUniqueId());
    }

    private static void handleRespawnListClick(Player player, int slot, ItemStack clickedItem, RespawnMenuHolder holder) {
        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> showListMenu(player, holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT, CLOSE_SLOT -> sendPlayerToDefaultRespawn(player, false);
            case NEXT_PAGE_SLOT -> showListMenu(player, holder.getPage() + 1);
            default -> {
                if (clickedItem == null || !clickedItem.hasItemMeta()) {
                    return;
                }

                ItemMeta meta = clickedItem.getItemMeta();
                PersistentDataContainer data = meta.getPersistentDataContainer();
                String uuid = data.get(PluginKeys.uuid(), PersistentDataType.STRING);
                if (uuid == null || data.get(PluginKeys.menuStatus(), PersistentDataType.STRING) == null) {
                    return;
                }

                PlayerBedsData playerBedsData = getPlayerBedsData(player);
                if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
                    sendPlayerToDefaultRespawn(player, false);
                    return;
                }

                BedData bedData = playerBedsData.getPlayerBedData().get(uuid);
                if (bedData == null || !isRegisteredRespawnPointPresent(bedData, uuid)) {
                    showListMenu(player, holder.getPage());
                    return;
                }

                BedStatus currentStatus = resolveStatus(player, uuid, bedData);
                if (currentStatus == BedStatus.OBSTRUCTED) {
                    sendObstructedMessage(player);
                    showListMenu(player, holder.getPage());
                    return;
                }
                if (currentStatus != BedStatus.AVAILABLE) {
                    showListMenu(player, holder.getPage());
                    return;
                }

                teleportToSavedBed(player, playerBedsData, uuid);
            }
        }
    }

    private static void handleManageListClick(Player player, int slot, ItemStack clickedItem, RespawnMenuHolder holder) {
        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> showListMenu(player, holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT -> {
                cancelSession(player.getUniqueId());
                player.closeInventory();
            }
            case CLOSE_SLOT -> {
                if (!getPendingShareInvites(player).isEmpty()) {
                    showShareInviteListMenu(player, 0);
                }
            }
            case NEXT_PAGE_SLOT -> showListMenu(player, holder.getPage() + 1);
            default -> {
                if (clickedItem == null || !clickedItem.hasItemMeta()) {
                    return;
                }

                String uuid = clickedItem.getItemMeta().getPersistentDataContainer()
                        .get(PluginKeys.uuid(), PersistentDataType.STRING);
                if (uuid == null) {
                    return;
                }

                showManageActionMenu(player, holder.getPage(), uuid);
            }
        }
    }

    private static void handleActionMenuClick(Player player, int slot, RespawnMenuHolder holder) {
        String bedUuid = holder.getBedUuid();
        if (bedUuid == null) {
            openManageMenu(player, holder.getPage());
            return;
        }

        BedMenuEntry entry = getMenuEntry(player, bedUuid);
        if (entry == null) {
            openManageMenu(player, holder.getPage());
            return;
        }

        switch (slot) {
            case ACTION_BACK_SLOT -> openManageMenu(player, holder.getPage());
            case ACTION_RENAME_SLOT -> {
                if (entry.status() == BedStatus.MISSING) {
                    return;
                }

                cancelSession(player.getUniqueId());
                BedMenuInputListener.beginRenamePrompt(player, bedUuid, holder.getPage(), entry.displayName());
            }
            case ACTION_PRIMARY_SLOT -> {
                if (entry.status() == BedStatus.MISSING
                        || entry.status() == BedStatus.DISABLED
                        || entry.bedData().isPrimary()) {
                    return;
                }

                if (!setPrimaryBed(player, bedUuid)) {
                    openManageMenu(player, holder.getPage());
                    return;
                }

                player.sendMessage(ChatColor.YELLOW + plugin.message("bed-primary-set-successfully-message",
                        "Primary bed set successfully!"));
                showManageActionMenu(player, holder.getPage(), bedUuid);
            }
            case ACTION_SHARE_SLOT -> {
                if (!plugin.getConfig().getBoolean("bed-sharing") || entry.status() == BedStatus.MISSING) {
                    return;
                }

                showShareMenu(player, bedUuid, 0);
            }
            case ACTION_REMOVE_SLOT -> {
                removePlayerBed(bedUuid, player);
                player.sendMessage(ChatColor.YELLOW
                        + plugin.message("bed-removed-successfully-message", "Bed removed successfully!"));
                openManageMenu(player, holder.getPage());
            }
            default -> {
            }
        }
    }

    private static void handleShareMenuClick(Player player, int slot, ItemStack clickedItem, RespawnMenuHolder holder) {
        String bedUuid = holder.getBedUuid();
        if (bedUuid == null) {
            openManageMenu(player, getListPage(player));
            return;
        }

        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> showShareMenu(player, bedUuid, holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT -> showManageActionMenu(player, getListPage(player), bedUuid);
            case NEXT_PAGE_SLOT -> showShareMenu(player, bedUuid, holder.getPage() + 1);
            default -> {
                if (clickedItem == null || !clickedItem.hasItemMeta()) {
                    return;
                }

                String targetIdValue = clickedItem.getItemMeta().getPersistentDataContainer()
                        .get(PluginKeys.sharePlayer(), PersistentDataType.STRING);
                UUID targetId = parseUuid(targetIdValue);
                if (targetId == null) {
                    return;
                }

                OfflinePlayer receiver = Bukkit.getOfflinePlayer(targetId);
                if (receiver.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + plugin.message("player-not-found", "Player not found!"));
                    showShareMenu(player, bedUuid, holder.getPage());
                    return;
                }

                if (sendShareInvite(player, receiver, bedUuid)) {
                    openManageMenu(player, getListPage(player));
                } else {
                    showManageActionMenu(player, getListPage(player), bedUuid);
                }
            }
        }
    }

    private static void handleShareInviteListClick(Player player, int slot, ItemStack clickedItem,
            RespawnMenuHolder holder) {
        switch (slot) {
            case PREVIOUS_PAGE_SLOT -> showShareInviteListMenu(player, holder.getPage() - 1);
            case PRIMARY_ACTION_SLOT -> openManageMenu(player, getListPage(player));
            case NEXT_PAGE_SLOT -> showShareInviteListMenu(player, holder.getPage() + 1);
            default -> {
                if (clickedItem == null || !clickedItem.hasItemMeta()) {
                    return;
                }

                String inviteIdValue = clickedItem.getItemMeta().getPersistentDataContainer()
                        .get(PluginKeys.shareInvite(), PersistentDataType.STRING);
                UUID inviteId = parseUuid(inviteIdValue);
                if (inviteId == null) {
                    return;
                }

                showShareInviteActionMenu(player, holder.getPage(), inviteId);
            }
        }
    }

    private static void handleShareInviteActionClick(Player player, int slot, RespawnMenuHolder holder) {
        UUID inviteId = parseUuid(holder.getBedUuid());
        if (inviteId == null) {
            showShareInviteListMenu(player, holder.getPage());
            return;
        }

        ShareInvite invite = plugin.getPlayerBedStore().getShareInvite(inviteId);
        switch (slot) {
            case INVITE_BACK_SLOT -> showShareInviteListMenu(player, holder.getPage());
            case INVITE_ACCEPT_SLOT -> {
                if (!isUsableInvite(player, invite)) {
                    showShareInviteListMenu(player, holder.getPage());
                    return;
                }

                if (applyShareInvite(player, invite)) {
                    plugin.getPlayerBedStore().deleteShareInvite(invite.inviteId());
                }
                showShareInviteListMenu(player, holder.getPage());
            }
            case INVITE_DENY_SLOT -> {
                if (!isUsableInvite(player, invite)) {
                    showShareInviteListMenu(player, holder.getPage());
                    return;
                }

                denyShareInvite(player, invite);
                showShareInviteListMenu(player, holder.getPage());
            }
            default -> {
            }
        }
    }

    private static void showListMenu(Player player, int requestedPage) {
        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        List<BedMenuEntry> entries = getMenuEntries(player);
        if (entries.isEmpty()) {
            handleEmptyMenu(player, session.getMode());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        RespawnMenuHolder.ViewType viewType = session.getMode() == SessionMode.RESPAWN
                ? RespawnMenuHolder.ViewType.RESPAWN_LIST
                : RespawnMenuHolder.ViewType.MANAGE_LIST;

        prepareInventorySwap(player, session);

        RespawnMenuHolder holder = new RespawnMenuHolder(player.getUniqueId(), viewType, page, null);
        String title = session.getMode() == SessionMode.RESPAWN
                ? plugin.message("menu-title", "Beds")
                : plugin.message("manage-menu-title", "Manage beds");
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE, title);
        holder.setInventory(inventory);

        renderListMenu(inventory, entries, page, totalPages, session.getMode());
        session.setListPage(page);
        syncRefreshTask(player, session, hasDynamicEntries(entries));
        player.openInventory(inventory);
    }

    private static void showManageActionMenu(Player player, int listPage, String bedUuid) {
        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        if (session == null || session.getMode() != SessionMode.MANAGE) {
            openManageMenu(player, listPage);
            return;
        }

        BedMenuEntry entry = getMenuEntry(player, bedUuid);
        if (entry == null) {
            openManageMenu(player, listPage);
            return;
        }

        prepareInventorySwap(player, session);
        session.setListPage(listPage);
        session.cancelRefreshTask();

        RespawnMenuHolder holder = new RespawnMenuHolder(player.getUniqueId(), RespawnMenuHolder.ViewType.ACTIONS,
                listPage, bedUuid);
        Inventory inventory = Bukkit.createInventory(holder, ACTION_SIZE, plugin.message("bed-actions-title", "Manage bed"));
        holder.setInventory(inventory);

        renderActionMenu(inventory, entry);
        player.openInventory(inventory);
    }

    private static void showShareMenu(Player player, String bedUuid, int requestedPage) {
        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        if (session == null || session.getMode() != SessionMode.MANAGE) {
            openManageMenu(player, 0);
            return;
        }

        BedMenuEntry entry = getMenuEntry(player, bedUuid);
        if (entry == null) {
            openManageMenu(player, session.getListPage());
            return;
        }

        List<OfflinePlayer> candidates = getShareCandidates(player, bedUuid);
        int totalPages = Math.max(1, (int) Math.ceil(candidates.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        prepareInventorySwap(player, session);
        session.cancelRefreshTask();

        RespawnMenuHolder holder = new RespawnMenuHolder(player.getUniqueId(), RespawnMenuHolder.ViewType.SHARE_LIST,
                page, bedUuid);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE,
                sharingModeMessage("bed-share-title", "Share bed", "bed-transfer-title", "Transfer respawn"));
        holder.setInventory(inventory);

        renderShareMenu(inventory, candidates, page, totalPages);
        player.openInventory(inventory);
    }

    private static void showShareInviteListMenu(Player player, int requestedPage) {
        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        if (session == null || session.getMode() != SessionMode.MANAGE) {
            cancelSession(player.getUniqueId());
            session = new BedMenuSession(SessionMode.MANAGE);
            ACTIVE_SESSIONS.put(player.getUniqueId(), session);
        }

        List<ShareInvite> invites = new ArrayList<>(getPendingShareInvites(player));
        invites.sort(shareInviteSenderComparator());
        if (invites.isEmpty()) {
            if (getMenuEntries(player).isEmpty()) {
                cancelSession(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + sharingModeMessage("bed-share-invites-empty",
                        "You have no pending share requests.", "bed-transfer-invites-empty",
                        "You have no pending transfer requests."));
                return;
            }

            openManageMenu(player, getListPage(player));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(invites.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        prepareInventorySwap(player, session);
        session.cancelRefreshTask();

        RespawnMenuHolder holder = new RespawnMenuHolder(player.getUniqueId(),
                RespawnMenuHolder.ViewType.SHARE_INVITE_LIST, page, null);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE,
                sharingModeMessage("bed-share-invites-title", "Share requests", "bed-transfer-invites-title",
                        "Transfer requests"));
        holder.setInventory(inventory);

        renderShareInviteListMenu(inventory, invites, page, totalPages);
        player.openInventory(inventory);
    }

    private static void showShareInviteActionMenu(Player player, int listPage, UUID inviteId) {
        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        if (session == null || session.getMode() != SessionMode.MANAGE) {
            openManageMenu(player, 0);
            return;
        }

        ShareInvite invite = plugin.getPlayerBedStore().getShareInvite(inviteId);
        if (!isUsableInvite(player, invite)) {
            showShareInviteListMenu(player, listPage);
            return;
        }

        prepareInventorySwap(player, session);
        session.cancelRefreshTask();

        RespawnMenuHolder holder = new RespawnMenuHolder(player.getUniqueId(),
                RespawnMenuHolder.ViewType.SHARE_INVITE_ACTIONS, listPage, invite.inviteId().toString());
        Inventory inventory = Bukkit.createInventory(holder, ACTION_SIZE,
                inviteModeMessage(invite, "bed-share-invite-actions-title", "Share request",
                        "bed-transfer-invite-actions-title", "Transfer request"));
        holder.setInventory(inventory);

        renderShareInviteActionMenu(inventory, invite);
        player.openInventory(inventory);
    }

    private static void renderListMenu(Inventory inventory, List<BedMenuEntry> entries, int page, int totalPages,
            SessionMode mode) {
        inventory.clear();
        fillBottomRow(inventory);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, entries.size());
        for (int slot = 0; slot + startIndex < endIndex; slot++) {
            inventory.setItem(slot, createBedListItem(entries.get(startIndex + slot), mode));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-previous", "Previous page"), List.of()));
        }

        if (mode == SessionMode.RESPAWN) {
            inventory.setItem(INFO_SLOT, createControlItem(Material.COMPASS,
                    ChatColor.GOLD + plugin.message("respawn-menu-page", "Page {1}/{2}")
                            .replace("{1}", Integer.toString(page + 1))
                            .replace("{2}", Integer.toString(totalPages)),
                    List.of(ChatColor.GRAY + plugin.message("respawn-menu-prompt", "Choose a respawn bed"))));
            inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.GRASS_BLOCK,
                    ChatColor.YELLOW + plugin.message("respawn-menu-spawn", "Respawn at spawn"),
                    List.of(ChatColor.GRAY + plugin.message("respawn-menu-spawn-lore",
                            "Leave this menu and use the normal respawn point."))));
            inventory.setItem(CLOSE_SLOT, createControlItem(Material.BARRIER,
                    ChatColor.RED + plugin.message("respawn-menu-close", "Close menu"),
                    List.of(ChatColor.GRAY + plugin.message("respawn-menu-close-lore",
                            "Close now and default to your normal respawn point."))));
        } else {
            inventory.setItem(INFO_SLOT, createControlItem(Material.BOOK,
                    ChatColor.GOLD + plugin.message("manage-menu-page", "Beds {1}/{2}")
                            .replace("{1}", Integer.toString(page + 1))
                            .replace("{2}", Integer.toString(totalPages)),
                    List.of(ChatColor.GRAY + sharingModeMessage("manage-menu-prompt",
                            "Click a bed to rename, remove, or share it.", "manage-menu-prompt-transfer",
                            "Click a bed to rename, remove, or transfer it."))));
            inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.BARRIER,
                    ChatColor.RED + plugin.message("manage-menu-close", "Close menu"),
                    List.of(ChatColor.GRAY + plugin.message("manage-menu-close-lore",
                            "Close the beds management menu."))));
            List<ShareInvite> invites = getPendingShareInvites(Bukkit.getPlayer(
                    ((RespawnMenuHolder) inventory.getHolder()).getPlayerId()));
            if (!invites.isEmpty()) {
                inventory.setItem(CLOSE_SLOT, createShareInvitesButton(invites.size()));
            }
        }

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-next", "Next page"), List.of()));
        }
    }

    private static void renderActionMenu(Inventory inventory, BedMenuEntry entry) {
        fillInventory(inventory, Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        inventory.setItem(ACTION_PREVIEW_SLOT, createBedPreviewItem(entry));
        inventory.setItem(ACTION_RENAME_SLOT, createActionItem(
                entry.status() != BedStatus.MISSING ? Material.NAME_TAG : Material.GRAY_DYE,
                entry.status() != BedStatus.MISSING,
                plugin.message("bed-action-rename", "Rename bed"),
                plugin.message("bed-action-rename-lore", "Rename this saved bed.")));
        inventory.setItem(ACTION_PRIMARY_SLOT, createPrimaryActionItem(entry));
        if (plugin.getConfig().getBoolean("bed-sharing")) {
            inventory.setItem(ACTION_SHARE_SLOT, createShareActionItem(entry));
        }
        inventory.setItem(ACTION_BACK_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to beds"), List.of()));
        inventory.setItem(ACTION_REMOVE_SLOT, createActionItem(Material.BARRIER, true,
                plugin.message("bed-action-remove", "Remove bed"),
                plugin.message("bed-action-remove-lore", "Remove this bed from your saved list.")));
    }

    private static void renderShareMenu(Inventory inventory, List<OfflinePlayer> candidates, int page, int totalPages) {
        inventory.clear();
        fillBottomRow(inventory);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, candidates.size());
        for (int slot = 0; slot + startIndex < endIndex; slot++) {
            inventory.setItem(slot, createPlayerItem(candidates.get(startIndex + slot)));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-previous", "Previous page"), List.of()));
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + sharingModeMessage("bed-share-prompt",
                "Choose a player to receive a share request.", "bed-transfer-prompt",
                "Choose a player to receive a transfer request."));
        if (candidates.isEmpty()) {
            lore.add(ChatColor.RED + plugin.message("bed-share-no-players", "No other known players are available."));
        }
        inventory.setItem(INFO_SLOT, createControlItem(Material.PLAYER_HEAD,
                ChatColor.GOLD + plugin.message("bed-share-page", "Players {1}/{2}")
                        .replace("{1}", Integer.toString(page + 1))
                        .replace("{2}", Integer.toString(totalPages)),
                lore));
        inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to beds"),
                List.of(ChatColor.GRAY + plugin.message("bed-share-back-lore", "Return to the selected bed."))));

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-next", "Next page"), List.of()));
        }
    }

    private static void renderShareInviteListMenu(Inventory inventory, List<ShareInvite> invites, int page,
            int totalPages) {
        inventory.clear();
        fillBottomRow(inventory);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, invites.size());
        for (int slot = 0; slot + startIndex < endIndex; slot++) {
            inventory.setItem(slot, createShareInviteItem(invites.get(startIndex + slot)));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-previous", "Previous page"), List.of()));
        }

        inventory.setItem(INFO_SLOT, createControlItem(Material.WRITABLE_BOOK,
                ChatColor.GOLD + plugin.message("bed-share-invites-page", "Requests {1}/{2}")
                        .replace("{1}", Integer.toString(page + 1))
                        .replace("{2}", Integer.toString(totalPages)),
                List.of(ChatColor.GRAY + sharingModeMessage("bed-share-invites-prompt",
                        "Choose a share request to review.", "bed-transfer-invites-prompt",
                        "Choose a transfer request to review."))));
        inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to points"),
                List.of(ChatColor.GRAY + plugin.message("bed-share-invites-back-lore",
                        "Return to your saved respawn points."))));

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createControlItem(Material.ARROW,
                    ChatColor.YELLOW + plugin.message("respawn-menu-next", "Next page"), List.of()));
        }
    }

    private static void renderShareInviteActionMenu(Inventory inventory, ShareInvite invite) {
        fillInventory(inventory, Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

        inventory.setItem(INVITE_PREVIEW_SLOT, createShareInvitePreviewItem(invite));
        inventory.setItem(INVITE_ACCEPT_SLOT, createActionItem(Material.LIME_CONCRETE, true,
                plugin.message("bed-share-invite-accept", "Accept"),
                inviteModeMessage(invite, "bed-share-invite-accept-lore",
                        "Add this shared respawn point to your list.", "bed-transfer-invite-accept-lore",
                        "Add this transferred respawn point to your list.")));
        inventory.setItem(INVITE_DENY_SLOT, createActionItem(Material.RED_CONCRETE, true,
                plugin.message("bed-share-invite-deny", "Deny"),
                inviteModeMessage(invite, "bed-share-invite-deny-lore", "Decline this share request.",
                        "bed-transfer-invite-deny-lore", "Decline this transfer request.")));
        inventory.setItem(INVITE_BACK_SLOT, createControlItem(Material.ARROW,
                ChatColor.YELLOW + plugin.message("bed-action-back", "Back to points"), List.of()));
    }

    private static ItemStack createBedListItem(BedMenuEntry entry, SessionMode mode) {
        ItemStack item = new ItemStack(entry.displayMaterial(), entry.displayAmount());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(switch (entry.status()) {
            case AVAILABLE -> ChatColor.GREEN + entry.displayName();
            case COOLDOWN -> ChatColor.GOLD + entry.displayName();
            case DEPLETED, DISABLED, MISSING, OBSTRUCTED -> ChatColor.RED + entry.displayName();
        });
        hideMenuTooltipDetails(meta);
        meta.setLore(buildBedLore(entry, mode == SessionMode.RESPAWN));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(PluginKeys.uuid(), PersistentDataType.STRING, entry.uuid());
        data.set(PluginKeys.menuStatus(), PersistentDataType.STRING, entry.status().name());

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createBedPreviewItem(BedMenuEntry entry) {
        ItemStack item = new ItemStack(entry.displayMaterial(), entry.displayAmount());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + entry.displayName());
        hideMenuTooltipDetails(meta);

        List<String> lore = new ArrayList<>();
        boolean hasMetadata = RespawnPointMenuLore.appendMetadataLore(lore, plugin, entry, true);
        if (hasMetadata) {
            lore.add("");
        }
        switch (entry.status()) {
            case AVAILABLE -> lore.add(ChatColor.GREEN + plugin.message("bed-action-ready", "Ready to manage."));
            case COOLDOWN -> lore.add(ChatColor.GOLD + plugin.message("respawn-menu-bed-cooldown", "Available in {1}s.")
                    .replace("{1}", Long.toString(entry.remainingCooldownSeconds())));
            case DEPLETED -> lore.add(ChatColor.RED + plugin.message("respawn-anchor-depleted",
                    "This respawn anchor has no charges."));
            case DISABLED -> lore.add(ChatColor.RED + plugin.message("respawn-anchor-disabled",
                    "Respawn anchors are disabled in config."));
            case MISSING -> lore.add(ChatColor.RED
                    + plugin.message("respawn-menu-bed-missing", "This bed no longer exists."));
            case OBSTRUCTED -> lore.add(ChatColor.RED + plugin.message("respawn-menu-bed-obstructed",
                    "Your bed is obstructed. Choose a different registered respawn point."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createPrimaryActionItem(BedMenuEntry entry) {
        if (entry.status() == BedStatus.MISSING || entry.status() == BedStatus.DISABLED) {
            return createActionItem(Material.GRAY_DYE, false,
                    plugin.message("bed-action-primary", "Set primary"),
                    plugin.message("bed-action-unavailable-lore", "This action is unavailable for this respawn point."));
        }
        if (entry.bedData().isPrimary()) {
            return createActionItem(Material.GRAY_DYE, false,
                    plugin.message("bed-action-primary-selected", "Primary bed"),
                    plugin.message("bed-action-primary-selected-lore", "This is already your primary bed."));
        }

        return createActionItem(Material.NETHER_STAR, true,
                plugin.message("bed-action-primary", "Set primary"),
                plugin.message("bed-action-primary-lore", "Make this your primary bed."));
    }

    private static ItemStack createShareActionItem(BedMenuEntry entry) {
        if (entry.status() == BedStatus.MISSING) {
            return createActionItem(Material.GRAY_DYE, false,
                    sharingModeMessage("bed-action-share", "Share bed", "bed-action-transfer", "Transfer point"),
                    plugin.message("bed-action-unavailable-lore", "This action is unavailable for missing beds."));
        }

        return createActionItem(Material.PLAYER_HEAD, true,
                sharingModeMessage("bed-action-share", "Share bed", "bed-action-transfer", "Transfer point"),
                sharingModeMessage("bed-action-share-lore",
                        "Invite another player to accept this saved respawn point.", "bed-action-transfer-lore",
                        "Invite another player to accept this saved respawn point transfer."));
    }

    private static ItemStack createPlayerItem(OfflinePlayer target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName((target.isOnline() ? ChatColor.GREEN : ChatColor.RED) + getOfflinePlayerName(target));
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of(ChatColor.GRAY + sharingModeMessage("bed-share-click",
                "Click to send this player a share request.", "bed-transfer-click",
                "Click to send this player a transfer request.")));
        meta.getPersistentDataContainer().set(PluginKeys.sharePlayer(), PersistentDataType.STRING,
                target.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createShareInvitesButton(int inviteCount) {
        String count = Integer.toString(inviteCount);
        return createControlItem(Material.WRITABLE_BOOK,
                ChatColor.YELLOW + sharingModeMessage("bed-share-invites-button", "Share requests ({1})",
                        "bed-transfer-invites-button", "Transfer requests ({1})")
                        .replace("{1}", count),
                List.of(ChatColor.GRAY + sharingModeMessage("bed-share-invites-button-lore",
                        "Review pending share requests.", "bed-transfer-invites-button-lore",
                        "Review pending transfer requests.")));
    }

    private static ItemStack createShareInviteItem(ShareInvite invite) {
        OfflinePlayer sender = Bukkit.getOfflinePlayer(invite.senderId());
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(sender);
        meta.setDisplayName(ChatColor.YELLOW + plugin.message("bed-share-invite-from", "From {1}")
                .replace("{1}", getInviteSenderName(invite)));
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of(
                ChatColor.GRAY + plugin.message("bed-share-invite-expires", "Expires in {1}.")
                        .replace("{1}", formatRemaining(invite.expiresAt())),
                ChatColor.YELLOW + plugin.message("bed-share-invite-click", "Click to review this request.")));
        meta.getPersistentDataContainer().set(PluginKeys.shareInvite(), PersistentDataType.STRING,
                invite.inviteId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createShareInvitePreviewItem(ShareInvite invite) {
        BedData bedData = getInviteBedData(invite);
        Material material = bedData == null ? Material.BARRIER : bedData.getBedMaterial();
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + inviteModeMessage(invite, "bed-share-invite-preview",
                "Shared respawn point", "bed-transfer-invite-preview", "Transferred respawn point"));
        hideMenuTooltipDetails(meta);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.BLUE + transferredByLabel(invite, getInviteSenderName(invite)));
        lore.add(ChatColor.GRAY + plugin.message("bed-share-invite-expires", "Expires in {1}.")
                .replace("{1}", formatRemaining(invite.expiresAt())));
        if (bedData == null) {
            lore.add(ChatColor.RED + inviteModeMessage(invite, "bed-share-invite-unavailable",
                    "That shared respawn point is no longer available.", "bed-transfer-invite-unavailable",
                    "That transferred respawn point is no longer available."));
        } else {
            if (!plugin.getConfig().getBoolean("disable-bed-world-desc")) {
                lore.add(ChatColor.DARK_PURPLE + bedData.getBedWorld().toUpperCase());
            }
            if (!plugin.getConfig().getBoolean("disable-bed-coords-desc")) {
                lore.add(ChatColor.GRAY + bedData.formatCoords());
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean setPrimaryBed(Player player, String uuid) {
        PlayerBedsData playerBedsData = getPlayerBedsData(player);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return false;
        }
        if (!playerBedsData.setPrimaryBed(uuid)) {
            return false;
        }

        savePlayerBedsData(player, playerBedsData);
        return true;
    }

    private static void teleportToSavedBed(Player player, PlayerBedsData playerBedsData, String uuid) {
        BedData bedData = playerBedsData.getPlayerBedData().get(uuid);
        if (bedData == null) {
            sendPlayerToDefaultRespawn(player, false);
            return;
        }

        Location respawnLocation = bedData.getSpawnLocation();
        if (respawnLocation == null || respawnLocation.getWorld() == null) {
            sendPlayerToDefaultRespawn(player, false);
            return;
        }

        Location safeRespawnLocation = findSafeRespawnLocation(bedData.getBedLocation(), respawnLocation);
        if (safeRespawnLocation == null) {
            sendObstructedMessage(player);
            showListMenu(player, getListPage(player));
            return;
        }

        cancelSession(player.getUniqueId());

        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (bedData.usesCooldown() && !plugin.hasSkipCooldownPermission(player)) {
            bedData.setBedCooldown(System.currentTimeMillis() + (plugin.getConfig().getLong("bed-cooldown") * 1000L));
        }
        savePlayerBedsData(player, playerBedsData);
        playerData.remove(PluginKeys.spawnLoc());

        undoPropPlayer(player);
        if (!teleport(player, safeRespawnLocation)) {
            Bukkit.getScheduler().runTask(plugin, () -> sendPlayerToDefaultRespawn(player, false));
            return;
        }

        if (bedData.isRespawnAnchor()) {
            consumeRespawnAnchorCharge(bedData);
        }
    }

    private static void sendPlayerToDefaultRespawn(Player player, boolean timedOut) {
        cancelSession(player.getUniqueId());

        Location defaultRespawn = getPlayerRespawnLoc(player);
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        playerData.remove(PluginKeys.spawnLoc());

        if (timedOut) {
            player.sendActionBar(Component.text(stripColors(plugin.message("respawn-menu-timeout",
                    "Respawn menu timed out. Sending you to spawn."))).color(NamedTextColor.YELLOW));
        }

        undoPropPlayer(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (teleport(player, defaultRespawn)) {
                runCommandOnSpawn(player);
            }
        });
    }

    private static boolean tryRespawnAtPrimaryBed(Player player) {
        PlayerBedsData playerBedsData = getPlayerBedsData(player);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return false;
        }

        for (BedMenuEntry entry : getMenuEntries(player)) {
            if (!entry.bedData().isPrimary()) {
                continue;
            }
            if (entry.status() != BedStatus.AVAILABLE) {
                return false;
            }

            teleportToSavedBed(player, playerBedsData, entry.uuid());
            return true;
        }

        return false;
    }

    private static void denyShareInvite(Player receiver, ShareInvite invite) {
        plugin.getPlayerBedStore().deleteShareInvite(invite.inviteId());
        String senderName = getInviteSenderName(invite);
        receiver.sendMessage(ChatColor.YELLOW + inviteModeMessage(invite, "bed-share-invite-denied",
                "Denied {1}'s shared respawn point request.", "bed-transfer-invite-denied",
                "Denied {1}'s transferred respawn point request.").replace("{1}", senderName));
        notifyPlayerOrQueue(invite.senderId(), ChatColor.YELLOW + inviteModeMessage(invite,
                "bed-share-invite-denied-owner", "{1} denied your shared respawn point request.",
                "bed-transfer-invite-denied-owner", "{1} denied your transferred respawn point request.")
                .replace("{1}", getOfflinePlayerName(receiver)));
    }

    public static void sendPendingShareInviteNotifications(Player player) {
        List<ShareInvite> invites = getPendingShareInvites(player);
        if (!invites.isEmpty()) {
            sendShareInviteNotification(player, invites.size());
        }
    }

    private static boolean sendShareInvite(Player owner, OfflinePlayer receiver, String bedUuid) {
        PlayerBedsData ownerBedsData = getPlayerBedsData(owner);
        if (ownerBedsData == null || ownerBedsData.getPlayerBedData() == null || !ownerBedsData.hasBed(bedUuid)) {
            owner.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "You have not registered this bed!"));
            return false;
        }

        PlayerBedsData receiverBedsData = getPlayerBedsData(receiver);
        if (receiverBedsData == null) {
            receiverBedsData = new PlayerBedsData();
        }
        String receiverName = getOfflinePlayerName(receiver);
        if (receiverBedsData.hasBed(bedUuid)) {
            owner.sendMessage(ChatColor.RED + plugin.message("bed-share-invite-target-already-has",
                    "{1} already has this respawn point.").replace("{1}", receiverName));
            return false;
        }

        String ownerName = getOfflinePlayerName(owner);
        long expiresAt = System.currentTimeMillis() + plugin.getShareInviteExpirySeconds() * 1000L;
        ShareInvite invite = plugin.getPlayerBedStore().createShareInvite(owner.getUniqueId(), ownerName,
                receiver.getUniqueId(), receiverName, bedUuid, plugin.getConfig().getBoolean("exclusive-bed"),
                expiresAt);
        if (invite == null) {
            owner.sendMessage(ChatColor.RED + sharingModeMessage("bed-share-invite-create-failed",
                    "Could not create that share request.", "bed-transfer-invite-create-failed",
                    "Could not create that transfer request."));
            return false;
        }

        owner.sendMessage(ChatColor.YELLOW + inviteModeMessage(invite, "bed-share-invite-sent",
                "Share request sent to {1}. It expires in {2}.", "bed-transfer-invite-sent",
                "Transfer request sent to {1}. It expires in {2}.")
                .replace("{1}", receiverName)
                .replace("{2}", formatRemaining(expiresAt)));

        Player onlineReceiver = receiver.getPlayer();
        if (onlineReceiver != null && onlineReceiver.isOnline()) {
            sendShareInviteNotification(onlineReceiver, getPendingShareInvites(onlineReceiver).size());
        }
        return true;
    }

    private static PlayerBedsData getPlayerBedsData(Player player) {
        return loadPlayerBedsData(player);
    }

    private static PlayerBedsData getPlayerBedsData(OfflinePlayer player) {
        return loadPlayerBedsData(player);
    }

    private static boolean isUsableInvite(Player receiver, ShareInvite invite) {
        if (invite == null || invite.isExpired(System.currentTimeMillis())
                || !invite.targetId().equals(receiver.getUniqueId())) {
            receiver.sendMessage(ChatColor.RED + inviteModeMessage(invite, "bed-share-invite-not-found",
                    "Share request not found or expired.", "bed-transfer-invite-not-found",
                    "Transfer request not found or expired."));
            return false;
        }

        return true;
    }

    private static boolean applyShareInvite(Player receiver, ShareInvite invite) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(invite.senderId());
        PlayerBedsData ownerBedsData = getPlayerBedsData(owner);
        if (ownerBedsData == null || ownerBedsData.getPlayerBedData() == null
                || !ownerBedsData.hasBed(invite.bedUuid())) {
            plugin.getPlayerBedStore().deleteShareInvite(invite.inviteId());
            receiver.sendMessage(ChatColor.RED + inviteModeMessage(invite, "bed-share-invite-unavailable",
                    "That shared respawn point is no longer available.", "bed-transfer-invite-unavailable",
                    "That transferred respawn point is no longer available."));
            return false;
        }

        PlayerBedsData receiverBedsData = getPlayerBedsData(receiver);
        if (receiverBedsData == null) {
            receiverBedsData = new PlayerBedsData();
        }
        if (receiverBedsData.hasBed(invite.bedUuid())) {
            plugin.getPlayerBedStore().deleteShareInvite(invite.inviteId());
            receiver.sendMessage(ChatColor.RED + plugin.message("bed-share-invite-already-has",
                    "You already have that respawn point."));
            return false;
        }

        String ownerName = getInviteSenderName(invite);
        ownerBedsData.shareBed(receiverBedsData, invite.bedUuid(), invite.transferOwnership(), ownerName);
        savePlayerBedsData(receiver, receiverBedsData);
        savePlayerBedsData(owner, ownerBedsData);
        if (invite.transferOwnership()) {
            plugin.getPlayerBedStore().deleteShareInvitesForBed(invite.bedUuid());
        }

        receiver.sendMessage(ChatColor.YELLOW + inviteModeMessage(invite, "bed-share-invite-accepted",
                "Accepted {1}'s shared respawn point.", "bed-transfer-invite-accepted",
                "Accepted {1}'s transferred respawn point.").replace("{1}", ownerName));
        notifyPlayerOrQueue(invite.senderId(), ChatColor.YELLOW + inviteModeMessage(invite,
                "bed-share-invite-accepted-owner", "{1} accepted your shared respawn point request.",
                "bed-transfer-invite-accepted-owner", "{1} accepted your transferred respawn point request.")
                .replace("{1}", getOfflinePlayerName(receiver)));
        return true;
    }

    private static void sendShareInviteNotification(Player receiver, int inviteCount) {
        receiver.sendMessage(ChatColor.YELLOW + sharingModeMessage("bed-share-invite-received",
                "You have {1} pending share requests.", "bed-transfer-invite-received",
                "You have {1} pending transfer requests.")
                .replace("{1}", Integer.toString(inviteCount)));
        receiver.sendMessage(Component.text(stripColors(sharingModeMessage("bed-share-invite-actions",
                        "Click here to review Share Requests.", "bed-transfer-invite-actions",
                        "Click here to review Transfer Requests.")))
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(PENDING_REQUESTS_COMMAND))
                .hoverEvent(HoverEvent.showText(Component.text(stripColors(sharingModeMessage(
                        "bed-share-invite-actions-hover", "Open pending share requests.",
                        "bed-transfer-invite-actions-hover", "Open pending transfer requests.")))
                        .color(NamedTextColor.YELLOW))));
    }

    private static void notifyPlayerOrQueue(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
            return;
        }

        plugin.getPlayerBedStore().queuePendingMessage(playerId, message);
    }

    private static String getInviteSenderName(ShareInvite invite) {
        if (invite.senderName() != null && !invite.senderName().isBlank()) {
            return invite.senderName();
        }

        return getOfflinePlayerName(Bukkit.getOfflinePlayer(invite.senderId()));
    }

    private static String sharingModeMessage(String shareKey, String shareFallback, String transferKey,
            String transferFallback) {
        return plugin.sharingModeMessage(shareKey, shareFallback, transferKey, transferFallback);
    }

    private static String inviteModeMessage(ShareInvite invite, String shareKey, String shareFallback,
            String transferKey, String transferFallback) {
        return plugin.sharingModeMessage(isTransferInvite(invite), shareKey, shareFallback, transferKey,
                transferFallback);
    }

    private static boolean isTransferInvite(ShareInvite invite) {
        return invite != null ? invite.transferOwnership() : plugin.usesTransferSharingLanguage();
    }

    private static String transferredByLabel(ShareInvite invite, String playerName) {
        return inviteModeMessage(invite, "bed-shared-by-label", "Shared By: {1}", "bed-transferred-by-label",
                "Transferred By: {1}").replace("{1}", playerName);
    }

    private static List<ShareInvite> getPendingShareInvites(Player player) {
        if (player == null) {
            return List.of();
        }

        return plugin.getPlayerBedStore().getPendingShareInvites(player.getUniqueId());
    }

    private static BedData getInviteBedData(ShareInvite invite) {
        PlayerBedsData ownerBedsData = getPlayerBedsData(Bukkit.getOfflinePlayer(invite.senderId()));
        if (ownerBedsData == null || ownerBedsData.getPlayerBedData() == null) {
            return null;
        }

        return ownerBedsData.getPlayerBedData().get(invite.bedUuid());
    }

    private static List<BedMenuEntry> getMenuEntries(Player player) {
        PlayerBedsData playerBedsData = getPlayerBedsData(player);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return List.of();
        }

        HashMap<String, BedData> savedBeds = playerBedsData.getPlayerBedData();
        if (savedBeds.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<String, BedData>> sortedBeds = new ArrayList<>(savedBeds.entrySet());
        if (!plugin.getConfig().getBoolean("link-worlds")) {
            World respawnWorld = getPlayerRespawnLoc(player).getWorld();
            if (respawnWorld == null) {
                return List.of();
        }
            sortedBeds.removeIf(entry -> !entry.getValue().getBedWorld().equalsIgnoreCase(respawnWorld.getName()));
        }
        sortedBeds.sort(Comparator.comparing((Map.Entry<String, BedData> entry) -> entry.getValue().isPrimary())
                .reversed()
                .thenComparing(entry -> entry.getValue().getSortKey(), String.CASE_INSENSITIVE_ORDER));

        List<BedMenuEntry> entries = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, BedData> entry : sortedBeds) {
            BedData bedData = entry.getValue();
            BedStatus status = resolveStatus(player, entry.getKey(), bedData);
            long remainingSeconds = Math.max(0L, (bedData.getBedCooldown() - System.currentTimeMillis() + 999L) / 1000L);
            entries.add(BedMenuEntry.create(plugin, entry.getKey(), bedData, index, status, remainingSeconds));
            index++;
        }
        return entries;
    }

    private static BedMenuEntry getMenuEntry(Player player, String uuid) {
        for (BedMenuEntry entry : getMenuEntries(player)) {
            if (entry.uuid().equalsIgnoreCase(uuid)) {
                return entry;
            }
        }
        return null;
    }

    private static BedStatus resolveStatus(Player player, String uuid, BedData bedData) {
        if (!isRegisteredRespawnPointPresent(bedData, uuid)) {
            return BedStatus.MISSING;
        }
        if (bedData.isRespawnAnchor()) {
            if (!plugin.getConfig().getBoolean("respawn-anchors-enabled")) {
                return BedStatus.DISABLED;
            }
            if (getRespawnAnchorCharges(bedData) <= 0) {
                return BedStatus.DEPLETED;
            }
            return resolveSafetyStatus(bedData);
        }
        if (!plugin.hasSkipCooldownPermission(player) && bedData.getBedCooldown() > System.currentTimeMillis()) {
            return BedStatus.COOLDOWN;
        }
        return resolveSafetyStatus(bedData);
    }

    private static BedStatus resolveSafetyStatus(BedData bedData) {
        return hasSafeRespawnLocation(bedData.getBedLocation(), bedData.getSpawnLocation())
                ? BedStatus.AVAILABLE
                : BedStatus.OBSTRUCTED;
    }

    private static void sendObstructedMessage(Player player) {
        player.sendMessage(ChatColor.RED + plugin.message("respawn-menu-bed-obstructed",
                "Your bed is obstructed. Choose a different registered respawn point."));
    }

    private static List<OfflinePlayer> getShareCandidates(Player player, String bedUuid) {
        Map<UUID, OfflinePlayer> knownPlayers = new HashMap<>();
        Bukkit.getOnlinePlayers().forEach(target -> knownPlayers.put(target.getUniqueId(), target));
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (target != null) {
                knownPlayers.put(target.getUniqueId(), target);
            }
        }

        Set<UUID> existingOwners = plugin.getPlayerBedStore().getOwners(bedUuid);
        List<OfflinePlayer> candidates = new ArrayList<>();
        for (OfflinePlayer target : knownPlayers.values()) {
            if (target == null
                    || target.getUniqueId().equals(player.getUniqueId())
                    || existingOwners.contains(target.getUniqueId())) {
                continue;
            }

            candidates.add(target);
        }
        candidates.sort(PlayerUtils.offlinePlayerListComparator());
        return candidates;
    }

    private static Comparator<ShareInvite> shareInviteSenderComparator() {
        return Comparator.comparing((ShareInvite invite) ->
                        !PlayerUtils.isOnlinePlayer(Bukkit.getOfflinePlayer(invite.senderId())))
                .thenComparing(RespawnMenuHandler::getInviteSenderName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(invite -> invite.senderId().toString())
                .thenComparingLong(ShareInvite::createdAt);
    }

    private static String formatRemaining(long expiresAt) {
        long seconds = Math.max(1L, (expiresAt - System.currentTimeMillis() + 999L) / 1000L);
        if (seconds < 60L) {
            return plugin.message("bed-share-invite-seconds", "{1}s").replace("{1}", Long.toString(seconds));
        }

        long minutes = (seconds + 59L) / 60L;
        return plugin.message("bed-share-invite-minutes", "{1}m").replace("{1}", Long.toString(minutes));
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> buildBedLore(BedMenuEntry entry, boolean respawnMode) {
        List<String> lore = new ArrayList<>();
        boolean hasMetadata = RespawnPointMenuLore.appendMetadataLore(lore, plugin, entry, true);
        if (hasMetadata) {
            lore.add("");
        }

        if (respawnMode) {
            switch (entry.status()) {
                case AVAILABLE -> lore.add(ChatColor.YELLOW
                        + plugin.message("respawn-menu-click-respawn", "Left-click to respawn here."));
                case COOLDOWN -> lore.add(ChatColor.RED
                        + plugin.message("respawn-menu-bed-cooldown", "Available in {1}s.")
                                .replace("{1}", Long.toString(entry.remainingCooldownSeconds())));
                case DEPLETED -> lore.add(ChatColor.RED
                        + plugin.message("respawn-anchor-depleted", "This respawn anchor has no charges."));
                case DISABLED -> lore.add(ChatColor.RED
                        + plugin.message("respawn-anchor-disabled", "Respawn anchors are disabled in config."));
                case MISSING -> lore.add(ChatColor.RED
                        + plugin.message("respawn-menu-bed-missing", "This bed no longer exists."));
                case OBSTRUCTED -> lore.add(ChatColor.RED + plugin.message("respawn-menu-bed-obstructed",
                        "Your bed is obstructed. Choose a different registered respawn point."));
            }
        } else {
            switch (entry.status()) {
                case AVAILABLE -> lore.add(ChatColor.YELLOW
                        + plugin.message("manage-menu-click-manage", "Click to manage this bed."));
                case COOLDOWN -> {
                    lore.add(ChatColor.GOLD
                            + plugin.message("respawn-menu-bed-cooldown", "Available in {1}s.")
                                    .replace("{1}", Long.toString(entry.remainingCooldownSeconds())));
                    lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage", "Click to manage this bed."));
                }
                case DEPLETED -> {
                    lore.add(ChatColor.RED
                            + plugin.message("respawn-anchor-depleted", "This respawn anchor has no charges."));
                    lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage", "Click to manage this bed."));
                }
                case DISABLED -> {
                    lore.add(ChatColor.RED
                            + plugin.message("respawn-anchor-disabled", "Respawn anchors are disabled in config."));
                    lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage", "Click to manage this bed."));
                }
                case MISSING -> {
                    lore.add(ChatColor.RED + plugin.message("respawn-menu-bed-missing", "This bed no longer exists."));
                    lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage", "Click to manage this bed."));
                }
                case OBSTRUCTED -> {
                    lore.add(ChatColor.RED + plugin.message("respawn-menu-bed-obstructed",
                            "Your bed is obstructed. Choose a different registered respawn point."));
                    lore.add(ChatColor.YELLOW + plugin.message("manage-menu-click-manage", "Click to manage this bed."));
                }
            }
        }

        return lore;
    }

    private static boolean hasDynamicEntries(List<BedMenuEntry> entries) {
        for (BedMenuEntry entry : entries) {
            if (entry.hasDynamicDisplay()) {
                return true;
            }
        }
        return false;
    }

    private static void handleEmptyMenu(Player player, SessionMode mode) {
        if (mode == SessionMode.RESPAWN) {
            sendPlayerToDefaultRespawn(player, false);
            return;
        }

        if (!getPendingShareInvites(player).isEmpty()) {
            showShareInviteListMenu(player, 0);
            return;
        }

        cancelSession(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + plugin.message("no-beds-saved", "You have no saved beds."));
    }

    private static void prepareInventorySwap(Player player, BedMenuSession session) {
        Inventory currentTopInventory = player.getOpenInventory().getTopInventory();
        if (currentTopInventory.getHolder() instanceof RespawnMenuHolder currentHolder
                && currentHolder.getPlayerId().equals(player.getUniqueId())) {
            session.suppressNextClose();
        }
    }

    private static void scheduleRespawnTimeout(Player player, BedMenuSession session) {
        session.setTimeoutTaskId(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ACTIVE_SESSIONS.get(player.getUniqueId()) == session) {
                if (!tryRespawnAtPrimaryBed(player)) {
                    sendPlayerToDefaultRespawn(player, true);
                }
            }
        }, Math.max(1L, plugin.getConfig().getLong("respawn-menu-timeout-seconds") * 20L)).getTaskId());
    }

    private static void syncRefreshTask(Player player, BedMenuSession session, boolean hasDynamicEntries) {
        if (!hasDynamicEntries) {
            session.cancelRefreshTask();
            return;
        }
        if (session.getRefreshTaskId() != -1) {
            return;
        }

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> refreshOpenMenu(player), 20L, 20L).getTaskId();
        session.setRefreshTaskId(taskId);
    }

    private static void refreshOpenMenu(Player player) {
        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder() instanceof RespawnMenuHolder holder)
                || !holder.getPlayerId().equals(player.getUniqueId())) {
            return;
        }
        if (holder.getViewType() != RespawnMenuHolder.ViewType.RESPAWN_LIST
                && holder.getViewType() != RespawnMenuHolder.ViewType.MANAGE_LIST) {
            return;
        }

        List<BedMenuEntry> entries = getMenuEntries(player);
        if (entries.isEmpty()) {
            handleEmptyMenu(player, session.getMode());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(holder.getPage(), totalPages - 1));
        if (page != holder.getPage()) {
            showListMenu(player, page);
            return;
        }

        renderListMenu(inventory, entries, page, totalPages, session.getMode());
        syncRefreshTask(player, session, hasDynamicEntries(entries));
    }

    private static boolean isRespawnProtected(Player player) {
        return player.getPersistentDataContainer().has(PluginKeys.hasProp(), PersistentDataType.BOOLEAN);
    }

    private static int getListPage(Player player) {
        BedMenuSession session = ACTIVE_SESSIONS.get(player.getUniqueId());
        return session == null ? 0 : session.getListPage();
    }

    private static void cancelSession(UUID playerId) {
        BedMenuSession session = ACTIVE_SESSIONS.remove(playerId);
        if (session != null) {
            session.cancelAllTasks();
        }
    }

    private static String stripColors(String value) {
        String stripped = ChatColor.stripColor(value);
        return stripped == null ? value : stripped;
    }

    private enum SessionMode {
        RESPAWN,
        MANAGE
    }

    private static final class BedMenuSession {
        private final SessionMode mode;
        private int timeoutTaskId = -1;
        private int refreshTaskId = -1;
        private int listPage = 0;
        private boolean suppressNextClose;

        private BedMenuSession(SessionMode mode) {
            this.mode = mode;
        }

        public SessionMode getMode() {
            return mode;
        }

        public int getListPage() {
            return listPage;
        }

        public void setListPage(int listPage) {
            this.listPage = listPage;
        }

        public int getRefreshTaskId() {
            return refreshTaskId;
        }

        public void setRefreshTaskId(int refreshTaskId) {
            this.refreshTaskId = refreshTaskId;
        }

        public void setTimeoutTaskId(int timeoutTaskId) {
            this.timeoutTaskId = timeoutTaskId;
        }

        public void suppressNextClose() {
            suppressNextClose = true;
        }

        public boolean consumeSuppressedClose() {
            if (!suppressNextClose) {
                return false;
            }
            suppressNextClose = false;
            return true;
        }

        public void cancelRefreshTask() {
            if (refreshTaskId != -1) {
                Bukkit.getScheduler().cancelTask(refreshTaskId);
                refreshTaskId = -1;
            }
        }

        public void cancelAllTasks() {
            if (timeoutTaskId != -1) {
                Bukkit.getScheduler().cancelTask(timeoutTaskId);
                timeoutTaskId = -1;
            }
            cancelRefreshTask();
        }
    }
}
