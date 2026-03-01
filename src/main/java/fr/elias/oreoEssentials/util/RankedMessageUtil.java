package fr.elias.oreoEssentials.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class RankedMessageUtil {

    private RankedMessageUtil() {
    }


    public static String resolveRankedText(FileConfiguration c,
                                           String baseSection,
                                           String key,
                                           Player p,
                                           String fallback) {

        if (fallback == null) fallback = "";
        if (c == null || p == null || baseSection == null || key == null) return fallback;

        String listPath = baseSection + ".formats." + key;

        List<Map<?, ?>> list = c.getMapList(listPath);
        if (list == null || list.isEmpty()) return fallback;

        String catchAll = null;

        for (Map<?, ?> map : list) {
            if (map == null) continue;

            String perm = getString(map, "permission");
            String text = getString(map, "text");

            if (text.isEmpty()) continue;

            // "*" or empty permission = catch-all fallback (used if nothing else matches)
            if (perm.isEmpty() || perm.equals("*")) {
                if (catchAll == null) catchAll = text;
                continue;
            }

            if (p.hasPermission(perm)) {
                return text;
            }
        }

        return catchAll != null ? catchAll : fallback;
    }

    private static String getString(Map<?, ?> map, String key) {
        if (map == null || key == null) return "";
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
