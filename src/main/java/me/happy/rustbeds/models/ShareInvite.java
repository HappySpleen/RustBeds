package me.happy.rustbeds.models;

import java.util.UUID;

public record ShareInvite(UUID inviteId, UUID senderId, String senderName, UUID targetId, String targetName,
                          String bedUuid, boolean transferOwnership, long createdAt, long expiresAt) {
    public boolean isExpired(long now) {
        return expiresAt <= now;
    }
}
