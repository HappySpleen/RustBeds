package me.gabij.multiplebedspawn.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import static me.gabij.multiplebedspawn.listeners.RemoveMenuHandler.openRemoveMenu;

public class RemoveCommand implements BasicCommand {
    public static final String LABEL = "removebed";
    public static final String DESCRIPTION = "Opens a menu to remove saved beds";

    public RemoveCommand() {
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        if (commandSourceStack.getSender() instanceof Player p) {
            openRemoveMenu(p);
        }
    }
}
