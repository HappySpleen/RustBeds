package me.gabij.multiplebedspawn.utils;

import io.papermc.paper.persistence.PersistentDataContainerView;
import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerBedStore implements AutoCloseable {
    private static final String DATABASE_FILE_NAME = "respawn-points.db";
    private static final String LEGACY_YAML_MIGRATED_KEY = "legacy-bed-ownership-yaml-migrated";

    private final MultipleBedSpawn plugin;
    private final File databaseFile;

    private Connection connection;

    public PlayerBedStore(MultipleBedSpawn plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), DATABASE_FILE_NAME);
        reload();
    }

    public synchronized void reload() {
        ensureConnection();
        initializeSchema();
        importLegacyBeds(plugin.getServer().getOfflinePlayers());
        migrateLegacyOwnershipYaml();
    }

    public synchronized void importLegacyBeds(OfflinePlayer... players) {
        if (players == null || players.length == 0) {
            return;
        }

        for (OfflinePlayer player : players) {
            if (player == null) {
                continue;
            }

            String playerId = player.getUniqueId().toString();
            if (isLegacyImportComplete(playerId)) {
                continue;
            }

            PersistentDataContainerView playerData = player.getPersistentDataContainer();
            if (!playerData.has(PluginKeys.beds(), PluginKeys.bedsDataType())) {
                continue;
            }

            if (hasAnyBeds(player.getUniqueId())) {
                markLegacyImportComplete(playerId);
                continue;
            }

            PlayerBedsData legacyData = playerData.get(PluginKeys.beds(), PluginKeys.bedsDataType());
            if (legacyData != null && legacyData.getPlayerBedData() != null) {
                legacyData.normalizePrimarySelection();
                savePlayerBeds(player.getUniqueId(), legacyData);
            }

            markLegacyImportComplete(playerId);
        }
    }

    public synchronized PlayerBedsData loadPlayerBeds(UUID playerId) {
        ensureConnection();

        PlayerBedsData playerBedsData = new PlayerBedsData();
        String sql = "SELECT bed_uuid, respawn_point_type, bed_name, bed_material, bed_coords, "
                + "bed_spawn_coords, bed_world, bed_cooldown, is_primary, shared_by_name "
                + "FROM player_beds WHERE player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String bedUuid = resultSet.getString("bed_uuid");
                    BedData.RespawnPointType pointType = parseRespawnPointType(resultSet.getString("respawn_point_type"));
                    Material material = resolveMaterial(resultSet.getString("bed_material"), pointType);
                    BedData bedData = new BedData(
                            pointType,
                            resultSet.getString("bed_name"),
                            material,
                            resultSet.getString("bed_coords"),
                            resultSet.getString("bed_spawn_coords"),
                            resultSet.getString("bed_world"),
                            resultSet.getLong("bed_cooldown"),
                            resultSet.getInt("is_primary") == 1,
                            resultSet.getString("shared_by_name")
                    );
                    playerBedsData.getPlayerBedData().put(bedUuid, bedData);
                }
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not load player beds for " + playerId, exception);
            return null;
        }

        if (playerBedsData.getPlayerBedData().isEmpty()) {
            return null;
        }

        if (playerBedsData.normalizePrimarySelection()) {
            savePlayerBeds(playerId, playerBedsData);
        }
        return playerBedsData;
    }

    public synchronized void savePlayerBeds(UUID playerId, PlayerBedsData playerBedsData) {
        ensureConnection();

        PlayerBedsData normalizedData = playerBedsData;
        if (normalizedData != null) {
            normalizedData.normalizePrimarySelection();
        }

        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteStatement = connection.prepareStatement(
                    "DELETE FROM player_beds WHERE player_uuid = ?")) {
                deleteStatement.setString(1, playerId.toString());
                deleteStatement.executeUpdate();
            }

            if (normalizedData != null && normalizedData.getPlayerBedData() != null
                    && !normalizedData.getPlayerBedData().isEmpty()) {
                String insertSql = "INSERT INTO player_beds (player_uuid, bed_uuid, respawn_point_type, bed_name, "
                        + "bed_material, bed_coords, bed_spawn_coords, bed_world, bed_cooldown, is_primary, "
                        + "shared_by_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                    for (var entry : normalizedData.getPlayerBedData().entrySet()) {
                        BedData bedData = entry.getValue();
                        if (bedData == null) {
                            continue;
                        }

                        insertStatement.setString(1, playerId.toString());
                        insertStatement.setString(2, entry.getKey());
                        insertStatement.setString(3, bedData.getRespawnPointType().name());
                        insertStatement.setString(4, bedData.hasCustomName() ? bedData.getBedName() : null);
                        insertStatement.setString(5, bedData.getBedMaterial().name());
                        insertStatement.setString(6, bedData.getBedCoords());
                        insertStatement.setString(7, bedData.getBedSpawnCoords());
                        insertStatement.setString(8, bedData.getBedWorld());
                        insertStatement.setLong(9, bedData.getBedCooldown());
                        insertStatement.setInt(10, bedData.isPrimary() ? 1 : 0);
                        insertStatement.setString(11, bedData.getSharedByName());
                        insertStatement.addBatch();
                    }
                    insertStatement.executeBatch();
                }
            }

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            logSqlWarning("Could not save player beds for " + playerId, exception);
        } finally {
            resetAutoCommitQuietly(originalAutoCommit);
        }
    }

    public synchronized BedData removePlayerBed(UUID playerId, String bedUuid) {
        PlayerBedsData playerBedsData = loadPlayerBeds(playerId);
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null || !playerBedsData.hasBed(bedUuid)) {
            return null;
        }

        BedData removedBed = playerBedsData.getPlayerBedData().get(bedUuid);
        playerBedsData.removeBed(bedUuid);
        savePlayerBeds(playerId, playerBedsData);
        return removedBed;
    }

    public synchronized Set<UUID> getOwners(String bedUuid) {
        ensureConnection();

        Set<UUID> owners = new LinkedHashSet<>();
        String sql = "SELECT player_uuid FROM player_beds WHERE bed_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bedUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        owners.add(UUID.fromString(resultSet.getString("player_uuid")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not read owners for bed " + bedUuid, exception);
        }
        return owners;
    }

    public synchronized boolean hasAnyKnownOwner(String bedUuid) {
        ensureConnection();

        String sql = "SELECT 1 FROM player_beds WHERE bed_uuid = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bedUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not check owners for bed " + bedUuid, exception);
            return false;
        }
    }

    public synchronized boolean hasOwnerOtherThan(String bedUuid, UUID playerId) {
        ensureConnection();

        String sql = "SELECT 1 FROM player_beds WHERE bed_uuid = ? AND lower(player_uuid) != lower(?) LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bedUuid);
            statement.setString(2, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not check alternate owners for bed " + bedUuid, exception);
            return false;
        }
    }

    public synchronized List<UUID> getPlayersWithBeds() {
        ensureConnection();

        List<UUID> playerIds = new ArrayList<>();
        String sql = "SELECT DISTINCT player_uuid FROM player_beds";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID playerId = parseUuid(resultSet.getString("player_uuid"));
                if (playerId != null) {
                    playerIds.add(playerId);
                }
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not load players with saved beds", exception);
        }

        return playerIds;
    }

    public synchronized void queuePendingMessage(UUID playerId, String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        ensureConnection();
        String sql = "INSERT INTO pending_messages (player_uuid, message) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, message);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logSqlWarning("Could not queue pending bed message for " + playerId, exception);
        }
    }

    public synchronized List<String> consumePendingMessages(UUID playerId) {
        ensureConnection();

        List<String> messages = new ArrayList<>();
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement selectStatement = connection.prepareStatement(
                    "SELECT id, message FROM pending_messages WHERE player_uuid = ? ORDER BY id ASC")) {
                selectStatement.setString(1, playerId.toString());
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    while (resultSet.next()) {
                        messages.add(resultSet.getString("message"));
                    }
                }
            }

            try (PreparedStatement deleteStatement = connection.prepareStatement(
                    "DELETE FROM pending_messages WHERE player_uuid = ?")) {
                deleteStatement.setString(1, playerId.toString());
                deleteStatement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            logSqlWarning("Could not consume pending bed messages for " + playerId, exception);
            return List.of();
        } finally {
            resetAutoCommitQuietly(originalAutoCommit);
        }

        return messages;
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            logSqlWarning("Could not close respawn point database", exception);
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
            throw new IllegalStateException("Could not open respawn point database", exception);
        }
    }

    private void initializeSchema() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_beds (
                        player_uuid TEXT NOT NULL,
                        bed_uuid TEXT NOT NULL,
                        respawn_point_type TEXT NOT NULL,
                        bed_name TEXT,
                        bed_material TEXT NOT NULL,
                        bed_coords TEXT NOT NULL,
                        bed_spawn_coords TEXT NOT NULL,
                        bed_world TEXT NOT NULL,
                        bed_cooldown INTEGER NOT NULL DEFAULT 0,
                        is_primary INTEGER NOT NULL DEFAULT 0,
                        shared_by_name TEXT,
                        PRIMARY KEY (player_uuid, bed_uuid)
                    )
                    """);
            ensurePlayerBedsColumn(statement, "shared_by_name", "TEXT");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_beds_bed_uuid ON player_beds (bed_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_beds_player_uuid ON player_beds (player_uuid)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        message TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS legacy_player_imports (
                        player_uuid TEXT PRIMARY KEY,
                        imported_at INTEGER NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize respawn point database schema", exception);
        }
    }

    private void migrateLegacyOwnershipYaml() {
        if (isMetaFlagSet(LEGACY_YAML_MIGRATED_KEY)) {
            return;
        }

        File legacyFile = new File(plugin.getDataFolder(), "bed-ownership.yml");
        if (!legacyFile.exists()) {
            setMetaFlag(LEGACY_YAML_MIGRATED_KEY);
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(legacyFile);
        migrateLegacyPendingRemovals(data.getConfigurationSection("pending-removals"));
        migrateLegacyPendingMessages(data.getConfigurationSection("pending-messages"));
        setMetaFlag(LEGACY_YAML_MIGRATED_KEY);
    }

    private void migrateLegacyPendingRemovals(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String playerIdValue : section.getKeys(false)) {
            UUID playerId = parseUuid(playerIdValue);
            if (playerId == null) {
                continue;
            }

            for (String bedUuid : section.getStringList(playerIdValue)) {
                BedData removedBed = removePlayerBed(playerId, bedUuid);
                if (removedBed != null) {
                    queuePendingMessage(playerId, buildDestroyedMessage(removedBed));
                }
            }
        }
    }

    private void migrateLegacyPendingMessages(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String playerIdValue : section.getKeys(false)) {
            UUID playerId = parseUuid(playerIdValue);
            if (playerId == null) {
                continue;
            }

            for (String message : section.getStringList(playerIdValue)) {
                queuePendingMessage(playerId, message);
            }
        }
    }

    private boolean hasAnyBeds(UUID playerId) {
        ensureConnection();

        String sql = "SELECT 1 FROM player_beds WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not check saved beds for " + playerId, exception);
            return false;
        }
    }

    private boolean isLegacyImportComplete(String playerId) {
        ensureConnection();

        String sql = "SELECT 1 FROM legacy_player_imports WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not check legacy import marker for " + playerId, exception);
            return false;
        }
    }

    private void markLegacyImportComplete(String playerId) {
        ensureConnection();

        String sql = "INSERT OR REPLACE INTO legacy_player_imports (player_uuid, imported_at) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            logSqlWarning("Could not save legacy import marker for " + playerId, exception);
        }
    }

    private boolean isMetaFlagSet(String key) {
        ensureConnection();

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
        ensureConnection();

        String sql = "INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, "1");
            statement.executeUpdate();
        } catch (SQLException exception) {
            logSqlWarning("Could not save metadata flag " + key, exception);
        }
    }

    private BedData.RespawnPointType parseRespawnPointType(String value) {
        if (value == null || value.isBlank()) {
            return BedData.RespawnPointType.BED;
        }

        try {
            return BedData.RespawnPointType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return BedData.RespawnPointType.BED;
        }
    }

    private Material resolveMaterial(String value, BedData.RespawnPointType respawnPointType) {
        Material material = value == null ? null : Material.matchMaterial(value);
        if (material != null) {
            return material;
        }

        return respawnPointType == BedData.RespawnPointType.ANCHOR
                ? Material.RESPAWN_ANCHOR
                : Material.RED_BED;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String buildDestroyedMessage(BedData bedData) {
        if (bedData.hasCustomName()) {
            return ChatColor.RED + plugin.message(
                    bedData.isRespawnAnchor() ? "anchor-destroyed-message" : "bed-destroyed-message",
                    bedData.isRespawnAnchor()
                            ? "Your saved respawn anchor {1} was destroyed and removed."
                            : "Your saved bed {1} was destroyed and removed.")
                    .replace("{1}", bedData.getBedName());
        }

        return ChatColor.RED + plugin.message(
                bedData.isRespawnAnchor() ? "anchor-destroyed-message-location" : "bed-destroyed-message-location",
                bedData.isRespawnAnchor()
                        ? "Your saved respawn anchor at {1} in {2} was destroyed and removed."
                        : "Your saved bed at {1} in {2} was destroyed and removed.")
                .replace("{1}", bedData.formatCoords())
                .replace("{2}", bedData.getBedWorld());
    }

    private void rollbackQuietly() {
        try {
            if (connection != null && !connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not rollback respawn point database transaction", exception);
        }
    }

    private void resetAutoCommitQuietly(boolean originalAutoCommit) {
        try {
            if (connection != null) {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            logSqlWarning("Could not restore auto-commit for respawn point database", exception);
        }
    }

    private void logSqlWarning(String message, SQLException exception) {
        plugin.getLogger().warning(message + ": " + exception.getMessage());
    }

    private void ensurePlayerBedsColumn(Statement statement, String columnName, String columnType) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(player_beds)")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }

        statement.executeUpdate("ALTER TABLE player_beds ADD COLUMN " + columnName + " " + columnType);
    }
}
