package fr.elias.oreoEssentials.modules.chat;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.PlaceholderAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatManager {

    private final CustomConfig customYmlManager;

    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern GRADIENT_PATTERN =
            Pattern.compile("<gradient:([^>]+)>(.*?)</gradient>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public FormatManager(CustomConfig customYmlManager) {
        this.customYmlManager = customYmlManager;
    }

    public String formatMessage(Player p, String message) {
        final FileConfiguration cfg = customYmlManager.getCustomConfig();

        final boolean useMiniMessage = cfg.getBoolean("chat.use-minimessage", false);
        final boolean miniMessageTranslateLegacyAmp =
                cfg.getBoolean("chat.minimessage-translate-legacy-amp", true);

        String group;
        try {
            group = LuckPermsProvider.get().getUserManager()
                    .getUser(p.getUniqueId())
                    .getPrimaryGroup();
        } catch (Throwable t) {
            group = "default";
        }

        String format = cfg.getString("chat." + group);
        if (format == null) {
            format = cfg.getString("chat.default", "&7%player_displayname% » &f%chat_message%");
        }

        if (message == null) message = "";
        format = format.replace("%chat_message%", message);

        boolean stripNameColors = cfg.getBoolean("chat.strip-name-colors", true);
        String displayName = p.getDisplayName();
        String plainName = p.getName();
        if (stripNameColors) {
            displayName = stripAll(displayName);
            plainName = stripAll(plainName);
        }

        if (format.contains("%player_displayname%")) {
            format = format.replace("%player_displayname%", displayName);
        }
        if (format.contains("%player_name%")) {
            String nameToUse = format.contains("%player_displayname%") ? plainName : displayName;
            format = format.replace("%player_name%", nameToUse);
        }

        // Apply PlaceholderAPI placeholders before formatting
        try {
            // Store the format before PAPI to detect what changed
            String beforePapi = format;
            format = PlaceholderAPI.setPlaceholders(p, format);

            // Clean up faction placeholder for players without factions
            // This must happen BEFORE MiniMessage processing
            format = cleanFactionPlaceholder(format);

            // Extra safety: escape any unmatched tags that might break MiniMessage
            format = escapeBrokenTags(format);

        } catch (Exception ignored) {
            // PlaceholderAPI not loaded or error occurred
        }

        if (useMiniMessage) {
            if (miniMessageTranslateLegacyAmp) {
                format = legacyAmpToMiniMessage(format);
            }
            return format;
        }

        format = applyGradients(format);
        format = colorize(format);

        return format;
    }

    private String applyGradients(String input) {
        Matcher m = GRADIENT_PATTERN.matcher(input);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            String colorsSpec = m.group(1);
            String text = m.group(2);

            List<int[]> stops = parseColorStops(colorsSpec);
            if (stops.size() < 2 || text.isEmpty()) {
                m.appendReplacement(out, Matcher.quoteReplacement(text));
                continue;
            }

            String colored = applyGradientToText(text, stops);
            m.appendReplacement(out, Matcher.quoteReplacement(colored));
        }
        m.appendTail(out);
        return out.toString();
    }

    private List<int[]> parseColorStops(String spec) {
        String[] parts = spec.split("[:;,\\s]+");
        List<int[]> list = new ArrayList<>();
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.startsWith("#")) p = p.substring(1);
            if (p.length() == 3) {
                p = "" + p.charAt(0) + p.charAt(0)
                        + p.charAt(1) + p.charAt(1)
                        + p.charAt(2) + p.charAt(2);
            }
            if (p.length() != 6) continue;
            try {
                int r = Integer.parseInt(p.substring(0, 2), 16);
                int g = Integer.parseInt(p.substring(2, 4), 16);
                int b = Integer.parseInt(p.substring(4, 6), 16);
                list.add(new int[]{r, g, b});
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private String applyGradientToText(String raw, List<int[]> stops) {
        StringBuilder out = new StringBuilder(raw.length() * 10);

        List<Integer> visibleIndexes = new ArrayList<>();
        char[] arr = raw.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (isColorCodeStart(c) && i + 1 < arr.length && isLegacyCodeChar(arr[i + 1])) {
                i++;
                continue;
            }
            visibleIndexes.add(i);
        }

        int n = visibleIndexes.size();
        if (n == 0) return raw;

        for (int v = 0, i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (isColorCodeStart(c) && i + 1 < arr.length && isLegacyCodeChar(arr[i + 1])) {
                out.append('§').append(Character.toLowerCase(arr[i + 1]));
                i++;
                continue;
            }

            if (v < n && i == visibleIndexes.get(v)) {
                double t = (n == 1) ? 0.0 : (double) v / (double) (n - 1);
                int[] rgb = sampleMultiStop(stops, t);
                out.append(toMcHex(rgb[0], rgb[1], rgb[2])).append(c);
                v++;
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }

    private int[] sampleMultiStop(List<int[]> stops, double t) {
        if (t <= 0) return stops.get(0);
        if (t >= 1) return stops.get(stops.size() - 1);

        double scaled = t * (stops.size() - 1);
        int idx = (int) Math.floor(scaled);
        double localT = scaled - idx;

        int[] a = stops.get(idx);
        int[] b = stops.get(idx + 1);

        int r = (int) Math.round(a[0] + (b[0] - a[0]) * localT);
        int g = (int) Math.round(a[1] + (b[1] - a[1]) * localT);
        int bC = (int) Math.round(a[2] + (b[2] - a[2]) * localT);

        return new int[]{clamp8(r), clamp8(g), clamp8(bC)};
    }

    private int clamp8(int v) {
        return Math.min(255, Math.max(0, v));
    }

    private boolean isColorCodeStart(char c) {
        return c == '§' || c == '&';
    }

    private boolean isLegacyCodeChar(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || c == 'r'
                || c == 'x';
    }

    private String toMcHex(int r, int g, int b) {
        String hex = String.format(Locale.ROOT, "%02X%02X%02X", r, g, b);
        return "§x§" + hex.charAt(0)
                + "§" + hex.charAt(1)
                + "§" + hex.charAt(2)
                + "§" + hex.charAt(3)
                + "§" + hex.charAt(4)
                + "§" + hex.charAt(5);
    }

    private String colorize(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder mc = new StringBuilder("§x");
            for (char c : hex.toCharArray()) mc.append('§').append(c);
            matcher.appendReplacement(buffer, mc.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }

    private String stripAll(String s) {
        if (s == null) return "";
        String t = s.replace('&', '§');
        return ChatColor.stripColor(t);
    }

    private String legacyAmpToMiniMessage(String s) {
        if (s == null || s.isEmpty()) return s;

        s = s.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&r", "<reset>");

        s = s.replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>");

        return s;
    }

    private String cleanFactionPlaceholder(String format) {
        if (format == null) return format;

        // First, extract what the faction placeholder returned by checking between known markers
        // This helps us target just the faction value, not the entire format

        // Common values returned by FactionsUUID when player has no faction
        String[] noFactionValues = {
                "Wilderness", "wilderness",
                "[no-faction]", "no-faction",
                "None", "none",
                "N/A", "n/a",
                "NoFaction", "nofaction",
                "no faction", "No Faction",
                "-", "null", "Null", "NULL",
                "0"  // FactionsUUID returns "0" for players without a faction!
        };

        for (String badValue : noFactionValues) {
            format = format.replace(" • " + badValue, "");  // Remove with separator
            format = format.replace(badValue + " • ", "");  // Remove with separator after
            format = format.replace(badValue, "");          // Remove standalone
        }

        // Remove any stray MiniMessage/HTML-like tags that might break parsing
        // This catches things like "</gradient>" or "<reset>" that might come from placeholders
        format = format.replaceAll("</gradient>(?![^<]*>)", "");
        format = format.replaceAll("<gradient:[^>]*>(?!.*</gradient>)", "");

        // Clean up orphaned separators and extra spaces
        format = format.replaceAll("\\s+•\\s+•", " • ");   // Remove double separators
        format = format.replaceAll("\\s+•\\s+\\]", "]");   // Remove " • ]"
        format = format.replaceAll("\\[\\s+•\\s+", "[");   // Remove "[ • "
        format = format.replaceAll("\\s{2,}", " ");        // Remove extra spaces
        format = format.replaceAll("\\[\\s*\\]", "");      // Remove empty brackets

        return format;
    }

    private String escapeBrokenTags(String format) {
        if (format == null) return format;

        // Count gradient tags to detect unmatched pairs
        int openGradient = countOccurrences(format, "<gradient:");
        int closeGradient = countOccurrences(format, "</gradient>");

        // If unmatched, something went wrong - escape the broken ones
        if (openGradient != closeGradient) {
            // Log for debugging
            System.out.println("[FormatManager] WARNING: Unmatched gradient tags detected! Open: " + openGradient + ", Close: " + closeGradient);

            // Remove orphaned closing tags that don't have an opening tag
            if (closeGradient > openGradient) {
                format = format.replaceAll("</gradient>(?![^<]*<gradient:)", "");
            }

            // Remove orphaned opening tags that don't have a closing tag
            if (openGradient > closeGradient) {
                format = format.replaceAll("<gradient:[^>]*>(?![^<]*</gradient>)", "");
            }
        }

        return format;
    }

    private int countOccurrences(String str, String substring) {
        if (str == null || substring == null || substring.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}