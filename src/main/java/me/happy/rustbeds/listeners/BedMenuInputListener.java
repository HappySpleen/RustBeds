package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.models.PlayerBedsData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.happy.rustbeds.gui.MenuItems.hideMenuTooltipDetails;
import static me.happy.rustbeds.utils.PlayerUtils.loadPlayerBedsData;
import static me.happy.rustbeds.utils.PlayerUtils.savePlayerBedsData;

public class BedMenuInputListener implements Listener {
    private static final Map<UUID, RenamePrompt> RENAME_PROMPTS = new HashMap<>();
    private static final int ANVIL_RESULT_SLOT = 2;

    private static RustBeds plugin;

    public BedMenuInputListener(RustBeds plugin) {
        BedMenuInputListener.plugin = plugin;
    }

    public static void beginRenamePrompt(Player player, String bedUuid, int returnPage, String currentName) {
        RenamePrompt prompt = RenamePrompt.player(bedUuid, returnPage, currentName);
        RENAME_PROMPTS.put(player.getUniqueId(), prompt);
        openRenameAnvil(player, prompt);
    }

    public static void beginRenamePrompt(Player admin, UUID ownerId, String bedUuid, int returnPage,
            String currentName) {
        RenamePrompt prompt = RenamePrompt.admin(ownerId, bedUuid, returnPage, currentName);
        RENAME_PROMPTS.put(admin.getUniqueId(), prompt);
        openRenameAnvil(admin, prompt);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        Player player = getPromptPlayer(event.getView());
        if (player == null) {
            return;
        }

        RenamePrompt prompt = RENAME_PROMPTS.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }

        AnvilView view = event.getView();
        view.setRepairCost(0);
        view.setRepairItemCountCost(0);
        view.setMaximumRepairCost(0);
        event.setResult(createRenameResultItem(getAnvilRenameText(view, prompt)));
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        Player player = getPromptPlayer(event.getView());
        if (player == null) {
            return;
        }

        RenamePrompt prompt = RENAME_PROMPTS.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() != ANVIL_RESULT_SLOT) {
            return;
        }

        String input = getAnvilRenameText(event.getView(), prompt).trim();
        if (input.isBlank()) {
            player.sendMessage(ChatColor.RED + plugin.message("rename-prompt-empty",
                    "Respawn point name cannot be empty."));
            return;
        }

        RENAME_PROMPTS.remove(player.getUniqueId());
        player.closeInventory();
        handleRenameInput(player, prompt, input);
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        RenamePrompt prompt = RENAME_PROMPTS.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            player.sendMessage(ChatColor.YELLOW + plugin.message("rename-prompt-cancelled", "Renaming cancelled."));
            reopenPromptMenu(player, prompt);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        RENAME_PROMPTS.remove(event.getPlayer().getUniqueId());
    }

    private void handleRenameInput(Player player, RenamePrompt prompt, String input) {
        if (input.isBlank()) {
            player.sendMessage(ChatColor.RED + plugin.message("rename-prompt-empty", "Bed name cannot be empty."));
            reopenPromptMenu(player, prompt);
            return;
        }

        if (prompt.isAdminPrompt()) {
            handleAdminRenameInput(player, prompt, input);
            return;
        }

        handlePlayerRenameInput(player, prompt, input);
    }

    private void handlePlayerRenameInput(Player player, RenamePrompt prompt, String input) {
        PlayerBedsData playerBedsData = loadPlayerBedsData(player);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            player.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "You have not registered this bed!"));
            RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
            return;
        }

        BedData bedData = playerBedsData.getPlayerBedData().get(prompt.bedUuid());
        if (bedData == null) {
            player.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "You have not registered this bed!"));
            RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
            return;
        }

        bedData.setBedName(input);
        savePlayerBedsData(player, playerBedsData);
        player.sendMessage(plugin.renameSuccessMessage(bedData.getRespawnPointType()));
        RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
    }

    private void handleAdminRenameInput(Player admin, RenamePrompt prompt, String input) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(prompt.ownerId());
        PlayerBedsData playerBedsData = loadPlayerBedsData(owner);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "That player has no registered beds."));
            AdminBedsMenuHandler.openOwnerMenu(admin, 0);
            return;
        }

        BedData bedData = playerBedsData.getPlayerBedData().get(prompt.bedUuid());
        if (bedData == null) {
            admin.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "That player no longer has that bed saved."));
            AdminBedsMenuHandler.openOwnerBedsMenu(admin, prompt.ownerId(), prompt.returnPage());
            return;
        }

        bedData.setBedName(input);
        savePlayerBedsData(owner, playerBedsData);
        admin.sendMessage(ChatColor.YELLOW + plugin.renameSuccessMessage(bedData.getRespawnPointType()));
        AdminBedsMenuHandler.openActionMenu(admin, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
    }

    private static void reopenPromptMenu(Player player, RenamePrompt prompt) {
        if (prompt.isAdminPrompt()) {
            AdminBedsMenuHandler.openActionMenu(player, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
            return;
        }

        RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
    }

    private static void openRenameAnvil(Player player, RenamePrompt prompt) {
        InventoryView view = player.openAnvil(null, true);
        if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvilInventory)) {
            RENAME_PROMPTS.remove(player.getUniqueId());
            reopenPromptMenu(player, prompt);
            return;
        }

        view.setTitle(plugin.message("rename-anvil-title", "Rename respawn point"));
        anvilInventory.setFirstItem(createRenameInputItem(prompt.currentName()));
        anvilInventory.setRepairCost(0);
        anvilInventory.setMaximumRepairCost(0);
        anvilInventory.setResult(createRenameResultItem(prompt.currentName()));
    }

    private static ItemStack createRenameInputItem(String currentName) {
        ItemStack item = new ItemStack(Material.NAME_TAG, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(currentName);
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of(ChatColor.GRAY + plugin.message("rename-anvil-input-lore",
                "Enter a new respawn point name.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createRenameResultItem(String renameText) {
        ItemStack item = new ItemStack(Material.NAME_TAG, 1);
        ItemMeta meta = item.getItemMeta();
        String displayName = renameText == null || renameText.isBlank()
                ? plugin.message("rename-anvil-empty-name", "New respawn point name")
                : renameText;
        meta.setDisplayName(displayName);
        hideMenuTooltipDetails(meta);
        meta.setLore(List.of(ChatColor.GRAY + plugin.message("rename-anvil-save-lore",
                "Click the result to save this name.")));
        item.setItemMeta(meta);
        return item;
    }

    private static String getAnvilRenameText(InventoryView view, RenamePrompt prompt) {
        if (view instanceof AnvilView anvilView && anvilView.getRenameText() != null) {
            return anvilView.getRenameText();
        }
        if (view.getTopInventory() instanceof AnvilInventory anvilInventory
                && anvilInventory.getRenameText() != null) {
            return anvilInventory.getRenameText();
        }
        return prompt.currentName();
    }

    private static Player getPromptPlayer(InventoryView view) {
        if (!(view.getTopInventory() instanceof AnvilInventory)) {
            return null;
        }
        if (!(view.getPlayer() instanceof Player player)) {
            return null;
        }
        return player;
    }

    private static String normalizeCurrentName(String currentName) {
        String stripped = ChatColor.stripColor(currentName);
        if (stripped != null && !stripped.isBlank()) {
            return stripped.trim();
        }
        if (currentName != null && !currentName.isBlank()) {
            return currentName.trim();
        }
        return plugin.message("rename-anvil-empty-name", "New respawn point name");
    }

    private record RenamePrompt(UUID ownerId, String bedUuid, int returnPage, String currentName) {
        static RenamePrompt player(String bedUuid, int returnPage, String currentName) {
            return new RenamePrompt(null, bedUuid, returnPage, normalizeCurrentName(currentName));
        }

        static RenamePrompt admin(UUID ownerId, String bedUuid, int returnPage, String currentName) {
            return new RenamePrompt(ownerId, bedUuid, returnPage, normalizeCurrentName(currentName));
        }

        boolean isAdminPrompt() {
            return ownerId != null;
        }
    }
}
