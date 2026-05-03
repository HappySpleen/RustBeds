package me.happy.rustbeds;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.happy.rustbeds.commands.RespawnMenuCommand;
import me.happy.rustbeds.listeners.*;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.utils.PlayerBedStore;
import me.happy.rustbeds.utils.RespawnAnchorStore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class RustBeds extends JavaPlugin {
    private static final int CURRENT_CONFIG_VERSION = 5;
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String LEGACY_PLUGIN_FOLDER_NAME = "MultipleBedSpawn";
    private static final String DATABASE_FILE_NAME = "respawn-points.db";
    private static final String SAFE_LOCATION_VERTICAL_RADIUS_PATH = "safe-location-search.vertical-radius-blocks";
    private static final String SAFE_LOCATION_REQUIRED_SPACE_HEIGHT_PATH =
            "safe-location-search.required-space-height-blocks";
    private static final double OLD_DEFAULT_SAFE_LOCATION_VERTICAL_RADIUS = 0.375D;
    private static final double OLD_DEFAULT_SAFE_LOCATION_REQUIRED_SPACE_HEIGHT = 1.625D;
    private static final double DEFAULT_SAFE_LOCATION_VERTICAL_RADIUS = 0.5D;
    private static final double DEFAULT_SAFE_LOCATION_REQUIRED_SPACE_HEIGHT = 1.5D;
    private static final double CONFIG_DOUBLE_EPSILON = 0.0000001D;
    private static final List<String> LEGACY_DATA_FILES = List.of(
            CONFIG_FILE_NAME,
            DATABASE_FILE_NAME,
            "bed-ownership.yml",
            "respawn-anchor-registry.yml"
    );
    private static final Set<String> REMOVED_CONFIG_PATHS = Set.of(
            "disable-sleeping",
            "remove-beds-gui"
    );

    public static final String ADMIN_PERMISSION = "rustbeds.admin";
    public static final String LEGACY_ADMIN_PERMISSION = "multiplebedspawn.admin";
    public static final String SKIP_COOLDOWN_PERMISSION = "rustbeds.skipcooldown";
    public static final String LEGACY_SKIP_COOLDOWN_PERMISSION = "multiplebedspawn.skipcooldown";
    public static final String MAXCOUNT_PERMISSION_PREFIX = "rustbeds.maxcount.";
    public static final String LEGACY_MAXCOUNT_PERMISSION_PREFIX = "multiplebedspawn.maxcount.";

    private Configuration messages;
    private PlayerBedStore playerBedStore;
    private RespawnAnchorStore respawnAnchorStore;

    private static RustBeds instance;

    @Override
    public void onEnable() {
        instance = this;

        migrateLegacyDataFolder();
        initializeConfig();
        createLanguageConfig();
        playerBedStore = new PlayerBedStore(this);
        respawnAnchorStore = new RespawnAnchorStore(this);

        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new BedMenuInputListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminBedsMenuHandler(this), this);
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

    public static RustBeds getInstance() {
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

    public long getOfflineRespawnPointDestroyedMessageDelayTicks() {
        return Math.max(0L, getConfig().getLong("offline-respawn-point-destroyed-message-delay-ticks", 100L));
    }

    public long getShareInviteExpirySeconds() {
        return Math.max(1L, getConfig().getLong("share-invite-expiry-seconds", 300L));
    }

    public boolean usesTransferSharingLanguage() {
        return getConfig().getBoolean("exclusive-bed");
    }

    public String sharingModeMessage(String shareKey, String shareFallback, String transferKey,
            String transferFallback) {
        return sharingModeMessage(usesTransferSharingLanguage(), shareKey, shareFallback, transferKey,
                transferFallback);
    }

    public String sharingModeMessage(boolean transfer, String shareKey, String shareFallback, String transferKey,
            String transferFallback) {
        return transfer
                ? message(transferKey, transferFallback)
                : message(shareKey, shareFallback);
    }

    public int getSafeLocationHorizontalRadiusBlocks() {
        return Math.max(0, getConfig().getInt("safe-location-search.horizontal-radius-blocks", 2));
    }

    public double getSafeLocationVerticalRadiusBlocks() {
        return Math.max(0.0D, getConfig().getDouble(SAFE_LOCATION_VERTICAL_RADIUS_PATH,
                DEFAULT_SAFE_LOCATION_VERTICAL_RADIUS));
    }

    public double getSafeLocationRequiredSpaceHeightBlocks() {
        return Math.max(0.0625D, getConfig().getDouble(SAFE_LOCATION_REQUIRED_SPACE_HEIGHT_PATH,
                DEFAULT_SAFE_LOCATION_REQUIRED_SPACE_HEIGHT));
    }

    public boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(LEGACY_ADMIN_PERMISSION);
    }

    public boolean hasSkipCooldownPermission(Permissible permissible) {
        return permissible.hasPermission(SKIP_COOLDOWN_PERMISSION)
                || permissible.hasPermission(LEGACY_SKIP_COOLDOWN_PERMISSION);
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

    private void migrateLegacyDataFolder() {
        File currentDataFolder = getDataFolder();
        File pluginsFolder = currentDataFolder.getParentFile();
        if (pluginsFolder == null) {
            return;
        }

        File legacyDataFolder = new File(pluginsFolder, LEGACY_PLUGIN_FOLDER_NAME);
        if (!legacyDataFolder.isDirectory()) {
            return;
        }

        if (!currentDataFolder.exists() && !currentDataFolder.mkdirs()) {
            getLogger().warning("Could not create plugin data folder at " + currentDataFolder.getAbsolutePath()
                    + " before migrating legacy data.");
            return;
        }

        for (String fileName : LEGACY_DATA_FILES) {
            migrateLegacyDataFile(legacyDataFolder, currentDataFolder, fileName);
        }

        removeLegacyFolderIfEmpty(legacyDataFolder.toPath());
    }

    private void migrateLegacyDataFile(File legacyDataFolder, File currentDataFolder, String fileName) {
        Path source = legacyDataFolder.toPath().resolve(fileName);
        if (!Files.isRegularFile(source)) {
            return;
        }

        Path target = currentDataFolder.toPath().resolve(fileName);
        if (Files.exists(target)) {
            getLogger().info("Keeping existing RustBeds " + fileName
                    + " and leaving the legacy MultipleBedSpawn copy in place.");
            return;
        }

        try {
            Files.move(source, target);
            getLogger().info("Migrated " + fileName + " from plugins/"
                    + LEGACY_PLUGIN_FOLDER_NAME + " to plugins/" + getDataFolder().getName() + ".");
        } catch (IOException exception) {
            getLogger().warning("Could not migrate legacy " + fileName + " from "
                    + LEGACY_PLUGIN_FOLDER_NAME + ": " + exception.getMessage());
        }
    }

    private void removeLegacyFolderIfEmpty(Path legacyFolder) {
        try (Stream<Path> children = Files.list(legacyFolder)) {
            if (children.findAny().isPresent()) {
                return;
            }
        } catch (IOException exception) {
            getLogger().warning("Could not inspect legacy data folder " + legacyFolder + ": "
                    + exception.getMessage());
            return;
        }

        try {
            Files.deleteIfExists(legacyFolder);
        } catch (IOException exception) {
            getLogger().warning("Could not delete empty legacy data folder " + legacyFolder + ": "
                    + exception.getMessage());
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
        applyConfigMigrations(existingConfig, mergedConfig);
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
            if (REMOVED_CONFIG_PATHS.contains(path)) {
                continue;
            }

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

    private void applyConfigMigrations(YamlConfiguration source, YamlConfiguration target) {
        int configVersion = source.isInt("config-version") ? source.getInt("config-version") : 0;
        if (configVersion >= 5) {
            return;
        }

        updateOldDefaultDouble(target, SAFE_LOCATION_VERTICAL_RADIUS_PATH,
                OLD_DEFAULT_SAFE_LOCATION_VERTICAL_RADIUS, DEFAULT_SAFE_LOCATION_VERTICAL_RADIUS);
        updateOldDefaultDouble(target, SAFE_LOCATION_REQUIRED_SPACE_HEIGHT_PATH,
                OLD_DEFAULT_SAFE_LOCATION_REQUIRED_SPACE_HEIGHT, DEFAULT_SAFE_LOCATION_REQUIRED_SPACE_HEIGHT);
    }

    private void updateOldDefaultDouble(YamlConfiguration config, String path, double oldDefault, double newDefault) {
        if (!config.isDouble(path) && !config.isInt(path)) {
            return;
        }

        if (Math.abs(config.getDouble(path) - oldDefault) <= CONFIG_DOUBLE_EPSILON) {
            config.set(path, newDefault);
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
