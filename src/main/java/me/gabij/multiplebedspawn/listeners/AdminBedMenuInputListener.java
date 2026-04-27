package me.gabij.multiplebedspawn.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
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

import static me.gabij.multiplebedspawn.utils.PlayerUtils.loadPlayerBedsData;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.savePlayerBedsData;

public class AdminBedMenuInputListener implements Listener {
    private static final Map<UUID, RenamePrompt> RENAME_PROMPTS = new HashMap<>();

    private final MultipleBedSpawn plugin;

    public AdminBedMenuInputListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    public static void beginRenamePrompt(Player admin, UUID ownerId, String bedUuid, int returnPage) {
        RENAME_PROMPTS.put(admin.getUniqueId(), new RenamePrompt(ownerId, bedUuid, returnPage));
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

    private void handleRenameInput(Player admin, RenamePrompt prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            admin.sendMessage(ChatColor.YELLOW + plugin.message("rename-prompt-cancelled", "Renaming cancelled."));
            AdminBedsMenuHandler.openActionMenu(admin, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
            return;
        }

        if (input.isBlank()) {
            admin.sendMessage(ChatColor.RED + plugin.message("rename-prompt-empty", "Bed name cannot be empty."));
            AdminBedsMenuHandler.openActionMenu(admin, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
            return;
        }

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
        admin.sendMessage(ChatColor.YELLOW + plugin.message("admin-beds-rename-success", "Renamed {1}'s bed to {2}.")
                .replace("{1}", owner.getName() == null ? owner.getUniqueId().toString() : owner.getName())
                .replace("{2}", input));
        AdminBedsMenuHandler.openActionMenu(admin, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
    }

    private record RenamePrompt(UUID ownerId, String bedUuid, int returnPage) {
    }
}
