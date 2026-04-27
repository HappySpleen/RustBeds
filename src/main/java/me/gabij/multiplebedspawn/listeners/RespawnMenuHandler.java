package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.gui.RespawnMenuHolder;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import me.gabij.multiplebedspawn.utils.PluginKeys;
import net.kyori.adventure.text.Component;
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

import static me.gabij.multiplebedspawn.utils.BedsUtils.consumeRespawnAnchorCharge;
import static me.gabij.multiplebedspawn.utils.BedsUtils.getRespawnAnchorCharges;
import static me.gabij.multiplebedspawn.utils.BedsUtils.getRespawnAnchorMaxCharges;
import static me.gabij.multiplebedspawn.utils.BedsUtils.isRegisteredRespawnPointPresent;
import static me.gabij.multiplebedspawn.utils.BedsUtils.removePlayerBed;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.getPlayerRespawnLoc;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.loadPlayerBedsData;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.savePlayerBedsData;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.setPropPlayer;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.undoPropPlayer;
import static me.gabij.multiplebedspawn.utils.RunCommandUtils.runCommandOnSpawn;
import static me.gabij.multiplebedspawn.utils.TeleportUtils.teleport;

public class RespawnMenuHandler implements Listener {
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

    private static final Map<UUID, BedMenuSession> ACTIVE_SESSIONS = new HashMap<>();

    private static MultipleBedSpawn plugin;

    public RespawnMenuHandler(MultipleBedSpawn plugin) {
        RespawnMenuHandler.plugin = plugin;
    }

    public static void openCommandMenu(Player player) {
        if (isRespawnProtected(player)) {
            beginRespawnMenu(player, 0L);
            return;
        }

        openManageMenu(player, 0);
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
                String status = data.get(PluginKeys.menuStatus(), PersistentDataType.STRING);
                if (uuid == null || status == null || !BedStatus.AVAILABLE.name().equalsIgnoreCase(status)) {
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
                BedMenuInputListener.beginRenamePrompt(player, bedUuid, holder.getPage());
                player.sendMessage(ChatColor.YELLOW + plugin.message("rename-prompt",
                        "Type the new bed name in chat. Type 'cancel' to abort."));
                player.closeInventory();
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

                String targetName = clickedItem.getItemMeta().getPersistentDataContainer()
                        .get(PluginKeys.sharePlayer(), PersistentDataType.STRING);
                if (targetName == null) {
                    return;
                }

                Player receiver = Bukkit.getPlayerExact(targetName);
                if (receiver == null || receiver.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + plugin.message("player-not-found", "Player not found!"));
                    showShareMenu(player, bedUuid, holder.getPage());
                    return;
                }

                if (shareBed(player, receiver, bedUuid)) {
                    openManageMenu(player, getListPage(player));
                } else {
                    showManageActionMenu(player, getListPage(player), bedUuid);
                }
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

        List<Player> candidates = getShareCandidates(player);
        int totalPages = Math.max(1, (int) Math.ceil(candidates.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        prepareInventorySwap(player, session);
        session.cancelRefreshTask();

        RespawnMenuHolder holder = new RespawnMenuHolder(player.getUniqueId(), RespawnMenuHolder.ViewType.SHARE_LIST,
                page, bedUuid);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE, plugin.message("bed-share-title", "Share bed"));
        holder.setInventory(inventory);

        renderShareMenu(inventory, candidates, page, totalPages);
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
                    List.of(ChatColor.GRAY + plugin.message("manage-menu-prompt",
                            "Click a bed to rename, remove, or share it."))));
            inventory.setItem(PRIMARY_ACTION_SLOT, createControlItem(Material.BARRIER,
                    ChatColor.RED + plugin.message("manage-menu-close", "Close menu"),
                    List.of(ChatColor.GRAY + plugin.message("manage-menu-close-lore",
                            "Close the beds management menu."))));
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

    private static void renderShareMenu(Inventory inventory, List<Player> candidates, int page, int totalPages) {
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
        lore.add(ChatColor.GRAY + plugin.message("bed-share-prompt", "Choose a player to receive this bed."));
        if (candidates.isEmpty()) {
            lore.add(ChatColor.RED + plugin.message("bed-share-no-players", "No other online players are available."));
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

    private static ItemStack createBedListItem(BedMenuEntry entry, SessionMode mode) {
        Material material = getBedDisplayMaterial(entry);
        ItemStack item = new ItemStack(material, getDisplayAmount(entry));
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(switch (entry.status()) {
            case AVAILABLE -> ChatColor.GREEN + entry.displayName();
            case COOLDOWN -> ChatColor.GOLD + entry.displayName();
            case DEPLETED, DISABLED, MISSING -> ChatColor.RED + entry.displayName();
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
        Material material = getBedDisplayMaterial(entry);
        ItemStack item = new ItemStack(material, getDisplayAmount(entry));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + entry.displayName());
        hideMenuTooltipDetails(meta);

        List<String> lore = new ArrayList<>();
        boolean hasMetadata = appendBedMetadataLore(lore, entry);
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
                    plugin.message("bed-action-share", "Share bed"),
                    plugin.message("bed-action-unavailable-lore", "This action is unavailable for missing beds."));
        }

        return createActionItem(Material.PLAYER_HEAD, true,
                plugin.message("bed-action-share", "Share bed"),
                plugin.message("bed-action-share-lore", "Give this saved bed to another online player."));
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

    private static ItemStack createPlayerItem(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName(ChatColor.GREEN + target.getName());
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of(ChatColor.GRAY + plugin.message("bed-share-click",
                "Click to give this bed to the player.")));
        meta.getPersistentDataContainer().set(PluginKeys.sharePlayer(), PersistentDataType.STRING, target.getName());
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

    private static Material getBedDisplayMaterial(BedMenuEntry entry) {
        return switch (entry.status()) {
            case COOLDOWN -> Material.CLOCK;
            case DEPLETED, DISABLED, MISSING -> Material.BARRIER;
            case AVAILABLE -> entry.bedData().getBedMaterial();
        };
    }

    private static int getDisplayAmount(BedMenuEntry entry) {
        if (entry.bedData().isRespawnAnchor() && entry.status() == BedStatus.AVAILABLE) {
            return Math.max(1, Math.min(64, entry.currentCharges()));
        }

        return 1;
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

        cancelSession(player.getUniqueId());

        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (bedData.usesCooldown() && !player.hasPermission("multiplebedspawn.skipcooldown")) {
            bedData.setBedCooldown(System.currentTimeMillis() + (plugin.getConfig().getLong("bed-cooldown") * 1000L));
        }
        savePlayerBedsData(player, playerBedsData);
        playerData.remove(PluginKeys.spawnLoc());

        undoPropPlayer(player);
        if (!teleport(player, respawnLocation)) {
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

    private static boolean shareBed(Player owner, Player receiver, String bedUuid) {
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

        ownerBedsData.shareBed(receiverBedsData, bedUuid);
        savePlayerBedsData(receiver, receiverBedsData);
        savePlayerBedsData(owner, ownerBedsData);

        owner.sendMessage(ChatColor.YELLOW + plugin.message("bed-shared-successfully-message",
                "Bed shared successfully with {1}!").replace("{1}", receiver.getName()));
        receiver.sendMessage(ChatColor.YELLOW + plugin.message("bed-shared-received-message",
                "You received a shared bed from {1}!").replace("{1}", owner.getName()));
        return true;
    }

    private static PlayerBedsData getPlayerBedsData(Player player) {
        return loadPlayerBedsData(player);
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
            String displayName = bedData.hasCustomName()
                    ? bedData.getBedName()
                    : plugin.message(bedData.isRespawnAnchor() ? "default-anchor-name" : "default-bed-name",
                            bedData.isRespawnAnchor() ? "Respawn Anchor {1}" : "Bed {1}")
                    .replace("{1}", Integer.toString(index));
            BedStatus status = resolveStatus(player, entry.getKey(), bedData);
            long remainingSeconds = Math.max(0L, (bedData.getBedCooldown() - System.currentTimeMillis() + 999L) / 1000L);
            int currentCharges = bedData.isRespawnAnchor() ? getRespawnAnchorCharges(bedData) : 0;
            int maxCharges = bedData.isRespawnAnchor() ? getRespawnAnchorMaxCharges(bedData) : 0;
            entries.add(new BedMenuEntry(entry.getKey(), bedData, displayName, status, remainingSeconds,
                    currentCharges, maxCharges));
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
            return BedStatus.AVAILABLE;
        }
        if (!player.hasPermission("multiplebedspawn.skipcooldown") && bedData.getBedCooldown() > System.currentTimeMillis()) {
            return BedStatus.COOLDOWN;
        }
        return BedStatus.AVAILABLE;
    }

    private static List<Player> getShareCandidates(Player player) {
        List<Player> candidates = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(target -> {
            if (!target.getUniqueId().equals(player.getUniqueId())) {
                candidates.add(target);
            }
        });
        candidates.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return candidates;
    }

    private static List<String> buildBedLore(BedMenuEntry entry, boolean respawnMode) {
        List<String> lore = new ArrayList<>();
        boolean hasMetadata = appendBedMetadataLore(lore, entry);
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
            }
        }

        return lore;
    }

    private static boolean appendBedMetadataLore(List<String> lore, BedMenuEntry entry) {
        boolean hasMetadata = false;
        if (entry.bedData().isPrimary()) {
            lore.add(ChatColor.AQUA + plugin.message("bed-primary-label", "Primary bed"));
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
        return hasMetadata;
    }

    private static boolean hasDynamicEntries(List<BedMenuEntry> entries) {
        for (BedMenuEntry entry : entries) {
            if (entry.status() == BedStatus.COOLDOWN || entry.bedData().isRespawnAnchor()) {
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
        return createFillerItem(material, ChatColor.DARK_GRAY + " ");
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

    private static String stripColors(String value) {
        String stripped = ChatColor.stripColor(value);
        return stripped == null ? value : stripped;
    }

    private enum SessionMode {
        RESPAWN,
        MANAGE
    }

    private enum BedStatus {
        AVAILABLE,
        COOLDOWN,
        DEPLETED,
        DISABLED,
        MISSING
    }

    private record BedMenuEntry(String uuid, BedData bedData, String displayName, BedStatus status,
                                long remainingCooldownSeconds, int currentCharges, int maxCharges) {
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
