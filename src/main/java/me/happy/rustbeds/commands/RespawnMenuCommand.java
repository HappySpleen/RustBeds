package me.happy.rustbeds.commands;

import me.happy.rustbeds.RustBeds;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static me.happy.rustbeds.utils.KeyUtils.hasAny;
import static me.happy.rustbeds.listeners.RespawnMenuHandler.openRespawnMenu;

import java.util.ArrayList;

public class RespawnMenuCommand extends BukkitCommand {
    static RustBeds plugin;

    public RespawnMenuCommand(RustBeds plugin, String name) {
        super(name);
        RespawnMenuCommand.plugin = plugin;
        this.description = "Opens a menu with saved beds, only works if on death fails to open menu";
        this.usageMessage = "/respawnbed";
        this.setAliases(new ArrayList<String>());
    }

    @Override
    public boolean execute(CommandSender sender, String alias, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;

            PersistentDataContainer playerData = p.getPersistentDataContainer();

            if (hasAny(playerData, plugin.getName(), "hasProp", PersistentDataType.BOOLEAN)) {
                openRespawnMenu(p);
            }
        }
        return true;
    }
}
