package me.happy.rustbeds.gui;

import me.happy.rustbeds.RustBeds;
import me.happy.rustbeds.gui.BedrockFormTransport.Button;
import me.happy.rustbeds.models.BedData;
import me.happy.rustbeds.utils.PluginKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.logging.Level;

public final class BedrockMenus {
    // The resource pack recognizes this formatting sequence on read-only rows.
    private static final String DETAILS_ROW_MARKER = "§r§0§r§0§r§0§r";

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
            IntFunction<BedData> pointAtSlot, IntConsumer onSelect, Runnable onClose) {
        Forms available = availableForms();
        return available != null
                && available.open(player, inventory, title, informationSlot, menu, pointAtSlot, onSelect, onClose);
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

    static String pointTexture(BedData point) {
        return pointAppearance(point).texture();
    }

    static PointAppearance pointAppearance(BedData point) {
        Location location = point.getBedLocation();
        World world = location == null ? null : location.getWorld();
        BlockData block = null;
        if (world != null && (point.isRespawnAnchor()
                || world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))) {
            block = world.getBlockAt(location).getBlockData();
        }

        if (point.isRespawnAnchor()) {
            if (block instanceof RespawnAnchor anchor) {
                int charges = anchor.getCharges();
                String texture = "textures/blocks/respawn_anchor_side" + Math.clamp(charges, 0, 4);
                return new PointAppearance(texture, charges, anchor.getMaximumCharges());
            }
            return new PointAppearance("textures/blocks/respawn_anchor_side0", 0, 0);
        }

        Material material = block instanceof Bed bed ? bed.getMaterial() : point.getBedMaterial();
        return new PointAppearance(bedTexture(material), 0, 0);
    }

    private static String bedTexture(Material material) {
        if (material == Material.LIGHT_GRAY_BED) {
            return "textures/items/bed_silver";
        }
        String name = material == null ? "" : material.name();
        if (!name.endsWith("_BED") || name.startsWith("LEGACY_")) {
            return "textures/items/bed_white";
        }
        return "textures/items/bed_" + name.substring(0, name.length() - "_BED".length()).toLowerCase(Locale.ROOT);
    }

    record PointAppearance(String texture, int charges, int maxCharges) {
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
                IntFunction<BedData> pointAtSlot, IntConsumer onSelect, Runnable onClose) {
            BedrockFormTransport client = BedrockFormTransport.find(player.getUniqueId());
            if (client == null) {
                return false;
            }

            List<Button> buttons = new ArrayList<>();
            List<String> information = new ArrayList<>();
            List<PointDetails> pointDetails = new ArrayList<>();
            String detailsTitle = plugin.message("beds-point-details", "Respawn point details");
            boolean respawnList = inventory.getHolder() instanceof RespawnMenuHolder holder
                    && holder.getViewType() == RespawnMenuHolder.ViewType.RESPAWN_LIST;
            String manageHint = ChatColor.stripColor(plugin.message("manage-menu-click-manage", "Click to manage this bed."));
            String respawnHint = ChatColor.stripColor(plugin.message("respawn-menu-click-respawn",
                    "Left-click to respawn here."));
            Runnable reopen = () -> open(player, inventory, title, informationSlot, menu, pointAtSlot, onSelect, onClose);
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
                BedData point = pointAtSlot.apply(slot);
                String image = point == null ? null : pointTexture(point);
                if (slot == informationSlot && point != null) {
                    PointDetails preview = new PointDetails(name, lore, point);
                    buttons.add(new Button(name + buttonSubtitle(lore, false), image,
                            () -> showDetails(player, client, menu, detailsTitle, List.of(preview), reopen)));
                    continue;
                }
                if (slot == informationSlot || item.getType() == Material.GRAY_DYE) {
                    information.add(lore.isEmpty() ? name : name + "\n" + String.join("\n", lore));
                    continue;
                }

                boolean savedPoint = isSavedPoint(meta);
                int selectedSlot = slot;
                buttons.add(new Button(name + buttonSubtitle(lore, respawnList && savedPoint), image,
                        () -> onSelect.accept(selectedSlot)));
                if (point != null && savedPoint) {
                    pointDetails.add(new PointDetails(name, detailsLore(lore, manageHint, respawnHint), point));
                }
            }

            if (!pointDetails.isEmpty()) {
                buttons.add(new Button(detailsTitle,
                        () -> showDetails(player, client, menu, detailsTitle, pointDetails, reopen)));
            }

            showChoices(player, client, menu, title, String.join("\n\n", information), buttons, onClose);
            return true;
        }

        private void showDetails(Player player, BedrockFormTransport client, Object menu, String title,
                List<PointDetails> details, Runnable onBack) {
            boolean useLayoutPack = plugin.getConfig().getBoolean("bedrock-details-layout-pack");
            List<Button> rows = new ArrayList<>();
            List<String> text = new ArrayList<>();
            for (PointDetails point : details) {
                PointAppearance appearance = pointAppearance(point.data());
                String description = pointDescription(point, appearance);
                if (useLayoutPack) {
                    rows.add(new Button(DETAILS_ROW_MARKER + description, appearance.texture(), onBack));
                } else {
                    text.add(description);
                }
            }
            showChoices(player, client, menu, title, String.join("\n\n", text), rows, onBack);
        }

        private String pointDescription(PointDetails point, PointAppearance appearance) {
            List<String> lore = new ArrayList<>(point.lore());
            if (point.data().isRespawnAnchor()) {
                String charges = RespawnPointMenuLore.anchorChargesLine(plugin, appearance.charges(),
                        appearance.maxCharges());
                // Anchor charges are the last line of saved-point metadata, before the status separator.
                int separator = lore.indexOf("");
                if (separator > 0) {
                    lore.set(separator - 1, charges);
                } else {
                    lore.add(charges);
                }
                if (appearance.charges() > 0) {
                    String depleted = ChatColor.stripColor(plugin.message("respawn-anchor-depleted",
                            "This respawn anchor has no charges."));
                    lore.removeIf(line -> ChatColor.stripColor(line).equals(depleted));
                }
            }
            lore.removeIf(String::isEmpty);
            return lore.isEmpty() ? point.name() : point.name() + "\n" + String.join("\n", lore);
        }

        private void showChoices(Player player, BedrockFormTransport client, Object menu,
                String title, String content, List<Button> buttons, Runnable onClose) {
            OpenForm pending = new OpenForm(menu, client);
            show(player.getUniqueId(), pending, () -> client.sendChoices(title, content, buttons, button -> {
                Runnable action = button >= 0 && button < buttons.size() ? buttons.get(button).action() : onClose;
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

        private static String buttonSubtitle(List<String> lore, boolean showAllStatusLines) {
            // Status lines follow a blank separator after the saved point's metadata.
            int separator = lore.indexOf("");
            int line = separator + 1;
            if (line >= lore.size()) {
                return "";
            }
            return "\n" + (showAllStatusLines ? String.join("\n", lore.subList(line, lore.size())) : lore.get(line));
        }

        private static List<String> detailsLore(List<String> lore, String manageHint, String respawnHint) {
            if (lore.isEmpty()) {
                return lore;
            }
            String last = ChatColor.stripColor(lore.getLast());
            if (!last.equals(manageHint) && !last.equals(respawnHint)) {
                return lore;
            }
            return lore.subList(0, lore.size() - 1);
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

        private record PointDetails(String name, List<String> lore, BedData data) {
        }
    }
}
