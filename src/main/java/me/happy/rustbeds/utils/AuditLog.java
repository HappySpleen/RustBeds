package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.models.BedData;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class AuditLog {
    private static final String CONFIG_ENABLED_PATH = "audit-log.enabled";
    private static final String CONFIG_RETENTION_DAYS_PATH = "audit-log.retention-days";
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private AuditLog() {
    }

    public static void initialize(RustBeds plugin) {
        if (!isEnabled(plugin)) {
            return;
        }

        try {
            Files.createDirectories(logDirectory(plugin));
            pruneOldLogs(plugin);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not initialize RustBeds audit logs: " + exception.getMessage());
        }
    }

    public static void logPlayerRename(Player player, String pointUuid, BedData point, String oldName,
            String newName) {
        writePlayer("rename_point", player, player, null, pointUuid, point,
                "old_name", oldName,
                "new_name", newName);
    }

    public static void logPlayerRemove(Player player, String pointUuid, BedData point) {
        writePlayer("remove_point", player, player, null, pointUuid, point);
    }

    public static void logPlayerShareRequest(Player owner, OfflinePlayer target, String pointUuid, BedData point,
            boolean transferOwnership) {
        writePlayer(sharingAction(transferOwnership) + "_point_request_sent", owner, owner, target, pointUuid, point,
                "mode", sharingAction(transferOwnership),
                "exclusive_bed", Boolean.toString(transferOwnership));
    }

    public static void logPlayerShareAccepted(Player receiver, OfflinePlayer owner, String pointUuid, BedData point,
            boolean transferOwnership) {
        writePlayer(sharingAction(transferOwnership) + "_point_accepted", receiver, owner, receiver, pointUuid, point,
                "mode", sharingAction(transferOwnership),
                "exclusive_bed", Boolean.toString(transferOwnership));
    }

    public static void logAdminRename(Player admin, OfflinePlayer owner, String pointUuid, BedData point,
            String oldName, String newName) {
        writeAdmin("rename_point", admin, owner, null, pointUuid, point,
                "old_name", oldName,
                "new_name", newName);
    }

    public static void logAdminRemove(Player admin, OfflinePlayer owner, String pointUuid, BedData point) {
        writeAdmin("remove_point", admin, owner, null, pointUuid, point);
    }

    public static void logAdminGrant(Player admin, OfflinePlayer owner, OfflinePlayer target, String pointUuid,
            BedData point, boolean transferOwnership) {
        writeAdmin(sharingAction(transferOwnership) + "_point", admin, owner, target, pointUuid, point,
                "mode", sharingAction(transferOwnership),
                "exclusive_bed", Boolean.toString(transferOwnership));
    }

    public static void logAdminTeleport(Player admin, OfflinePlayer owner, Player target, String pointUuid,
            BedData point) {
        writeAdmin("teleport_point", admin, owner, target, pointUuid, point);
    }

    private static void writePlayer(String action, Player actor, OfflinePlayer owner, OfflinePlayer target,
            String pointUuid, BedData point, String... details) {
        write("player", buildLine(action, actor, owner, target, pointUuid, point, details));
    }

    private static void writeAdmin(String action, Player actor, OfflinePlayer owner, OfflinePlayer target,
            String pointUuid, BedData point, String... details) {
        write("admin", buildLine(action, actor, owner, target, pointUuid, point, details));
    }

    private static void write(String logType, String line) {
        RustBeds plugin = RustBeds.getInstance();
        if (!isEnabled(plugin)) {
            return;
        }

        try {
            Path directory = logDirectory(plugin);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(logFileName(logType)), line + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write " + logType + " audit log: " + exception.getMessage());
        }
    }

    private static String buildLine(String action, Player actor, OfflinePlayer owner, OfflinePlayer target,
            String pointUuid, BedData point, String... details) {
        StringBuilder builder = new StringBuilder();
        append(builder, "timestamp", TIMESTAMP_FORMATTER.format(ZonedDateTime.now().withNano(0)));
        append(builder, "action", action);
        appendPlayer(builder, "actor", actor);
        appendOfflinePlayer(builder, "owner", owner);
        appendOfflinePlayer(builder, "target", target);
        appendPoint(builder, pointUuid, point);
        for (int index = 0; index + 1 < details.length; index += 2) {
            append(builder, details[index], details[index + 1]);
        }
        return builder.toString().trim();
    }

    private static void appendPlayer(StringBuilder builder, String prefix, Player player) {
        if (player == null) {
            return;
        }

        append(builder, prefix + "_name", player.getName());
        append(builder, prefix + "_uuid", player.getUniqueId().toString());
    }

    private static void appendOfflinePlayer(StringBuilder builder, String prefix, OfflinePlayer player) {
        if (player == null) {
            return;
        }

        append(builder, prefix + "_name", PlayerUtils.getOfflinePlayerName(player));
        append(builder, prefix + "_uuid", player.getUniqueId().toString());
    }

    private static void appendPoint(StringBuilder builder, String pointUuid, BedData point) {
        append(builder, "point_uuid", pointUuid);
        if (point == null) {
            return;
        }

        append(builder, "point_type", point.getRespawnPointType().name().toLowerCase());
        append(builder, "point_name", point.getBedName());
        append(builder, "point_material", point.getBedMaterial() == null
                ? ""
                : point.getBedMaterial().name().toLowerCase());
        append(builder, "world", point.getBedWorld());
        append(builder, "block_coords", point.getBedCoords());
        append(builder, "spawn_coords", point.getBedSpawnCoords());
    }

    private static void append(StringBuilder builder, String key, String value) {
        builder.append(key).append("=\"").append(sanitize(value)).append("\" ");
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private static boolean isEnabled(RustBeds plugin) {
        return plugin != null && plugin.getConfig().getBoolean(CONFIG_ENABLED_PATH, true);
    }

    private static String sharingAction(boolean transferOwnership) {
        return transferOwnership ? "give" : "share";
    }

    private static Path logDirectory(RustBeds plugin) {
        return plugin.getDataFolder().toPath().resolve("Logs");
    }

    private static String logFileName(String logType) {
        return logType + "-audit-" + FILE_DATE_FORMATTER.format(LocalDate.now()) + ".log";
    }

    private static int retentionDays(RustBeds plugin) {
        return Math.max(1, plugin.getConfig().getInt(CONFIG_RETENTION_DAYS_PATH, DEFAULT_RETENTION_DAYS));
    }

    private static void pruneOldLogs(RustBeds plugin) throws IOException {
        Path directory = logDirectory(plugin);
        if (!Files.isDirectory(directory)) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays(plugin)));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*-audit-*.log")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
