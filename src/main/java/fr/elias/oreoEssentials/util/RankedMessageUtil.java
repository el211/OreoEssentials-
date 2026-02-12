package fr.elias.oreoEssentials.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class RankedMessageUtil {

    private RankedMessageUtil() {
        // util
    }

    /**
     * Resolves a ranked message list based on permissions.
     *
     * Config path:
     * <baseSection>.formats.<key>:
     *   - permission: "oreo.rank.vip"
     *     text: "..."
     *
     * Example:
     * Join_messages.formats.rejoin_message
     */
    public static String resolveRankedText(FileConfiguration c, String baseSection, String key, Player p, String fallback) {
        if (c == null || p == null) return fallback;

        String listPath = baseSection + ".formats." + key;

        List<Map<?, ?>> list = c.getMapList(listPath);
        if (list == null || list.isEmpty()) return fallback;

        for (Map<?, ?> map : list) {
            if (map == null) continue;

            String perm = String.valueOf(map.getOrDefault("permission", "")).trim();
            String text = String.valueOf(map.getOrDefault("text", "")).trim();

            if (!perm.isEmpty() && !text.isEmpty() && p.hasPermission(perm)) {
                return text;
            }
        }

        return fallback;
    }
}
