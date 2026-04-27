package me.gabij.multiplebedspawn.utils;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.PlayerBedsData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BedOwnershipStore {
    private final MultipleBedSpawn plugin;
    private final File dataFile;
    private FileConfiguration data;

    public BedOwnershipStore(MultipleBedSpawn plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "bed-ownership.yml");
        reload();
    }

    public synchronized void reload() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!dataFile.exists()) {
            save();
        }
    }

    public synchronized void syncPlayerBeds(Player player) {
        String playerId = player.getUniqueId().toString();
        Set<String> currentBeds = getPlayerBedIds(player);
        Set<String> knownBeds = new HashSet<>();
        ConfigurationSection ownersSection = data.getConfigurationSection("owners");
        if (ownersSection != null) {
            knownBeds.addAll(ownersSection.getKeys(false));
        }

        boolean changed = false;
        for (String bedUuid : knownBeds) {
            String path = ownerPath(bedUuid);
            List<String> owners = new ArrayList<>(data.getStringList(path));
            boolean hasOwner = containsIgnoreCase(owners, playerId);
            boolean shouldHaveOwner = currentBeds.contains(bedUuid);

            if (shouldHaveOwner && !hasOwner) {
                owners.add(playerId);
                setListOrClear(path, owners);
                changed = true;
                continue;
            }

            if (!shouldHaveOwner && hasOwner) {
                removeIgnoreCase(owners, playerId);
                setListOrClear(path, owners);
                changed = true;
            }
        }

        for (String bedUuid : currentBeds) {
            if (knownBeds.contains(bedUuid)) {
                continue;
            }

            data.set(ownerPath(bedUuid), List.of(playerId));
            changed = true;
        }

        if (changed) {
            pruneEmptySections();
            save();
        }
    }

    public synchronized Set<UUID> getOwners(String bedUuid) {
        Set<UUID> owners = new LinkedHashSet<>();
        for (String value : data.getStringList(ownerPath(bedUuid))) {
            try {
                owners.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return owners;
    }

    public synchronized boolean hasAnyKnownOwner(String bedUuid) {
        return !getOwners(bedUuid).isEmpty();
    }

    public synchronized boolean hasOwnerOtherThan(String bedUuid, UUID playerId) {
        String playerIdValue = playerId.toString();
        for (String value : data.getStringList(ownerPath(bedUuid))) {
            if (!value.equalsIgnoreCase(playerIdValue)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void clearOwners(String bedUuid) {
        if (!data.contains(ownerPath(bedUuid))) {
            return;
        }

        data.set(ownerPath(bedUuid), null);
        pruneEmptySections();
        save();
    }

    public synchronized void queueDestroyedBed(UUID playerId, String bedUuid) {
        String playerPath = playerId.toString();
        String removalsPath = "pending-removals." + playerPath;
        List<String> removals = new ArrayList<>(data.getStringList(removalsPath));
        if (!containsIgnoreCase(removals, bedUuid)) {
            removals.add(bedUuid);
            data.set(removalsPath, removals);
        }

        pruneEmptySections();
        save();
    }

    public synchronized PendingBedUpdates consumePendingUpdates(UUID playerId) {
        String playerPath = playerId.toString();
        String removalsPath = "pending-removals." + playerPath;
        String messagesPath = "pending-messages." + playerPath;

        List<String> removals = new ArrayList<>(data.getStringList(removalsPath));
        List<String> messages = new ArrayList<>(data.getStringList(messagesPath));
        if (removals.isEmpty() && messages.isEmpty()) {
            return new PendingBedUpdates(List.of(), List.of());
        }

        data.set(removalsPath, null);
        data.set(messagesPath, null);
        pruneEmptySections();
        save();
        return new PendingBedUpdates(removals, messages);
    }

    private Set<String> getPlayerBedIds(Player player) {
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (!playerData.has(PluginKeys.beds(), PluginKeys.bedsDataType())) {
            return Set.of();
        }

        PlayerBedsData playerBedsData = playerData.get(PluginKeys.beds(), PluginKeys.bedsDataType());
        if (playerBedsData == null || playerBedsData.getPlayerBedData() == null) {
            return Set.of();
        }

        return new HashSet<>(playerBedsData.getPlayerBedData().keySet());
    }

    private String ownerPath(String bedUuid) {
        return "owners." + bedUuid;
    }

    private void setListOrClear(String path, List<String> values) {
        data.set(path, values.isEmpty() ? null : values);
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private void removeIgnoreCase(List<String> values, String target) {
        values.removeIf(value -> value.equalsIgnoreCase(target));
    }

    private void pruneEmptySections() {
        pruneSection("owners");
        pruneSection("pending-removals");
        pruneSection("pending-messages");
    }

    private void pruneSection(String path) {
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section != null && section.getKeys(false).isEmpty()) {
            data.set(path, null);
        }
    }

    private void save() {
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save " + dataFile.getName() + ": " + exception.getMessage());
        }
    }

    public record PendingBedUpdates(List<String> bedUuids, List<String> messages) {
    }
}
