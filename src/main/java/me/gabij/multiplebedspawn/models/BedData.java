package me.gabij.multiplebedspawn.models;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.Serializable;

public class BedData implements Serializable {
    private static final long serialVersionUID = -4751385549566406619L;

    private RespawnPointType respawnPointType;
    private String bedName;
    private Material bedMaterial;
    private String bedCoords;
    private String bedSpawnCoords;
    private String bedWorld;
    private long bedCooldown = 0;
    private boolean primary;
    private String sharedByName;

    public BedData(Block bed, Player p) {
        this(bed, p, resolveType(bed));
    }

    public BedData(Block bed, Player p, RespawnPointType respawnPointType) {
        this.respawnPointType = respawnPointType;
        this.bedMaterial = bed.getType();
        this.bedCoords = locationToString(bed.getLocation());
        this.bedSpawnCoords = locationToString(p.getLocation());
        this.bedWorld = bed.getWorld().getName();
    }

    public BedData(RespawnPointType respawnPointType, String bedName, Material bedMaterial, String bedCoords,
                   String bedSpawnCoords, String bedWorld, long bedCooldown, boolean primary) {
        this(respawnPointType, bedName, bedMaterial, bedCoords, bedSpawnCoords, bedWorld, bedCooldown, primary, null);
    }

    public BedData(RespawnPointType respawnPointType, String bedName, Material bedMaterial, String bedCoords,
                   String bedSpawnCoords, String bedWorld, long bedCooldown, boolean primary, String sharedByName) {
        this.respawnPointType = respawnPointType;
        this.bedName = bedName;
        this.bedMaterial = bedMaterial;
        this.bedCoords = bedCoords;
        this.bedSpawnCoords = bedSpawnCoords;
        this.bedWorld = bedWorld;
        this.bedCooldown = bedCooldown;
        this.primary = primary;
        this.sharedByName = normalizeSharedByName(sharedByName);
    }

    public String getBedName() {
        return bedName;
    }

    public void setBedName(String bedName) {
        this.bedName = bedName;
    }

    private String locationToString(Location loc) {
        return loc.getX() + ":" + loc.getY() + ":" + loc.getZ();
    }

    public Material getBedMaterial() {
        return bedMaterial;
    }

    public RespawnPointType getRespawnPointType() {
        return respawnPointType == null ? RespawnPointType.BED : respawnPointType;
    }

    public boolean isRespawnAnchor() {
        return getRespawnPointType() == RespawnPointType.ANCHOR;
    }

    public boolean usesCooldown() {
        return getRespawnPointType() == RespawnPointType.BED;
    }

    public String getBedCoords() {
        return bedCoords;
    }

    public String getBedSpawnCoords() {
        return bedSpawnCoords;
    }

    public String getBedWorld() {
        return bedWorld;
    }

    public boolean hasCustomName() {
        return bedName != null && !bedName.isBlank();
    }

    public Location getBedLocation() {
        return locationFromString(bedCoords);
    }

    public Location getSpawnLocation() {
        return locationFromString(bedSpawnCoords);
    }

    public String getSortKey() {
        if (!hasCustomName()) {
            return bedWorld + ":" + bedCoords;
        }

        String stripped = ChatColor.stripColor(bedName);
        return stripped == null ? bedName : stripped;
    }

    public String formatCoords() {
        String[] coords = bedCoords.split(":");
        return "X: " + formatCoord(coords[0]) + " Y: " + formatCoord(coords[1]) + " Z: " + formatCoord(coords[2]);
    }

    public long getBedCooldown() {
        return bedCooldown;
    }

    public void setBedCooldown(long cooldown) {
        this.bedCooldown = cooldown;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getSharedByName() {
        return sharedByName;
    }

    public boolean hasSharedByName() {
        return sharedByName != null && !sharedByName.isBlank();
    }

    public void setSharedByName(String sharedByName) {
        this.sharedByName = normalizeSharedByName(sharedByName);
    }

    public void prepareForShare(String sharedByName) {
        bedName = null;
        setSharedByName(sharedByName);
    }

    public BedData copy() {
        return new BedData(getRespawnPointType(), bedName, bedMaterial, bedCoords, bedSpawnCoords, bedWorld,
                bedCooldown, primary, sharedByName);
    }

    private Location locationFromString(String locationString) {
        World world = Bukkit.getWorld(bedWorld);
        if (world == null) {
            return null;
        }

        String[] coords = locationString.split(":");
        return new Location(world, Double.parseDouble(coords[0]), Double.parseDouble(coords[1]),
                Double.parseDouble(coords[2]));
    }

    private String formatCoord(String coordinate) {
        return Integer.toString((int) Math.floor(Double.parseDouble(coordinate)));
    }

    private static RespawnPointType resolveType(Block block) {
        return block.getType() == Material.RESPAWN_ANCHOR ? RespawnPointType.ANCHOR : RespawnPointType.BED;
    }

    private String normalizeSharedByName(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public enum RespawnPointType {
        BED,
        ANCHOR
    }
}
