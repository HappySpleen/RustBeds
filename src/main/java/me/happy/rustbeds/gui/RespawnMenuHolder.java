package me.happy.rustbeds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class RespawnMenuHolder implements InventoryHolder {
    private final UUID playerId;
    private final ViewType viewType;
    private final int page;
    private final String bedUuid;
    private Inventory inventory;

    public RespawnMenuHolder(UUID playerId, ViewType viewType, int page, String bedUuid) {
        this.playerId = playerId;
        this.viewType = viewType;
        this.page = page;
        this.bedUuid = bedUuid;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public int getPage() {
        return page;
    }

    public String getBedUuid() {
        return bedUuid;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public enum ViewType {
        RESPAWN_LIST,
        MANAGE_LIST,
        ACTIONS,
        SHARE_LIST
    }
}
