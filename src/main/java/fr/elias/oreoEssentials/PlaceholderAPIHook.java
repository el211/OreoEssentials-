package fr.elias.oreoEssentials;

import fr.elias.oreoEssentials.kits.Kit;
import fr.elias.oreoEssentials.kits.KitsManager;
import fr.elias.oreoEssentials.playervaults.PlayerVaultsConfig;
import fr.elias.oreoEssentials.playervaults.PlayerVaultsService;
import fr.elias.oreoEssentials.playtime.PlaytimeRewardsService;
import fr.elias.oreoEssentials.playtime.PlaytimeTracker;
import fr.elias.oreoEssentials.util.Lang;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Consolidated PlaceholderAPI hook for OreoEssentials.
 * Supports: economy, kits, playtime/prewards, player vaults, homes (safe fallbacks).
 *
 * Identifier: %oreo_<id>%
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {
    private final OreoEssentials plugin;
    private final Economy economy; // may be null

    public PlaceholderAPIHook(OreoEssentials plugin) {
        this.plugin = plugin;
        var reg = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = (reg != null) ? reg.getProvider() : null;
    }

    @Override public @NotNull String getIdentifier() { return "oreo"; }
    @Override public @NotNull String getAuthor()     { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean canRegister()           { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String idRaw) {
        if (player == null) return "";
        final String id = idRaw.toLowerCase(Locale.ROOT);

        /* ---------------- Economy ---------------- */
        if (id.equals("balance")) {
            if (economy == null) return "0";
            return String.valueOf(economy.getBalance(player));
        }
        /* ---------------- SERVER NICKNAMES ---------------- */
        if (id.equals("server_name")) {
            return Bukkit.getServer().getName();
        }

        if (id.equals("server_nick")) {
            return resolveServerNick(Bukkit.getServer().getName());
        }

        if (id.startsWith("server_nick_")) {
            String serverId = id.substring("server_nick_".length());
            if (serverId.isBlank()) return "";
            return resolveServerNick(serverId);
        }

        /* ---------------- KITS ---------------- */
        if (id.equals("kits_enabled")) {
            KitsManager km = kits();
            return (km != null && km.isEnabled()) ? "true" : "false";
        }
        if (id.equals("kits_count")) {
            KitsManager km = kits();
            return (km != null) ? String.valueOf(km.getKits().size()) : "0";
        }
        if (id.equals("kits_ready_count")) {
            Player p = player.getPlayer();
            KitsManager km = kits();
            if (p == null || km == null) return "0";
            return String.valueOf((int) km.getKits().values().stream()
                    .filter(k -> isKitReady(km, p, k))
                    .count());
        }
        if (id.equals("kits_ready_list")) {
            Player p = player.getPlayer();
            KitsManager km = kits();
            if (p == null || km == null) return "";
            List<String> ready = km.getKits().values().stream()
                    .filter(k -> isKitReady(km, p, k))
                    .map(Kit::getId)
                    .collect(Collectors.toList());
            return trim64(String.join(", ", ready));
        }
        if (id.startsWith("kit_ready_")) {
            Player p = player.getPlayer();
            if (p == null) return "false";
            String kitId = id.substring("kit_ready_".length());
            KitsManager km = kits();
            if (km == null) return "false";
            Kit k = km.getKits().get(kitId.toLowerCase(Locale.ROOT));
            return (k != null && isKitReady(km, p, k)) ? "true" : "false";
        }
        if (id.startsWith("kit_cooldown_")) {
            Player p = player.getPlayer();
            if (p == null) return "";
            String kitId = id.substring("kit_cooldown_".length());
            KitsManager km = kits();
            if (km == null) return "";
            Kit k = km.getKits().get(kitId.toLowerCase(Locale.ROOT));
            if (k == null) return "";
            long left = Math.max(0, km.getSecondsLeft(p, k));
            return left <= 0 ? "ready" : String.valueOf(left);
        }
// %oreo_kit_cooldown_formatted_<id>%
// OU %oreo_kit_<id>_cooldown_formatted%
        if (id.startsWith("kit_cooldown_formatted_") || (id.startsWith("kit_") && id.endsWith("_cooldown_formatted"))) {
            Player p = player.getPlayer();
            if (p == null) return "";

            String kitId;

            if (id.startsWith("kit_cooldown_formatted_")) {
                // style: kit_cooldown_formatted_pvp
                kitId = id.substring("kit_cooldown_formatted_".length());
            } else {
                // style: kit_pvp_cooldown_formatted
                String core = id.substring("kit_".length(), id.length() - "_cooldown_formatted".length());
                kitId = core;
            }

            KitsManager km = kits();
            if (km == null) return "";
            Kit k = km.getKits().get(kitId.toLowerCase(Locale.ROOT));
            if (k == null) return "";

            long left = Math.max(0, km.getSecondsLeft(p, k));
            if (left <= 0) {
                // texte quand le kit est prêt (tu peux mettre ça dans lang.yml si tu veux)
                return "ready";
                // ou ex.:
                // return Lang.get("kits.placeholder.ready", "ready");
            }

            // Temps formaté, genre "3m 25s"
            return Lang.timeHuman(left);
        }


        /* -------- PLAYTIME / PLAYTIME-REWARDS -------- */
        if (id.equals("playtime_total_seconds")) {
            long secs = playtimeTotalSeconds(player);
            return String.valueOf(secs);
        }
        if (id.equals("prewards_enabled")) {
            PlaytimeRewardsService svc = prewards();
            return (svc != null && svc.isEnabled()) ? "true" : "false";
        }
        if (id.equals("prewards_ready_count")) {
            Player p = player.getPlayer();
            PlaytimeRewardsService svc = prewards();
            if (p == null || svc == null || !svc.isEnabled()) return "0";
            return String.valueOf(svc.rewardsReady(p).size());
        }
        if (id.equals("prewards_ready_list")) {
            Player p = player.getPlayer();
            PlaytimeRewardsService svc = prewards();
            if (p == null || svc == null || !svc.isEnabled()) return "";
            return trim64(String.join(", ", svc.rewardsReady(p)));
        }
        if (id.startsWith("prewards_state_")) {
            Player p = player.getPlayer();
            PlaytimeRewardsService svc = prewards();
            if (p == null || svc == null || !svc.isEnabled()) return "";
            String rewardId = id.substring("prewards_state_".length());
            var entry = svc.rewards.get(rewardId);
            if (entry == null) return "";
            return svc.stateOf(p, entry).name(); // LOCKED | READY | CLAIMED | REPEATING
        }

        /* ---------------- PLAYER VAULTS ---------------- */
        if (id.equals("vaults_enabled")) {
            PlayerVaultsService pv = pvService();
            return (pv != null && pv.enabled()) ? "true" : "false";
        }
        if (id.equals("vaults_max")) {
            int max = pvConfigMax();
            return String.valueOf(Math.max(0, max));
        }
        if (id.equals("vaults_unlocked_count")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "0";
            int max = pvConfigMax();
            int count = 0;
            for (int i = 1; i <= max; i++) if (pv.canAccess(p, i)) count++;
            return String.valueOf(count);
        }
        if (id.equals("vaults_unlocked_list")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "";
            int max = pvConfigMax();
            List<String> ids = new ArrayList<>();
            for (int i = 1; i <= max; i++) if (pv.canAccess(p, i)) ids.add(String.valueOf(i));
            return trim64(String.join(", ", ids));
        }
        if (id.equals("vaults_locked_list")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "";
            int max = pvConfigMax();
            List<String> ids = new ArrayList<>();
            for (int i = 1; i <= max; i++) if (!pv.canAccess(p, i)) ids.add(String.valueOf(i));
            return trim64(String.join(", ", ids));
        }
        if (id.startsWith("vault_can_access_")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "false";
            int vid = parseIntSafe(id.substring("vault_can_access_".length()));
            if (vid <= 0) return "false";
            return pv.canAccess(p, vid) ? "true" : "false";
        }
        if (id.startsWith("vault_slots_")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "0";
            int vid = parseIntSafe(id.substring("vault_slots_".length()));
            if (vid <= 0) return "0";
            return String.valueOf(pv.resolveSlots(p, vid));
        }
        if (id.startsWith("vault_rows_")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "0";
            int vid = parseIntSafe(id.substring("vault_rows_".length()));
            if (vid <= 0) return "0";
            int slots = pv.resolveSlots(p, vid);
            int rows = Math.max(1, (int) Math.ceil(slots / 9.0));
            return String.valueOf(rows);
        }
        if (id.startsWith("vault_title_preview_")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            PlayerVaultsConfig cfg = pvConfig();
            if (p == null || pv == null || !pv.enabled() || cfg == null) return "";
            int vid = parseIntSafe(id.substring("vault_title_preview_".length()));
            if (vid <= 0) return "";
            int slots = pv.resolveSlots(p, vid);
            int rows = Math.max(1, (int) Math.ceil(slots / 9.0));
            String title = cfg.vaultTitle()
                    .replace("<id>", String.valueOf(vid))
                    .replace("<rows>", String.valueOf(rows));
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', title);
        }

        /* ---------------- HOMES (safe reflection) ---------------- */
        if (id.equals("homes_used") || id.equals("homes_max") || id.equals("homes")) {
            int used = 0;
            int max  = 0;
            try {
                Object homes = homeService();
                if (homes != null && player.getUniqueId() != null) {
                    // call: homes.homes(UUID) -> Collection/Map size
                    Method m = homes.getClass().getMethod("homes", java.util.UUID.class);
                    Object list = m.invoke(homes, player.getUniqueId());
                    if (list instanceof java.util.Collection<?> coll) used = coll.size();
                    else if (list instanceof java.util.Map<?, ?> map) used = map.size();
                }
            } catch (Throwable ignored) {}

            try {
                Object cfg = configService();
                Player p = player.getPlayer();
                if (cfg != null && p != null) {
                    // prefer permission-based max when online
                    Method m = cfg.getClass().getMethod("getMaxHomesFor", org.bukkit.entity.Player.class);
                    Object out = m.invoke(cfg, p);
                    if (out instanceof Number n) max = n.intValue();
                } else if (cfg != null) {
                    Method m = cfg.getClass().getMethod("defaultMaxHomes");
                    Object out = m.invoke(cfg);
                    if (out instanceof Number n) max = n.intValue();
                }
            } catch (Throwable ignored) {}

            if (id.equals("homes_used")) return String.valueOf(used);
            if (id.equals("homes_max"))  return String.valueOf(max);
            return used + "/" + max;
        }

        return null; // unknown placeholder
    }

    /* ---------------- helpers ---------------- */

    private boolean isKitReady(KitsManager km, Player p, Kit k) {
        if (!km.isEnabled()) return false;
        if (!p.hasPermission("oreo.kit.claim")) return false;
        long left = Math.max(0, km.getSecondsLeft(p, k));
        return left == 0 || p.hasPermission("oreo.kit.bypasscooldown");
    }
    private String resolveServerNick(String serverName) {
        try {
            var c = plugin.getConfig();
            String def = c.getString("server_nicknames.default", serverName);

            // store keys as-is, but compare case-insensitive
            var sec = c.getConfigurationSection("server_nicknames.map");
            if (sec == null) return def;

            for (String key : sec.getKeys(false)) {
                if (key != null && key.equalsIgnoreCase(serverName)) {
                    return sec.getString(key, def);
                }
            }
            return def;
        } catch (Throwable t) {
            return serverName;
        }
    }

    /** Best-effort total seconds using your stack: Prewards.getPlaytimeSeconds -> Tracker -> Bukkit stat. */
    private long playtimeTotalSeconds(OfflinePlayer off) {
        Player p = off.getPlayer();
        if (p != null) {
            PlaytimeRewardsService svc = prewards();
            if (svc != null && svc.isEnabled()) {
                try { return Math.max(0, svc.getPlaytimeSeconds(p)); } catch (Throwable ignored) {}
            }
        }
        PlaytimeTracker tr = tracker();
        if (tr != null && off.getUniqueId() != null) {
            try { return Math.max(0, tr.getSeconds(off.getUniqueId())); } catch (Throwable ignored) {}
        }
        try {
            if (p != null) {
                int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
                return Math.max(0, ticks / 20L);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static String trim64(String s) {
        if (s == null) return "";
        return s.length() > 64 ? s.substring(0, 61) + "..." : s;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    /* ---- Reflection getters: keep your API flexible without hard deps ---- */

    private KitsManager kits() {
        try {
            Method m = plugin.getClass().getMethod("getKitsManager");
            return (KitsManager) m.invoke(plugin);
        } catch (Throwable ignored) { return null; }
    }

    private PlaytimeRewardsService prewards() {
        try {
            Method m = plugin.getClass().getMethod("getPlaytimeRewardsService");
            return (PlaytimeRewardsService) m.invoke(plugin);
        } catch (Throwable ignored) { return null; }
    }

    private PlaytimeTracker tracker() {
        try {
            Method m = plugin.getClass().getMethod("getPlaytimeTracker");
            return (PlaytimeTracker) m.invoke(plugin);
        } catch (Throwable ignored) { return null; }
    }

    private PlayerVaultsService pvService() {
        try {
            Method m = plugin.getClass().getMethod("getPlayerVaultsService");
            return (PlayerVaultsService) m.invoke(plugin);
        } catch (Throwable ignored) { return null; }
    }

    private PlayerVaultsConfig pvConfig() {
        try {
            PlayerVaultsService svc = pvService();
            if (svc == null) return null;
            Method gm = svc.getClass().getMethod("getConfigBean");
            return (PlayerVaultsConfig) gm.invoke(svc);
        } catch (Throwable ignored) { return null; }
    }

    private int pvConfigMax() {
        PlayerVaultsConfig c = pvConfig();
        return (c != null) ? c.maxVaults() : 0;
    }

    private Object homeService() {
        try {
            Method m = plugin.getClass().getMethod("getHomeService");
            return m.invoke(plugin);
        } catch (Throwable ignored) { return null; }
    }

    private Object configService() {
        try {
            Method m = plugin.getClass().getMethod("getConfigService");
            return m.invoke(plugin);
        } catch (Throwable ignored) { return null; }
    }
}
