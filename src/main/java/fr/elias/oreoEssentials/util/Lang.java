package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lang utility for OreoEssentials
 *
 * Goals (FIXED):
 * - Supports MiniMessage (<gradient>, <#RRGGBB>, etc.) + legacy & codes + legacy hex variants.
 * - NEVER feeds MiniMessage a string containing legacy '§' formatting codes.
 * - Keeps %prefix% as RAW MiniMessage (not serialized legacy) to avoid mixing formats.
 * - Provides both:
 *   - Component API (preferred on Paper/Adventure)
 *   - Legacy String API (for Inventory titles / old Bukkit APIs)
 * - Optional PlaceholderAPI support.
 */
public final class Lang {

    private static OreoEssentials plugin;
    private static YamlConfiguration cfg;

    /**
     * Prefix MUST remain RAW (MiniMessage string), never legacy §.
     * If we store it as legacy, it will get injected into MiniMessage inputs and trigger
     * "Legacy formatting codes detected in a MiniMessage string".
     */
    private static String prefixRaw = "";

    // MiniMessage (non-strict: unknown tags are ignored instead of crashing)
    private static final MiniMessage MINI = MiniMessage.builder()
            .strict(false)
            .build();

    // Serialize Component -> legacy § string (with hex in §x§R§R§G§G§B§B format)
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .build();

    // ------------------------------------------------------------
    // Legacy (&) -> MiniMessage conversion patterns
    // ------------------------------------------------------------

    // &#RRGGBB -> <#RRGGBB>
    private static final Pattern LEGACY_HEX_HASH = Pattern.compile("(?i)&#([0-9a-f]{6})");

    // &x&8&7&c&e&e&b -> <#87ceeb>
    private static final Pattern LEGACY_HEX_AMP_X = Pattern.compile("(?i)&x((&[0-9a-f]){6})");

    // §x§8§7§c§e§e§b -> <#87ceeb> (shouldn't exist in MM input, but we normalize just in case)
    private static final Pattern LEGACY_HEX_SECTION_X = Pattern.compile("(?i)§x((§[0-9a-f]){6})");

    // &a, &l, &r, ...
    private static final Pattern LEGACY_CODES = Pattern.compile("(?i)&([0-9a-fk-or])");

    private Lang() {}

    // ------------------------------------------------------------
    // INIT + LOAD / MERGE
    // ------------------------------------------------------------

    public static void init(OreoEssentials pl) {
        plugin = pl;

        File f = new File(plugin.getDataFolder(), "lang.yml");
        if (!f.exists()) {
            plugin.saveResource("lang.yml", false);
        }

        cfg = YamlConfiguration.loadConfiguration(f);

        // Load default lang.yml from jar
        YamlConfiguration defCfg = new YamlConfiguration();
        try (InputStream is = plugin.getResource("lang.yml")) {
            if (is != null) {
                defCfg = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(is, StandardCharsets.UTF_8)
                );
            } else {
                plugin.getLogger().warning("[Lang] Could not find default lang.yml in JAR.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[Lang] Failed to load default lang.yml from JAR: " + ex.getMessage());
        }

        // Merge missing keys from default into disk config
        if (defCfg != null) {
            boolean changed = false;

            for (String key : defCfg.getKeys(true)) {
                Object defVal = defCfg.get(key);
                if (defVal instanceof ConfigurationSection) continue;

                if (!cfg.contains(key)) {
                    cfg.set(key, defVal);
                    changed = true;
                }
            }

            if (changed) {
                try {
                    cfg.save(f);
                    plugin.getLogger().info("[Lang] Added missing lang.yml keys from default.");
                } catch (IOException e) {
                    plugin.getLogger().warning("[Lang] Failed to save merged lang.yml: " + e.getMessage());
                }
            }
        }

        // IMPORTANT FIX: store prefix RAW, do not color() it here.
        prefixRaw = get("general.prefix", "");
    }

    public static String get(String path, String def) {
        if (cfg == null) return def;
        return cfg.getString(path, def);
    }

    public static List<String> getList(String path) {
        if (cfg == null) return List.of();
        return cfg.getStringList(path);
    }

    public static boolean getBool(String path, boolean def) {
        if (cfg == null) return def;
        return cfg.getBoolean(path, def);
    }

    public static double getDouble(String path, double def) {
        if (cfg == null) return def;
        return cfg.getDouble(path, def);
    }

    // ------------------------------------------------------------
    // MESSAGE BUILDERS (RAW -> Component / Legacy)
    // ------------------------------------------------------------

    /**
     * Returns the fully resolved raw string (MiniMessage + placeholders),
     * WITHOUT converting it to legacy §.
     *
     * This guarantees MiniMessage never sees legacy codes.
     */
    public static String raw(String path, Map<String, String> vars, Player papiFor) {
        String s = get(path, "");
        if (s == null || s.isEmpty()) return "";

        // allow "\n" in config
        s = s.replace("\\n", "\n");

        // inject %prefix% using RAW prefix (MiniMessage string)
        if (s.contains("%prefix%")) {
            s = s.replace("%prefix%", prefixRaw);
        }

        // simple variables: %key%
        if (vars != null) {
            for (var e : vars.entrySet()) {
                String key = "%" + e.getKey() + "%";
                s = s.replace(key, String.valueOf(e.getValue()));
            }
        }

        // PlaceholderAPI (optional)
        if (papiFor != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                s = PlaceholderAPI.setPlaceholders(papiFor, s);
            } catch (Throwable ignored) {}
        }

        return s;
    }

    /**
     * Preferred: returns a Component (Adventure).
     * Safe: legacy codes are converted to MiniMessage tags BEFORE parsing.
     */
    public static Component msgComp(String path, Map<String, String> vars, Player papiFor) {
        String s = raw(path, vars, papiFor);
        if (s.isEmpty()) return Component.empty();
        return toComponent(s);
    }

    /**
     * For old Bukkit APIs: returns legacy § string (with hex).
     * Safe: we build Component first, then serialize.
     */
    public static String msgLegacy(String path, Map<String, String> vars, Player papiFor) {
        Component c = msgComp(path, vars, papiFor);
        if (c == null) return "";
        return LEGACY_HEX.serialize(c);
    }

    /**
     * Send a message safely.
     * - Players: use Adventure Component (no legacy mixing)
     * - Console/others: fallback to legacy string
     */
    public static void send(CommandSender to, String path, Map<String, String> vars, Player papiFor) {
        if (to == null) return;

        Component c = msgComp(path, vars, papiFor);
        if (c == null || c.equals(Component.empty())) return;

        if (to instanceof Player p) {
            p.sendMessage(c);
        } else {
            // console etc.
            to.sendMessage(LEGACY_HEX.serialize(c));
        }
    }

    // ------------------------------------------------------------
    // TITLE HELPERS
    // ------------------------------------------------------------

    public static void sendTitle(Player player,
                                 String titleKey,
                                 String subKey,
                                 Map<String, String> vars,
                                 int fadeIn,
                                 int stay,
                                 int fadeOut) {
        if (player == null) return;

        Component title = Component.empty();
        Component subtitle = Component.empty();

        if (titleKey != null && !titleKey.isEmpty()) {
            title = msgComp(titleKey, vars, player);
        }
        if (subKey != null && !subKey.isEmpty()) {
            subtitle = msgComp(subKey, vars, player);
        }

        player.showTitle(net.kyori.adventure.title.Title.title(
                title,
                subtitle,
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }

    public static void sendTitle(Player player,
                                 String titleKey,
                                 String subKey,
                                 Map<String, String> vars) {
        sendTitle(player, titleKey, subKey, vars, 10, 40, 10);
    }

    // ------------------------------------------------------------
    // TIME HUMANIZER (UNCHANGED LOGIC)
    // ------------------------------------------------------------

    public static String timeHuman(long seconds) {
        boolean humanize = cfg != null && cfg.getBoolean("kits.time.humanize", true);
        if (!humanize) return seconds + "s";

        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        long secs = seconds;

        String ud = get("kits.time.units.day", "d");
        String uh = get("kits.time.units.hour", "h");
        String um = get("kits.time.units.minute", "m");
        String us = get("kits.time.units.second", "s");
        int maxChunks = cfg != null ? cfg.getInt("kits.time.max-chunks", 3) : 3;

        StringBuilder out = new StringBuilder();
        int chunks = 0;

        if (days > 0 && chunks < maxChunks) { out.append(days).append(ud).append(' '); chunks++; }
        if (hours > 0 && chunks < maxChunks) { out.append(hours).append(uh).append(' '); chunks++; }
        if (minutes > 0 && chunks < maxChunks) { out.append(minutes).append(um).append(' '); chunks++; }
        if (secs > 0 && chunks < maxChunks) { out.append(secs).append(us).append(' '); chunks++; }

        String s = out.toString().trim();
        return s.isEmpty() ? "0" + us : s;
    }

    // ------------------------------------------------------------
    // CORE FIX: Convert ANY legacy (& or §) into MiniMessage tags BEFORE deserializing
    // ------------------------------------------------------------

    /**
     * Convert a raw config string (may contain MiniMessage tags + legacy codes)
     * into a safe Adventure Component.
     *
     * Key properties:
     * - NEVER calls ChatColor.translateAlternateColorCodes before MiniMessage (that would create § codes).
     * - Converts legacy into MiniMessage tags:
     *     &a -> <green>, &l -> <bold>, &#RRGGBB -> <#RRGGBB>, &x&... -> <#...>
     * - Normalizes any accidental '§' to '&' then converts.
     * - Disables italics by default (matches common chat expectations).
     */
    public static Component toComponent(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        // Normalize any accidental legacy § to '&' (so we can convert safely)
        String mmInput = input.replace(LegacyComponentSerializer.SECTION_CHAR, '&');

        // Convert legacy variants into MiniMessage tags
        mmInput = legacyAmpToMiniMessage(mmInput);

        // Parse with MiniMessage
        Component c = MINI.deserialize(mmInput);

        // Common MC behavior: no italics by default
        return c.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Convert legacy ampersand codes into MiniMessage tags.
     * Supports:
     * - &x&8&7&c&e&e&b (legacy hex)
     * - &#87CEEB (legacy hex)
     * - &a &l &r etc
     */
    private static String legacyAmpToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return "";

        // Handle legacy hex with &x&... (e.g. &x&8&7&c&e&e&b)
        Matcher mx = LEGACY_HEX_AMP_X.matcher(input);
        StringBuffer sbx = new StringBuffer();
        while (mx.find()) {
            String hex = mx.group(1).replace("&", ""); // 87ceeb
            mx.appendReplacement(sbx, "<#" + hex + ">");
        }
        mx.appendTail(sbx);
        input = sbx.toString();

        // Handle legacy hex with §x§... (rare, but just in case after weird sources)
        Matcher ms = LEGACY_HEX_SECTION_X.matcher(input.replace('&', '§'));
        // We intentionally run this on a §-version to match pattern, then restore output back into input.
        // Convert by scanning original input for §x... patterns after reintroducing §.
        String sectionCandidate = input.replace('&', '§');
        ms = LEGACY_HEX_SECTION_X.matcher(sectionCandidate);
        StringBuffer sbs = new StringBuffer();
        while (ms.find()) {
            String group = ms.group(1); // §8§7§c§e§e§b
            String hex = group.replace("§", "");
            ms.appendReplacement(sbs, "<#" + hex + ">");
        }
        ms.appendTail(sbs);
        // Convert back to normal string (it already contains <#...>)
        input = sbs.toString().replace('§', '&'); // keep consistent

        // Handle legacy hex &#RRGGBB
        Matcher mh = LEGACY_HEX_HASH.matcher(input);
        StringBuffer sbh = new StringBuffer();
        while (mh.find()) {
            mh.appendReplacement(sbh, "<#" + mh.group(1) + ">");
        }
        mh.appendTail(sbh);
        input = sbh.toString();

        // Handle classic & codes -> tags
        Matcher m = LEGACY_CODES.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            char code = Character.toLowerCase(m.group(1).charAt(0));
            String repl = legacyCodeToMiniMessage(code);
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String legacyCodeToMiniMessage(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }

    // ------------------------------------------------------------
    // BACKWARD-COMPAT HELPERS (Optional)
    // ------------------------------------------------------------

    /**
     * Old API compatibility: colorize to legacy string.
     * This is SAFE because it converts to Component first (no legacy -> MiniMessage mixing).
     */
    public static String color(String s) {
        if (s == null || s.isEmpty()) return "";
        return LEGACY_HEX.serialize(toComponent(s));
    }

    /**
     * If you still have code that expects a String message:
     * Prefer msgLegacy(...) instead of msg(...).
     */
    @Deprecated
    public static String msg(String path, Map<String, String> vars, Player papiFor) {
        return msgLegacy(path, vars, papiFor);
    }
}
