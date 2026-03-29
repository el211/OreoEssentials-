package fr.elias.oreoEssentials.modules.tab;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.config.SettingsConfig;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TabListManager {

    private static final Pattern ANIM_TAG = Pattern.compile(
            "%animations_<tag(?:\\s+interval=(\\d+))?>\\s*(.*?)\\s*</tag>%",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private final OreoEssentials plugin;
    private final SettingsConfig settings;
    private final PlayerDirectory playerDirectory;

    private CustomTablistLayout customLayout;
    private boolean useCustomLayout;

    private File file;
    private FileConfiguration cfg;
    private OreTask task;
    private OreTask nameTask;

    private boolean enabled;
    private boolean usePapi;
    private String header;
    private String footer;
    private int intervalTicks;
    private boolean networkMode;
    private final Set<String> networkExcludedServers = new HashSet<>();
    private String serverTagPattern;
    private boolean titleEnabled;
    private boolean titleShowOnJoin;
    private String titleText;
    private String titleSub;
    private int titleIn, titleStay, titleOut;
    private boolean nameEnabled;
    private boolean useRankFormats;
    private String rankKey;
    private String namePattern;
    private boolean nameEnforceMax;
    private int nameMaxLen;
    private OverflowMode overflowMode;
    private final Map<String, String> rankFormats = new HashMap<>();
    private final Map<String, WorldOverrides> worldOverrides = new HashMap<>();
    private final Set<UUID> titleShown = new HashSet<>();

    private enum OverflowMode { TRIM, ELLIPSIS }

    private static class WorldOverrides {
        Boolean usePapi;
        String header;
        String footer;
        NameOverrides name;
    }

    private static class NameOverrides {
        Boolean enabled;
        Boolean useRankFormats;
        String rankKey;
        Map<String, String> rankFormats;
        String pattern;
        Boolean enforceMax;
        Integer maxLen;
        String overflow;
    }

    public TabListManager(OreoEssentials plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettingsConfig();
        this.playerDirectory = plugin.getPlayerDirectory();

        load();

        if (titleEnabled && titleShowOnJoin) {
            Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                    sendTitleOnce(e.getPlayer());
                }
            }, plugin);
        }
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "tab.yml");
        if (!file.exists()) {
            plugin.saveResource("tab.yml", false);
        }

        cfg = YamlConfiguration.loadConfiguration(file);

        enabled = cfg.getBoolean("tab.enabled", true);
        if (!enabled) {
            plugin.getLogger().info("[TAB] tab.yml 'tab.enabled' is deprecated. Use settings.yml features.tab.enabled instead.");
        }

        usePapi = cfg.getBoolean("tab.use-placeholderapi", true)
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        header = color(cfg.getString("tab.header", ""));
        footer = color(cfg.getString("tab.footer", ""));
        intervalTicks = Math.max(20, cfg.getInt("tab.interval-ticks", 200));

        ConfigurationSection net = cfg.getConfigurationSection("tab.network");
        this.networkMode = net != null && net.getBoolean("all-servers", false);
        networkExcludedServers.clear();
        if (net != null) {
            for (String s : net.getStringList("excluded-servers")) {
                networkExcludedServers.add(s.toLowerCase(Locale.ROOT));
            }
        }
        plugin.getLogger().info("[TAB] Network mode: " + (networkMode
                ? "NETWORK_ALL (using PlayerDirectory if available)"
                : "LOCAL_ONLY")
                + (networkExcludedServers.isEmpty() ? "" : ", excluded: " + networkExcludedServers));

        ConfigurationSection t = cfg.getConfigurationSection("tab.title");
        titleEnabled = t != null && t.getBoolean("enabled", false);
        titleShowOnJoin = t != null && t.getBoolean("show-on-join", true);
        titleText = color(t != null ? t.getString("text", "") : "");
        titleSub = color(t != null ? t.getString("subtitle", "") : "");
        titleIn = t != null ? t.getInt("fade-in", 10) : 10;
        titleStay = t != null ? t.getInt("stay", 60) : 60;
        titleOut = t != null ? t.getInt("fade-out", 10) : 10;

        ConfigurationSection nf = cfg.getConfigurationSection("tab.name-format");
        nameEnabled = nf != null && nf.getBoolean("enabled", true);
        useRankFormats = nf != null && nf.getBoolean("use-rank-formats", true);
        rankKey = nf != null ? nf.getString("rank-key", "%luckperms_primary_group%") : "%luckperms_primary_group%";
        namePattern = nf != null ? nf.getString("pattern", "&7%nick_or_name%") : "&7%nick_or_name%";
        nameEnforceMax = nf != null && nf.getBoolean("enforce-max-length", true);
        nameMaxLen = nf != null ? Math.max(1, nf.getInt("max-length", 16)) : 16;
        String ov = nf != null ? nf.getString("overflow", "TRIM") : "TRIM";
        overflowMode = "ELLIPSIS".equalsIgnoreCase(ov) ? OverflowMode.ELLIPSIS : OverflowMode.TRIM;

        String layoutMode = cfg.getString("tab.layout-mode", "CLASSIC");
        this.useCustomLayout = "CUSTOM".equalsIgnoreCase(layoutMode);

        if (useCustomLayout) {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
                plugin.getLogger().warning("[TAB] Custom layout requires ProtocolLib! Falling back to classic mode.");
                plugin.getLogger().warning("[TAB] Download ProtocolLib: https://www.spigotmc.org/resources/protocollib.1997/");
                this.useCustomLayout = false;
            } else {
                plugin.getLogger().info("[TAB] Using CUSTOM layout mode (packet-based tablist like the image)");
            }
        } else {
            plugin.getLogger().info("[TAB] Using CLASSIC layout mode (traditional header/footer)");
        }

        serverTagPattern = nf != null ? nf.getString("server-tag", "") : "";

        rankFormats.clear();
        if (nf != null && nf.isConfigurationSection("rank-formats")) {
            for (String k : nf.getConfigurationSection("rank-formats").getKeys(false)) {
                rankFormats.put(k.toLowerCase(Locale.ROOT), nf.getString("rank-formats." + k));
            }
        }

        worldOverrides.clear();
        ConfigurationSection pw = cfg.getConfigurationSection("tab.per-world");
        if (pw != null) {
            for (String world : pw.getKeys(false)) {
                ConfigurationSection w = pw.getConfigurationSection(world);
                if (w == null) continue;

                WorldOverrides o = new WorldOverrides();
                o.usePapi = w.isSet("use-placeholderapi") ? w.getBoolean("use-placeholderapi") : null;
                o.header = w.isSet("header") ? color(w.getString("header")) : null;
                o.footer = w.isSet("footer") ? color(w.getString("footer")) : null;

                if (w.isConfigurationSection("name-format")) {
                    ConfigurationSection wn = w.getConfigurationSection("name-format");
                    NameOverrides no = new NameOverrides();
                    no.enabled = wn.isSet("enabled") ? wn.getBoolean("enabled") : null;
                    no.useRankFormats = wn.isSet("use-rank-formats") ? wn.getBoolean("use-rank-formats") : null;
                    no.rankKey = wn.isSet("rank-key") ? wn.getString("rank-key") : null;

                    if (wn.isConfigurationSection("rank-formats")) {
                        Map<String, String> map = new HashMap<>();
                        for (String k : wn.getConfigurationSection("rank-formats").getKeys(false)) {
                            map.put(k.toLowerCase(Locale.ROOT), wn.getString("rank-formats." + k));
                        }
                        no.rankFormats = map;
                    }

                    no.pattern = wn.isSet("pattern") ? wn.getString("pattern") : null;
                    no.enforceMax = wn.isSet("enforce-max-length") ? wn.getBoolean("enforce-max-length") : null;
                    no.maxLen = wn.isSet("max-length") ? wn.getInt("max-length") : null;
                    no.overflow = wn.isSet("overflow") ? wn.getString("overflow") : null;
                    o.name = no;
                }

                worldOverrides.put(world.toLowerCase(Locale.ROOT), o);
            }
        }
    }

    public void start() {
        stop();

        if (useCustomLayout) {
            if (customLayout == null) {
                customLayout = new CustomTablistLayout(plugin, this);
            }
            customLayout.start(intervalTicks);
            plugin.getLogger().info("[TAB] Started in CUSTOM layout mode");

            // When player-section is disabled in custom layout, run name-format from this config
            boolean playerSectionEnabled = cfg.getBoolean("tab.custom-layout.player-section.enabled", true);
            if (nameEnabled && !playerSectionEnabled) {
                nameTask = OreScheduler.runTimer(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        applyNameFormat(p);
                    }
                }, 1L, intervalTicks);
                plugin.getLogger().info("[TAB] Name formatting delegated to name-format section (player-section disabled)");
            }
        } else {
            // Use classic header/footer mode (your original code)
            startClassicMode();
            plugin.getLogger().info("[TAB] Started in CLASSIC layout mode");
        }
    }

    /**
     * Classic mode: Traditional header/footer with player name formatting
     */
    private void startClassicMode() {
        task = OreScheduler.runTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {

                World world = p.getWorld();
                WorldOverrides o = worldOverrides.get(world.getName().toLowerCase(Locale.ROOT));

                boolean papi = (o != null && o.usePapi != null)
                        ? (o.usePapi && hasPapi())
                        : (usePapi && hasPapi());

                String h = firstNonNull(o != null ? o.header : null, header);
                String f = firstNonNull(o != null ? o.footer : null, footer);

                h = applyTagAnimations(h);
                f = applyTagAnimations(f);

                h = applyInternalPlaceholders(p, h);
                f = applyInternalPlaceholders(p, f);

                h = runPapiIf(papi, p, h);
                f = runPapiIf(papi, p, f);

                setTab(p, h, f);

                boolean nEnabled = (o != null && o.name != null && o.name.enabled != null)
                        ? o.name.enabled
                        : nameEnabled;
                if (!nEnabled) continue;

                boolean localUseRank = (o != null && o.name != null && o.name.useRankFormats != null)
                        ? o.name.useRankFormats
                        : useRankFormats;

                String localRankKey = (o != null && o.name != null && o.name.rankKey != null)
                        ? o.name.rankKey
                        : rankKey;

                String localPattern = (o != null && o.name != null && o.name.pattern != null)
                        ? o.name.pattern
                        : namePattern;

                Map<String, String> localRankFormats =
                        (o != null && o.name != null && o.name.rankFormats != null)
                                ? o.name.rankFormats
                                : rankFormats;

                String patternToUse = localPattern;
                if (localUseRank && !localRankFormats.isEmpty()) {
                    String rk = runPapiIf(papi, p, localRankKey);
                    if (rk == null) rk = "";
                    String byRank = localRankFormats.get(rk.toLowerCase(Locale.ROOT));
                    if (byRank == null) byRank = localRankFormats.get("default");
                    if (byRank != null) patternToUse = byRank;
                }

                String nickOrName = safeNickOrName(p);
                String rendered = patternToUse.replace("%nick_or_name%", nickOrName);

                // Apply PAPI BEFORE color() to avoid mixing §-codes with MiniMessage tags
                rendered = applyInternalPlaceholders(p, rendered);
                rendered = runPapiIf(papi, p, rendered);
                rendered = color(rendered); // single color pass after all substitutions

                boolean enforce = (o != null && o.name != null && o.name.enforceMax != null)
                        ? o.name.enforceMax
                        : nameEnforceMax;

                int maxLen = (o != null && o.name != null && o.name.maxLen != null)
                        ? o.name.maxLen
                        : nameMaxLen;

                String ov = (o != null && o.name != null && o.name.overflow != null)
                        ? o.name.overflow
                        : (overflowMode == OverflowMode.ELLIPSIS ? "ELLIPSIS" : "TRIM");

                OverflowMode mode = "ELLIPSIS".equalsIgnoreCase(ov)
                        ? OverflowMode.ELLIPSIS
                        : OverflowMode.TRIM;

                if (enforce) {
                    rendered = fitToMax(rendered, maxLen, mode);
                }

                try {
                    p.playerListName(LEGACY_HEX.deserialize(rendered));
                } catch (Throwable e) {
                    try { p.setPlayerListName(rendered); } catch (Throwable ignored) {}
                }
            }
        }, 1L, intervalTicks);
    }

    /** Applies name-format.rank-formats to a single player (used in CUSTOM layout mode). */
    private void applyNameFormat(Player p) {
        World world = p.getWorld();
        WorldOverrides o = worldOverrides.get(world.getName().toLowerCase(Locale.ROOT));

        boolean papi = (o != null && o.usePapi != null)
                ? (o.usePapi && hasPapi())
                : (usePapi && hasPapi());

        boolean nEnabled = (o != null && o.name != null && o.name.enabled != null)
                ? o.name.enabled : nameEnabled;
        if (!nEnabled) return;

        boolean localUseRank = (o != null && o.name != null && o.name.useRankFormats != null)
                ? o.name.useRankFormats : useRankFormats;
        String localRankKey = (o != null && o.name != null && o.name.rankKey != null)
                ? o.name.rankKey : rankKey;
        String localPattern = (o != null && o.name != null && o.name.pattern != null)
                ? o.name.pattern : namePattern;
        Map<String, String> localRankFormats = (o != null && o.name != null && o.name.rankFormats != null)
                ? o.name.rankFormats : rankFormats;

        String patternToUse = localPattern;
        if (localUseRank && !localRankFormats.isEmpty()) {
            String rk = runPapiIf(papi, p, localRankKey);
            if (rk == null) rk = "";
            String byRank = localRankFormats.get(rk.toLowerCase(Locale.ROOT));
            if (byRank == null) byRank = localRankFormats.get("default");
            if (byRank != null) patternToUse = byRank;
        }

        String nickOrName = safeNickOrName(p);
        String rendered = patternToUse.replace("%nick_or_name%", nickOrName);
        // Apply PAPI BEFORE color() so that MiniMessage tags from PAPI (e.g. %luckperms_prefix%)
        // are not mixed with §-codes from a premature color() call, which would break MM.deserialize.
        rendered = applyInternalPlaceholders(p, rendered);
        rendered = runPapiIf(papi, p, rendered);
        rendered = color(rendered); // single color pass after all substitutions

        boolean enforce = (o != null && o.name != null && o.name.enforceMax != null)
                ? o.name.enforceMax : nameEnforceMax;
        int maxLen = (o != null && o.name != null && o.name.maxLen != null)
                ? o.name.maxLen : nameMaxLen;
        String ov = (o != null && o.name != null && o.name.overflow != null)
                ? o.name.overflow : (overflowMode == OverflowMode.ELLIPSIS ? "ELLIPSIS" : "TRIM");
        OverflowMode mode = "ELLIPSIS".equalsIgnoreCase(ov) ? OverflowMode.ELLIPSIS : OverflowMode.TRIM;
        if (enforce) rendered = fitToMax(rendered, maxLen, mode);

        try {
            p.playerListName(LEGACY_HEX.deserialize(rendered));
        } catch (Throwable e) {
            try { p.setPlayerListName(rendered); } catch (Throwable ignored) {}
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (nameTask != null) {
            nameTask.cancel();
            nameTask = null;
        }

        if (customLayout != null) {
            customLayout.stop();
        }

        titleShown.clear();
    }

    public void reload() {
        stop();
        load();

        try {
            cfg.save(file);
        } catch (IOException ignored) {
        }

        start();
        plugin.getLogger().info("[TAB] tab.yml reloaded (via TabListManager.reload()).");
    }

    private void sendTitleOnce(Player p) {
        if (!titleEnabled || !titleShowOnJoin) return;
        if (titleShown.contains(p.getUniqueId())) return;

        String t = applyInternalPlaceholders(p, titleText);
        String s = applyInternalPlaceholders(p, titleSub);

        boolean papi = usePapi && hasPapi();
        t = runPapiIf(papi, p, t);
        s = runPapiIf(papi, p, s);

        try {
            p.sendTitle(t, s, titleIn, titleStay, titleOut);
        } catch (Throwable ignored) {
        }
        titleShown.add(p.getUniqueId());
    }

    private boolean hasPapi() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    private String runPapiIf(boolean papi, Player p, String s) {
        if (!papi || s == null || s.isEmpty()) return s;
        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            return (String) m.invoke(null, p, s);
        } catch (Throwable ignored) {
            return s;
        }
    }

    private void setTab(Player player, String header, String footer) {
        try {
            player.setPlayerListHeaderFooter(header, footer);
        } catch (Throwable ignored) {
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final java.util.regex.Pattern AMP_HEX = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final java.util.Map<Character, String> AMP_TO_MINI;
    static {
        java.util.Map<Character, String> m = new java.util.HashMap<>();
        m.put('l', "<bold>"); m.put('o', "<italic>"); m.put('n', "<underlined>");
        m.put('m', "<strikethrough>"); m.put('k', "<obfuscated>"); m.put('r', "<reset>");
        m.put('0', "<black>"); m.put('1', "<dark_blue>"); m.put('2', "<dark_green>");
        m.put('3', "<dark_aqua>"); m.put('4', "<dark_red>"); m.put('5', "<dark_purple>");
        m.put('6', "<gold>"); m.put('7', "<gray>"); m.put('8', "<dark_gray>");
        m.put('9', "<blue>"); m.put('a', "<green>"); m.put('b', "<aqua>");
        m.put('c', "<red>"); m.put('d', "<light_purple>"); m.put('e', "<yellow>");
        m.put('f', "<white>");
        AMP_TO_MINI = java.util.Collections.unmodifiableMap(m);
    }

    private static String convertAmpToMiniMessage(String s) {
        if (s == null || s.indexOf('&') == -1) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String mini = AMP_TO_MINI.get(code);
                if (mini != null) { sb.append(mini); i++; continue; }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String color(String s) {
        if (s == null) return "";

        // 1. &#RRGGBB → <#RRGGBB>
        s = AMP_HEX.matcher(s).replaceAll("<#$1>");

        // 2. §-codes (from PAPI, e.g. %simplenick_nickname% → §9hi) → & codes
        //    so they survive MM.deserialize when mixed with MiniMessage tags
        s = s.replace('§', '&');

        // 3. Full MiniMessage parse — converts &-codes to MM tags first so the
        //    entire string (both PAPI §-output and MiniMessage prefix tags) is handled uniformly
        if (s.indexOf('<') != -1 && s.indexOf('>') != -1) {
            try {
                Component comp = MM.deserialize(convertAmpToMiniMessage(s));
                s = LEGACY_HEX.serialize(comp);
                return s.replace("\\n", "\n");
            } catch (Throwable ignored) {}
        }

        // 4. Fallback: translate remaining & codes
        s = org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
        return s.replace("\\n", "\n");
    }

    private static final java.util.regex.Pattern STRIP_COLOR = java.util.regex.Pattern.compile(
            "(?i)§x(§[0-9A-F]){6}|§[0-9A-FK-ORX]");

    private static String safeNickOrName(Player p) {
        try {
            String d = p.getDisplayName();
            if (d != null && !d.isEmpty()) {
                // Strip color codes so the rank-format's own color takes effect
                return STRIP_COLOR.matcher(d).replaceAll("");
            }
        } catch (Throwable ignored) {
        }
        return p.getName();
    }

    private static String fitToMax(String s, int max, OverflowMode mode) {
        if (s == null) return "";
        int visible = 0;
        StringBuilder out = new StringBuilder();
        char[] arr = s.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];
            if ((c == '§' || c == '&') && i + 1 < arr.length) {
                out.append('§');
                out.append(arr[++i]);
                continue;
            }
            if (visible < max) {
                out.append(c);
                visible++;
            } else {
                if (mode == OverflowMode.ELLIPSIS) {
                    return trimWithEllipsis(out.toString(), max);
                }
                break;
            }
        }
        return out.toString();
    }

    private static String trimWithEllipsis(String colored, int max) {
        String raw = colored.replaceAll("(?i)§[0-9A-FK-ORX]", "");
        if (max <= 3) return "...".substring(0, Math.min(3, max));
        if (raw.length() <= max) return raw;
        return raw.substring(0, max - 3) + "...";
    }

    private String applyInternalPlaceholders(Player viewer, String input) {
        if (input == null || input.isEmpty()) return input;

        int localOnline = Bukkit.getOnlinePlayers().size();
        int networkOnline = localOnline;
        List<String> networkLines = Collections.emptyList();

        if (networkMode && playerDirectory != null) {
            try {
                Method m = playerDirectory.getClass().getMethod("snapshotOnline");
                @SuppressWarnings("unchecked")
                Collection<?> entries = (Collection<?>) m.invoke(playerDirectory);

                List<String> lines = new ArrayList<>();
                int count = 0;
                if (entries != null) {
                    for (Object e : entries) {
                        String name = invokeString(e, "getName", "name");
                        String server = invokeString(e, "getServer", "server", "getServerName");
                        if (name == null || name.isEmpty()) continue;
                        if (server == null || server.isEmpty()) {
                            server = plugin.getConfigService().serverName();
                        }
                        if (!networkExcludedServers.isEmpty()
                                && networkExcludedServers.contains(server.toLowerCase(Locale.ROOT))) continue;
                        lines.add("§f" + name + " §8(§7" + server + "§8)");
                        count++;
                    }
                }
                if (!lines.isEmpty()) {
                    networkLines = lines;
                    networkOnline = count;
                }
            } catch (Throwable ignored) {
                networkOnline = localOnline;
                networkLines = Collections.emptyList();
            }
        }

        String out = input;
        out = out.replace("%oe_local_online%", String.valueOf(localOnline));
        out = out.replace("%oe_network_online%", String.valueOf(networkOnline));

        if (out.contains("%oe_network_list%")) {
            if (!networkMode || networkLines.isEmpty()) {
                out = out.replace("%oe_network_list%", "");
            } else {
                out = out.replace("%oe_network_list%", String.join("\n", networkLines));
            }
        }

        if (out.contains("%oe_server_tag%")) {
            String tag = serverTagPattern;
            if (tag == null) tag = "";
            tag = color(tag);
            out = out.replace("%oe_server_tag%", tag);
        }

        return out;
    }

    private String applyTagAnimations(String input) {
        if (input == null || input.isEmpty()) return input;

        Matcher m = ANIM_TAG.matcher(input);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            int interval = 20;
            try {
                if (m.group(1) != null) interval = Math.max(1, Integer.parseInt(m.group(1)));
            } catch (Throwable ignored) {}

            String body = m.group(2) == null ? "" : m.group(2);

            List<String> frames = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            String[] lines = body.replace("\r\n", "\n").split("\n", -1);
            for (String line : lines) {
                if (line.trim().equals("|")) {
                    frames.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(line).append("\n");
                }
            }
            frames.add(current.toString());

            frames.replaceAll(s -> s.endsWith("\n") ? s.substring(0, s.length() - 1) : s);

            String replacement;
            if (frames.isEmpty()) {
                replacement = "";
            } else {
                long ticks = System.currentTimeMillis() / 50L;
                int idx = (int) ((ticks / interval) % frames.size());
                replacement = frames.get(Math.max(0, idx));
            }

            replacement = Matcher.quoteReplacement(replacement);
            m.appendReplacement(out, replacement);
        }

        m.appendTail(out);
        return out.toString();
    }

    private static String invokeString(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                Object o = m.invoke(target);
                if (o instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public FileConfiguration getConfig() {
        return cfg;
    }
}