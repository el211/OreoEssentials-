package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.modules.chat.hooks.ItemsAdderHook;
import fr.elias.oreoEssentials.modules.chat.hooks.NexoHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class ChatItemHandler {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final boolean enabled;
    private final String placeholder;
    private final Component emptyHandComponent;

    private final boolean itemsAdderEnabled;
    private final boolean nexoEnabled;

    public ChatItemHandler(CustomConfig chatConfig) {
        FileConfiguration cfg = chatConfig.getCustomConfig();

        this.enabled     = cfg.getBoolean("chat.item-in-chat.enabled", true);
        this.placeholder = cfg.getString("chat.item-in-chat.placeholder", "[item]");

        String emptyText = cfg.getString("chat.item-in-chat.empty-hand-text", "<gray>[Air]</gray>");
        Component parsed;
        try {
            parsed = MM.deserialize(emptyText);
        } catch (Throwable t) {
            parsed = Component.text("[Air]").color(NamedTextColor.GRAY);
        }
        this.emptyHandComponent = parsed;

        this.itemsAdderEnabled = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        this.nexoEnabled       = Bukkit.getPluginManager().isPluginEnabled("Nexo");

        Bukkit.getLogger().info("[ChatItemHandler] Initialized. enabled=" + enabled
                + ", placeholder=" + placeholder
                + ", ItemsAdder=" + itemsAdderEnabled
                + ", Nexo=" + nexoEnabled);
    }


    public boolean containsItemPlaceholder(String rawMessage) {
        return enabled
                && rawMessage != null
                && rawMessage.toLowerCase().contains(placeholder.toLowerCase());
    }


    public Component processItemPlaceholder(Component component, Player player) {
        Component itemComponent = buildItemComponent(player.getInventory().getItemInMainHand(), player);

        return component.replaceText(TextReplacementConfig.builder()
                .matchLiteral(placeholder)
                .replacement(itemComponent)
                .build());
    }


    private Component buildItemComponent(ItemStack item, Player player) {
        if (item == null || item.getType() == Material.AIR) {
            return emptyHandComponent;
        }

        Component displayName = resolveItemName(item);

        return Component.text()
                .append(Component.text("[").color(NamedTextColor.WHITE))
                .append(displayName)
                .append(Component.text("]").color(NamedTextColor.WHITE))
                .hoverEvent(buildHoverEvent(item))
                .build();
    }


    @SuppressWarnings("UnstableApiUsage")
    private HoverEvent<?> buildHoverEvent(ItemStack item) {
        try {
            return item.asHoverEvent();
        } catch (Throwable t) {
            return HoverEvent.showText(buildFallbackHoverText(item));
        }
    }

    private Component buildFallbackHoverText(ItemStack item) {
        List<Component> lines = new ArrayList<>();

        // Name
        lines.add(resolveItemName(item));

        // Custom item ID
        String customId = resolveCustomId(item);
        if (customId != null) {
            lines.add(Component.text(customId).color(NamedTextColor.DARK_GRAY));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<Component> loreComponents = meta.lore();   // Adventure API (Paper)
            if (loreComponents != null && !loreComponents.isEmpty()) {
                lines.add(Component.empty());
                lines.addAll(loreComponents);
            } else {
                List<String> legacyLore = meta.getLore();
                if (legacyLore != null && !legacyLore.isEmpty()) {
                    lines.add(Component.empty());
                    for (String line : legacyLore) {
                        lines.add(LEGACY.deserialize(line));
                    }
                }
            }
        }

        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (!enchants.isEmpty()) {
            lines.add(Component.empty());
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                String name  = friendlyEnchantName(entry.getKey().getKey().getKey());
                String level = toRoman(entry.getValue());
                lines.add(Component.text(name + " " + level).color(NamedTextColor.GRAY));
            }
        }

        if (meta instanceof Damageable damageable) {
            short maxDur = item.getType().getMaxDurability();
            if (maxDur > 0) {
                int remaining = maxDur - damageable.getDamage();
                double fraction = (double) remaining / maxDur;
                NamedTextColor color = fraction > 0.5 ? NamedTextColor.GREEN
                        : fraction > 0.25 ? NamedTextColor.YELLOW
                        : NamedTextColor.RED;
                lines.add(Component.empty());
                lines.add(Component.text("Durability: " + remaining + " / " + maxDur).color(color));
            }
        }

        if (item.getAmount() > 1) {
            lines.add(Component.text("x" + item.getAmount()).color(NamedTextColor.GRAY));
        }

        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i));
            if (i < lines.size() - 1) builder.append(Component.newline());
        }
        return builder.build();
    }


    private Component resolveItemName(ItemStack item) {
        if (itemsAdderEnabled) {
            try {
                Component name = ItemsAdderHook.getItemName(item);
                if (name != null) return name;
            } catch (Throwable ignored) {}
        }
        if (nexoEnabled) {
            try {
                Component name = NexoHook.getItemName(item);
                if (name != null) return name;
            } catch (Throwable ignored) {}
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            try {
                Component comp = meta.displayName();
                if (comp != null) return comp;
            } catch (Throwable ignored) {}
            return LEGACY.deserialize(meta.getDisplayName());
        }
        return Component.text(friendlyMaterialName(item.getType())).color(NamedTextColor.WHITE);
    }

    private String resolveCustomId(ItemStack item) {
        if (itemsAdderEnabled) {
            try {
                String id = ItemsAdderHook.getItemId(item);
                if (id != null) return id;
            } catch (Throwable ignored) {}
        }
        if (nexoEnabled) {
            try {
                String id = NexoHook.getItemId(item);
                if (id != null) return id;
            } catch (Throwable ignored) {}
        }
        return null;
    }


    private String friendlyMaterialName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String friendlyEnchantName(String key) {
        String[] words = key.replace("-", "_").split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV";
            case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII";
            case 9 -> "IX"; case 10 -> "X"; default -> String.valueOf(level);
        };
    }
}