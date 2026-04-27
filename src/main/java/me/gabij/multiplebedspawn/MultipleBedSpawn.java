package me.gabij.multiplebedspawn;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.gabij.multiplebedspawn.commands.RespawnMenuCommand;
import me.gabij.multiplebedspawn.listeners.*;
import me.gabij.multiplebedspawn.utils.PlayerBedStore;
import me.gabij.multiplebedspawn.utils.RespawnAnchorStore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.List;

public final class MultipleBedSpawn extends JavaPlugin {

    private Configuration messages;
    private PlayerBedStore playerBedStore;
    private RespawnAnchorStore respawnAnchorStore;

    private static MultipleBedSpawn instance;

    @Override
    public void onEnable() {
        instance = this;

        getConfig().options().copyDefaults(true);
        saveConfig();
        createLanguageConfig();
        playerBedStore = new PlayerBedStore(this);
        respawnAnchorStore = new RespawnAnchorStore(this);

        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new BedMenuInputListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminBedsMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new AdminBedMenuInputListener(this), this);
        getServer().getPluginManager().registerEvents(new BedDestroyedListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerGetsOnBedListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnAnchorListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerSetSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(RespawnMenuCommand.LABEL, RespawnMenuCommand.DESCRIPTION, List.of(),
                    new RespawnMenuCommand(this));
        });

        this.getLogger().info("Commands registered with Paper lifecycle manager");
    }

    public static MultipleBedSpawn getInstance() {
        return instance;
    }

    public PlayerBedStore getPlayerBedStore() {
        return playerBedStore;
    }

    public RespawnAnchorStore getRespawnAnchorStore() {
        return respawnAnchorStore;
    }

    // get message of selected language
    public String getMessages(String path) {
        return this.messages.getString(path);
    }

    public String message(String path, String fallback) {
        String value = getMessages(path);
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public boolean isWorldEnabled(String worldName) {
        List<String> denylist = getConfig().getStringList("denylist");
        List<String> allowlist = getConfig().getStringList("allowlist");
        return !denylist.contains(worldName) && (allowlist.contains(worldName) || allowlist.isEmpty());
    }

    public void reloadPluginSettings() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        createLanguageConfig();
        playerBedStore.reload();
        respawnAnchorStore.reload();
    }

    @Override
    public void onDisable() {
        if (playerBedStore != null) {
            playerBedStore.close();
        }
        if (respawnAnchorStore != null) {
            respawnAnchorStore.close();
        }
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
