package fr.elias.oreoEssentials.modules.commandcontrol;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public final class CommandControlService {

    public enum Mode { ALLOW_ONLY, DENY_LIST }
    public enum DefaultPolicy { ALLOW_ALL, DENY_ALL }

    public static final class Rule {
        public final String root;
        public final Mode mode;
        public final boolean allowEmpty;
        public final Set<String> allow;
        public final Set<String> deny;
        public final String permissionPrefix;

        public Rule(String root, Mode mode, boolean allowEmpty, String permissionPrefix, Set<String> allow, Set<String> deny) {
            this.root = root;
            this.mode = mode;
            this.allowEmpty = allowEmpty;
            this.permissionPrefix = permissionPrefix == null ? "" : permissionPrefix.trim();
            this.allow = allow == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(allow));
            this.deny = deny == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(deny));
        }
    }

    private boolean enabled;
    private boolean hideFromTab;
    private String bypassPerm;
    private String denyMessage;

    private DefaultPolicy defaultPolicy = DefaultPolicy.ALLOW_ALL;

    private final Set<String> allowRoots = new HashSet<>();
    private final Map<String, String> permissionMap = new HashMap<>();
    private final Set<String> blockedExact = new HashSet<>();
    private final Map<String, Rule> rules = new HashMap<>();

    public void load(FileConfiguration cfg) {
        if (cfg == null) {
            enabled = false;
            hideFromTab = true;
            bypassPerm = "oreo.commandcontrol.bypass";
            denyMessage = "<red>You can't use that command.</red>";
            defaultPolicy = DefaultPolicy.ALLOW_ALL;
            allowRoots.clear();
            permissionMap.clear();
            blockedExact.clear();
            rules.clear();
            return;
        }

        enabled = cfg.getBoolean("command-control.enabled", false);
        hideFromTab = cfg.getBoolean("command-control.hide-from-tab", true);
        bypassPerm = cfg.getString("command-control.bypass-permission", "oreo.commandcontrol.bypass");

        denyMessage = cfg.getString("command-control.deny-message", "<red>You can't use that command.</red>");

        String pol = cfg.getString("command-control.default-policy", "ALLOW_ALL");
        defaultPolicy = "DENY_ALL".equalsIgnoreCase(pol) ? DefaultPolicy.DENY_ALL : DefaultPolicy.ALLOW_ALL;

        allowRoots.clear();
        List<String> allowList = cfg.getStringList("command-control.allow-roots");
        if (allowList != null) {
            for (String s : allowList) {
                String r = normalizeRoot(s);
                if (!r.isEmpty()) allowRoots.add(r);
            }
        }

        permissionMap.clear();
        var pmSec = cfg.getConfigurationSection("command-control.permission-map");
        if (pmSec != null) {
            for (String key : pmSec.getKeys(false)) {
                if (key == null) continue;
                String root = normalizeRoot(key);
                if (root.isEmpty()) continue;

                String perm = pmSec.getString(key, "");
                if (perm != null) perm = perm.trim();

                if (perm != null && !perm.isEmpty()) {
                    permissionMap.put(root, perm);
                }
            }
        }

        blockedExact.clear();
        List<String> blockedList = cfg.getStringList("command-control.blocked-commands");
        if (blockedList != null) {
            for (String s : blockedList) {
                String r = normalizeRoot(s);
                if (!r.isEmpty()) blockedExact.add(r);
            }
        }

        rules.clear();
        List<Map<?, ?>> list = cfg.getMapList("command-control.rules");
        if (list != null) {
            for (Map<?, ?> raw : list) {
                if (raw == null) continue;

                Object rObj = raw.get("root");
                if (rObj == null) continue;

                String root = normalizeRoot(String.valueOf(rObj));
                if (root.isEmpty()) continue;

                Object modeObj = raw.get("mode");
                String modeStr = modeObj == null ? "ALLOW_ONLY" : String.valueOf(modeObj);
                Mode mode = "DENY_LIST".equalsIgnoreCase(modeStr) ? Mode.DENY_LIST : Mode.ALLOW_ONLY;

                Object allowEmptyObj = raw.get("allow-empty");
                boolean allowEmpty = allowEmptyObj != null && Boolean.parseBoolean(String.valueOf(allowEmptyObj));

                Object permPrefixObj = raw.get("permission-prefix");
                String permissionPrefix = permPrefixObj == null ? "" : String.valueOf(permPrefixObj).trim();

                Set<String> allow = new HashSet<>();
                Object allowObj = raw.get("allow");
                if (allowObj instanceof List<?> aList) {
                    for (Object o : aList) {
                        if (o == null) continue;
                        String v = normalizeSub(String.valueOf(o));
                        if (!v.isEmpty()) allow.add(v);
                    }
                }

                Set<String> deny = new HashSet<>();
                Object denyObj = raw.get("deny");
                if (denyObj instanceof List<?> dList) {
                    for (Object o : dList) {
                        if (o == null) continue;
                        String v = normalizeSub(String.valueOf(o));
                        if (!v.isEmpty()) deny.add(v);
                    }
                }

                rules.put(root, new Rule(root, mode, allowEmpty, permissionPrefix, allow, deny));
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public boolean isHideFromTab() { return hideFromTab; }
    public String getDenyMessage() { return denyMessage; }

    public boolean canBypass(Player p) {
        if (p == null) return true;
        if (bypassPerm == null || bypassPerm.isBlank()) return false;
        return p.hasPermission(bypassPerm);
    }

    public String normalizeRootPublic(String root) {
        return normalizeRoot(root);
    }

    public String normalizeSubPublic(String sub) {
        return normalizeSub(sub);
    }

    public boolean isBlocked(Player p, String root, String sub, String rawLine) {
        if (!enabled) return false;
        if (p != null && canBypass(p)) return false;

        String r = normalizeRoot(root);
        if (r.isEmpty()) return false;

        if (blockedExact.contains(r)) return true;

        if (defaultPolicy == DefaultPolicy.DENY_ALL && !allowRoots.contains(r)) {
            return true;
        }

        String rootPerm = permissionMap.get(r);
        if (p != null && rootPerm != null && !rootPerm.isBlank()) {
            if (!p.hasPermission(rootPerm)) return true;
        }

        Rule rule = rules.get(r);
        if (rule == null) {
            return false;
        }

        String s = normalizeSub(sub);

        if (s.isEmpty()) {
            return rule.mode == Mode.ALLOW_ONLY && !rule.allowEmpty;
        }

        if (!rule.permissionPrefix.isBlank() && p != null) {
            if (!p.hasPermission(rule.permissionPrefix + s)) return true;
        }

        if (rule.mode == Mode.ALLOW_ONLY) return !rule.allow.contains(s);
        return rule.deny.contains(s);
    }

    public boolean canUseSub(Player p, String root, String sub) {
        if (!enabled) return true;
        if (p == null) return false;
        if (canBypass(p)) return true;

        String r = normalizeRoot(root);
        if (r.isEmpty()) return true;

        if (blockedExact.contains(r)) return false;

        if (defaultPolicy == DefaultPolicy.DENY_ALL && !allowRoots.contains(r)) {
            return false;
        }

        String rootPerm = permissionMap.get(r);
        if (rootPerm != null && !rootPerm.isBlank() && !p.hasPermission(rootPerm)) {
            return false;
        }

        Rule rule = rules.get(r);
        if (rule == null) {
            return true;
        }

        String s = normalizeSub(sub);

        if (s.isEmpty()) {
            return rule.mode != Mode.ALLOW_ONLY || rule.allowEmpty;
        }

        if (!rule.permissionPrefix.isBlank()) {
            if (!p.hasPermission(rule.permissionPrefix + s)) return false;
        }

        if (rule.mode == Mode.ALLOW_ONLY) return rule.allow.contains(s);
        return !rule.deny.contains(s);
    }

    public boolean shouldHideRootFromSend(Player p, String root) {
        if (!enabled) return false;
        if (p != null && canBypass(p)) return false;

        String r = normalizeRoot(root);
        if (r.isEmpty()) return false;

        if (blockedExact.contains(r)) return true;

        if (defaultPolicy == DefaultPolicy.DENY_ALL) {
            if (!allowRoots.contains(r)) return true;

            String rootPerm = permissionMap.get(r);
            if (p != null && rootPerm != null && !rootPerm.isBlank() && !p.hasPermission(rootPerm)) {
                return true;
            }
            return false;
        }

        if (rules.containsKey(r)) {
            String rootPerm = permissionMap.get(r);
            if (p != null && rootPerm != null && !rootPerm.isBlank() && !p.hasPermission(rootPerm)) {
                return true;
            }
            return true;
        }

        String rootPerm = permissionMap.get(r);
        if (p != null && rootPerm != null && !rootPerm.isBlank() && !p.hasPermission(rootPerm)) {
            return true;
        }

        return false;
    }

    private static String normalizeRoot(String root) {
        if (root == null) return "";
        String r = root.trim().toLowerCase(Locale.ROOT);
        if (r.startsWith("/")) r = r.substring(1);
        int colon = r.indexOf(':');
        if (colon >= 0) r = r.substring(colon + 1);
        int space = r.indexOf(' ');
        if (space >= 0) r = r.substring(0, space);
        return r.trim();
    }

    private static String normalizeSub(String sub) {
        if (sub == null) return "";
        String s = sub.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("/")) s = s.substring(1);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        int space = s.indexOf(' ');
        if (space >= 0) s = s.substring(0, space);
        return s.trim();
    }
}
