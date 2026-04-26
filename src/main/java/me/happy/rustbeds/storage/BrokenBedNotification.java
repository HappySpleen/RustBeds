package me.happy.rustbeds.storage;

import java.util.UUID;

public class BrokenBedNotification {
    private final long notificationId;
    private final UUID playerUuid;
    private final String playerName;
    private final String bedId;
    private final String bedName;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public BrokenBedNotification(long notificationId, UUID playerUuid, String playerName, String bedId, String bedName,
            String worldName, int x, int y, int z) {
        this.notificationId = notificationId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.bedId = bedId;
        this.bedName = bedName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public long getNotificationId() {
        return notificationId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getBedId() {
        return bedId;
    }

    public String getBedName() {
        return bedName;
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

    public String locationText() {
        return worldName + " (" + x + ", " + y + ", " + z + ")";
    }
}
