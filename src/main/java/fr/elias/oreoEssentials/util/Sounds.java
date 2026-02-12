package fr.elias.oreoEssentials.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class Sounds {

    private Sounds() {}

    public static void play(@Nullable Player player,
                            @Nullable String raw,
                            float volume,
                            float pitch) {
        if (player == null || raw == null || raw.isEmpty()) return;

        final String key = normalizeToKey(raw);
        if (key == null) return;

        player.playSound(player.getLocation(), key, volume, pitch);
    }


    @Nullable
    private static String normalizeToKey(@NotNull String raw) {
        final String s = raw.trim();
        if (s.indexOf('.') >= 0) {
            return s.indexOf(':') >= 0 ? s : "minecraft:" + s;
        }
        if (s.indexOf(':') >= 0 && s.indexOf('.') < 0) {
            return null;
        }

        final String lowered = s.toLowerCase(Locale.ROOT);
        final String dotted = lowered.replace('_', '.');
        return "minecraft:" + dotted;
    }
}
