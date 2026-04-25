package me.gabij.multiplebedspawn.commands;

import static me.gabij.multiplebedspawn.utils.BedsUtils.checkIfIsBed;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.ensureLegacyPlayerData;

import java.util.ArrayList;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.storage.StoredBed;

import java.sql.SQLException;

public class ShareCommand extends BukkitCommand {
    static MultipleBedSpawn plugin;

    public ShareCommand(MultipleBedSpawn plugin, String name) {
        super(name);
        ShareCommand.plugin = plugin;
        this.description = "Gives bed to another player";
        this.usageMessage = "/sharebed <player>";
        this.setAliases(new ArrayList<String>());
    }

    @Override
    public boolean execute(CommandSender sender, String alias, String[] args) {
        if (args.length != 1) {
            return false;
        }
        if (sender instanceof Player) {
            Player ownerPlayer = (Player) sender;
            Player receiverPlayer = Bukkit.getPlayer(args[0]);
            if (receiverPlayer == null) {
                ownerPlayer.sendMessage(ChatColor.RED + plugin.getMessages("player-not-found"));
                return false;
            }
            if (receiverPlayer == ownerPlayer) {
                return false;
            }
            ensureLegacyPlayerData(ownerPlayer);
            ensureLegacyPlayerData(receiverPlayer);
            Block bed = checkIfIsBed(ownerPlayer.getTargetBlockExact(4));
            if (bed != null) {
                try {
                    Optional<StoredBed> storedBed = plugin.getBedStorage()
                            .findBedByLocation(bed.getWorld(), bed.getX(), bed.getY(), bed.getZ());
                    if (storedBed.isPresent() && plugin.getBedStorage().transferOwnership(
                            storedBed.get().getBedId(), ownerPlayer, receiverPlayer)) {
                        receiverPlayer.sendMessage(plugin.getMessages("bed-registered-successfully-message"));
                    } else {
                        ownerPlayer.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-registered-message"));
                        return false;
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().warning("Could not share bed for " + ownerPlayer.getName() + ": "
                            + exception.getMessage());
                    return false;
                }
            } else {
                plugin.getLogger().info("Not found");
                ownerPlayer.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-found-message"));
                return false;
            }

        }
        return true;
    }
}
