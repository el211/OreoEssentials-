package fr.elias.oreoEssentials.holograms;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class HoloText {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();

    private HoloText() {}

    /** Global render (player = null) -> only global-ish PAPI placeholders can resolve. */
    public static Component render(List<String> lines) {
        return render(lines, null);
    }

    /**
     * Render with an optional player for PlaceholderAPI.
     * WARNING: If you use a shared TextDisplay for everyone, player placeholders will be wrong for others.
     */
    public static Component render(List<String> lines, Player player) {
        if (lines == null || lines.isEmpty()) return Component.empty();

        Component out = Component.empty();
        boolean first = true;

        for (String raw : lines) {
            if (!first) out = out.append(Component.newline());
            first = false;

            raw = (raw == null) ? "" : raw;

            // 1) Apply PlaceholderAPI before formatting parse
            raw = applyPapi(raw, player);

            // 2) Parse MiniMessage OR legacy
            Component line = looksLikeMiniMessage(raw)
                    ? safeMiniMessage(raw)
                    : LEGACY_AMP.deserialize(raw.replace('§', '&'));

            out = out.append(line);
        }

        return out;
    }

    /** PAPI apply: if player is null, only placeholders that do not require a player will resolve. */
    public static String applyPapi(String text, Player player) {
        if (text == null) return "";
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;

        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    /** Convenience overload */
    public static String applyPapi(String text) {
        return applyPapi(text, null);
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
            // fallback so invalid tags don't break holograms
            return LEGACY_AMP.deserialize(raw.replace('§', '&'));
        }
    }

    /** Fallback: convert Component to legacy § codes (gradients won't survive). */
    public static String toLegacySection(Component c) {
        return LEGACY_SEC.serialize(c);
    }
}
