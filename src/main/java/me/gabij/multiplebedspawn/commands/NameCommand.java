package me.gabij.multiplebedspawn.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.BedsDataType;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static me.gabij.multiplebedspawn.utils.BedsUtils.checkIfIsBed;

public class NameCommand implements BasicCommand {
    public static final String LABEL = "renamebed";
    public static final String DESCRIPTION = "Changes the name of the bed you are looking at";

    private final MultipleBedSpawn plugin;

    public NameCommand(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        if (commandSourceStack.getSender() instanceof Player p) {
            String name = String.join(" ", args).trim();
            if (name.isEmpty()) {
                p.sendMessage(ChatColor.RED + "Usage: /renamebed <name of the bed>");
                return;
            }

            Block bed = checkIfIsBed(p.getTargetBlockExact(4));
            if (bed != null) {
                BlockState blockState = bed.getState();
                String bedUUID = null;
                if (blockState instanceof TileState tileState) {
                    PersistentDataContainer container = tileState.getPersistentDataContainer();

                    if (container.has(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING)) {
                        bedUUID = container.get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING);
                    }

                    tileState.update();
                }

                if (bedUUID == null) {
                    p.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-registered-message"));
                    return;
                }

                PlayerBedsData playerBedsData = null;
                PersistentDataContainer playerData = p.getPersistentDataContainer();

                if (playerData.has(new NamespacedKey(plugin, "beds"), new BedsDataType())) {
                    playerBedsData = playerData.get(new NamespacedKey(plugin, "beds"), new BedsDataType());
                    if (playerBedsData != null && playerBedsData.getPlayerBedData() != null
                            && playerBedsData.hasBed(bedUUID)) {
                        BedData bedData = playerBedsData.getPlayerBedData().get(bedUUID);
                        bedData.setBedName(name);
                        playerData.set(new NamespacedKey(plugin, "beds"), new BedsDataType(), playerBedsData);
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                plugin.getMessages("bed-name-registered-successfully-message")));
                    } else {
                        p.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-registered-message"));
                    }
                }
            } else {
                p.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-found-message"));
            }
        }
    }
}
