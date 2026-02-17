package fr.elias.oreoEssentials;

import fr.elias.oreoEssentials.modules.economy.EconomyService;
import fr.elias.oreoEssentials.modules.kits.Kit;
import fr.elias.oreoEssentials.modules.kits.KitsManager;
import fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsConfig;
import fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsService;
import fr.elias.oreoEssentials.modules.playtime.PlaytimeRewardsService;
import fr.elias.oreoEssentials.modules.playtime.PlaytimeTracker;
import fr.elias.oreoEssentials.util.Lang;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class PlaceholderAPIHook extends PlaceholderExpansion {
    private final OreoEssentials plugin;
    private boolean debug;
    private final String thisServerName;

    public PlaceholderAPIHook(OreoEssentials plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("placeholder-debug", false);
        this.thisServerName = plugin.getConfigService().serverName();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "oreo";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String idRaw) {
        if (player == null) {
            debugLog("Player is null for placeholder: %oreo_" + idRaw + "%");
            return "";
        }

        final String id = idRaw.toLowerCase(Locale.ROOT);

        debugLog("Processing placeholder: %oreo_" + idRaw + "% for player: " + player.getName());

        if (id.equals("balance")) {
            EconomyService eco = getEconomyService();
            if (eco == null || player.getUniqueId() == null) {
                debugLog("Economy is null or player UUID is null for 'balance'");
                return "0";
            }
            double bal = eco.getBalance(player.getUniqueId());
            String result = String.valueOf(bal);
            debugLog("balance = " + result);
            return result;
        }

        if (id.equals("balance_formatted")) {
            EconomyService eco = getEconomyService();
            if (eco == null || player.getUniqueId() == null) {
                debugLog("Economy is null or player UUID is null for 'balance_formatted'");
                return "0";
            }
            double bal = eco.getBalance(player.getUniqueId());
            DecimalFormat df = new DecimalFormat("#,##0.##");
            String result = df.format(bal);
            debugLog("balance_formatted = " + result);
            return result;
        }

        if (id.equals("server_name")) {
            try {
                return plugin.getConfigService().serverName();
            } catch (Throwable t) {
                return Bukkit.getServer().getName();
            }
        }

        if (id.equals("server_nick")) {
            return resolveServerNick(Bukkit.getServer().getName());
        }

        if (id.startsWith("server_nick_")) {
            String serverId = id.substring("server_nick_".length());
            if (serverId.isBlank()) return "";
            return resolveServerNick(serverId);
        }

        if (id.equals("crossserver_players_total")) {
            return String.valueOf(
                    fr.elias.oreoEssentials.network.NetworkCountReceiver.getNetworkTotal()
            );
        }

        if (id.equals("server_players_total")) {
            int cached = fr.elias.oreoEssentials.network.NetworkCountReceiver
                    .getServerTotal(thisServerName);
            return String.valueOf(cached > 0 ? cached : Bukkit.getOnlinePlayers().size());
        }

        if (id.startsWith("server_players_")) {
            String targetServer = idRaw.substring("server_players_".length());
            return String.valueOf(
                    fr.elias.oreoEssentials.network.NetworkCountReceiver.getServerTotal(targetServer)
            );
        }

        if (id.endsWith("_players_total")
                && !id.equals("crossserver_players_total")
                && !id.equals("server_players_total")) {
            String targetServer = idRaw.substring(0, idRaw.length() - "_players_total".length());
            return String.valueOf(
                    fr.elias.oreoEssentials.network.NetworkCountReceiver.getServerTotal(targetServer)
            );
        }

        if (id.equals("world_name") || id.equals("world")) {
            Player p = player.getPlayer();
            if (p == null) return "";
            return p.getWorld().getName();
        }

        if (id.equals("world_nick")) {
            Player p = player.getPlayer();
            if (p == null) return "";
            return resolveWorldNick(p.getWorld().getName());
        }

        if (id.startsWith("world_nick_")) {
            String worldId = id.substring("world_nick_".length());
            if (worldId.isBlank()) return "";
            return resolveWorldNick(worldId);
        }

        if (id.equals("network_online")) {
            return String.valueOf(Bukkit.getOnlinePlayers().size());
        }

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

        if (id.startsWith("kit_cooldown_formatted_") || (id.startsWith("kit_") && id.endsWith("_cooldown_formatted"))) {
            Player p = player.getPlayer();
            if (p == null) return "";

            String kitId;
            if (id.startsWith("kit_cooldown_formatted_")) {
                kitId = id.substring("kit_cooldown_formatted_".length());
            } else {
                kitId = id.substring("kit_".length(), id.length() - "_cooldown_formatted".length());
            }

            KitsManager km = kits();
            if (km == null) return "";
            Kit k = km.getKits().get(kitId.toLowerCase(Locale.ROOT));
            if (k == null) return "";

            long left = Math.max(0, km.getSecondsLeft(p, k));
            if (left <= 0) return "ready";

            return Lang.timeHuman(left);
        }

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
            return svc.stateOf(p, entry).name();
        }

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
            for (int i = 1; i <= max; i++) {
                if (pv.canAccess(p, i)) count++;
            }
            return String.valueOf(count);
        }

        if (id.equals("vaults_unlocked_list")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "";
            int max = pvConfigMax();
            List<String> ids = new ArrayList<>();
            for (int i = 1; i <= max; i++) {
                if (pv.canAccess(p, i)) ids.add(String.valueOf(i));
            }
            return trim64(String.join(", ", ids));
        }

        if (id.equals("vaults_locked_list")) {
            Player p = player.getPlayer();
            PlayerVaultsService pv = pvService();
            if (p == null || pv == null || !pv.enabled()) return "";
            int max = pvConfigMax();
            List<String> ids = new ArrayList<>();
            for (int i = 1; i <= max; i++) {
                if (!pv.canAccess(p, i)) ids.add(String.valueOf(i));
            }
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

        if (id.equals("homes_used") || id.equals("homes_max") || id.equals("homes")) {
            int used = 0;
            int max = 0;

            try {
                Object homes = homeService();
                if (homes != null && player.getUniqueId() != null) {
                    Method m = homes.getClass().getMethod("homes", UUID.class);
                    Object list = m.invoke(homes, player.getUniqueId());
                    if (list instanceof Collection<?> coll) {
                        used = coll.size();
                    } else if (list instanceof Map<?, ?> map) {
                        used = map.size();
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Object cfg = configService();
                Player p = player.getPlayer();
                if (cfg != null && p != null) {
                    Method m = cfg.getClass().getMethod("getMaxHomesFor", Player.class);
                    Object out = m.invoke(cfg, p);
                    if (out instanceof Number n) max = n.intValue();
                } else if (cfg != null) {
                    Method m = cfg.getClass().getMethod("defaultMaxHomes");
                    Object out = m.invoke(cfg);
                    if (out instanceof Number n) max = n.intValue();
                }
            } catch (Throwable ignored) {}

            if (id.equals("homes_used")) return String.valueOf(used);
            if (id.equals("homes_max")) return String.valueOf(max);
            return used + "/" + max;
        }

        debugLog("Unknown placeholder: %oreo_" + idRaw + "%");
        return null;
    }

    private void debugLog(String message) {
        if (debug) {
            plugin.getLogger().info("[PLACEHOLDER DEBUG] " + message);
        }
    }

    private EconomyService getEconomyService() {
        try {
            return plugin.getEcoBootstrap().api();
        } catch (Throwable t) {
            debugLog("Failed to get EconomyService: " + t.getMessage());
            return null;
        }
    }

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

    private String resolveWorldNick(String worldName) {
        try {
            var c = plugin.getConfig();
            String def = c.getString("world_nicknames.default", worldName);
            var sec = c.getConfigurationSection("world_nicknames.map");
            if (sec == null) return def;
            for (String key : sec.getKeys(false)) {
                if (key != null && key.equalsIgnoreCase(worldName)) {
                    return sec.getString(key, def);
                }
            }
            return def;
        } catch (Throwable t) {
            return worldName;
        }
    }

    private long playtimeTotalSeconds(OfflinePlayer off) {
        Player p = off.getPlayer();
        if (p != null) {
            PlaytimeRewardsService svc = prewards();
            if (svc != null && svc.isEnabled()) {
                try {
                    return Math.max(0, svc.getPlaytimeSeconds(p));
                } catch (Throwable ignored) {}
            }
        }

        PlaytimeTracker tr = tracker();
        if (tr != null && off.getUniqueId() != null) {
            try {
                return Math.max(0, tr.getSeconds(off.getUniqueId()));
            } catch (Throwable ignored) {}
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
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }

    private KitsManager kits() {
        try {
            Method m = plugin.getClass().getMethod("getKitsManager");
            return (KitsManager) m.invoke(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private PlaytimeRewardsService prewards() {
        try {
            Method m = plugin.getClass().getMethod("getPlaytimeRewardsService");
            return (PlaytimeRewardsService) m.invoke(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private PlaytimeTracker tracker() {
        try {
            Method m = plugin.getClass().getMethod("getPlaytimeTracker");
            return (PlaytimeTracker) m.invoke(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private PlayerVaultsService pvService() {
        try {
            Method m = plugin.getClass().getMethod("getPlayerVaultsService");
            return (PlayerVaultsService) m.invoke(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private PlayerVaultsConfig pvConfig() {
        try {
            PlayerVaultsService svc = pvService();
            if (svc == null) return null;
            Method gm = svc.getClass().getMethod("getConfigBean");
            return (PlayerVaultsConfig) gm.invoke(svc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int pvConfigMax() {
        PlayerVaultsConfig c = pvConfig();
        return (c != null) ? c.maxVaults() : 0;
    }

    private Object homeService() {
        try {
            Method m = plugin.getClass().getMethod("getHomeService");
            return m.invoke(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object configService() {
        try {
            Method m = plugin.getClass().getMethod("getConfigService");
            return m.invoke(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }
}