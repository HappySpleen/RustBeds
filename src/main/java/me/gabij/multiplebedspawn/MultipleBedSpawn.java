package me.gabij.multiplebedspawn;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.gabij.multiplebedspawn.commands.RespawnMenuCommand;
import me.gabij.multiplebedspawn.listeners.*;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.utils.PlayerBedStore;
import me.gabij.multiplebedspawn.utils.RespawnAnchorStore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public final class MultipleBedSpawn extends JavaPlugin {
    private static final int CURRENT_CONFIG_VERSION = 1;
    private static final String CONFIG_FILE_NAME = "config.yml";

    private Configuration messages;
    private PlayerBedStore playerBedStore;
    private RespawnAnchorStore respawnAnchorStore;

    private static MultipleBedSpawn instance;

    @Override
    public void onEnable() {
        instance = this;

        initializeConfig();
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

    public String registrationSuccessMessage(BedData.RespawnPointType respawnPointType) {
        if (respawnPointType == BedData.RespawnPointType.ANCHOR) {
            return message("anchor-registered-successfully-message", "Respawn Anchor Registered Successfully");
        }

        return message("bed-registered-successfully-message", "Bed Registered Successfully");
    }

    public String renameSuccessMessage(BedData.RespawnPointType respawnPointType) {
        if (respawnPointType == BedData.RespawnPointType.ANCHOR) {
            return message("anchor-name-change-success-message", "Respawn Anchor name change successful!");
        }

        return message("bed-name-change-success-message", "Bed name change successful!");
    }

    public boolean isWorldEnabled(String worldName) {
        List<String> denylist = getConfig().getStringList("denylist");
        List<String> allowlist = getConfig().getStringList("allowlist");
        return !denylist.contains(worldName) && (allowlist.contains(worldName) || allowlist.isEmpty());
    }

    public void reloadPluginSettings() {
        initializeConfig();
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

    private void initializeConfig() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("Could not create plugin data folder at " + dataFolder.getAbsolutePath() + ".");
        }

        File configFile = new File(dataFolder, CONFIG_FILE_NAME);
        try {
            if (!configFile.exists()) {
                saveDefaultConfig();
            } else {
                mergeConfigWithTemplate(configFile);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().severe("Could not update config.yml with bundled comments: " + exception.getMessage());
        }

        reloadConfig();
    }

    private void mergeConfigWithTemplate(File configFile) throws IOException, InvalidConfigurationException {
        String existingContent = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        YamlConfiguration existingConfig = loadConfigWithComments(existingContent);
        YamlConfiguration mergedConfig = loadConfigWithComments(readBundledConfigTemplate());

        copyConfiguredValues(existingConfig, mergedConfig);
        copyCustomValues(existingConfig, mergedConfig);
        syncConfigVersion(mergedConfig);

        String mergedContent = mergedConfig.saveToString();
        if (!normalizeLineEndings(existingContent).equals(normalizeLineEndings(mergedContent))) {
            Files.writeString(configFile.toPath(), mergedContent, StandardCharsets.UTF_8);
        }
    }

    private YamlConfiguration loadConfigWithComments(String content) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        config.loadFromString(content);
        return config;
    }

    private String readBundledConfigTemplate() throws IOException {
        try (InputStream inputStream = Objects.requireNonNull(getResource(CONFIG_FILE_NAME),
                "Bundled config.yml is missing")) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void copyConfiguredValues(YamlConfiguration source, YamlConfiguration target) {
        for (String path : target.getKeys(true)) {
            if (target.isConfigurationSection(path) || !source.isSet(path)) {
                continue;
            }

            target.set(path, source.get(path));
        }
    }

    private void copyCustomValues(YamlConfiguration source, YamlConfiguration target) {
        for (String path : source.getKeys(true)) {
            if (target.contains(path)) {
                continue;
            }

            if (source.isConfigurationSection(path)) {
                target.createSection(path);
                copyComments(source, target, path);
                continue;
            }

            ensureParentSections(target, source, path);
            target.set(path, source.get(path));
            copyComments(source, target, path);
        }
    }

    private void ensureParentSections(YamlConfiguration target, YamlConfiguration source, String path) {
        int separatorIndex = path.lastIndexOf('.');
        while (separatorIndex >= 0) {
            String parentPath = path.substring(0, separatorIndex);
            if (!target.isConfigurationSection(parentPath)) {
                target.createSection(parentPath);
                if (source.isConfigurationSection(parentPath)) {
                    copyComments(source, target, parentPath);
                }
            }
            separatorIndex = parentPath.lastIndexOf('.');
        }
    }

    private void copyComments(ConfigurationSection source, ConfigurationSection target, String path) {
        target.setComments(path, source.getComments(path));
        target.setInlineComments(path, source.getInlineComments(path));
    }

    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n");
    }

    private void syncConfigVersion(YamlConfiguration config) {
        if (!config.isInt("config-version")) {
            if (config.contains("config-version")) {
                getLogger().warning("config.yml has a non-numeric config-version. Resetting it to "
                        + CURRENT_CONFIG_VERSION + ".");
            }

            config.set("config-version", CURRENT_CONFIG_VERSION);
            return;
        }

        int configVersion = config.getInt("config-version");
        if (configVersion < CURRENT_CONFIG_VERSION) {
            getLogger().warning("Outdated config.yml version " + configVersion + " detected. Updating it to "
                    + CURRENT_CONFIG_VERSION + " and applying any new default values.");
            config.set("config-version", CURRENT_CONFIG_VERSION);
            return;
        }

        if (configVersion > CURRENT_CONFIG_VERSION) {
            getLogger().warning("config.yml version " + configVersion + " is newer than this plugin supports ("
                    + CURRENT_CONFIG_VERSION + ").");
        }
    }
}
