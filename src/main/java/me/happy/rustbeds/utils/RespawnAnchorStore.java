package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class RespawnAnchorStore implements AutoCloseable {
    private static final String DATABASE_FILE_NAME = "respawn-points.db";
    private static final String LEGACY_YAML_MIGRATED_KEY = "legacy-respawn-anchor-yaml-migrated";

    private final RustBeds plugin;
    private final File databaseFile;
    private final File legacyDataFile;

    private Connection connection;

    public RespawnAnchorStore(RustBeds plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), DATABASE_FILE_NAME);
        this.legacyDataFile = new File(plugin.getDataFolder(), "respawn-anchor-registry.yml");
        reload();
    }

    public synchronized void reload() {
        ensureConnection();
        initializeSchema();
        migrateLegacyYaml();
    }

    public synchronized String getAnchorUuid(Location location) {
        String locationKey = toLocationKey(location);
        if (locationKey == null) {
            return null;
        }

        String sql = "SELECT anchor_uuid FROM respawn_anchors WHERE location_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("anchor_uuid") : null;
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not load respawn anchor UUID for " + locationKey, exception);
            return null;
        }
    }

    public synchronized void bindAnchor(String uuid, Location location) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }

        String locationKey = toLocationKey(location);
        if (locationKey == null) {
            return;
        }

        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteByUuid = connection.prepareStatement(
                    "DELETE FROM respawn_anchors WHERE anchor_uuid = ? OR location_key = ?")) {
                deleteByUuid.setString(1, uuid);
                deleteByUuid.setString(2, locationKey);
                deleteByUuid.executeUpdate();
            }

            try (PreparedStatement insertStatement = connection.prepareStatement(
                    "INSERT INTO respawn_anchors (anchor_uuid, location_key) VALUES (?, ?)")) {
                insertStatement.setString(1, uuid);
                insertStatement.setString(2, locationKey);
                insertStatement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            logSqlWarning("Could not bind respawn anchor " + uuid + " to " + locationKey, exception);
        } finally {
            resetAutoCommitQuietly(originalAutoCommit);
        }
    }

    public synchronized boolean isAnchorRegistered(Location location, String uuid) {
        String storedUuid = getAnchorUuid(location);
        return storedUuid != null && storedUuid.equalsIgnoreCase(uuid);
    }

    public synchronized void clearAnchor(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM respawn_anchors WHERE anchor_uuid = ?")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logSqlWarning("Could not clear respawn anchor " + uuid, exception);
        }
    }

    public synchronized void clearAnchor(Location location) {
        String locationKey = toLocationKey(location);
        if (locationKey == null) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM respawn_anchors WHERE location_key = ?")) {
            statement.setString(1, locationKey);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logSqlWarning("Could not clear respawn anchor at " + locationKey, exception);
        }
    }

    private String toLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.getWorld().getName()
                + ";" + location.getBlockX()
                + ";" + location.getBlockY()
                + ";" + location.getBlockZ();
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            logSqlWarning("Could not close respawn anchor database", exception);
        } finally {
            connection = null;
        }
    }

    private void ensureConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            File parent = databaseFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not open respawn anchor database", exception);
        }
    }

    private void initializeSchema() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS respawn_anchors (
                        anchor_uuid TEXT PRIMARY KEY,
                        location_key TEXT NOT NULL UNIQUE
                    )
                    """);
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_respawn_anchors_location_key "
                    + "ON respawn_anchors (location_key)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize respawn anchor schema", exception);
        }
    }

    private void migrateLegacyYaml() {
        if (isMetaFlagSet(LEGACY_YAML_MIGRATED_KEY)) {
            return;
        }

        if (!legacyDataFile.exists()) {
            setMetaFlag(LEGACY_YAML_MIGRATED_KEY);
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(legacyDataFile);
        ConfigurationSection uuidsSection = data.getConfigurationSection("uuids");
        if (uuidsSection != null) {
            for (String uuid : uuidsSection.getKeys(false)) {
                String locationKey = uuidsSection.getString(uuid);
                if (uuid == null || uuid.isBlank() || locationKey == null || locationKey.isBlank()) {
                    continue;
                }

                bindAnchorByLocationKey(uuid, locationKey);
            }
        }

        setMetaFlag(LEGACY_YAML_MIGRATED_KEY);
    }

    private void bindAnchorByLocationKey(String uuid, String locationKey) {
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteStatement = connection.prepareStatement(
                    "DELETE FROM respawn_anchors WHERE anchor_uuid = ? OR location_key = ?")) {
                deleteStatement.setString(1, uuid);
                deleteStatement.setString(2, locationKey);
                deleteStatement.executeUpdate();
            }

            try (PreparedStatement insertStatement = connection.prepareStatement(
                    "INSERT INTO respawn_anchors (anchor_uuid, location_key) VALUES (?, ?)")) {
                insertStatement.setString(1, uuid);
                insertStatement.setString(2, locationKey);
                insertStatement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            logSqlWarning("Could not migrate respawn anchor " + uuid + " from legacy YAML", exception);
        } finally {
            resetAutoCommitQuietly(originalAutoCommit);
        }
    }

    private boolean isMetaFlagSet(String key) {
        String sql = "SELECT value FROM meta WHERE key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not read metadata flag " + key, exception);
            return false;
        }
    }

    private void setMetaFlag(String key) {
        String sql = "INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, "1");
            statement.executeUpdate();
        } catch (SQLException exception) {
            logSqlWarning("Could not save metadata flag " + key, exception);
        }
    }

    private void rollbackQuietly() {
        try {
            if (connection != null && !connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not rollback respawn anchor transaction", exception);
        }
    }

    private void resetAutoCommitQuietly(boolean originalAutoCommit) {
        try {
            if (connection != null) {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not restore auto-commit for respawn anchor database", exception);
        }
    }

    private void logSqlWarning(String message, SQLException exception) {
        plugin.getLogger().warning(message + ": " + exception.getMessage());
    }
}
