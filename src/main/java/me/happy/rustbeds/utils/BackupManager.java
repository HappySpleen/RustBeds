package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BackupManager {
    private static final String DATABASE_FILE_NAME = "respawn-points.db";
    private static final String CONFIG_BACKUP_FOLDER = "config";
    private static final String DATABASE_BACKUP_FOLDER = "DB";
    private static final String BACKUP_STATE_FILE_NAME = "backup-state.yml";
    private static final String DATABASE_BACKUP_BEFORE_UPDATE_PATH = "backups.database.backup-before-update";
    private static final String DATABASE_BACKUP_INTERVAL_DAYS_PATH = "backups.database.backup-interval-days";
    private static final String DATABASE_MAX_SCHEDULED_BACKUPS_PATH = "backups.database.max-scheduled-backups";
    private static final String LAST_PLUGIN_VERSION_KEY = "last-plugin-version";
    private static final String LAST_SCHEDULED_BACKUP_DATE_KEY = "last-scheduled-backup-date";
    private static final DateTimeFormatter CONFIG_BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern SCHEDULED_DATABASE_BACKUP_PATTERN =
            Pattern.compile("^respawn-points-(\\d{4}-\\d{2}-\\d{2})\\.db$");

    private final RustBeds plugin;

    public BackupManager(RustBeds plugin) {
        this.plugin = plugin;
    }

    public boolean backupConfigBeforeVersionUpdate(File configFile, String sourceConfigVersion,
            int targetConfigVersion) {
        if (configFile == null || !configFile.isFile()) {
            return false;
        }

        String backupName = "config-v" + sanitizeNamePart(sourceConfigVersion)
                + "-to-v" + targetConfigVersion
                + "-" + LocalDateTime.now().format(CONFIG_BACKUP_TIMESTAMP)
                + ".yml";
        BackupResult backupResult = copyBackup(configFile.toPath(), configBackupFolder(), backupName, true);
        if (backupResult.created()) {
            plugin.getLogger().info("Backed up config.yml before updating it to config version "
                    + targetConfigVersion + ".");
            logBackupLocation("Config backup", backupResult.path());
        }
        return backupResult.isUsable();
    }

    public void runStartupDatabaseBackups() {
        Path databaseFile = plugin.getDataFolder().toPath().resolve(DATABASE_FILE_NAME);
        boolean databaseExists = Files.isRegularFile(databaseFile);
        String pluginVersion = pluginVersion();
        YamlConfiguration backupState = loadBackupState();
        boolean stateChanged = false;

        boolean updateBackupSucceeded = true;
        if (plugin.getConfig().getBoolean(DATABASE_BACKUP_BEFORE_UPDATE_PATH, true)) {
            updateBackupSucceeded = backupDatabaseBeforeUpdate(databaseFile, databaseExists, pluginVersion,
                    backupState.getString(LAST_PLUGIN_VERSION_KEY));
        }

        if (databaseExists) {
            stateChanged |= runScheduledDatabaseBackup(databaseFile, backupState);
        }

        if (updateBackupSucceeded && !Objects.equals(backupState.getString(LAST_PLUGIN_VERSION_KEY), pluginVersion)) {
            backupState.set(LAST_PLUGIN_VERSION_KEY, pluginVersion);
            stateChanged = true;
        }

        if (stateChanged) {
            saveBackupState(backupState);
        }
    }

    private boolean backupDatabaseBeforeUpdate(Path databaseFile, boolean databaseExists, String pluginVersion,
            String lastPluginVersion) {
        if (!databaseExists || Objects.equals(lastPluginVersion, pluginVersion)) {
            return true;
        }

        String backupName = "respawn-points-v" + sanitizeNamePart(pluginVersion) + ".db";
        BackupResult backupResult = copyBackup(databaseFile, databaseBackupFolder(), backupName, false);
        if (backupResult.created()) {
            plugin.getLogger().info("Backed up respawn-points.db before opening it with RustBeds "
                    + pluginVersion + ".");
            logBackupLocation("Database update backup", backupResult.path());
        }
        return backupResult.isUsable();
    }

    private boolean runScheduledDatabaseBackup(Path databaseFile, YamlConfiguration backupState) {
        int intervalDays = Math.max(0, plugin.getConfig().getInt(DATABASE_BACKUP_INTERVAL_DAYS_PATH, 0));
        if (intervalDays <= 0) {
            return false;
        }

        boolean stateChanged = false;
        LocalDate today = LocalDate.now();
        LocalDate lastBackupDate = readBackupDate(backupState.getString(LAST_SCHEDULED_BACKUP_DATE_KEY))
                .or(() -> latestScheduledDatabaseBackupDate())
                .orElse(null);
        if (lastBackupDate == null || ChronoUnit.DAYS.between(lastBackupDate, today) >= intervalDays) {
            String backupName = "respawn-points-" + today + ".db";
            BackupResult backupResult = copyBackup(databaseFile, databaseBackupFolder(), backupName, false);
            if (backupResult.isUsable()) {
                if (backupResult.created()) {
                    plugin.getLogger().info("Created scheduled respawn-points.db backup for " + today + ".");
                    logBackupLocation("Scheduled database backup", backupResult.path());
                }
                backupState.set(LAST_SCHEDULED_BACKUP_DATE_KEY, today.toString());
                stateChanged = true;
            }
        }

        pruneScheduledDatabaseBackups();
        return stateChanged;
    }

    private void pruneScheduledDatabaseBackups() {
        int maxBackups = Math.max(1, plugin.getConfig().getInt(DATABASE_MAX_SCHEDULED_BACKUPS_PATH, 7));
        Path backupFolder = databaseBackupFolder();
        if (!Files.isDirectory(backupFolder)) {
            return;
        }

        try (Stream<Path> paths = Files.list(backupFolder)) {
            List<ScheduledDatabaseBackup> backups = paths
                    .map(this::scheduledDatabaseBackup)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(ScheduledDatabaseBackup::backupDate).reversed()
                            .thenComparing(ScheduledDatabaseBackup::path))
                    .toList();
            for (int index = maxBackups; index < backups.size(); index++) {
                try {
                    Files.deleteIfExists(backups.get(index).path());
                } catch (IOException exception) {
                    plugin.getLogger().warning("Could not delete old database backup "
                            + backups.get(index).path().getFileName() + ": " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not inspect database backups: " + exception.getMessage());
        }
    }

    private Optional<LocalDate> latestScheduledDatabaseBackupDate() {
        Path backupFolder = databaseBackupFolder();
        if (!Files.isDirectory(backupFolder)) {
            return Optional.empty();
        }

        try (Stream<Path> paths = Files.list(backupFolder)) {
            return paths
                    .map(this::scheduledDatabaseBackup)
                    .flatMap(Optional::stream)
                    .map(ScheduledDatabaseBackup::backupDate)
                    .max(Comparator.naturalOrder());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not inspect database backups: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ScheduledDatabaseBackup> scheduledDatabaseBackup(Path path) {
        Matcher matcher = SCHEDULED_DATABASE_BACKUP_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return readBackupDate(matcher.group(1))
                .map(backupDate -> new ScheduledDatabaseBackup(path, backupDate));
    }

    private Optional<LocalDate> readBackupDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private BackupResult copyBackup(Path sourceFile, Path backupFolder, String backupFileName, boolean makeUnique) {
        try {
            Files.createDirectories(backupFolder);
            Path backupFile = backupFolder.resolve(backupFileName);
            if (Files.exists(backupFile)) {
                if (!makeUnique) {
                    return new BackupResult(BackupStatus.EXISTS, backupFile);
                }
                backupFile = uniqueBackupFile(backupFolder, backupFileName);
            }

            Files.copy(sourceFile, backupFile, StandardCopyOption.COPY_ATTRIBUTES);
            return new BackupResult(BackupStatus.CREATED, backupFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create backup for " + sourceFile.getFileName()
                    + ": " + exception.getMessage());
            return new BackupResult(BackupStatus.FAILED, null);
        }
    }

    private void logBackupLocation(String label, Path backupFile) {
        if (backupFile == null) {
            return;
        }

        plugin.getServer().getConsoleSender().sendMessage(ChatColor.YELLOW
                + "[RustBeds] " + label + " location: " + backupFile.toAbsolutePath());
    }

    private Path uniqueBackupFile(Path backupFolder, String backupFileName) {
        int extensionIndex = backupFileName.lastIndexOf('.');
        String baseName = extensionIndex >= 0 ? backupFileName.substring(0, extensionIndex) : backupFileName;
        String extension = extensionIndex >= 0 ? backupFileName.substring(extensionIndex) : "";

        int counter = 1;
        Path candidate;
        do {
            candidate = backupFolder.resolve(baseName + "-" + counter + extension);
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }

    private YamlConfiguration loadBackupState() {
        File stateFile = backupStateFile().toFile();
        if (!stateFile.isFile()) {
            return new YamlConfiguration();
        }

        return YamlConfiguration.loadConfiguration(stateFile);
    }

    private void saveBackupState(YamlConfiguration backupState) {
        try {
            Files.createDirectories(databaseBackupFolder());
            backupState.save(backupStateFile().toFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save database backup state: " + exception.getMessage());
        }
    }

    private Path configBackupFolder() {
        return plugin.getDataFolder().toPath().resolve("backups").resolve(CONFIG_BACKUP_FOLDER);
    }

    private Path databaseBackupFolder() {
        return plugin.getDataFolder().toPath().resolve("backups").resolve(DATABASE_BACKUP_FOLDER);
    }

    private Path backupStateFile() {
        return databaseBackupFolder().resolve(BACKUP_STATE_FILE_NAME);
    }

    private String pluginVersion() {
        String version = plugin.getDescription().getVersion();
        if (version == null || version.isBlank()) {
            return "unknown";
        }
        return version;
    }

    private String sanitizeNamePart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private record ScheduledDatabaseBackup(Path path, LocalDate backupDate) {
    }

    private record BackupResult(BackupStatus status, Path path) {
        private boolean created() {
            return status == BackupStatus.CREATED;
        }

        private boolean isUsable() {
            return status != BackupStatus.FAILED;
        }
    }

    private enum BackupStatus {
        CREATED,
        EXISTS,
        FAILED
    }
}
