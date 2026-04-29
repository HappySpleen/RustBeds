package me.happy.rustbeds.utils;

import me.happy.rustbeds.models.BedsDataType;
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
        private static final String LEGACY_DATA_NAMESPACE = "multiplebedspawn";

        private static final BedsDataType BEDS_DATA_TYPE = new BedsDataType();

        private static final NamespacedKey BEDS = new NamespacedKey(LEGACY_DATA_NAMESPACE, "beds");
        private static final NamespacedKey UUID = new NamespacedKey(LEGACY_DATA_NAMESPACE, "uuid");
        private static final NamespacedKey MENU_STATUS = new NamespacedKey(LEGACY_DATA_NAMESPACE, "menu-status");
        private static final NamespacedKey SHARE_PLAYER = new NamespacedKey(LEGACY_DATA_NAMESPACE, "share-player");
        private static final NamespacedKey ADMIN_OWNER = new NamespacedKey(LEGACY_DATA_NAMESPACE, "admin-owner");
        private static final NamespacedKey ADMIN_BED = new NamespacedKey(LEGACY_DATA_NAMESPACE, "admin-bed");
        private static final NamespacedKey ADMIN_TARGET = new NamespacedKey(LEGACY_DATA_NAMESPACE, "admin-target");
        private static final NamespacedKey SPAWN_LOC = new NamespacedKey(LEGACY_DATA_NAMESPACE, "spawnloc");
        private static final NamespacedKey HAS_PROP = new NamespacedKey(LEGACY_DATA_NAMESPACE, "hasprop");
        private static final NamespacedKey IS_INVISIBLE = new NamespacedKey(LEGACY_DATA_NAMESPACE, "isinvisible");
        private static final NamespacedKey CAN_PICKUP_ITEMS = new NamespacedKey(LEGACY_DATA_NAMESPACE, "canpickupitems");
        private static final NamespacedKey ALLOW_FLY = new NamespacedKey(LEGACY_DATA_NAMESPACE, "allowfly");
        private static final NamespacedKey LAST_WALK_SPEED = new NamespacedKey(LEGACY_DATA_NAMESPACE, "lastwalkspeed");
    }
}
