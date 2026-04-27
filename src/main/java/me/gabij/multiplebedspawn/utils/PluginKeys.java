package me.gabij.multiplebedspawn.utils;

import me.gabij.multiplebedspawn.MultipleBedSpawn;
import me.gabij.multiplebedspawn.models.BedsDataType;
import org.bukkit.NamespacedKey;

public final class PluginKeys {
    private PluginKeys() {
    }

    public static NamespacedKey beds() {
        return Holder.BEDS;
    }

    public static BedsDataType bedsDataType() {
        return Holder.BEDS_DATA_TYPE;
    }

    public static NamespacedKey uuid() {
        return Holder.UUID;
    }

    public static NamespacedKey menuStatus() {
        return Holder.MENU_STATUS;
    }

    public static NamespacedKey sharePlayer() {
        return Holder.SHARE_PLAYER;
    }

    public static NamespacedKey adminOwner() {
        return Holder.ADMIN_OWNER;
    }

    public static NamespacedKey adminBed() {
        return Holder.ADMIN_BED;
    }

    public static NamespacedKey adminTarget() {
        return Holder.ADMIN_TARGET;
    }

    public static NamespacedKey spawnLoc() {
        return Holder.SPAWN_LOC;
    }

    public static NamespacedKey hasProp() {
        return Holder.HAS_PROP;
    }

    public static NamespacedKey isInvisible() {
        return Holder.IS_INVISIBLE;
    }

    public static NamespacedKey canPickupItems() {
        return Holder.CAN_PICKUP_ITEMS;
    }

    public static NamespacedKey allowFly() {
        return Holder.ALLOW_FLY;
    }

    public static NamespacedKey lastWalkSpeed() {
        return Holder.LAST_WALK_SPEED;
    }

    private static final class Holder {
        private static final MultipleBedSpawn PLUGIN = MultipleBedSpawn.getInstance();

        private static final BedsDataType BEDS_DATA_TYPE = new BedsDataType();

        private static final NamespacedKey BEDS = new NamespacedKey(PLUGIN, "beds");
        private static final NamespacedKey UUID = new NamespacedKey(PLUGIN, "uuid");
        private static final NamespacedKey MENU_STATUS = new NamespacedKey(PLUGIN, "menu-status");
        private static final NamespacedKey SHARE_PLAYER = new NamespacedKey(PLUGIN, "share-player");
        private static final NamespacedKey ADMIN_OWNER = new NamespacedKey(PLUGIN, "admin-owner");
        private static final NamespacedKey ADMIN_BED = new NamespacedKey(PLUGIN, "admin-bed");
        private static final NamespacedKey ADMIN_TARGET = new NamespacedKey(PLUGIN, "admin-target");
        private static final NamespacedKey SPAWN_LOC = new NamespacedKey(PLUGIN, "spawnLoc");
        private static final NamespacedKey HAS_PROP = new NamespacedKey(PLUGIN, "hasProp");
        private static final NamespacedKey IS_INVISIBLE = new NamespacedKey(PLUGIN, "isInvisible");
        private static final NamespacedKey CAN_PICKUP_ITEMS = new NamespacedKey(PLUGIN, "canPickupItems");
        private static final NamespacedKey ALLOW_FLY = new NamespacedKey(PLUGIN, "allowFly");
        private static final NamespacedKey LAST_WALK_SPEED = new NamespacedKey(PLUGIN, "lastWalkspeed");
    }
}
