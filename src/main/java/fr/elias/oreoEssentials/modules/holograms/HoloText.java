package fr.elias.oreoEssentials.modules.holograms;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HoloText {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();

    private static final Map<Character, String> AMP_TO_MINI;
    static {
        Map<Character, String> m = new HashMap<>();
        m.put('l', "<bold>"); m.put('o', "<italic>"); m.put('n', "<underlined>");
        m.put('m', "<strikethrough>"); m.put('k', "<obfuscated>"); m.put('r', "<reset>");
        m.put('0', "<black>"); m.put('1', "<dark_blue>"); m.put('2', "<dark_green>");
        m.put('3', "<dark_aqua>"); m.put('4', "<dark_red>"); m.put('5', "<dark_purple>");
        m.put('6', "<gold>"); m.put('7', "<gray>"); m.put('8', "<dark_gray>");
        m.put('9', "<blue>"); m.put('a', "<green>"); m.put('b', "<aqua>");
        m.put('c', "<red>"); m.put('d', "<light_purple>"); m.put('e', "<yellow>");
        m.put('f', "<white>");
        AMP_TO_MINI = Collections.unmodifiableMap(m);
    }

    private HoloText() {}


    public static Component render(List<String> lines) {
        return render(lines, null);
    }


    public static Component render(List<String> lines, Player player) {
        if (lines == null || lines.isEmpty()) return Component.empty();

        Component out = Component.empty();
        boolean first = true;

        for (String raw : lines) {
            if (!first) out = out.append(Component.newline());
            first = false;

            raw = (raw == null) ? "" : raw;

            raw = applyPapi(raw, player);

            // Normalise §-codes (from PAPI output, e.g. §6Blood) to & so they
            // survive both the legacy path and the MiniMessage path.
            raw = raw.replace('§', '&');

            Component line;
            if (looksLikeMiniMessage(raw)) {
                // Convert & codes → MiniMessage tags so they aren't swallowed by MM.deserialize
                line = safeMiniMessage(convertAmpToMiniMessage(raw));
            } else {
                line = LEGACY_AMP.deserialize(raw);
            }

            out = out.append(line);
        }

        return out;
    }

    public static String applyPapi(String text, Player player) {
        if (text == null) return "";
        // Without a real player, PAPI returns empty string for many expansions (e.g. LuckPerms prefix).
        // Skip processing entirely so the raw placeholder text remains visible as a fallback.
        if (player == null) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;

        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    public static String applyPapi(String text) {
        return applyPapi(text, null);
    }

    private static String convertAmpToMiniMessage(String s) {
        if (s == null || s.indexOf('&') == -1) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String mini = AMP_TO_MINI.get(code);
                if (mini != null) {
                    sb.append(mini);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean looksLikeMiniMessage(String s) {
        int lt = s.indexOf('<');
        if (lt < 0) return false;
        int gt = s.indexOf('>', lt + 1);
        return gt > lt;
    }

    private static Component safeMiniMessage(String raw) {
        try {
            return MM.deserialize(raw);
        } catch (Throwable t) {
            return LEGACY_AMP.deserialize(raw.replace('§', '&'));
        }
    }

    public static String toLegacySection(Component c) {
        return LEGACY_SEC.serialize(c);
    }
}
