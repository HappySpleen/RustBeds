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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuditLog {
    private static final String CONFIG_ENABLED_PATH = "audit-log.enabled";
    private static final String CONFIG_FORMAT_PATH = "audit-log.format";
    private static final String CONFIG_INCLUDE_UUIDS_PATH = "audit-log.include-uuids";
    private static final String CONFIG_INCLUDE_SPAWN_COORDINATES_PATH = "audit-log.include-spawn-coordinates";
    private static final String CONFIG_COORDINATE_DECIMALS_PATH = "audit-log.coordinate-decimals";
    private static final String CONFIG_DIVIDER_PATH = "audit-log.divider";
    private static final String CONFIG_RETENTION_DAYS_PATH = "audit-log.retention-days";
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String ENTRY_DIVIDER =
            "--------------------------------------------------------------------------------";
    private static final int DETAIL_LABEL_WIDTH = 15;
    private static final int DEFAULT_COORDINATE_DECIMALS = 2;
    private static final int MAX_COORDINATE_DECIMALS = 6;

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
        write("player", buildEntry(action, actor, owner, target, pointUuid, point, details));
    }

    private static void writeAdmin(String action, Player actor, OfflinePlayer owner, OfflinePlayer target,
            String pointUuid, BedData point, String... details) {
        write("admin", buildEntry(action, actor, owner, target, pointUuid, point, details));
    }

    private static void write(String logType, String entry) {
        RustBeds plugin = RustBeds.getInstance();
        if (!isEnabled(plugin)) {
            return;
        }

        try {
            Path directory = logDirectory(plugin);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(logFileName(logType)), entry + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write " + logType + " audit log: " + exception.getMessage());
        }
    }

    private static String buildEntry(String action, Player actor, OfflinePlayer owner, OfflinePlayer target,
            String pointUuid, BedData point, String... details) {
        AuditLogSettings settings = auditLogSettings(RustBeds.getInstance());
        Map<String, String> detailValues = detailsMap(details);
        String actionName = friendlyActionName(action);
        String timestamp = TIMESTAMP_FORMATTER.format(ZonedDateTime.now().withNano(0));
        AuditEvent event = new AuditEvent(
                timestamp,
                action,
                actionName,
                buildSummary(action, actionName, actor, owner, target, point, detailValues),
                playerDisplayName(actor),
                playerDisplayName(target),
                playerDisplayName(owner),
                pointDetail(point),
                pointTypeDetail(point),
                modeDetail(detailValues.get("mode")),
                normalizedBoolean(detailValues.get("exclusive_bed")),
                point == null ? null : point.getBedWorld(),
                point == null ? null : formatBlockLocation(point.getBedCoords()),
                point == null ? null : formatSpawnLocation(point.getBedSpawnCoords(), settings.coordinateDecimals()),
                pointUuid,
                playerUuid(actor),
                playerUuid(owner),
                playerUuid(target)
        );

        return switch (settings.format()) {
            case JSON -> buildJsonEntry(event, settings);
            case LOGFMT -> buildLogfmtEntry(event, settings);
            case READABLE -> buildReadableEntry(event, settings);
        };
    }

    private static String buildReadableEntry(AuditEvent event, AuditLogSettings settings) {
        String lineSeparator = System.lineSeparator();

        StringBuilder builder = new StringBuilder();
        builder.append('[')
                .append(event.timestamp())
                .append("] ")
                .append(event.summary())
                .append(lineSeparator)
                .append("Details:")
                .append(lineSeparator);

        appendDetail(builder, "Action", event.actionName());
        appendDetail(builder, "Actor", event.actor());
        appendDetail(builder, "Target", event.target());
        appendDetail(builder, "Owner", event.owner());
        appendDetail(builder, "Point", event.point());
        appendDetail(builder, "Point Type", event.pointType());
        appendDetail(builder, "Mode", event.mode());
        appendDetail(builder, "Exclusive Bed", booleanDetail(event.exclusiveBed()));
        appendDetail(builder, "World", event.world());
        appendDetail(builder, "Block Location", event.blockLocation());
        if (settings.includeSpawnCoordinates()) {
            appendDetail(builder, "Spawn Location", event.spawnLocation());
        }
        if (settings.includeUuids()) {
            appendDetail(builder, "Point UUID", event.pointUuid());
            appendDetail(builder, "Actor UUID", event.actorUuid());
            appendDetail(builder, "Owner UUID", event.ownerUuid());
            appendDetail(builder, "Target UUID", event.targetUuid());
        }
        if (settings.divider()) {
            builder.append(ENTRY_DIVIDER);
        } else {
            trimTrailingLineSeparator(builder, lineSeparator);
        }

        return builder.toString();
    }

    private static String buildJsonEntry(AuditEvent event, AuditLogSettings settings) {
        Map<String, String> fields = orderedOutputFields(event, settings);
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"')
                    .append(jsonEscape(field.getKey()))
                    .append("\":\"")
                    .append(jsonEscape(field.getValue()))
                    .append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    private static String buildLogfmtEntry(AuditEvent event, AuditLogSettings settings) {
        Map<String, String> fields = orderedOutputFields(event, settings);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(field.getKey()).append("=\"").append(logfmtEscape(field.getValue())).append('"');
        }
        return builder.toString();
    }

    private static Map<String, String> orderedOutputFields(AuditEvent event, AuditLogSettings settings) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIfPresent(fields, "timestamp", event.timestamp());
        putIfPresent(fields, "summary", event.summary());
        putIfPresent(fields, "action", event.action());
        putIfPresent(fields, "action_name", event.actionName());
        putIfPresent(fields, "actor", event.actor());
        putIfPresent(fields, "target", event.target());
        putIfPresent(fields, "owner", event.owner());
        putIfPresent(fields, "point", event.point());
        putIfPresent(fields, "point_type", event.pointType());
        putIfPresent(fields, "mode", event.mode());
        putIfPresent(fields, "exclusive_bed", event.exclusiveBed());
        putIfPresent(fields, "world", event.world());
        putIfPresent(fields, "block_location", event.blockLocation());
        if (settings.includeSpawnCoordinates()) {
            putIfPresent(fields, "spawn_location", event.spawnLocation());
        }
        if (settings.includeUuids()) {
            putIfPresent(fields, "point_uuid", event.pointUuid());
            putIfPresent(fields, "actor_uuid", event.actorUuid());
            putIfPresent(fields, "owner_uuid", event.ownerUuid());
            putIfPresent(fields, "target_uuid", event.targetUuid());
        }
        return fields;
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (hasText(value)) {
            fields.put(key, clean(value));
        }
    }

    private static String buildSummary(String action, String actionName, Player actor, OfflinePlayer owner,
            OfflinePlayer target, BedData point, Map<String, String> details) {
        String actorName = summaryPlayerName(actor);
        String ownerName = summaryPlayerName(owner);
        String targetName = summaryPlayerName(target);
        String ownedPoint = ownedPointDescription(actor, owner, point);

        String summary;
        switch (action) {
            case "rename_point":
                String renamePoint = hasText(details.get("old_name")) || hasText(details.get("new_name"))
                        ? ownedPointKindDescription(actor, owner, point)
                        : ownedPoint;
                summary = actorName + " renamed " + renamePoint + renameSuffix(details) + ".";
                break;
            case "remove_point":
                summary = actorName + " removed " + ownedPoint + ".";
                break;
            case "share_point_request_sent":
            case "give_point_request_sent":
                summary = actorName + " sent a " + requestMode(action, details) + " request to "
                        + targetName + " for " + ownedPoint + ".";
                break;
            case "share_point_accepted":
            case "give_point_accepted":
                summary = actorName + " accepted " + possessive(ownerName) + " " + requestMode(action, details)
                        + " request for " + pointDescription(point) + ".";
                break;
            case "share_point":
                summary = actorName + " shared " + ownedPoint + " with " + targetName + ".";
                break;
            case "give_point":
                summary = actorName + " transferred " + ownedPoint + " to " + targetName + ".";
                break;
            case "teleport_point":
                summary = actorName + " teleported " + targetName + " to " + ownedPoint + ".";
                break;
            default:
                summary = actorName + " performed " + actionName + " on " + ownedPoint + ".";
                break;
        }

        return actionName + ": " + summary;
    }

    private static Map<String, String> detailsMap(String... details) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < details.length; index += 2) {
            if (hasText(details[index]) && hasText(details[index + 1])) {
                values.put(details[index], clean(details[index + 1]));
            }
        }
        return values;
    }

    private static void appendDetail(StringBuilder builder, String label, String value) {
        if (!hasText(value)) {
            return;
        }

        String labelText = label + ":";
        builder.append("  ").append(labelText);
        for (int index = labelText.length(); index < DETAIL_LABEL_WIDTH; index++) {
            builder.append(' ');
        }
        builder.append(' ').append(clean(value)).append(System.lineSeparator());
    }

    private static void trimTrailingLineSeparator(StringBuilder builder, String lineSeparator) {
        int separatorLength = lineSeparator.length();
        if (builder.length() >= separatorLength
                && builder.substring(builder.length() - separatorLength).equals(lineSeparator)) {
            builder.setLength(builder.length() - separatorLength);
        }
    }

    private static boolean samePlayer(OfflinePlayer first, OfflinePlayer second) {
        UUID firstId = first == null ? null : first.getUniqueId();
        UUID secondId = second == null ? null : second.getUniqueId();
        return firstId != null && firstId.equals(secondId);
    }

    private static String playerDisplayName(OfflinePlayer player) {
        if (player == null) {
            return null;
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer != null && hasText(onlinePlayer.getName())) {
            return clean(onlinePlayer.getName());
        }

        String playerName = player.getName();
        return hasText(playerName) ? clean(playerName) : "Unknown player";
    }

    private static String summaryPlayerName(OfflinePlayer player) {
        String displayName = playerDisplayName(player);
        return hasText(displayName) ? displayName : "Unknown player";
    }

    private static String playerUuid(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return null;
        }

        return player.getUniqueId().toString();
    }

    private static String pointDetail(BedData point) {
        if (point == null) {
            return null;
        }

        if (hasText(point.getBedName())) {
            return clean(point.getBedName());
        }

        String blockLocation = formatBlockLocation(point.getBedCoords());
        if (hasText(blockLocation)) {
            return pointTypeDetail(point) + " at " + blockLocation;
        }

        return pointTypeDetail(point);
    }

    private static String pointTypeDetail(BedData point) {
        if (point == null) {
            return null;
        }

        return point.getRespawnPointType() == BedData.RespawnPointType.ANCHOR ? "Respawn Anchor" : "Bed";
    }

    private static String ownedPointDescription(OfflinePlayer actor, OfflinePlayer owner, BedData point) {
        if (owner == null || samePlayer(actor, owner)) {
            return pointDescription(point);
        }

        return possessive(summaryPlayerName(owner)) + " " + pointDescription(point);
    }

    private static String ownedPointKindDescription(OfflinePlayer actor, OfflinePlayer owner, BedData point) {
        if (owner == null || samePlayer(actor, owner)) {
            return pointKind(point);
        }

        return possessive(summaryPlayerName(owner)) + " " + pointKind(point);
    }

    private static String pointDescription(BedData point) {
        String pointKind = pointKind(point);
        if (point == null) {
            return pointKind;
        }

        if (hasText(point.getBedName())) {
            return pointKind + " " + quote(point.getBedName());
        }

        String blockLocation = formatBlockLocation(point.getBedCoords());
        if (hasText(point.getBedWorld()) && hasText(blockLocation)) {
            return pointKind + " in " + clean(point.getBedWorld()) + " at " + blockLocation;
        }
        if (hasText(blockLocation)) {
            return pointKind + " at " + blockLocation;
        }

        return pointKind;
    }

    private static String pointKind(BedData point) {
        if (point != null && point.getRespawnPointType() == BedData.RespawnPointType.ANCHOR) {
            return "respawn anchor";
        }

        return "bed";
    }

    private static String friendlyActionName(String action) {
        return switch (action) {
            case "rename_point" -> "RENAME POINT";
            case "remove_point" -> "REMOVE POINT";
            case "share_point_request_sent" -> "SHARE REQUEST SENT";
            case "give_point_request_sent" -> "TRANSFER REQUEST SENT";
            case "share_point_accepted" -> "SHARE ACCEPTED";
            case "give_point_accepted" -> "TRANSFER ACCEPTED";
            case "share_point" -> "SHARE POINT";
            case "give_point" -> "TRANSFER POINT";
            case "teleport_point" -> "TELEPORT POINT";
            default -> clean(action).replace('_', ' ').toUpperCase(Locale.ROOT);
        };
    }

    private static String renameSuffix(Map<String, String> details) {
        String oldName = details.get("old_name");
        String newName = details.get("new_name");
        if (hasText(oldName) && hasText(newName)) {
            return " from " + quote(oldName) + " to " + quote(newName);
        }
        if (hasText(newName)) {
            return " to " + quote(newName);
        }
        if (hasText(oldName)) {
            return " from " + quote(oldName);
        }

        return "";
    }

    private static String requestMode(String action, Map<String, String> details) {
        String mode = details.get("mode");
        if ("give".equalsIgnoreCase(mode) || action.startsWith("give_")) {
            return "transfer";
        }

        return "share";
    }

    private static String modeDetail(String value) {
        if (!hasText(value)) {
            return null;
        }

        if ("give".equalsIgnoreCase(value)) {
            return "Transfer";
        }
        if ("share".equalsIgnoreCase(value)) {
            return "Share";
        }

        return clean(value);
    }

    private static String booleanDetail(String value) {
        if (!hasText(value)) {
            return null;
        }

        if ("true".equalsIgnoreCase(value)) {
            return "Yes";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "No";
        }

        return clean(value);
    }

    private static String normalizedBoolean(String value) {
        if (!hasText(value)) {
            return null;
        }

        if ("true".equalsIgnoreCase(value)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "false";
        }

        return clean(value);
    }

    private static String possessive(String value) {
        String name = hasText(value) ? clean(value) : "Unknown player";
        return name.endsWith("s") || name.endsWith("S") ? name + "'" : name + "'s";
    }

    private static String quote(String value) {
        return "\"" + clean(value) + "\"";
    }

    private static String formatBlockLocation(String value) {
        double[] coords = parseCoordinates(value);
        if (coords == null) {
            return null;
        }

        return (int) Math.floor(coords[0]) + " " + (int) Math.floor(coords[1]) + " "
                + (int) Math.floor(coords[2]);
    }

    private static String formatSpawnLocation(String value, int coordinateDecimals) {
        double[] coords = parseCoordinates(value);
        if (coords == null) {
            return null;
        }

        return String.format(Locale.ROOT, "%." + coordinateDecimals + "f %." + coordinateDecimals
                + "f %." + coordinateDecimals + "f", coords[0], coords[1], coords[2]);
    }

    private static double[] parseCoordinates(String value) {
        if (!hasText(value)) {
            return null;
        }

        String[] parts = value.split(":");
        if (parts.length != 3) {
            return null;
        }

        try {
            return new double[]{
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2])
            };
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }

        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private static String jsonEscape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String logfmtEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private static boolean isEnabled(RustBeds plugin) {
        return plugin != null && plugin.getConfig().getBoolean(CONFIG_ENABLED_PATH, true);
    }

    private static AuditLogSettings auditLogSettings(RustBeds plugin) {
        if (plugin == null) {
            return AuditLogSettings.defaults();
        }

        return new AuditLogSettings(
                auditFormat(plugin.getConfig().getString(CONFIG_FORMAT_PATH, "readable")),
                plugin.getConfig().getBoolean(CONFIG_INCLUDE_UUIDS_PATH, true),
                plugin.getConfig().getBoolean(CONFIG_INCLUDE_SPAWN_COORDINATES_PATH, true),
                coordinateDecimals(plugin.getConfig().getInt(CONFIG_COORDINATE_DECIMALS_PATH,
                        DEFAULT_COORDINATE_DECIMALS)),
                plugin.getConfig().getBoolean(CONFIG_DIVIDER_PATH, true)
        );
    }

    private static AuditFormat auditFormat(String value) {
        if (value == null) {
            return AuditFormat.READABLE;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "json", "jsonl", "json-lines" -> AuditFormat.JSON;
            case "logfmt" -> AuditFormat.LOGFMT;
            default -> AuditFormat.READABLE;
        };
    }

    private static int coordinateDecimals(int value) {
        return Math.max(0, Math.min(MAX_COORDINATE_DECIMALS, value));
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

    private enum AuditFormat {
        READABLE,
        JSON,
        LOGFMT
    }

    private record AuditLogSettings(AuditFormat format, boolean includeUuids, boolean includeSpawnCoordinates,
                                    int coordinateDecimals, boolean divider) {
        private static AuditLogSettings defaults() {
            return new AuditLogSettings(AuditFormat.READABLE, true, true, DEFAULT_COORDINATE_DECIMALS, true);
        }
    }

    private record AuditEvent(String timestamp, String action, String actionName, String summary, String actor,
                              String target, String owner, String point, String pointType, String mode,
                              String exclusiveBed, String world, String blockLocation, String spawnLocation,
                              String pointUuid, String actorUuid, String ownerUuid, String targetUuid) {
    }
}
