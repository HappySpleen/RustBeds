package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class TeleportUtils {
    private static boolean warnedInvalidProvider;
    private static boolean warnedMissingMultiverse;
    private static boolean warnedMultiverseDispatchFailure;

    private TeleportUtils() {
    }

    public static boolean teleport(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }

        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        String provider = getPlugin().getConfig().getString("teleport-provider", "vanilla");
        if (provider == null) {
            return teleportVanilla(player, location);
        }

        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        if (normalizedProvider.isEmpty() || normalizedProvider.equals("vanilla")) {
            return teleportVanilla(player, location);
        }

        if (normalizedProvider.equals("multiverse")) {
            return teleportMultiverse(player, location);
        }

        if (!warnedInvalidProvider) {
            getPlugin().getLogger().warning("Unknown teleport-provider '" + provider
                    + "'. Falling back to vanilla teleporting.");
            warnedInvalidProvider = true;
        }
        return teleportVanilla(player, location);
    }

    private static boolean teleportVanilla(Player player, Location location) {
        return player.teleport(location);
    }

    private static boolean teleportMultiverse(Player player, Location location) {
        Plugin multiverse = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (multiverse == null || !multiverse.isEnabled()) {
            if (!warnedMissingMultiverse) {
                getPlugin().getLogger().warning("teleport-provider is set to multiverse, but Multiverse-Core is not enabled. Falling back to vanilla teleporting.");
                warnedMissingMultiverse = true;
            }
            return teleportVanilla(player, location);
        }

        String command = "mv tp " + player.getName() + " " + buildDestination(location) + " --unsafe";
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
            if (!warnedMultiverseDispatchFailure) {
                getPlugin().getLogger().warning("Failed to dispatch Multiverse teleport command. Falling back to vanilla teleporting.");
                warnedMultiverseDispatchFailure = true;
            }
            return teleportVanilla(player, location);
        }

        return true;
    }

    private static String buildDestination(Location location) {
        return String.format(Locale.US, "e:%s:%.3f,%.3f,%.3f:%.2f:%.2f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    private static RustBeds getPlugin() {
        return RustBeds.getInstance();
    }
}
