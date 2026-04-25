package me.gabij.multiplebedspawn.storage;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedData;
import me.gabij.multiplebedspawn.models.BedsDataType;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import me.gabij.multiplebedspawn.utils.KeyUtils;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.Closeable;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static me.gabij.multiplebedspawn.utils.BedsUtils.checkIfIsBed;

public class BedStorage implements Closeable {
    private static final String DATABASE_NAME = "beds.sqlite";

    private final MultipleBedSpawn plugin;
    private Connection connection;

    public BedStorage(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() throws SQLException, ClassNotFoundException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        Class.forName("org.sqlite.JDBC");

        File dataFolder = plugin.getDataFolder();
        File parent = dataFolder.getParentFile();
        File storageFolder = parent == null ? dataFolder : new File(parent, "RustBedsData");
        if (!storageFolder.exists() && !storageFolder.mkdirs()) {
            throw new SQLException("Could not create storage folder " + storageFolder.getAbsolutePath());
        }

        File databaseFile = new File(storageFolder, DATABASE_NAME);
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS beds (
                        bed_id TEXT PRIMARY KEY,
                        world_uuid TEXT,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        spawn_x REAL NOT NULL,
                        spawn_y REAL NOT NULL,
                        spawn_z REAL NOT NULL,
                        material_key TEXT NOT NULL,
                        custom_name TEXT,
                        cooldown_until INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        UNIQUE(world_uuid, x, y, z)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bed_owners (
                        bed_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT,
                        added_at INTEGER NOT NULL,
                        PRIMARY KEY (bed_id, player_uuid),
                        FOREIGN KEY (bed_id) REFERENCES beds(bed_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS broken_bed_notifications (
                        notification_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT,
                        bed_id TEXT NOT NULL,
                        bed_name TEXT,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        delivered_at INTEGER
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS legacy_player_imports (
                        player_uuid TEXT PRIMARY KEY,
                        imported_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public synchronized RegisterBedResult registerBed(Player player, Block block, boolean exclusiveBed, int maxBeds,
            boolean linkWorlds) throws SQLException {
        Block bed = checkIfIsBed(block);
        if (bed == null) {
            return RegisterBedResult.ALREADY_REGISTERED;
        }

        Optional<StoredBed> existing = findBedByLocation(bed.getWorld(), bed.getX(), bed.getY(), bed.getZ());
        UUID playerUuid = player.getUniqueId();

        if (existing.isPresent()) {
            StoredBed storedBed = existing.get();
            if (playerOwnsBed(playerUuid, storedBed.getBedId())) {
                return RegisterBedResult.ALREADY_REGISTERED;
            }
            if (exclusiveBed && getOwnerCount(storedBed.getBedId()) > 0) {
                return RegisterBedResult.EXCLUSIVE_CONFLICT;
            }
            if (getPlayerBedCount(playerUuid, player.getWorld(), linkWorlds) >= maxBeds) {
                return RegisterBedResult.MAX_BEDS_REACHED;
            }
            addOwner(storedBed.getBedId(), playerUuid, player.getName());
            return RegisterBedResult.ADDED_OWNER;
        }

        if (getPlayerBedCount(playerUuid, player.getWorld(), linkWorlds) >= maxBeds) {
            return RegisterBedResult.MAX_BEDS_REACHED;
        }

        long now = System.currentTimeMillis();
        String bedId = UUID.randomUUID().toString();
        Location playerLocation = player.getLocation();

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO beds (
                    bed_id, world_uuid, world_name, x, y, z, spawn_x, spawn_y, spawn_z,
                    material_key, custom_name, cooldown_until, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, bedId);
            statement.setString(2, bed.getWorld().getUID().toString());
            statement.setString(3, bed.getWorld().getName());
            statement.setInt(4, bed.getX());
            statement.setInt(5, bed.getY());
            statement.setInt(6, bed.getZ());
            statement.setDouble(7, playerLocation.getX());
            statement.setDouble(8, playerLocation.getY());
            statement.setDouble(9, playerLocation.getZ());
            statement.setString(10, bed.getType().name());
            statement.setString(11, null);
            statement.setLong(12, 0L);
            statement.setLong(13, now);
            statement.setLong(14, now);
            statement.executeUpdate();
        }

        addOwner(bedId, playerUuid, player.getName());
        return RegisterBedResult.CREATED;
    }

    public synchronized Optional<StoredBed> findBedByLocation(World world, int x, int y, int z) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bed_id, world_uuid, world_name, x, y, z, spawn_x, spawn_y, spawn_z, material_key, custom_name, cooldown_until
                FROM beds
                WHERE (world_uuid = ? OR (world_uuid IS NULL AND world_name = ?))
                  AND x = ? AND y = ? AND z = ?
                LIMIT 1
                """)) {
            statement.setString(1, world.getUID().toString());
            statement.setString(2, world.getName());
            statement.setInt(3, x);
            statement.setInt(4, y);
            statement.setInt(5, z);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapBed(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<StoredBed> findBedById(String bedId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bed_id, world_uuid, world_name, x, y, z, spawn_x, spawn_y, spawn_z, material_key, custom_name, cooldown_until
                FROM beds
                WHERE bed_id = ?
                LIMIT 1
                """)) {
            statement.setString(1, bedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapBed(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public synchronized List<StoredBed> getPlayerBeds(UUID playerUuid, World world, boolean linkWorlds) throws SQLException {
        StringBuilder query = new StringBuilder("""
                SELECT b.bed_id, b.world_uuid, b.world_name, b.x, b.y, b.z, b.spawn_x, b.spawn_y, b.spawn_z,
                       b.material_key, b.custom_name, b.cooldown_until
                FROM beds b
                INNER JOIN bed_owners o ON o.bed_id = b.bed_id
                WHERE o.player_uuid = ?
                """);

        if (!linkWorlds && world != null) {
            query.append(" AND (b.world_uuid = ? OR (b.world_uuid IS NULL AND b.world_name = ?))");
        }
        query.append(" ORDER BY b.world_name ASC, b.x ASC, b.y ASC, b.z ASC");

        List<StoredBed> beds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
            statement.setString(1, playerUuid.toString());
            if (!linkWorlds && world != null) {
                statement.setString(2, world.getUID().toString());
                statement.setString(3, world.getName());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    beds.add(mapBed(resultSet));
                }
            }
        }
        return beds;
    }

    public synchronized int getPlayerBedCount(UUID playerUuid, World world, boolean linkWorlds) throws SQLException {
        return getPlayerBeds(playerUuid, world, linkWorlds).size();
    }

    public synchronized boolean playerOwnsBed(UUID playerUuid, String bedId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM bed_owners WHERE bed_id = ? AND player_uuid = ? LIMIT 1
                """)) {
            statement.setString(1, bedId);
            statement.setString(2, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public synchronized void renameBed(String bedId, String newName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE beds
                SET custom_name = ?, updated_at = ?
                WHERE bed_id = ?
                """)) {
            statement.setString(1, newName == null || newName.isBlank() ? null : newName.trim());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, bedId);
            statement.executeUpdate();
        }
    }

    public synchronized void setBedCooldown(String bedId, long cooldownUntil) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE beds
                SET cooldown_until = ?, updated_at = ?
                WHERE bed_id = ?
                """)) {
            statement.setLong(1, cooldownUntil);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, bedId);
            statement.executeUpdate();
        }
    }

    public synchronized boolean transferOwnership(String bedId, OfflinePlayer fromPlayer, OfflinePlayer toPlayer)
            throws SQLException {
        if (!playerOwnsBed(fromPlayer.getUniqueId(), bedId)) {
            return false;
        }

        connection.setAutoCommit(false);
        try {
            removeOwnerInternal(bedId, fromPlayer.getUniqueId());
            addOwnerInternal(bedId, toPlayer.getUniqueId(), toPlayer.getName());
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized boolean removeOwnership(String bedId, UUID playerUuid) throws SQLException {
        if (!playerOwnsBed(playerUuid, bedId)) {
            return false;
        }

        connection.setAutoCommit(false);
        try {
            removeOwnerInternal(bedId, playerUuid);
            if (getOwnerCount(bedId) == 0) {
                deleteBedInternal(bedId);
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized List<BrokenBedNotification> markBedBroken(World world, int x, int y, int z) throws SQLException {
        Optional<StoredBed> existing = findBedByLocation(world, x, y, z);
        if (existing.isEmpty()) {
            return List.of();
        }

        StoredBed bed = existing.get();
        List<BrokenBedNotification> notifications = new ArrayList<>();

        connection.setAutoCommit(false);
        try (PreparedStatement owners = connection.prepareStatement("""
                SELECT player_uuid, player_name
                FROM bed_owners
                WHERE bed_id = ?
                """);
                PreparedStatement insertNotification = connection.prepareStatement("""
                        INSERT INTO broken_bed_notifications (
                            player_uuid, player_name, bed_id, bed_name, world_name, x, y, z, created_at, delivered_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                        """, Statement.RETURN_GENERATED_KEYS)) {
            owners.setString(1, bed.getBedId());
            try (ResultSet ownerRows = owners.executeQuery()) {
                while (ownerRows.next()) {
                    UUID playerUuid = UUID.fromString(ownerRows.getString("player_uuid"));
                    String playerName = ownerRows.getString("player_name");

                    insertNotification.setString(1, playerUuid.toString());
                    insertNotification.setString(2, playerName);
                    insertNotification.setString(3, bed.getBedId());
                    insertNotification.setString(4, bed.getCustomName());
                    insertNotification.setString(5, bed.getWorldName());
                    insertNotification.setInt(6, bed.getX());
                    insertNotification.setInt(7, bed.getY());
                    insertNotification.setInt(8, bed.getZ());
                    insertNotification.setLong(9, System.currentTimeMillis());
                    insertNotification.executeUpdate();

                    long notificationId = -1L;
                    try (ResultSet keys = insertNotification.getGeneratedKeys()) {
                        if (keys.next()) {
                            notificationId = keys.getLong(1);
                        }
                    }

                    notifications.add(new BrokenBedNotification(notificationId, playerUuid, playerName, bed.getBedId(),
                            bed.getCustomName(), bed.getWorldName(), bed.getX(), bed.getY(), bed.getZ()));
                }
            }

            deleteBedInternal(bed.getBedId());
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }

        return notifications;
    }

    public synchronized List<BrokenBedNotification> consumeBrokenBedNotifications(UUID playerUuid) throws SQLException {
        List<BrokenBedNotification> notifications = new ArrayList<>();
        List<Long> ids = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT notification_id, player_uuid, player_name, bed_id, bed_name, world_name, x, y, z
                FROM broken_bed_notifications
                WHERE player_uuid = ? AND delivered_at IS NULL
                ORDER BY created_at ASC, notification_id ASC
                """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long notificationId = resultSet.getLong("notification_id");
                    ids.add(notificationId);
                    notifications.add(new BrokenBedNotification(
                            notificationId,
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
                            resultSet.getString("bed_id"),
                            resultSet.getString("bed_name"),
                            resultSet.getString("world_name"),
                            resultSet.getInt("x"),
                            resultSet.getInt("y"),
                            resultSet.getInt("z")));
                }
            }
        }

        if (!ids.isEmpty()) {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE broken_bed_notifications
                    SET delivered_at = ?
                    WHERE notification_id = ?
                    """)) {
                long deliveredAt = System.currentTimeMillis();
                for (Long id : ids) {
                    update.setLong(1, deliveredAt);
                    update.setLong(2, id);
                    update.addBatch();
                }
                update.executeBatch();
            }
        }

        return notifications;
    }

    public synchronized boolean hasImportedLegacyData(UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM legacy_player_imports WHERE player_uuid = ? LIMIT 1
                """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public synchronized void markLegacyImported(UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO legacy_player_imports(player_uuid, imported_at)
                VALUES(?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET imported_at = excluded.imported_at
                """)) {
            statement.setString(1, playerUuid.toString());
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public synchronized void importLegacyPlayerData(Player player) throws SQLException {
        if (hasImportedLegacyData(player.getUniqueId())) {
            return;
        }

        PersistentDataContainer data = player.getPersistentDataContainer();
        PlayerBedsData legacyData = KeyUtils.getAny(data, plugin.getName(), "beds", new BedsDataType());

        if (legacyData != null && legacyData.getPlayerBedData() != null) {
            for (BedData legacyBed : legacyData.getPlayerBedData().values()) {
                importLegacyBed(player, legacyBed);
            }
        }

        KeyUtils.removeAll(data, plugin.getName(), "beds");
        markLegacyImported(player.getUniqueId());
    }

    public synchronized List<StoredBed> getAllBeds() throws SQLException {
        List<StoredBed> beds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bed_id, world_uuid, world_name, x, y, z, spawn_x, spawn_y, spawn_z, material_key, custom_name, cooldown_until
                FROM beds
                ORDER BY world_name ASC, x ASC, y ASC, z ASC
                """);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                beds.add(mapBed(resultSet));
            }
        }
        return beds;
    }

    public synchronized List<UUID> getBedOwners(String bedId) throws SQLException {
        List<UUID> owners = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid FROM bed_owners WHERE bed_id = ? ORDER BY player_name ASC
                """)) {
            statement.setString(1, bedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    owners.add(UUID.fromString(resultSet.getString("player_uuid")));
                }
            }
        }
        return owners;
    }

    private void importLegacyBed(Player player, BedData legacyBed) throws SQLException {
        World world = plugin.getServer().getWorld(legacyBed.getBedWorld());
        if (world == null) {
            return;
        }

        String[] bedCoords = legacyBed.getBedCoords().split(":");
        String[] spawnCoords = legacyBed.getBedSpawnCoords().split(":");
        int x = (int) Double.parseDouble(bedCoords[0]);
        int y = (int) Double.parseDouble(bedCoords[1]);
        int z = (int) Double.parseDouble(bedCoords[2]);

        Optional<StoredBed> existing = findBedByLocation(world, x, y, z);
        if (existing.isPresent()) {
            StoredBed storedBed = existing.get();
            if (!playerOwnsBed(player.getUniqueId(), storedBed.getBedId())) {
                addOwner(storedBed.getBedId(), player.getUniqueId(), player.getName());
            }
            if ((storedBed.getCustomName() == null || storedBed.getCustomName().isBlank())
                    && legacyBed.getBedName() != null && !legacyBed.getBedName().isBlank()) {
                renameBed(storedBed.getBedId(), legacyBed.getBedName());
            }
            if (legacyBed.getBedCooldown() > storedBed.getCooldownUntil()) {
                setBedCooldown(storedBed.getBedId(), legacyBed.getBedCooldown());
            }
            return;
        }

        long now = System.currentTimeMillis();
        String bedId = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO beds (
                    bed_id, world_uuid, world_name, x, y, z, spawn_x, spawn_y, spawn_z,
                    material_key, custom_name, cooldown_until, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, bedId);
            statement.setString(2, world.getUID().toString());
            statement.setString(3, world.getName());
            statement.setInt(4, x);
            statement.setInt(5, y);
            statement.setInt(6, z);
            statement.setDouble(7, Double.parseDouble(spawnCoords[0]));
            statement.setDouble(8, Double.parseDouble(spawnCoords[1]));
            statement.setDouble(9, Double.parseDouble(spawnCoords[2]));
            statement.setString(10, legacyBed.getBedMaterial().name());
            statement.setString(11, legacyBed.getBedName());
            statement.setLong(12, legacyBed.getBedCooldown());
            statement.setLong(13, now);
            statement.setLong(14, now);
            statement.executeUpdate();
        }

        addOwner(bedId, player.getUniqueId(), player.getName());
    }

    private void addOwner(String bedId, UUID playerUuid, String playerName) throws SQLException {
        addOwnerInternal(bedId, playerUuid, playerName);
    }

    private void addOwnerInternal(String bedId, UUID playerUuid, String playerName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bed_owners(bed_id, player_uuid, player_name, added_at)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(bed_id, player_uuid) DO UPDATE SET player_name = excluded.player_name
                """)) {
            statement.setString(1, bedId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, playerName);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void removeOwnerInternal(String bedId, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM bed_owners
                WHERE bed_id = ? AND player_uuid = ?
                """)) {
            statement.setString(1, bedId);
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private void deleteBedInternal(String bedId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM beds WHERE bed_id = ?
                """)) {
            statement.setString(1, bedId);
            statement.executeUpdate();
        }
    }

    private int getOwnerCount(String bedId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM bed_owners WHERE bed_id = ?
                """)) {
            statement.setString(1, bedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private StoredBed mapBed(ResultSet resultSet) throws SQLException {
        String worldUuidValue = resultSet.getString("world_uuid");
        UUID worldUuid = worldUuidValue == null || worldUuidValue.isBlank() ? null : UUID.fromString(worldUuidValue);
        return new StoredBed(
                resultSet.getString("bed_id"),
                worldUuid,
                resultSet.getString("world_name"),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"),
                resultSet.getDouble("spawn_x"),
                resultSet.getDouble("spawn_y"),
                resultSet.getDouble("spawn_z"),
                resultSet.getString("material_key"),
                resultSet.getString("custom_name"),
                resultSet.getLong("cooldown_until"));
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
