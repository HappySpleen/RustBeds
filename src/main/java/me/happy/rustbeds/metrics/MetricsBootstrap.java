package me.happy.rustbeds.metrics;

import me.happy.rustbeds.RustBeds;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.util.Locale;

public final class MetricsBootstrap {
    private static final int BSTATS_PLUGIN_ID = 31130;

    private MetricsBootstrap() {
    }

    public static void start(RustBeds plugin) {
        if (BSTATS_PLUGIN_ID <= 0) {
            plugin.getLogger().info("bStats metrics are installed but disabled until a plugin ID is configured.");
            return;
        }

        Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("respawn_anchors_enabled",
                () -> enabledState(plugin.getConfig().getBoolean("respawn-anchors-enabled"))));
        metrics.addCustomChart(new SimplePie("bed_sharing_enabled",
                () -> enabledState(plugin.getConfig().getBoolean("bed-sharing"))));
        metrics.addCustomChart(new SimplePie("exclusive_bed_enabled",
                () -> enabledState(plugin.getConfig().getBoolean("exclusive-bed"))));
        metrics.addCustomChart(new SimplePie("teleport_provider",
                () -> plugin.getConfig().getString("teleport-provider", "vanilla").toLowerCase(Locale.ROOT)));
    }

    private static String enabledState(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
