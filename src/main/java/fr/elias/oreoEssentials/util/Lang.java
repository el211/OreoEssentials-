// File: src/main/java/fr/elias/oreoEssentials/util/Lang.java
package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MiniMessage-first lang with legacy-& bridge + PAPI + console fallback + GUI helpers.
 * Now with Map-based variable replacement support.
 */
public final class Lang {

    private static OreoEssentials plugin;
    private static YamlConfiguration cfg;

    /** RAW MiniMessage, never pre-serialized (to keep hover/gradient possible). */
    private static String prefixRaw = "";

    // Parsers/serializers
    private static final MiniMessage MM = MiniMessage.builder().strict(false).build();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    // Detection + conversions
    private static final Pattern AMP_OR_SECTION = Pattern.compile("(?i)(?:&|§)[0-9A-FK-ORX]");
    private static final Pattern LEGACY_HEX_AMP_X = Pattern.compile("(?i)&x((&[0-9a-f]){6})");
    private static final Pattern LEGACY_HEX_SECTION_X = Pattern.compile("(?i)§x((§[0-9a-f]){6})");
    private static final Pattern LEGACY_HEX_HASH = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY_CODES = Pattern.compile("(?i)&([0-9a-fk-or])");

    private Lang() {}

    /* ============================= INIT / RELOAD ============================= */

    public static void init(OreoEssentials pl) {
        plugin = Objects.requireNonNull(pl, "plugin");

        File f = new File(plugin.getDataFolder(), "lang.yml");
        if (!f.exists()) plugin.saveResource("lang.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);

        // Merge defaults from jar (fills missing keys; preserves user edits)
        YamlConfiguration defCfg = new YamlConfiguration();
        try (InputStream is = plugin.getResource("lang.yml")) {
            if (is != null) {
                defCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            } else {
                plugin.getLogger().warning("[Lang] Default lang.yml wasn't found in JAR.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[Lang] Failed to read default lang.yml: " + ex.getMessage());
        }
        if (defCfg != null) {
            boolean changed = false;
            for (String key : defCfg.getKeys(true)) {
                Object defVal = defCfg.get(key);
                if (defVal instanceof ConfigurationSection) continue;
                if (!cfg.contains(key)) { cfg.set(key, defVal); changed = true; }
            }
            if (changed) {
                try { cfg.save(f); }
                catch (IOException e) { plugin.getLogger().warning("[Lang] Save merged lang.yml failed: " + e.getMessage()); }
            }
        }

        prefixRaw = get("general.prefix", "");
    }

    public static void reload() {
        File f = new File(Objects.requireNonNull(plugin).getDataFolder(), "lang.yml");
        cfg = YamlConfiguration.loadConfiguration(f);
        prefixRaw = get("general.prefix", "");
    }

    /* ============================= LOW-LEVEL GETTERS ============================= */

    public static String get(String path, String def) {
        if (cfg == null) return def;
        String v = cfg.getString(path, def);
        return v == null ? def : v;
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

    /* ============================= CORE MSG METHODS WITH MAP SUPPORT ============================= */

    /**
     * Get message with variable replacement support.
     * @param key Lang key
     * @param vars Variables to replace (can be null)
     * @param viewer Player for PAPI (can be null)
     * @return Formatted message as legacy string
     */
    public static String msg(String key, Map<?, ?> vars, Player viewer) {
        String resolved = rawOrDefault(key, null, vars, viewer);
        return LEGACY_SEC.serialize(toComponent(resolved));
    }

    /**
     * Get message without variables (simple key + player).
     * @param key Lang key
     * @param viewer Player for PAPI (can be null)
     * @return Formatted message as legacy string
     */
    public static String msg(String key, Player viewer) {
        return msg(key, (Map<?, ?>) null, viewer);
    }

    /**
     * Get message with default value and variable replacement.
     * @param key Lang key
     * @param def Default if key not found
     * @param vars Variables to replace (can be null)
     * @param viewer Player for PAPI (can be null)
     * @return Formatted message as legacy string
     */
    public static String msgWithDefault(String key, String def, Map<?, ?> vars, Player viewer) {
        String resolved = rawOrDefault(key, def, vars, viewer);
        return LEGACY_SEC.serialize(toComponent(resolved));
    }

    /**
     * Get message with default value, no variables.
     * @param key Lang key
     * @param def Default if key not found
     * @param viewer Player for PAPI (can be null)
     * @return Formatted message as legacy string
     */
    public static String msgWithDefault(String key, String def, Player viewer) {
        return msgWithDefault(key, def, null, viewer);
    }

    /**
     * DEPRECATED: Old signature for backwards compatibility.
     * Use msg(key, viewer) or msgWithDefault(key, def, viewer) instead.
     * This overload exists to catch old code patterns.
     */
    @Deprecated
    public static String msg(String key, String def, Player viewer) {
        return msgWithDefault(key, def, viewer);
    }

    /**
     * Convenience: resolve message by key, then apply %placeholders% from vars.
     */
    public static String msgv(String key, Map<?, ?> vars, Player context) {
        return msg(key, vars, context);
    }

    /**
     * Same as above but allows a default string if the key is missing.
     */
    public static String msgv(String key, String def, Map<?, ?> vars, Player context) {
        return msgWithDefault(key, def, vars, context);
    }

    /* ============================= RESOLVE / PARSE ============================= */

    /** Resolve lang → apply vars → PAPI → raw string (MiniMessage or legacy). */
    public static String raw(String key, Map<?, ?> vars, Player papiFor) {
        String s = get(key, "");
        if (s == null) return "";
        s = s.replace("\\n", "\n");
        if (s.contains("%prefix%")) s = s.replace("%prefix%", prefixRaw);
        if (vars != null && !vars.isEmpty()) {
            for (var e : vars.entrySet()) {
                String k = String.valueOf(e.getKey());
                String v = String.valueOf(e.getValue());
                s = s.replace("%" + k + "%", v);
            }
        }
        if (papiFor != null) s = applyPapiMaybe(papiFor, s);
        return s;
    }

    /** Build chat Component (MiniMessage-first + legacy-& bridge). */
    public static Component msgComp(String key, Map<?, ?> vars, Player papiFor) {
        return toComponent(raw(key, vars, papiFor));
    }

    public static Component msgComp(String key, Player papiFor) {
        return msgComp(key, null, papiFor);
    }

    /** Legacy string for GUI (inventory titles/lore). */
    public static String msgLegacy(String key, Map<?, ?> vars, Player papiFor) {
        return LEGACY_SEC.serialize(msgComp(key, vars, papiFor));
    }

    public static String msgLegacy(String key, Player papiFor) {
        return msgLegacy(key, null, papiFor);
    }

    /** GUI helper: MM or & → legacy (for titles/buttons). */
    public static String ui(String key, String def, Map<?, ?> vars, Player p) {
        String resolved = rawOrDefault(key, def, vars, p);
        return LEGACY_SEC.serialize(toComponent(resolved));
    }

    public static String ui(String key, String def, Player p) {
        return ui(key, def, null, p);
    }

    /** GUI lore helper (list under baseKey.0, .1, ...), with default if empty. */
    public static List<String> uiList(String baseKey, List<String> def, Player p) {
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < 400; i++) { // safe cap
            String v = get(baseKey + "." + i, null);
            if (v == null) break;
            raw.add(v);
        }
        if (raw.isEmpty() && def != null) raw.addAll(def);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) out.add(LEGACY_SEC.serialize(toComponent(applyPapiMaybe(p, line))));
        return out;
    }

    /** Direct parse of arbitrary raw text (MiniMessage or legacy). */
    public static Component parse(String raw, Player p) {
        return toComponent(applyPapiMaybe(p, raw));
    }

    public static String parseToLegacy(String raw, Player p) {
        return LEGACY_SEC.serialize(parse(raw, p));
    }

    /* ============================= SENDING ============================= */

    /** Send lang message with Map support. Player → Component; Console → plain text. */
    public static void send(CommandSender to, String key, String def, Map<?, ?> vars) {
        String resolved = rawOrDefault(key, def, vars, (to instanceof Player p) ? p : null);
        sendRaw(to, resolved);
    }

    public static void send(CommandSender to, String key, String def) {
        send(to, key, def, (Map<?, ?>) null);
    }

    public static void send(CommandSender to, String key) {
        send(to, key, "", (Map<?, ?>) null);
    }

    /** Send raw MiniMessage/legacy. Player → Component; Console → plain text. */
    public static void sendRaw(CommandSender to, String raw) {
        if (to instanceof Player p) {
            p.sendMessage(toComponent(applyPapiMaybe(p, raw)));
        } else {
            to.sendMessage(PLAIN.serialize(toComponent(raw)));
        }
    }

    /* ============================= TITLES ============================= */

    /** Title using lang keys; times in ticks. */
    public static void sendTitle(Player p,
                                 String titleKey, String titleDef,
                                 String subKey, String subDef,
                                 Map<?, ?> vars,
                                 int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Component title = toComponent(rawOrDefault(titleKey, titleDef, vars, p));
        Component subtitle = toComponent(rawOrDefault(subKey, subDef, vars, p));
        Title.Times times = Title.Times.times(Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L));
        p.showTitle(Title.title(title, subtitle, times));
    }

    public static void sendTitle(Player p, String titleKey, String subKey, Map<?, ?> vars) {
        sendTitle(p, titleKey, "", subKey, "", vars, 10, 60, 10);
    }

    public static void clearTitle(Player p) {
        p.clearTitle();
    }

    /* ============================= HUMANIZED TIME ============================= */

    /** Humanize duration (ms) using simple units; override strings via lang if desired. */
    public static String timeHuman(long millis) {
        if (millis < 0) millis = 0;
        long seconds = millis / 1000;
        long d = seconds / 86400; seconds %= 86400;
        long h = seconds / 3600;  seconds %= 3600;
        long m = seconds / 60;    long s = seconds % 60;

        List<String> parts = new ArrayList<>(4);
        if (d > 0) parts.add(d + " " + (d == 1 ? get("time.day", "day") : get("time.days", "days")));
        if (h > 0) parts.add(h + " " + (h == 1 ? get("time.hour", "hour") : get("time.hours", "hours")));
        if (m > 0) parts.add(m + " " + (m == 1 ? get("time.minute", "minute") : get("time.minutes", "minutes")));
        if (s > 0 || parts.isEmpty()) parts.add(s + " " + (s == 1 ? get("time.second", "second") : get("time.seconds", "seconds")));

        String sep = get("time.separator", ", ");
        String last = get("time.last-separator", " and ");
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + last + parts.get(1);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i == parts.size() - 1) out.append(parts.get(i));
            else if (i == parts.size() - 2) out.append(parts.get(i)).append(last);
            else out.append(parts.get(i)).append(sep);
        }
        return out.toString();
    }

    /* ============================= MISC ============================= */

    /** Color utility: MiniMessage/legacy → legacy-§ with hex preserved. */
    public static String color(String s) { return LEGACY_HEX.serialize(toComponent(s)); }

    /* ============================= CORE CONVERSION ============================= */

    /** MM-first parse with legacy-& bridge; no-italic by default (Minecraft UI norm). */
    public static Component toComponent(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        String normalized = input.replace('§', '&');
        if (AMP_OR_SECTION.matcher(normalized).find()) {
            String mm = legacyToMiniMessage(normalized);
            try {
                return MM.deserialize(mm, baseResolvers()).decoration(TextDecoration.ITALIC, false);
            } catch (Throwable t) {
                return LEGACY_AMP.deserialize(normalized).decoration(TextDecoration.ITALIC, false);
            }
        }

        try {
            return MM.deserialize(normalized, baseResolvers()).decoration(TextDecoration.ITALIC, false);
        } catch (Throwable t) {
            return Component.text(normalized).decoration(TextDecoration.ITALIC, false);
        }
    }

    /** Legacy (&, §, &#, &x&… hex) → MiniMessage tags. */
    private static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return "";

        // &x&F&F&0&0&0&0 → <#FF0000>
        Matcher mx = LEGACY_HEX_AMP_X.matcher(input);
        StringBuffer sbx = new StringBuffer();
        while (mx.find()) {
            String hex = mx.group(1).replace("&", "");
            mx.appendReplacement(sbx, "<#" + hex + ">");
        }
        mx.appendTail(sbx);
        input = sbx.toString();

        // §x§F§F§0§0§0§0 → <#FF0000>
        String section = input.replace('&', '§');
        Matcher ms = LEGACY_HEX_SECTION_X.matcher(section);
        StringBuffer sbs = new StringBuffer();
        while (ms.find()) {
            String hex = ms.group(1).replace("§", "");
            ms.appendReplacement(sbs, "<#" + hex + ">");
        }
        ms.appendTail(sbs);
        input = sbs.toString().replace('§', '&');

        // &#RRGGBB → <#RRGGBB>
        Matcher mh = LEGACY_HEX_HASH.matcher(input);
        StringBuffer sbh = new StringBuffer();
        while (mh.find()) {
            mh.appendReplacement(sbh, "<#" + mh.group(1) + ">");
        }
        mh.appendTail(sbh);
        input = sbh.toString();

        // &a, &l, &r, …
        Matcher m = LEGACY_CODES.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            char code = Character.toLowerCase(m.group(1).charAt(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(codeToTag(code)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String codeToTag(char c) {
        return switch (c) {
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
            default  -> "";
        };
    }

    private static TagResolver baseResolvers() {
        return TagResolver.resolver(
                Placeholder.unparsed("server_name", safeServerName())
        );
    }

    private static String safeServerName() {
        try { return Bukkit.getServer().getName(); }
        catch (Throwable t) { return "Server"; }
    }

    private static String rawOrDefault(String key, String def, Map<?, ?> vars, Player p) {
        String v = get(key, null);
        if (v == null) v = def == null ? "" : def;
        if (v.contains("%prefix%")) v = v.replace("%prefix%", prefixRaw);
        if (vars != null && !vars.isEmpty()) {
            for (var e : vars.entrySet()) {
                String k = String.valueOf(e.getKey());
                String val = String.valueOf(e.getValue());
                v = v.replace("%" + k + "%", val);
            }
        }
        return applyPapiMaybe(p, v.replace("\\n", "\n"));
    }

    private static boolean isPapiPresent() {
        try { return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"); }
        catch (Throwable t) { return false; }
    }

    private static String applyPapiMaybe(Player p, String raw) {
        if (raw == null) return "";
        if (p == null || !isPapiPresent()) return raw;
        try { return PlaceholderAPI.setPlaceholders(p, raw); }
        catch (Throwable ignored) { return raw; }
    }
}