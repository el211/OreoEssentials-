package fr.elias.oreoEssentials.modules.tab;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;

public class CustomTablistLayout {

    private final OreoEssentials plugin;
    private final TabListManager tabManager;
    private BukkitTask updateTask;

    private int currentFrame = 0;
    private long lastFrameChange = 0;

    public CustomTablistLayout(OreoEssentials plugin, TabListManager tabManager) {
        this.plugin = plugin;
        this.tabManager = tabManager;
    }

    public void start(int intervalTicks) {
        stop();

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    updateCustomTablist(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("[CustomTab] Error updating tablist for " + player.getName() + ": " + e.getMessage());
                }
            }
        }, 20L, intervalTicks);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void updateCustomTablist(Player viewer) throws Exception {
        try {
            FileConfiguration cfg = tabManager.getConfig();

            boolean headerAnimated = cfg.contains("tab.custom-layout.top-section.texts");
            boolean footerAnimated = cfg.contains("tab.custom-layout.bottom-section.texts");

            int changeInterval = cfg.getInt("tab.custom-layout.change-interval", 80);

            long currentTime = System.currentTimeMillis();
            long ticksPassed = (currentTime - lastFrameChange) / 50;

            if (ticksPassed >= changeInterval) {
                currentFrame++;
                lastFrameChange = currentTime;
            }

            String headerText;
            if (headerAnimated) {
                headerText = getAnimatedText(cfg, "tab.custom-layout.top-section.texts", viewer);
            } else {
                String line1 = cfg.getString("tab.custom-layout.top-section.line-1", "&f&lWelcome &b%player_displayname%");
                String line2 = cfg.getString("tab.custom-layout.top-section.line-2", "&6&lWorld: &e%player_world% &a⬛⬛⬛");
                String line3 = cfg.getString("tab.custom-layout.top-section.line-3", "&6&lPing: &e%player_ping%ms &a⬛⬛⬛");

                line1 = applyPlaceholders(viewer, color(line1));
                line2 = applyPlaceholders(viewer, color(line2));
                line3 = applyPlaceholders(viewer, color(line3));

                headerText = line1 + "\n" + line2 + "  " + line3;
            }

            String footerText;
            if (footerAnimated) {
                footerText = getAnimatedText(cfg, "tab.custom-layout.bottom-section.texts", viewer);
            } else {
                String fLine1 = cfg.getString("tab.custom-layout.bottom-section.line-1", "&6&lPlayers: &e%oe_network_online%");
                String fLine2 = cfg.getString("tab.custom-layout.bottom-section.line-2", "&6&lBalance: &e$%vault_eco_balance_formatted%");
                String fLine3 = cfg.getString("tab.custom-layout.bottom-section.line-3", "&c&lTHE RED DRAGON");

                fLine1 = applyPlaceholders(viewer, color(fLine1));
                fLine2 = applyPlaceholders(viewer, color(fLine2));
                fLine3 = applyPlaceholders(viewer, color(fLine3));

                footerText = "\n" + fLine1 + "  " + fLine2 + "\n" + fLine3;
            }

            viewer.setPlayerListHeaderFooter(headerText, footerText);

            updatePlayerNames(viewer, cfg);

        } catch (Exception e) {
            plugin.getLogger().warning("[CustomTab] Error updating tablist: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getAnimatedText(FileConfiguration cfg, String path, Player viewer) {
        List<String> frames = cfg.getStringList(path);

        if (frames.isEmpty()) {
            return "";
        }

        int frameIndex = currentFrame % frames.size();
        String frame = frames.get(frameIndex);

        frame = applyPlaceholders(viewer, color(frame));

        return frame;
    }

    private void updatePlayerNames(Player viewer, FileConfiguration cfg) {
        // When player-section is disabled, name formatting is handled by TabListManager's name-format section
        if (!cfg.getBoolean("tab.custom-layout.player-section.enabled", true)) return;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        players.sort((p1, p2) -> {
            int priority1 = getRankPriority(p1, cfg);
            int priority2 = getRankPriority(p2, cfg);
            return Integer.compare(priority2, priority1);
        });

        for (Player target : players) {
            String displayName = formatPlayerName(target, cfg);
            displayName = applyPlaceholders(target, displayName);

            try {
                target.setPlayerListName(displayName);
            } catch (Exception e) {
                plugin.getLogger().warning("[CustomTab] Failed to set player list name for " + target.getName());
            }
        }
    }

    private String formatPlayerName(Player player, FileConfiguration cfg) {
        String format = cfg.getString("tab.custom-layout.player-section.player-format.format",
                "%player_color%%player_name%%afk_indicator%");

        String rankColor = getRankColor(player, cfg);

        String afkIndicator = "";
        if (cfg.getBoolean("tab.custom-layout.player-section.player-format.show-afk", true)) {
            if (isPlayerAFK(player)) {
                afkIndicator = color(cfg.getString("tab.custom-layout.player-section.player-format.afk-format", " &7AFK"));
            }
        }

        format = format.replace("%player_color%", rankColor)
                .replace("%player_name%", player.getName())
                .replace("%afk_indicator%", afkIndicator);

        return color(format);
    }

    private String getRankColor(Player player, FileConfiguration cfg) {
        String rank = getPrimaryGroup(player);

        ConfigurationSection rankColors = cfg.getConfigurationSection("tab.custom-layout.player-section.player-format.rank-colors");
        if (rankColors != null && rankColors.contains(rank)) {
            return color(rankColors.getString(rank, "&a"));
        }

        return color(rankColors != null ? rankColors.getString("default", "&a") : "&a");
    }

    private int getRankPriority(Player player, FileConfiguration cfg) {
        String rank = getPrimaryGroup(player);

        ConfigurationSection priorities = cfg.getConfigurationSection("tab.custom-layout.sorting.rank-priority");
        if (priorities != null && priorities.contains(rank)) {
            return priorities.getInt(rank, 10);
        }

        return 10;
    }

    private String getPrimaryGroup(Player player) {
        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            String result = (String) m.invoke(null, player, "%luckperms_primary_group%");
            return result != null ? result : "default";
        } catch (Throwable e) {
            return "default";
        }
    }

    private boolean isPlayerAFK(Player player) {
        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            String result = (String) m.invoke(null, player, "%essentials_afk%");
            return "yes".equalsIgnoreCase(result);
        } catch (Throwable e) {
            return false;
        }
    }

    private int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable e) {
            return 0;
        }
    }

    private String applyPlaceholders(Player player, String text) {
        if (text == null) return "";

        text = text.replace("%player_displayname%", player.getDisplayName());
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%player_ping%", String.valueOf(player.getPing()));
        text = text.replace("%oe_network_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            text = (String) m.invoke(null, player, text);
        } catch (Throwable ignored) {
        }

        // Re-apply color conversion in case PAPI/LuckPerms returned hex codes
        text = color(text);

        return text;
    }

    private static final java.util.regex.Pattern HEX_AMP       = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final java.util.regex.Pattern HEX_MINI      = java.util.regex.Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final java.util.regex.Pattern GRADIENT_MINI = java.util.regex.Pattern.compile(
            "<gradient:#([A-Fa-f0-9]{6})(?::#[A-Fa-f0-9]{6})*>", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern MINI_TAG_STRIP = java.util.regex.Pattern.compile(
            "</?(?:gradient|rainbow|transition|font|lang|insertion|click|hover|key|selector|score|nbt|block_nbt|entity_nbt|storage_nbt)[^>]*>",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static String hexToLegacy(java.util.regex.Matcher m) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : m.group(1).toCharArray()) sb.append('§').append(c);
        return sb.toString();
    }

    private static String color(String s) {
        if (s == null) return "";
        // &#RRGGBB → §x§R§R§G§G§B§B
        java.util.regex.Matcher m1 = HEX_AMP.matcher(s);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) m1.appendReplacement(sb1, hexToLegacy(m1));
        m1.appendTail(sb1);
        s = sb1.toString();
        // <#RRGGBB> → §x§R§R§G§G§B§B
        java.util.regex.Matcher m2 = HEX_MINI.matcher(s);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) m2.appendReplacement(sb2, hexToLegacy(m2));
        m2.appendTail(sb2);
        s = sb2.toString();
        // <gradient:#RRGGBB:#RRGGBB> → use first color as legacy
        java.util.regex.Matcher mg = GRADIENT_MINI.matcher(s);
        StringBuffer sbg = new StringBuffer();
        while (mg.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : mg.group(1).toCharArray()) rep.append('§').append(c);
            mg.appendReplacement(sbg, rep.toString());
        }
        mg.appendTail(sbg);
        s = sbg.toString();
        // MiniMessage named tags → legacy codes
        s = s.replace("<reset>", "§r").replace("<gray>", "§7").replace("<white>", "§f")
             .replace("<black>", "§0").replace("<dark_blue>", "§1").replace("<dark_green>", "§2")
             .replace("<dark_aqua>", "§3").replace("<dark_red>", "§4").replace("<dark_purple>", "§5")
             .replace("<gold>", "§6").replace("<dark_gray>", "§8").replace("<blue>", "§9")
             .replace("<green>", "§a").replace("<aqua>", "§b").replace("<red>", "§c")
             .replace("<light_purple>", "§d").replace("<yellow>", "§e")
             // MiniMessage decoration tags → legacy codes
             .replace("<bold>", "§l").replace("</bold>", "§r")
             .replace("<b>", "§l").replace("</b>", "§r")
             .replace("<italic>", "§o").replace("</italic>", "§r")
             .replace("<i>", "§o").replace("</i>", "§r")
             .replace("<underlined>", "§n").replace("</underlined>", "§r")
             .replace("<u>", "§n").replace("</u>", "§r")
             .replace("<strikethrough>", "§m").replace("</strikethrough>", "§r")
             .replace("<st>", "§m").replace("</st>", "§r")
             .replace("<obfuscated>", "§k").replace("</obfuscated>", "§r")
             .replace("<obf>", "§k").replace("</obf>", "§r");
        // Strip remaining unsupported MiniMessage tags (e.g. </gradient>)
        s = MINI_TAG_STRIP.matcher(s).replaceAll("");
        return s.replace('&', '§');
    }
}
