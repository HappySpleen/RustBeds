package me.gabij.multiplebedspawn.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedsDataType;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static me.gabij.multiplebedspawn.utils.BedsUtils.checkIfIsBed;

public class ShareCommand implements BasicCommand {
    public static final String LABEL = "sharebed";
    public static final String DESCRIPTION = "Gives the bed you are looking at to another player";

    private final MultipleBedSpawn plugin;

    public ShareCommand(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        if (args.length != 1) {
            commandSourceStack.getSender().sendMessage(ChatColor.RED + "Usage: /sharebed <player>");
            return;
        }
        if (commandSourceStack.getSender() instanceof Player ownerPlayer) {
            Player receiverPlayer = Bukkit.getPlayer(args[0]);
            if (receiverPlayer == null) {
                ownerPlayer.sendMessage(ChatColor.RED + plugin.getMessages("player-not-found"));
                return;
            }
            if (receiverPlayer == ownerPlayer) {
                return;
            }
            Block bed = checkIfIsBed(ownerPlayer.getTargetBlockExact(4));
            if (bed != null) {
                BlockState blockState = bed.getState();
                String bedUUID = null;
                if (blockState instanceof TileState tileState) {
                    PersistentDataContainer container = tileState.getPersistentDataContainer();
                    if (container.has(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING)) {
                        bedUUID = container.get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING);
                    }
                }

                if (bedUUID == null) {
                    ownerPlayer.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-registered-message"));
                    return;
                }

                PlayerBedsData playerBedsData = null;
                PersistentDataContainer playerData = ownerPlayer.getPersistentDataContainer();

                if (playerData.has(new NamespacedKey(plugin, "beds"), new BedsDataType())) {
                    playerBedsData = playerData.get(new NamespacedKey(plugin, "beds"), new BedsDataType());
                    if (playerBedsData != null && playerBedsData.getPlayerBedData() != null
                            && playerBedsData.hasBed(bedUUID)) {
                        PersistentDataContainer receiverData = receiverPlayer.getPersistentDataContainer();
                        PlayerBedsData receiverBedsData = receiverData.has(new NamespacedKey(plugin, "beds"),
                                new BedsDataType())
                                        ? receiverData.get(new NamespacedKey(plugin, "beds"), new BedsDataType())
                                        : new PlayerBedsData();

                        playerBedsData.shareBed(receiverBedsData, bedUUID);
                        receiverData.set(new NamespacedKey(plugin, "beds"), new BedsDataType(), receiverBedsData);
                        playerData.set(new NamespacedKey(plugin, "beds"), new BedsDataType(), playerBedsData);
                        plugin.getBedOwnershipStore().syncPlayerBeds(ownerPlayer);
                        plugin.getBedOwnershipStore().syncPlayerBeds(receiverPlayer);

                        ownerPlayer.sendMessage(ChatColor.YELLOW + plugin.getMessages("bed-shared-successfully-message")
                                .replace("{1}", receiverPlayer.getName()));
                        receiverPlayer.sendMessage(ChatColor.YELLOW + plugin.getMessages("bed-shared-received-message")
                                .replace("{1}", ownerPlayer.getName()));
                    } else {
                        ownerPlayer.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-registered-message"));
                    }
                }
            } else {
                plugin.getLogger().info("Not found");
                ownerPlayer.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-found-message"));
            }

        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (args.length > 1) {
            return List.of();
        }

        String input = args.length == 0 ? "" : args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }
}
