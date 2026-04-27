package me.gabij.multiplebedspawn.utils;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class RespawnAnchorStore {
    private final MultipleBedSpawn plugin;
    private final File dataFile;
    private FileConfiguration data;

    public RespawnAnchorStore(MultipleBedSpawn plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "respawn-anchor-registry.yml");
        reload();
    }

    public synchronized void reload() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!dataFile.exists()) {
            save();
        }
    }

    public synchronized String getAnchorUuid(Location location) {
        String locationKey = toLocationKey(location);
        if (locationKey == null) {
            return null;
        }

        return data.getString("locations." + locationKey);
    }

    public synchronized void bindAnchor(String uuid, Location location) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }

        String locationKey = toLocationKey(location);
        if (locationKey == null) {
            return;
        }

        String existingLocation = data.getString("uuids." + uuid);
        if (existingLocation != null && !existingLocation.equalsIgnoreCase(locationKey)) {
            data.set("locations." + existingLocation, null);
        }

        String existingUuid = data.getString("locations." + locationKey);
        if (existingUuid != null && !existingUuid.equalsIgnoreCase(uuid)) {
            data.set("uuids." + existingUuid, null);
        }

        data.set("uuids." + uuid, locationKey);
        data.set("locations." + locationKey, uuid);
        save();
    }

    public synchronized boolean isAnchorRegistered(Location location, String uuid) {
        String storedUuid = getAnchorUuid(location);
        return storedUuid != null && storedUuid.equalsIgnoreCase(uuid);
    }

    public synchronized void clearAnchor(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }

        String locationKey = data.getString("uuids." + uuid);
        data.set("uuids." + uuid, null);
        if (locationKey != null) {
            data.set("locations." + locationKey, null);
        }
        save();
    }

    public synchronized void clearAnchor(Location location) {
        String locationKey = toLocationKey(location);
        if (locationKey == null) {
            return;
        }

        String uuid = data.getString("locations." + locationKey);
        data.set("locations." + locationKey, null);
        if (uuid != null) {
            data.set("uuids." + uuid, null);
        }
        save();
    }

    private String toLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.getWorld().getName()
                + ";" + location.getBlockX()
                + ";" + location.getBlockY()
                + ";" + location.getBlockZ();
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
}
