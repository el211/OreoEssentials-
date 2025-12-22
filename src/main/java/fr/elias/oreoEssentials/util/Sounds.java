// File: src/main/java/fr/elias/oreoEssentials/util/Sounds.java
package fr.elias.oreoEssentials.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class Sounds {

    private Sounds() {}

    /**
     * Plays a sound using modern string keys only.
     * Why: avoid deprecated Sound.valueOf(...) and future removal.
     */
    public static void play(@Nullable Player player,
                            @Nullable String raw,
                            float volume,
                            float pitch) {
        if (player == null || raw == null || raw.isEmpty()) return;

        final String key = normalizeToKey(raw);
        if (key == null) return; // invalid; skip

        player.playSound(player.getLocation(), key, volume, pitch);
    }

    /**
     * Normalize various inputs into a namespaced key:
     * - "minecraft:block.note_block.bass" → unchanged
     * - "block.note_block.bass" → "minecraft:block.note_block.bass"
     * - "BLOCK_NOTE_BLOCK_BASS" → "minecraft:block.note_block.bass"
     * - "ENTITY_EXPERIENCE_ORB_PICKUP" → "minecraft:entity.experience_orb.pickup"
     */
    @Nullable
    private static String normalizeToKey(@NotNull String raw) {
        final String s = raw.trim();
        // Already a modern key (contains '.'), ensure namespace
        if (s.indexOf('.') >= 0) {
            return s.indexOf(':') >= 0 ? s : "minecraft:" + s;
        }
        // Namespaced without dots (rare/mistyped) → reject
        if (s.indexOf(':') >= 0 && s.indexOf('.') < 0) {
            return null;
        }
        // Legacy enum-style → convert: UPPER_UNDERSCORE → lower.dot + "minecraft:"
        // e.g. BLOCK_NOTE_BLOCK_BASS → block.note_block.bass
        final String lowered = s.toLowerCase(Locale.ROOT);
        // if user already passed lowercase_underscore, still convert
        final String dotted = lowered.replace('_', '.');
        return "minecraft:" + dotted;
    }
}
