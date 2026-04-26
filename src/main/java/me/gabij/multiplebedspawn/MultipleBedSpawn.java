package me.gabij.multiplebedspawn;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.gabij.multiplebedspawn.commands.NameCommand;
import me.gabij.multiplebedspawn.commands.RemoveCommand;
import me.gabij.multiplebedspawn.commands.RespawnMenuCommand;
import me.gabij.multiplebedspawn.commands.ShareCommand;
import me.gabij.multiplebedspawn.listeners.*;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.List;

public final class MultipleBedSpawn extends JavaPlugin {

    private Configuration messages;

    private static MultipleBedSpawn instance;

    @Override
    public void onEnable() {
        instance = this;

        getConfig().options().copyDefaults(true);
        saveConfig();
        createLanguageConfig();

        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new RemoveMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new PlayerGetsOnBedListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(RespawnMenuCommand.LABEL, RespawnMenuCommand.DESCRIPTION, List.of(),
                    new RespawnMenuCommand(this));
            commands.register(NameCommand.LABEL, NameCommand.DESCRIPTION, List.of(), new NameCommand(this));

            if (this.getConfig().getBoolean("remove-beds-gui")) {
                commands.register(RemoveCommand.LABEL, RemoveCommand.DESCRIPTION, List.of(), new RemoveCommand());
            }
            if (this.getConfig().getBoolean("bed-sharing")) {
                commands.register(ShareCommand.LABEL, ShareCommand.DESCRIPTION, List.of(), new ShareCommand(this));
            }
        });

        this.getLogger().info("Commands registered with Paper lifecycle manager");
    }

    public static MultipleBedSpawn getInstance() {
        return instance;
    }

    // get message of selected language
    public String getMessages(String path) {
        return this.messages.getString(path);
    }

    private void createLanguageConfig() {
        String lang = this.getConfig().getString("lang");
        InputStream input;
        try { // tries getting selected languages
            input = getClass().getClassLoader().getResourceAsStream("languages/{key}.yml".replace("{key}", lang));
            this.messages = YamlConfiguration.loadConfiguration(new InputStreamReader(input));
        } catch (Exception e) { // else sets enUS as default
            input = getClass().getClassLoader().getResourceAsStream("languages/enUS.yml");
            this.messages = YamlConfiguration.loadConfiguration(new InputStreamReader(input));
        }
    }
}
