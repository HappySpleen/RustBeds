package me.happy.rustbeds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class AdminBedsMenuHolder implements InventoryHolder {
    private final UUID viewerId;
    private final ViewType viewType;
    private final int page;
    private final int contextPage;
    private final UUID ownerId;
    private final String bedUuid;
    private Inventory inventory;

    public AdminBedsMenuHolder(UUID viewerId, ViewType viewType, int page, int contextPage, UUID ownerId,
            String bedUuid) {
        this.viewerId = viewerId;
        this.viewType = viewType;
        this.page = page;
        this.contextPage = contextPage;
        this.ownerId = ownerId;
        this.bedUuid = bedUuid;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public int getPage() {
        return page;
    }

    public int getContextPage() {
        return contextPage;
    }

    public UUID getOwnerId() {
        return ownerId;
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
        OWNER_LIST,
        BED_LIST,
        ACTIONS,
        TELEPORT_TARGET_LIST
    }
}
