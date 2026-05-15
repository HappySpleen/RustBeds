package me.happy.rustbeds.utils;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import me.happy.rustbeds.RustBeds;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker implements Listener {
    private static final String MODRINTH_API_BASE = "https://api.modrinth.com/v2/project/";
    private static final String MODRINTH_WEB_BASE = "https://modrinth.com/plugin/";
    private static final String DEFAULT_PROJECT_SLUG = "rustbeds";
    private static final String DEFAULT_LOADER_FILTER = "[\"paper\"]";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10L);
    private static final Gson GSON = new Gson();
    private static final Pattern SEMVER_PATTERN =
            Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+][0-9A-Za-z][0-9A-Za-z._-]*)?");
    private static final Pattern DEV_PATTERN =
            Pattern.compile("(?i)(^|[\\s._-])dev([\\s._-]|$)");
    private static final Pattern ALPHA_PATTERN = Pattern.compile("(?i)alpha");

    private final RustBeds plugin;
    private final HttpClient httpClient;
    private final AtomicBoolean checkRunning = new AtomicBoolean(false);
    private BukkitTask activeTask;
    private volatile UpdateNotice lastNotice;

    public UpdateChecker(RustBeds plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public void checkForUpdates() {
        UpdateCheckerSettings settings = readSettings();
        if (!settings.enabled()) {
            lastNotice = null;
            return;
        }

        if (!checkRunning.compareAndSet(false, true)) {
            return;
        }

        activeTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<UpdateNotice> notice = fetchLatestUpdate(settings);
                Bukkit.getScheduler().runTask(plugin, () -> applyNotice(notice.orElse(null)));
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not check Modrinth for RustBeds updates: "
                        + exception.getMessage());
            } finally {
                checkRunning.set(false);
            }
        });
    }

    public void reload() {
        lastNotice = null;
        checkForUpdates();
    }

    public void shutdown() {
        BukkitTask task = activeTask;
        if (task != null) {
            task.cancel();
            activeTask = null;
        }
        lastNotice = null;
        checkRunning.set(false);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("update-checker.notify-admins", true)) {
            return;
        }

        Player player = event.getPlayer();
        UpdateNotice notice = lastNotice;
        if (notice != null && plugin.hasAdminPermission(player)) {
            sendChatNotice(player, notice);
        }
    }

    private UpdateCheckerSettings readSettings() {
        boolean enabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
        boolean notifyAdmins = plugin.getConfig().getBoolean("update-checker.notify-admins", true);
        String project = plugin.getConfig().getString("update-checker.modrinth-project", DEFAULT_PROJECT_SLUG);
        if (project == null || project.isBlank()) {
            project = DEFAULT_PROJECT_SLUG;
        }

        return new UpdateCheckerSettings(enabled, notifyAdmins, project.trim(),
                plugin.getPluginMeta().getVersion(), plugin.getPluginFileName());
    }

    private Optional<UpdateNotice> fetchLatestUpdate(UpdateCheckerSettings settings)
            throws IOException, InterruptedException {
        URI requestUri = URI.create(MODRINTH_API_BASE + encodePath(settings.project())
                + "/version?loaders=" + encodeQueryValue(DEFAULT_LOADER_FILTER)
                + "&include_changelog=false");
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "RustBeds/" + settings.currentVersion()
                        + " (https://github.com/HappySpleen/RustBeds)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            plugin.getLogger().warning("Could not check RustBeds updates: Modrinth project '"
                    + settings.project() + "' was not found.");
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            plugin.getLogger().warning("Could not check RustBeds updates: Modrinth returned HTTP "
                    + response.statusCode() + ".");
            return Optional.empty();
        }

        ModrinthVersion[] versions = GSON.fromJson(response.body(), ModrinthVersion[].class);
        if (versions == null || versions.length == 0) {
            return Optional.empty();
        }

        List<ModrinthVersion> versionList = Arrays.stream(versions)
                .filter(Objects::nonNull)
                .filter(ModrinthVersion::isListed)
                .toList();
        Instant currentPublishedAt = findCurrentPublishedAt(settings, versionList);

        return versionList.stream()
                .map(version -> createNotice(settings, version))
                .flatMap(Optional::stream)
                .filter(notice -> isNewerThanCurrent(settings, notice, currentPublishedAt))
                .max(Comparator.comparing(UpdateNotice::publishedAt));
    }

    private Instant findCurrentPublishedAt(UpdateCheckerSettings settings, List<ModrinthVersion> versions) {
        Optional<SemanticVersion> currentVersion = SemanticVersion.find(settings.currentVersion())
                .or(() -> SemanticVersion.find(settings.currentFileName()));
        if (currentVersion.isEmpty()) {
            return Instant.EPOCH;
        }

        return versions.stream()
                .filter(version -> isCurrentModrinthVersion(settings, version, currentVersion.get()))
                .map(ModrinthVersion::publishedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
    }

    private boolean isCurrentModrinthVersion(UpdateCheckerSettings settings, ModrinthVersion version,
            SemanticVersion currentVersion) {
        ModrinthFile primaryFile = version.primaryFile();
        String primaryFileName = primaryFile == null ? "" : primaryFile.filename();
        if (!primaryFileName.isBlank() && primaryFileName.equalsIgnoreCase(settings.currentFileName())) {
            return true;
        }

        Optional<SemanticVersion> modrinthVersion = SemanticVersion.find(version.versionNumber())
                .or(() -> SemanticVersion.find(version.displayName(primaryFileName)))
                .or(() -> SemanticVersion.find(primaryFileName));
        if (modrinthVersion.isEmpty() || modrinthVersion.get().compareBaseTo(currentVersion) != 0) {
            return false;
        }

        UpdateChannel channel = classifyChannel(version);
        if (version.versionNumber().equalsIgnoreCase(settings.currentVersion())) {
            return channel == UpdateChannel.RELEASE || currentFileNameLooksLike(channel, settings.currentFileName());
        }

        return channel == UpdateChannel.RELEASE
                && modrinthVersion.get().fullVersion().equalsIgnoreCase(currentVersion.fullVersion());
    }

    private boolean currentFileNameLooksLike(UpdateChannel channel, String currentFileName) {
        if (channel == UpdateChannel.DEV_ALPHA) {
            return textMatches(currentFileName, ALPHA_PATTERN);
        }
        if (channel == UpdateChannel.DEV) {
            return textMatches(currentFileName, DEV_PATTERN);
        }
        return true;
    }

    private Optional<UpdateNotice> createNotice(UpdateCheckerSettings settings, ModrinthVersion version) {
        ModrinthFile primaryFile = version.primaryFile();
        String primaryFileName = primaryFile == null ? "" : primaryFile.filename();
        UpdateChannel channel = classifyChannel(version);
        String versionDisplay = version.displayName(primaryFileName);
        String downloadUrl = buildVersionUrl(settings.project(), version.id());
        Instant publishedAt = version.publishedAt();

        return Optional.of(new UpdateNotice(channel, versionDisplay, version.versionNumber(),
                primaryFileName, downloadUrl, settings.currentVersion(), publishedAt));
    }

    private UpdateChannel classifyChannel(ModrinthVersion version) {
        if ("alpha".equalsIgnoreCase(version.versionType()) || anyFileNameMatches(version, ALPHA_PATTERN)) {
            return UpdateChannel.DEV_ALPHA;
        }
        if ("beta".equalsIgnoreCase(version.versionType())
                || textMatches(version.name(), DEV_PATTERN)
                || anyFileNameMatches(version, DEV_PATTERN)) {
            return UpdateChannel.DEV;
        }
        return UpdateChannel.RELEASE;
    }

    private boolean anyFileNameMatches(ModrinthVersion version, Pattern pattern) {
        if (version.files() == null) {
            return false;
        }

        return version.files().stream()
                .map(ModrinthFile::filename)
                .filter(Objects::nonNull)
                .anyMatch(filename -> pattern.matcher(filename).find());
    }

    private boolean isNewerThanCurrent(UpdateCheckerSettings settings, UpdateNotice notice, Instant currentPublishedAt) {
        if (notice.primaryFileName().equalsIgnoreCase(settings.currentFileName())) {
            return false;
        }

        Optional<SemanticVersion> currentVersion = SemanticVersion.find(settings.currentVersion())
                .or(() -> SemanticVersion.find(settings.currentFileName()));
        Optional<SemanticVersion> updateVersion = SemanticVersion.find(notice.versionNumber())
                .or(() -> SemanticVersion.find(notice.displayVersion()))
                .or(() -> SemanticVersion.find(notice.primaryFileName()));
        if (currentVersion.isEmpty() || updateVersion.isEmpty()) {
            return !notice.versionNumber().equalsIgnoreCase(settings.currentVersion());
        }

        int baseComparison = updateVersion.get().compareBaseTo(currentVersion.get());
        if (baseComparison > 0) {
            return true;
        }
        if (baseComparison < 0) {
            return false;
        }

        if (notice.channel() == UpdateChannel.RELEASE) {
            return !updateVersion.get().fullVersion().equalsIgnoreCase(currentVersion.get().fullVersion());
        }

        if (!currentPublishedAt.equals(Instant.EPOCH)) {
            return notice.publishedAt().isAfter(currentPublishedAt);
        }

        return true;
    }

    private void applyNotice(UpdateNotice notice) {
        lastNotice = notice;
        if (notice == null) {
            return;
        }

        for (String line : buildPlainNoticeLines(notice)) {
            plugin.getLogger().warning(line);
        }

        if (!plugin.getConfig().getBoolean("update-checker.notify-admins", true)) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.hasAdminPermission(player)) {
                sendChatNotice(player, notice);
            }
        }
    }

    private void sendChatNotice(Player player, UpdateNotice notice) {
        List<String> lines = buildPlainNoticeLines(notice);
        if (lines.isEmpty()) {
            return;
        }

        player.sendMessage(ChatColor.GOLD + "[RustBeds] " + ChatColor.YELLOW + lines.getFirst());
        for (int index = 1; index < lines.size(); index++) {
            ChatColor color = lines.get(index).startsWith("Download:") ? ChatColor.AQUA : ChatColor.RED;
            player.sendMessage(ChatColor.GOLD + "[RustBeds] " + color + lines.get(index));
        }
    }

    private List<String> buildPlainNoticeLines(UpdateNotice notice) {
        String updateLine = plugin.message("update-check-available",
                        "RustBeds {1} update available: {2} (running {3}).")
                .replace("{1}", notice.channel().displayName())
                .replace("{2}", notice.displayVersion())
                .replace("{3}", notice.currentVersion());
        String downloadLine = plugin.message("update-check-download", "Download: {1}")
                .replace("{1}", notice.downloadUrl());

        if (notice.channel() == UpdateChannel.DEV_ALPHA) {
            String devWarning = plugin.message("update-check-dev-warning",
                    "Dev builds are for testing a new feature or fix and require feedback and testing.");
            String alphaWarning = plugin.message("update-check-alpha-warning",
                    "Alpha builds are extremely experimental and could break databases from older versions. "
                            + "Back up before testing.");
            return List.of(stripColors(updateLine), stripColors(devWarning), stripColors(alphaWarning),
                    stripColors(downloadLine));
        }
        if (notice.channel() == UpdateChannel.DEV) {
            String devWarning = plugin.message("update-check-dev-warning",
                    "Dev builds are for testing a new feature or fix and require feedback and testing.");
            return List.of(stripColors(updateLine), stripColors(devWarning), stripColors(downloadLine));
        }

        return List.of(stripColors(updateLine), stripColors(downloadLine));
    }

    private static boolean textMatches(String text, Pattern pattern) {
        return text != null && pattern.matcher(text).find();
    }

    private static String stripColors(String text) {
        return ChatColor.stripColor(text);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String buildVersionUrl(String project, String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return MODRINTH_WEB_BASE + encodePath(project);
        }
        return MODRINTH_WEB_BASE + encodePath(project) + "/version/" + encodePath(versionId);
    }

    private enum UpdateChannel {
        RELEASE("Release"),
        DEV("Dev"),
        DEV_ALPHA("Dev Alpha");

        private final String displayName;

        UpdateChannel(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }

    private record UpdateCheckerSettings(boolean enabled, boolean notifyAdmins, String project, String currentVersion,
            String currentFileName) {
    }

    private record UpdateNotice(UpdateChannel channel, String displayVersion, String versionNumber,
            String primaryFileName, String downloadUrl, String currentVersion, Instant publishedAt) {
    }

    private record SemanticVersion(int major, int minor, int patch, String fullVersion) {
        private static Optional<SemanticVersion> find(String text) {
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }

            Matcher matcher = SEMVER_PATTERN.matcher(text);
            if (!matcher.find()) {
                return Optional.empty();
            }

            try {
                return Optional.of(new SemanticVersion(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        matcher.group(0)));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        private int compareBaseTo(SemanticVersion other) {
            int majorComparison = Integer.compare(major, other.major);
            if (majorComparison != 0) {
                return majorComparison;
            }

            int minorComparison = Integer.compare(minor, other.minor);
            if (minorComparison != 0) {
                return minorComparison;
            }

            return Integer.compare(patch, other.patch);
        }
    }

    private static class ModrinthVersion {
        private String id;
        private String name;
        private String status;
        @SerializedName("version_number")
        private String versionNumber;
        @SerializedName("version_type")
        private String versionType;
        @SerializedName("date_published")
        private String datePublished;
        private List<ModrinthFile> files;

        private boolean isListed() {
            return status == null || status.isBlank() || "listed".equalsIgnoreCase(status);
        }

        private String id() {
            return nullToBlank(id);
        }

        private String name() {
            return nullToBlank(name);
        }

        private String versionNumber() {
            return nullToBlank(versionNumber);
        }

        private String versionType() {
            return nullToBlank(versionType).toLowerCase(Locale.ROOT);
        }

        private List<ModrinthFile> files() {
            return files == null ? List.of() : files;
        }

        private ModrinthFile primaryFile() {
            List<ModrinthFile> availableFiles = files();
            if (availableFiles.isEmpty()) {
                return null;
            }

            return availableFiles.stream()
                    .filter(ModrinthFile::primary)
                    .findFirst()
                    .orElse(availableFiles.getFirst());
        }

        private Instant publishedAt() {
            if (datePublished == null || datePublished.isBlank()) {
                return Instant.EPOCH;
            }

            try {
                return Instant.parse(datePublished);
            } catch (DateTimeParseException exception) {
                return Instant.EPOCH;
            }
        }

        private String displayName(String primaryFileName) {
            if (versionNumber != null && !versionNumber.isBlank()) {
                if (name != null && !name.isBlank() && !name.equalsIgnoreCase(versionNumber)) {
                    return versionNumber + " (" + name + ")";
                }
                return versionNumber;
            }
            if (name != null && !name.isBlank()) {
                return name;
            }
            if (primaryFileName != null && !primaryFileName.isBlank()) {
                return primaryFileName;
            }
            return "unknown version";
        }
    }

    private static class ModrinthFile {
        private String filename;
        private boolean primary;

        private String filename() {
            return nullToBlank(filename);
        }

        private boolean primary() {
            return primary;
        }
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
