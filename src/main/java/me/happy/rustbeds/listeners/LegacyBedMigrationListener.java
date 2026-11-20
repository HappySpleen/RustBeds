package me.happy.rustbeds.listeners;

import io.papermc.paper.event.server.AsyncServerDataFixerRemoveBlockEntityEvent;
import me.happy.rustbeds.utils.BedBlockStore;
import me.happy.rustbeds.utils.PluginKeys;
import net.kyori.adventure.key.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class LegacyBedMigrationListener implements Listener {
    private static final Key BED_ENTITY = Key.key("minecraft", "bed");
    private final BedBlockStore bedBlocks;

    public LegacyBedMigrationListener(BedBlockStore bedBlocks) {
        this.bedBlocks = bedBlocks;
    }

    @EventHandler
    public void onBlockEntityRemoval(AsyncServerDataFixerRemoveBlockEntityEvent event) {
        if (!BED_ENTITY.equals(event.getBlockEntityType())) {
            return;
        }

        String uuid = event.getPersistentDataContainerView().get(PluginKeys.uuid(), PersistentDataType.STRING);
        if (uuid == null) {
            return;
        }

        // Ignore malformed tags. Keep the exact identifier used by the player database.
        try {
            if (!UUID.fromString(uuid).toString().equalsIgnoreCase(uuid)) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            return;
        }

        var position = event.getBlockPosition();
        bedBlocks.bind(event.getWorldKey(), position.blockX(), position.blockY(), position.blockZ(), uuid);
    }
}
