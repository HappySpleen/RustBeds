package me.gabij.multiplebedspawn;

import me.gabij.multiplebedspawn.commands.AdminBedsCommand;
import me.gabij.multiplebedspawn.commands.NameCommand;
import me.gabij.multiplebedspawn.commands.RemoveCommand;
import me.gabij.multiplebedspawn.commands.RespawnMenuCommand;
import me.gabij.multiplebedspawn.commands.ShareCommand;
import me.gabij.multiplebedspawn.listeners.*;
import me.gabij.multiplebedspawn.storage.BedStorage;

import org.bukkit.command.CommandMap;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

public final class MultipleBedSpawn extends JavaPlugin {

    private Configuration messages;
    private Configuration defaultMessages;
    private BedStorage bedStorage;

    private static MultipleBedSpawn instance;

    @Override
    public void onEnable() {
        instance = this;

        getConfig().options().copyDefaults(true);
        saveConfig();
        createLanguageConfig();
        initializeStorage();

        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new RemoveMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new PlayerGetsOnBedListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new BedBreakListener(this), this);

        try {
            CommandMap commandMap = me.gabij.multiplebedspawn.utils.CommandMapUtil.getCommandMap();
            commandMap.register(this.getName(), new RespawnMenuCommand(this, "respawnbed"));
            commandMap.register(this.getName(), new NameCommand(this, "renamebed"));
            commandMap.register(this.getName(), new AdminBedsCommand(this, "bedsadmin"));
            if (this.getConfig().getBoolean("remove-beds-gui")) {
                commandMap.register(this.getName(), new RemoveCommand(this, "removebed"));
            }
            if (this.getConfig().getBoolean("bed-sharing")) {
                commandMap.register(this.getName(), new ShareCommand(this, "sharebed"));
            }
            this.getLogger().info("Commands added successfully");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            this.getLogger().warning("Could not access commandMap. Commands will not work");
            this.getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (bedStorage != null) {
            bedStorage.close();
        }
    }

    public static MultipleBedSpawn getInstance() {
        return instance;
    }

    public BedStorage getBedStorage() {
        return bedStorage;
    }

    // get message of selected language
    public String getMessages(String path) {
        String message = this.messages.getString(path);
        if (message != null) {
            return message;
        }
        String fallback = this.defaultMessages.getString(path);
        return fallback != null ? fallback : path;
    }

    private void createLanguageConfig() {
        String lang = this.getConfig().getString("lang");
        InputStream input;
        InputStream defaultInput = getClass().getClassLoader().getResourceAsStream("languages/enUS.yml");
        this.defaultMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultInput));
        try { // tries getting selected languages
            input = getClass().getClassLoader().getResourceAsStream("languages/{key}.yml".replace("{key}", lang));
            this.messages = YamlConfiguration.loadConfiguration(new InputStreamReader(input));
        } catch (Exception e) { // else sets enUS as default
            this.messages = this.defaultMessages;
        }
    }

    private void initializeStorage() {
        try {
            this.bedStorage = new BedStorage(this);
            this.bedStorage.initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize bed storage", exception);
        }
    }
}
