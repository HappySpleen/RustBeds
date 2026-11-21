package me.happy.rustbeds.gui;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.utils.PluginKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public final class BedrockMenus {
    private final RustBeds plugin;
    private Forms forms;

    public BedrockMenus(RustBeds plugin) {
        this.plugin = plugin;
    }

    public boolean isBedrockPlayer(Player player) {
        Forms available = availableForms();
        return available != null && available.isBedrockPlayer(player);
    }

    public boolean open(Player player, Inventory inventory, String title, int informationSlot, Object menu,
            IntConsumer onSelect, Runnable onClose) {
        Forms available = availableForms();
        return available != null && available.open(player, inventory, title, informationSlot, menu, onSelect, onClose);
    }

    public boolean rename(Player player, String title, String label, String currentName, Consumer<String> onSubmit,
            Runnable onClose) {
        Forms available = availableForms();
        return available != null && available.rename(player, title, label, currentName, onSubmit, onClose);
    }

    public void close(UUID playerId, Object menu) {
        if (forms != null) {
            forms.close(playerId, menu);
        }
    }

    public boolean forget(UUID playerId) {
        return forms != null && forms.forget(playerId);
    }

    private Forms availableForms() {
        if (forms == null && (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")
                || Bukkit.getPluginManager().isPluginEnabled("floodgate"))) {
            forms = new Forms(plugin);
        }
        return forms;
    }

    // Keep optional API references in classes loaded only when their plugins are enabled.
    private static final class Forms {
        private static final LegacyComponentSerializer LEGACY_TEXT = LegacyComponentSerializer.legacySection();

        private final RustBeds plugin;
        private final Map<UUID, OpenForm> openForms = new HashMap<>();
        private final Set<Class<?>> failedProviders = new HashSet<>();

        private Forms(RustBeds plugin) {
            this.plugin = plugin;
        }

        private boolean isBedrockPlayer(Player player) {
            return BedrockFormTransport.find(player.getUniqueId()) != null;
        }

        private boolean open(Player player, Inventory inventory, String title, int informationSlot, Object menu,
                IntConsumer onSelect, Runnable onClose) {
            BedrockFormTransport client = BedrockFormTransport.find(player.getUniqueId());
            if (client == null) {
                return false;
            }

            List<String> buttons = new ArrayList<>();
            List<String> information = new ArrayList<>();
            List<String> pointDetails = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            String manageHint = ChatColor.stripColor(plugin.message("manage-menu-click-manage", "Click to manage this bed."));
            // These inventories supply the existing menu text and slot identities without opening a container.
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item == null || !item.hasItemMeta()) {
                    continue;
                }

                ItemMeta meta = item.getItemMeta();
                Component displayName = meta.displayName();
                String name = displayName == null ? "" : LEGACY_TEXT.serialize(displayName);
                if (ChatColor.stripColor(name).isBlank()) {
                    continue;
                }

                List<Component> sourceLore = meta.lore();
                List<String> lore = sourceLore == null ? List.of() : sourceLore.stream().map(LEGACY_TEXT::serialize).toList();
                if (slot == informationSlot || item.getType() == Material.GRAY_DYE) {
                    information.add(lore.isEmpty() ? name : name + "\n" + String.join("\n", lore));
                    continue;
                }

                buttons.add(name + buttonSubtitle(lore));
                int selectedSlot = slot;
                actions.add(() -> onSelect.accept(selectedSlot));
                if (lore.size() > 1 && isSavedPoint(meta)) {
                    pointDetails.add(name + "\n" + String.join("\n", detailsLore(lore, manageHint)));
                }
            }

            if (!pointDetails.isEmpty()) {
                String detailsTitle = plugin.message("beds-point-details", "Respawn point details");
                buttons.add(detailsTitle);
                actions.add(() -> showDetails(player, client, menu, detailsTitle, pointDetails, onClose,
                        () -> open(player, inventory, title, informationSlot, menu, onSelect, onClose)));
            }

            showChoices(player, client, menu, title, String.join("\n\n", information), buttons, actions, onClose);
            return true;
        }

        private void showDetails(Player player, BedrockFormTransport client, Object menu, String title, List<String> details,
                Runnable onClose, Runnable onBack) {
            showChoices(player, client, menu, title, String.join("\n\n", details),
                    List.of(plugin.message("bed-action-back", "Back to beds")), List.of(onBack), onClose);
        }

        private void showChoices(Player player, BedrockFormTransport client, Object menu,
                String title, String content, List<String> buttons,
                List<Runnable> actions, Runnable onClose) {
            OpenForm pending = new OpenForm(menu, client);
            show(player.getUniqueId(), pending, () -> client.sendChoices(title, content, buttons, button -> {
                Runnable action = button >= 0 && button < actions.size() ? actions.get(button) : onClose;
                complete(player, pending, action);
            }, () -> complete(player, pending, onClose)), onClose);
        }

        private boolean rename(Player player, String title, String label, String currentName, Consumer<String> onSubmit,
                Runnable onClose) {
            BedrockFormTransport client = BedrockFormTransport.find(player.getUniqueId());
            if (client == null) {
                return false;
            }

            OpenForm pending = new OpenForm(null, client);
            show(player.getUniqueId(), pending, () -> client.sendRename(title, label, currentName,
                    name -> complete(player, pending, () -> onSubmit.accept(name)),
                    () -> complete(player, pending, onClose)), onClose);
            return true;
        }

        private void close(UUID playerId, Object menu) {
            OpenForm pending = openForms.get(playerId);
            if (pending != null && pending.menu() == menu) {
                openForms.remove(playerId);
                closeForm(pending.client());
            }
        }

        private boolean forget(UUID playerId) {
            return openForms.remove(playerId) != null;
        }

        private void show(UUID playerId, OpenForm pending, SendForm send, Runnable onClose) {
            OpenForm previous = openForms.put(playerId, pending);
            if (previous != null) {
                closeForm(previous.client());
            }
            try {
                if (send.run()) {
                    return;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                logFormFailure(pending.client(), e);
            }
            openForms.remove(playerId);
            onClose.run();
        }

        private void closeForm(BedrockFormTransport client) {
            try {
                client.close().run();
            } catch (LinkageError | RuntimeException e) {
                logFormFailure(client, e);
            }
        }

        private void logFormFailure(BedrockFormTransport client, Throwable failure) {
            if (failedProviders.add(client.apiType())) {
                plugin.getLogger().log(Level.WARNING, "Could not use Bedrock forms through "
                        + client.apiType().getName(), failure);
            }
        }

        private void complete(Player player, OpenForm pending, Runnable action) {
            if (!plugin.isEnabled()) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                UUID playerId = player.getUniqueId();
                if (openForms.get(playerId) == pending && player.isOnline()) {
                    openForms.remove(playerId);
                    action.run();
                }
            });
        }

        private static String buttonSubtitle(List<String> lore) {
            // Status lines follow a blank separator after the saved point's metadata.
            int separator = lore.indexOf("");
            int line = separator + 1;
            return line < lore.size() ? "\n" + lore.get(line) : "";
        }

        private static List<String> detailsLore(List<String> lore, String manageHint) {
            if (!ChatColor.stripColor(lore.getLast()).equals(manageHint)) {
                return lore;
            }

            int end = lore.size() - 1;
            if (lore.get(end - 1).isEmpty()) {
                end--;
            }
            return lore.subList(0, end);
        }

        private static boolean isSavedPoint(ItemMeta meta) {
            var data = meta.getPersistentDataContainer();
            return data.has(PluginKeys.menuStatus(), PersistentDataType.STRING)
                    || data.has(PluginKeys.adminBed(), PersistentDataType.STRING);
        }

        @FunctionalInterface
        private interface SendForm {
            boolean run() throws ReflectiveOperationException;
        }

        private record OpenForm(Object menu, BedrockFormTransport client) {
        }
    }
}
