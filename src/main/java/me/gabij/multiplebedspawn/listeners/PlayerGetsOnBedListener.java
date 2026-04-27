package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.util.UUID;

import static me.gabij.multiplebedspawn.utils.BedsUtils.getMaxNumberOfBeds;
import static me.gabij.multiplebedspawn.utils.BedsUtils.getOrCreateRespawnPointUuid;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.getPlayerBedsCount;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.loadPlayerBedsData;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.savePlayerBedsData;

public class PlayerGetsOnBedListener implements Listener {

    MultipleBedSpawn plugin;

    public PlayerGetsOnBedListener(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerGetOnBed(PlayerBedEnterEvent e) {
        Player player = e.getPlayer();
        if (!plugin.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        if (e.enterAction().problem() != null || !e.enterAction().canSetSpawn().success()) {
            return;
        }

        Block bed = e.getBed();

        int maxBeds = getMaxNumberOfBeds(player);
        PlayerBedsData playerBedsData = null;

        int playerBedsCount = getPlayerBedsCount(player);

        playerBedsData = loadPlayerBedsData(player);

        if (playerBedsCount < maxBeds) {
            UUID randomUUID = UUID.randomUUID();
            String savedUuid = getOrCreateRespawnPointUuid(bed, randomUUID.toString());
            if (savedUuid == null) {
                return;
            }
            randomUUID = UUID.fromString(savedUuid);

            boolean alreadyRegisteredByPlayer = playerBedsData != null
                    && playerBedsData.hasBed(randomUUID.toString());
            if (!alreadyRegisteredByPlayer
                    && plugin.getConfig().getBoolean("exclusive-bed")
                    && plugin.getPlayerBedStore()
                            .hasOwnerOtherThan(randomUUID.toString(), player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + plugin.message("bed-already-has-owner",
                        "This bed already belongs to another player."));
                return;
            }

            boolean registerBed = false;
            if (playerBedsData == null) { // if the player doesnt have any bed

                playerBedsData = new PlayerBedsData(player, bed, randomUUID.toString());
                registerBed = true;

            } else if (!playerBedsData.hasBed(randomUUID.toString())) {

                playerBedsData.setNewBed(player, bed, randomUUID.toString());
                registerBed = true;

            }

            if (registerBed) {
                savePlayerBedsData(player, playerBedsData);
                player.sendMessage(plugin.message("bed-registered-successfully-message",
                        "Bed registered successfully!"));
            }

        } else {
            player.sendMessage(ChatColor.RED + plugin.message("max-beds-message",
                    "You have reached the maximum amount of saved beds."));
        }
    }

}
