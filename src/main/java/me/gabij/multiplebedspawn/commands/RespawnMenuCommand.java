package me.gabij.multiplebedspawn.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static me.gabij.multiplebedspawn.listeners.RespawnMenuHandler.openRespawnMenu;

public class RespawnMenuCommand implements BasicCommand {
    public static final String LABEL = "respawnbed";
    public static final String DESCRIPTION = "Opens a menu with saved beds if the respawn menu did not open";

    private final MultipleBedSpawn plugin;

    public RespawnMenuCommand(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        if (commandSourceStack.getSender() instanceof Player p) {
            PersistentDataContainer playerData = p.getPersistentDataContainer();

            if (playerData.has(new NamespacedKey(plugin, "hasProp"), PersistentDataType.BOOLEAN)) {
                openRespawnMenu(p);
            }
        }
    }
}
