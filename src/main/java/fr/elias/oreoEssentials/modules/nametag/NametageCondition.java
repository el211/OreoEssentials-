package fr.elias.oreoEssentials.modules.nametag;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Represents a single condition that must be met for a nametag layer to be visible.
 * Conditions can be applied to the tag owner or the viewer.
 */
public final class NametageCondition {

    public enum Type {
        PERMISSION,      // player has the specified permission
        GAMEMODE,        // player is in the specified gamemode
        WORLD,           // player is in the specified world
        HAS_NAMETAG,     // player's nametag is enabled (owner condition)
        PAPI_EQUALS,     // PlaceholderAPI placeholder equals value
        PAPI_CONTAINS,   // PlaceholderAPI placeholder contains value
        PAPI_GREATER,    // PlaceholderAPI placeholder > double value
        PAPI_LESS        // PlaceholderAPI placeholder < double value
    }

    private final Type type;
    private final String value;
    private final String placeholder; // used for PAPI types
    private final boolean negate;

    public NametageCondition(Type type, String value, String placeholder, boolean negate) {
        this.type = type;
        this.value = value;
        this.placeholder = placeholder;
        this.negate = negate;
    }

    public boolean evaluate(Player player) {
        boolean result = switch (type) {
            case PERMISSION -> player.hasPermission(value);
            case GAMEMODE -> {
                try { yield player.getGameMode() == GameMode.valueOf(value.toUpperCase(Locale.ROOT)); }
                catch (Exception e) { yield false; }
            }
            case WORLD -> player.getWorld().getName().equalsIgnoreCase(value);
            case HAS_NAMETAG -> true; // always true unless PlayerNametagManager overrides
            case PAPI_EQUALS -> {
                String resolved = resolvePapi(player, placeholder);
                yield resolved != null && resolved.equalsIgnoreCase(value);
            }
            case PAPI_CONTAINS -> {
                String resolved = resolvePapi(player, placeholder);
                yield resolved != null && resolved.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
            }
            case PAPI_GREATER -> {
                String resolved = resolvePapi(player, placeholder);
                try { yield resolved != null && Double.parseDouble(resolved) > Double.parseDouble(value); }
                catch (NumberFormatException e) { yield false; }
            }
            case PAPI_LESS -> {
                String resolved = resolvePapi(player, placeholder);
                try { yield resolved != null && Double.parseDouble(resolved) < Double.parseDouble(value); }
                catch (NumberFormatException e) { yield false; }
            }
        };
        return negate ? !result : result;
    }

    private static String resolvePapi(Player player, String placeholder) {
        try {
            if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Evaluates all conditions in the list. Returns true if ALL pass.
     */
    public static boolean evaluateAll(List<NametageCondition> conditions, Player player) {
        for (NametageCondition c : conditions) {
            if (!c.evaluate(player)) return false;
        }
        return true;
    }

    /**
     * Parses a list of conditions from a ConfigurationSection list.
     * Each entry in the list is a ConfigurationSection with at minimum:
     *   type: PERMISSION
     *   value: some.permission
     *   negate: false  (optional, default false)
     *
     * For PAPI types:
     *   type: PAPI_EQUALS
     *   placeholder: "%player_gamemode%"
     *   value: "SURVIVAL"
     */
    public static List<NametageCondition> parseList(ConfigurationSection parent, String key) {
        List<NametageCondition> result = new ArrayList<>();
        if (parent == null) return result;

        List<?> raw = parent.getList(key);
        if (raw == null) return result;

        for (Object entry : raw) {
            if (!(entry instanceof java.util.Map<?, ?> rawMap)) continue;
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) rawMap;
                String typeStr = String.valueOf(map.getOrDefault("type", "")).toUpperCase(Locale.ROOT);
                String value = String.valueOf(map.getOrDefault("value", ""));
                String placeholder = String.valueOf(map.getOrDefault("placeholder", ""));
                boolean negate = Boolean.parseBoolean(String.valueOf(map.getOrDefault("negate", "false")));

                Type type;
                try { type = Type.valueOf(typeStr); } catch (Exception e) { continue; }

                result.add(new NametageCondition(type, value, placeholder, negate));
            } catch (Exception ignored) {}
        }
        return result;
    }
}
