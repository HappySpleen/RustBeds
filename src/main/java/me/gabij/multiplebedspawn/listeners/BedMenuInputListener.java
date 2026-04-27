package me.gabij.multiplebedspawn.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import me.gabij.multiplebedspawn.utils.PluginKeys;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BedMenuInputListener implements Listener {
    private static final Map<UUID, RenamePrompt> RENAME_PROMPTS = new HashMap<>();

    private final MultipleBedSpawn plugin;

    public BedMenuInputListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    public static void beginRenamePrompt(Player player, String bedUuid, int returnPage) {
        RENAME_PROMPTS.put(player.getUniqueId(), new RenamePrompt(bedUuid, returnPage));
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
            RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
            return;
        }

        if (input.isBlank()) {
            player.sendMessage(ChatColor.RED + plugin.message("rename-prompt-empty", "Bed name cannot be empty."));
            RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
            return;
        }

        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (!playerData.has(PluginKeys.beds(), PluginKeys.bedsDataType())) {
            player.sendMessage(ChatColor.RED + plugin.message("bed-not-registered-message",
                    "You have not registered this bed!"));
            RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
            return;
        }

        PlayerBedsData playerBedsData = playerData.get(PluginKeys.beds(), PluginKeys.bedsDataType());
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
        playerData.set(PluginKeys.beds(), PluginKeys.bedsDataType(), playerBedsData);
        player.sendMessage(plugin.message("bed-name-registered-successfully-message",
                "Bed name registered successfully!"));
        RespawnMenuHandler.openManageMenu(player, prompt.returnPage());
    }

    private record RenamePrompt(String bedUuid, int returnPage) {
    }
}
