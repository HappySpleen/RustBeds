package me.gabij.multiplebedspawn.commands;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.storage.StoredBed;

import static me.gabij.multiplebedspawn.utils.BedsUtils.checkIfIsBed;
import static me.gabij.multiplebedspawn.utils.PlayerUtils.ensureLegacyPlayerData;

import java.util.ArrayList;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class NameCommand extends BukkitCommand {
    static MultipleBedSpawn plugin;

    public NameCommand(MultipleBedSpawn plugin, String name) {
        super(name);
        NameCommand.plugin = plugin;
        this.description = "Changes the name of the bed you are looking at";
        this.usageMessage = "/renamebed <name of the bed>";
        this.setAliases(new ArrayList<String>());
    }

    @Override
    public boolean execute(CommandSender sender, String alias, String[] args) {
        if (sender instanceof Player) {
            if (args.length == 0) {
                return false;
            }
            String name = String.join(" ", args).trim();
            Player p = (Player) sender;
            ensureLegacyPlayerData(p);
            Block bed = checkIfIsBed(p.getTargetBlockExact(4));
            if (bed != null) {
                try {
                    Optional<StoredBed> storedBed = plugin.getBedStorage()
                            .findBedByLocation(bed.getWorld(), bed.getX(), bed.getY(), bed.getZ());
                    if (storedBed.isPresent() && plugin.getBedStorage()
                            .playerOwnsBed(p.getUniqueId(), storedBed.get().getBedId())) {
                        plugin.getBedStorage().renameBed(storedBed.get().getBedId(), name);
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                plugin.getMessages("bed-name-registered-successfully-message")));
                    } else {
                        p.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-registered-message"));
                        return false;
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().warning("Could not rename bed for " + p.getName() + ": "
                            + exception.getMessage());
                    return false;
                }
            } else {
                p.sendMessage(ChatColor.RED + plugin.getMessages("bed-not-found-message"));
                return false;
            }

        }
        return true;
    }

}
