package me.gabij.multiplebedspawn.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.BedsDataType;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
            admin.sendMessage(ChatColor.YELLOW + message("rename-prompt-cancelled", "Renaming cancelled."));
            AdminBedsMenuHandler.openActionMenu(admin, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
            return;
        }

        if (input.isBlank()) {
            admin.sendMessage(ChatColor.RED + message("rename-prompt-empty", "Bed name cannot be empty."));
            AdminBedsMenuHandler.openActionMenu(admin, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
            return;
        }

        Player owner = Bukkit.getPlayer(prompt.ownerId());
        if (owner == null) {
            admin.sendMessage(ChatColor.RED + message("admin-beds-owner-offline",
                    "That player is no longer online."));
            AdminBedsMenuHandler.openOwnerMenu(admin, 0);
            return;
        }

        PersistentDataContainer ownerData = owner.getPersistentDataContainer();
        NamespacedKey bedsKey = new NamespacedKey(plugin, "beds");
        if (!ownerData.has(bedsKey, new BedsDataType())) {
            admin.sendMessage(ChatColor.RED + message("bed-not-registered-message",
                    "That player has no registered beds."));
            AdminBedsMenuHandler.openOwnerMenu(admin, 0);
            return;
        }

        PlayerBedsData playerBedsData = ownerData.get(bedsKey, new BedsDataType());
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            admin.sendMessage(ChatColor.RED + message("bed-not-registered-message",
                    "That player has no registered beds."));
            AdminBedsMenuHandler.openOwnerMenu(admin, 0);
            return;
        }

        BedData bedData = playerBedsData.getPlayerBedData().get(prompt.bedUuid());
        if (bedData == null) {
            admin.sendMessage(ChatColor.RED + message("bed-not-registered-message",
                    "That player no longer has that bed saved."));
            AdminBedsMenuHandler.openOwnerBedsMenu(admin, prompt.ownerId(), prompt.returnPage());
            return;
        }

        bedData.setBedName(input);
        ownerData.set(bedsKey, new BedsDataType(), playerBedsData);
        admin.sendMessage(ChatColor.YELLOW + message("admin-beds-rename-success", "Renamed {1}'s bed to {2}.")
                .replace("{1}", owner.getName())
                .replace("{2}", input));
        AdminBedsMenuHandler.openActionMenu(admin, prompt.ownerId(), prompt.returnPage(), prompt.bedUuid());
    }

    private String message(String key, String fallback) {
        String value = plugin.getMessages(key);
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private record RenamePrompt(UUID ownerId, String bedUuid, int returnPage) {
    }
}
