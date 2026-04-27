package me.gabij.multiplebedspawn.listeners;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import me.gabij.multiplebedspawn.utils.PluginKeys;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

import static me.gabij.multiplebedspawn.utils.BedsUtils.getMaxNumberOfBeds;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.getPlayerBedsCount;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.loadPlayerBedsData;

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
        PersistentDataContainer playerData = player.getPersistentDataContainer();

        int maxBeds = getMaxNumberOfBeds(player);
        PlayerBedsData playerBedsData = null;

        int playerBedsCount = getPlayerBedsCount(player);

        playerBedsData = loadPlayerBedsData(player);

        if (playerBedsCount < maxBeds) {
            UUID randomUUID = UUID.randomUUID();
            BlockState blockState = bed.getState();

            if (blockState instanceof TileState tileState) { // sets a randomUUID to the bed if the bed doesnt have it or get the bed uuid
                PersistentDataContainer container = tileState.getPersistentDataContainer();

                if (!container.has(PluginKeys.uuid(), PersistentDataType.STRING)) {
                    container.set(PluginKeys.uuid(), PersistentDataType.STRING, randomUUID.toString());
                } else {
                    randomUUID = UUID.fromString(container.get(PluginKeys.uuid(), PersistentDataType.STRING));
                    boolean alreadyRegisteredByPlayer = playerBedsData != null
                            && playerBedsData.hasBed(randomUUID.toString());
                    if (!alreadyRegisteredByPlayer
                            && plugin.getConfig().getBoolean("exclusive-bed")
                            && plugin.getBedOwnershipStore()
                                    .hasOwnerOtherThan(randomUUID.toString(), player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + plugin.message("bed-already-has-owner",
                                "This bed already belongs to another player."));
                        return;
                    }
                }

                tileState.update();
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
                playerData.set(PluginKeys.beds(), PluginKeys.bedsDataType(), playerBedsData);
                plugin.getBedOwnershipStore().syncPlayerBeds(player);
                player.sendMessage(plugin.message("bed-registered-successfully-message",
                        "Bed registered successfully!"));
            }

        } else {
            player.sendMessage(ChatColor.RED + plugin.message("max-beds-message",
                    "You have reached the maximum amount of saved beds."));
        }
    }

}
