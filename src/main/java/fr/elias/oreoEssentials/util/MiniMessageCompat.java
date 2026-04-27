package fr.elias.oreoEssentials.util;

import java.util.Map;
import java.util.regex.Pattern;

public final class MiniMessageCompat {
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("dar_puruple", "dark_purple"),
            Map.entry("dark_puruple", "dark_purple"),
            Map.entry("whilte", "white"),
            Map.entry("mwhilte", "white")
    );

    private MiniMessageCompat() {}

    public static String normalizeTagAliases(String input) {
        if (input == null || input.indexOf('<') == -1 || input.indexOf('>') == -1) return input;

        String out = input;
        for (Map.Entry<String, String> e : TAG_ALIASES.entrySet()) {
            String alias = e.getKey();
            String canonical = e.getValue();
            out = out.replaceAll("(?i)<\\s*" + Pattern.quote(alias) + "\\s*>", "<" + canonical + ">");
            out = out.replaceAll("(?i)<\\s*/\\s*" + Pattern.quote(alias) + "\\s*>", "</" + canonical + ">");
        }
        return out;
    }
}
