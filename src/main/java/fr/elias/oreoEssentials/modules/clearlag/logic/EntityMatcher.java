package fr.elias.oreoEssentials.modules.clearlag.logic;

import org.bukkit.entity.*;

import java.util.List;
import java.util.Locale;

public final class EntityMatcher {

    private EntityMatcher() {
    }

    public static boolean inAreaFilter(Entity e, Iterable<String> areaFilter) {
        String key = e.getType().name();
        for (String s : areaFilter) if (key.equalsIgnoreCase(s)) return true;
        return false;
    }

    public static boolean matchesTokens(Entity e, List<String> rules) {
        for (String r : rules) {
            if (matchesTokenRule(e, r)) return true;
        }
        return false;
    }

    private static boolean matchesTokenRule(Entity e, String rule) {
        String[] parts = rule.trim().split("\\s+");
        if (parts.length == 0) return false;
        String type = parts[0].toUpperCase(Locale.ROOT);

        if (!"ITEM".equals(type) && !e.getType().name().equalsIgnoreCase(type)) {
            return false;
        }

        for (int i = 1; i < parts.length; i++) {
            String token = parts[i];
            boolean neg = token.startsWith("!");
            String t = neg ? token.substring(1) : token;

            boolean ok = switch (t) {
                case "hasName" -> hasName(e);
                case "onGround" -> e.isOnGround();
                case "isMounted" -> isMounted(e);
                default -> {
                    if (t.startsWith("name=")) {
                        String expected = unquote(t.substring("name=".length()));
                        yield expected.equalsIgnoreCase(e.getCustomName());
                    }
                    yield true;
                }
            };

            if (neg ? ok : !ok) return false;
        }
        return true;
    }

    public static boolean isFilteredMob(LivingEntity le, List<String> filterLines) {
        String type = le.getType().name();
        for (String ln : filterLines) {
            String[] parts = ln.trim().split("\\s+");
            if (parts.length == 0) continue;
            if (!type.equalsIgnoreCase(parts[0])) continue;
            boolean matches = matchesTokenRule(le, ln);
            if (matches) return true;
        }
        return false;
    }

    private static boolean hasName(Entity e) {
        String n = e.getCustomName();
        return n != null && !n.isBlank();
    }

    private static boolean isMounted(Entity e) {
        return e.getVehicle() != null;
    }

    private static String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}