package me.gabij.multiplebedspawn.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import static me.gabij.multiplebedspawn.listeners.RespawnMenuHandler.openCommandMenu;

public class RespawnMenuCommand implements BasicCommand {
    public static final String LABEL = "beds";
    public static final String DESCRIPTION = "Opens the beds menu";

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        if (commandSourceStack.getSender() instanceof Player player) {
            openCommandMenu(player);
        }
    }
}
