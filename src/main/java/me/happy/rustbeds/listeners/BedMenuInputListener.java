package me.happy.rustbeds.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.models.PlayerBedsData;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static me.happy.rustbeds.utils.PlayerUtils.loadPlayerBedsData;
import static me.happy.rustbeds.utils.PlayerUtils.savePlayerBedsData;

public class BedMenuInputListener implements Listener {
    private static final Map<UUID, RenamePrompt> RENAME_PROMPTS = new HashMap<>();

    private final RustBeds plugin;

    public BedMenuInputListener(RustBeds plugin) {
        this.plugin = plugin;
    }

    public static void beginRenamePrompt(Player player, String bedUuid, int returnPage) {
        RENAME_PROMPTS.put(player.getUniqueId(), RenamePrompt.player(bedUuid, returnPage));
    }

    public static void beginRenamePrompt(Player admin, UUID ownerId, String bedUuid, int returnPage) {
        RENAME_PROMPTS.put(admin.getUniqueId(), RenamePrompt.admin(ownerId, bedUuid, returnPage));
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        RenamePrompt prompt = RENAME_PROMPTS.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleRenameInput(event.getPlayer(), prompt, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        RENAME_PROMPTS.remove(event.getPlayer().getUniqueId());
    }

    private void handleRenameInput(Player player, RenamePrompt prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + plugin.message("rename-prompt-cancelled", "Renaming cancelled."));
            reopenPromptMenu(player, prompt);
            return;
        }

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

    private void reopenPromptMenu(Player player, RenamePrompt prompt) {
        if (prompt.isAdminPrompt()) {
            AdminBedsMenuHandler.openActionMenu(player, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
            return;
        }

        RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
    }

    private record RenamePrompt(UUID ownerId, String bedUuid, int returnPage) {
        static RenamePrompt player(String bedUuid, int returnPage) {
            return new RenamePrompt(null, bedUuid, returnPage);
        }

        static RenamePrompt admin(UUID ownerId, String bedUuid, int returnPage) {
            return new RenamePrompt(ownerId, bedUuid, returnPage);
        }

        boolean isAdminPrompt() {
            return ownerId != null;
        }
    }
}
