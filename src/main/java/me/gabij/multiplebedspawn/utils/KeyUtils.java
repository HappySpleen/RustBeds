package me.gabij.multiplebedspawn.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class KeyUtils {
    public static final String STABLE_NAMESPACE = "rustbeds";

    private static final List<String> LEGACY_NAMESPACES = List.of(
            "multiplebedspawn",
            "multiple_bed_spawn",
            "me_gabij_multiplebedspawn"
    );

    private KeyUtils() {
    }

    public static NamespacedKey key(String key) {
        return new NamespacedKey(STABLE_NAMESPACE, normalizeKey(key));
    }

    public static List<NamespacedKey> allKeys(String pluginName, String key) {
        Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add(STABLE_NAMESPACE);
        if (pluginName != null && !pluginName.isBlank()) {
            namespaces.add(pluginName.toLowerCase(Locale.ROOT));
        }
        namespaces.addAll(LEGACY_NAMESPACES);

        List<NamespacedKey> keys = new ArrayList<>();
        String normalizedKey = normalizeKey(key);
        for (String namespace : namespaces) {
            keys.add(new NamespacedKey(namespace, normalizedKey));
        }
        return keys;
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    public static <P, C> boolean hasAny(PersistentDataContainer container, String pluginName, String key,
            PersistentDataType<P, C> type) {
        for (NamespacedKey namespacedKey : allKeys(pluginName, key)) {
            if (container.has(namespacedKey, type)) {
                return true;
            }
        }
        return false;
    }

    public static <P, C> C getAny(PersistentDataContainer container, String pluginName, String key,
            PersistentDataType<P, C> type) {
        for (NamespacedKey namespacedKey : allKeys(pluginName, key)) {
            if (container.has(namespacedKey, type)) {
                return container.get(namespacedKey, type);
            }
        }
        return null;
    }

    public static void removeAll(PersistentDataContainer container, String pluginName, String key) {
        for (NamespacedKey namespacedKey : allKeys(pluginName, key)) {
            container.remove(namespacedKey);
        }
    }
}
