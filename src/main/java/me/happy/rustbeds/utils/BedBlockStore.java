package me.happy.rustbeds.utils;

import net.kyori.adventure.key.Key;
import org.bukkit.block.Block;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Persistent UUIDs for physical beds. Beds no longer have block entities in 26.2+.
 */
public final class BedBlockStore implements AutoCloseable {
    private final Connection connection;

    public BedBlockStore(File dataFolder) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "respawn-points.db").getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bed_blocks (
                        world_key TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        bed_uuid TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        PRIMARY KEY (world_key, x, y, z)
                    )
                    """);
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public String getUuid(Block bedHead) {
        return getUuid(bedHead.getWorld().key(), bedHead.getX(), bedHead.getY(), bedHead.getZ());
    }

    public String getOrCreateUuid(Block bedHead, String generatedUuid) {
        Key world = bedHead.getWorld().key();
        int x = bedHead.getX();
        int y = bedHead.getY();
        int z = bedHead.getZ();
        bind(world, x, y, z, generatedUuid);
        return getUuid(world, x, y, z);
    }

    // The data fixer may call this from a chunk worker. Keep the statement small,
    // persist before conversion finishes, and never access the world under the database lock.
    public synchronized void bind(Key world, int x, int y, int z, String uuid) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bed_blocks (world_key, x, y, z, bed_uuid) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """)) {
            setPosition(statement, world, x, y, z);
            statement.setString(5, uuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not store bed UUID " + uuid + " in " + world, exception);
        }
    }

    private synchronized String getUuid(Key world, int x, int y, int z) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bed_uuid FROM bed_blocks WHERE world_key = ? AND x = ? AND y = ? AND z = ?
                """)) {
            setPosition(statement, world, x, y, z);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read bed UUID in " + world, exception);
        }
    }

    public synchronized void clear(String uuid) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM bed_blocks WHERE bed_uuid = ?")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not clear bed UUID " + uuid, exception);
        }
    }

    private static void setPosition(PreparedStatement statement, Key world, int x, int y, int z) throws SQLException {
        statement.setString(1, world.asString());
        statement.setInt(2, x);
        statement.setInt(3, y);
        statement.setInt(4, z);
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}
