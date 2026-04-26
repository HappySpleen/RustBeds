package me.happy.rustbeds.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public class StoredBed {
    private final String bedId;
    private final UUID worldUuid;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final String materialKey;
    private final String customName;
    private final long cooldownUntil;

    public StoredBed(String bedId, UUID worldUuid, String worldName, int x, int y, int z, double spawnX, double spawnY,
            double spawnZ, String materialKey, String customName, long cooldownUntil) {
        this.bedId = bedId;
        this.worldUuid = worldUuid;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.materialKey = materialKey;
        this.customName = customName;
        this.cooldownUntil = cooldownUntil;
    }

    public String getBedId() {
        return bedId;
    }

    public UUID getWorldUuid() {
        return worldUuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public String getMaterialKey() {
        return materialKey;
    }

    public String getCustomName() {
        return customName;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public Material getMaterial() {
        Material material = Material.matchMaterial(materialKey);
        return material == null ? Material.RED_BED : material;
    }

    public Location getBedLocation() {
        World world = resolveWorld();
        return world == null ? null : new Location(world, x, y, z);
    }

    public Location getSpawnLocation() {
        World world = resolveWorld();
        return world == null ? null : new Location(world, spawnX, spawnY, spawnZ);
    }

    public boolean hasCooldown() {
        return cooldownUntil > System.currentTimeMillis();
    }

    private World resolveWorld() {
        if (worldUuid != null) {
            World world = Bukkit.getWorld(worldUuid);
            if (world != null) {
                return world;
            }
        }
        return Bukkit.getWorld(worldName);
    }

    public String locationText() {
        return "X: " + x + " Y: " + y + " Z: " + z;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StoredBed other)) {
            return false;
        }
        return Objects.equals(bedId, other.bedId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bedId);
    }
}
