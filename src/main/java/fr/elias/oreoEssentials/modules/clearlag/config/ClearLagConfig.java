package fr.elias.oreoEssentials.modules.clearlag.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.stream.Collectors;

public class ClearLagConfig {

    public final boolean masterEnabled;

    public final Broadcast broadcast;
    public final Set<String> areaFilter;
    public final KillMobs killMobs;
    public final Removal auto;
    public final Removal cmd;
    public final TpsMeter tps;

    public ClearLagConfig(FileConfiguration root) {
        this.masterEnabled = root.getBoolean("enable", true);

        ConfigurationSection gb = root.getConfigurationSection("global-broadcasts");
        this.broadcast = new Broadcast(
                gb != null && gb.getBoolean("enabled", true),
                gb != null && gb.getBoolean("async", false),
                gb != null && gb.getBoolean("use-permission-for-broadcasts", false),
                gb != null ? gb.getString("permission", "bukkit.broadcast") : "bukkit.broadcast"
        );

        this.areaFilter = new HashSet<>(root.getStringList("area-filter"));

        ConfigurationSection km = root.getConfigurationSection("kill-mobs");
        boolean removeNamed = km != null && km.getBoolean("remove-named", false);
        List<String> mobFilter = km != null ? new ArrayList<>(km.getStringList("mob-filter")) : new ArrayList<>();
        this.killMobs = new KillMobs(removeNamed, mobFilter);

        this.auto = parseRemoval(root.getConfigurationSection("auto-removal"));
        this.cmd  = parseRemoval(root.getConfigurationSection("command-remove"));

        ConfigurationSection tm = root.getConfigurationSection("tps-meter");
        this.tps = new TpsMeter(
                tm != null && tm.getBoolean("enabled", false),
                tm != null ? tm.getInt("interval", 15) : 15,
                tm != null ? tm.getDouble("tps-trigger", 14.0) : 14.0,
                tm != null ? tm.getDouble("tps-recover", 19.0) : 19.0,
                tm != null && tm.getBoolean("broadcast-enabled", false),
                tm != null ? tm.getString("trigger-broadcast-message", "&6[OreoLag] &cLow TPS!") : "&6[OreoLag] &cLow TPS!",
                tm != null ? tm.getString("recover-broadcast-message", "&6[OreoLag] &aTPS recovered!") : "&6[OreoLag] &aTPS recovered!",
                tm != null ? new ArrayList<>(tm.getStringList("commands")) : new ArrayList<>(),
                tm != null ? new ArrayList<>(tm.getStringList("recover-commands")) : new ArrayList<>()
        );
    }

    private Removal parseRemoval(ConfigurationSection s) {
        if (s == null) {
            // Provide safe defaults if the section is missing
            return new Removal(
                    true,                       // enabled
                    480,                        // intervalSec
                    true,                       // broadcastRemoval
                    "&6[OreoLag] &aRemoved +RemoveAmount entities!", // broadcastMsg
                    new ArrayList<>(),          // worldFilter
                    new ArrayList<>(),          // removeEntities
                    new HashSet<>(),            // itemWhitelist
                    false,                      // flagBoat
                    true,                       // flagFallingBlock
                    true,                       // flagExp
                    false,                      // flagPainting
                    false,                      // flagProjectile
                    true,                       // flagItem
                    false,                      // flagItemFrame
                    false,                      // flagMinecart
                    true,                       // flagPrimedTnt
                    new ArrayList<>()           // warnings
            );
        }

        boolean enabled   = s.getBoolean("enabled", true);
        int interval      = s.getInt("autoremoval-interval", 480);
        boolean broadcast = s.getBoolean("broadcast-removal", true);
        String msg        = s.getString("broadcast-message", "&6[OreoLag] &aRemoved +RemoveAmount entities!");
        List<String> worlds = new ArrayList<>(s.getStringList("world-filter"));

        // flags
        ConfigurationSection f = s.getConfigurationSection("flags");
        boolean boat        = f != null && f.getBoolean("boat", false);
        boolean falling     = f == null || f.getBoolean("falling-block", true);
        boolean exp         = f == null || f.getBoolean("experience-orb", true);
        boolean painting    = f != null && f.getBoolean("painting", false);
        boolean projectile  = f != null && f.getBoolean("projectile", false);
        boolean item        = f == null || f.getBoolean("item", true);
        boolean itemframe   = f != null && f.getBoolean("itemframe", false);
        boolean minecart    = f != null && f.getBoolean("minecart", false);
        boolean tnt         = f == null || f.getBoolean("primed-tnt", true);

        // item whitelist
        Set<Material> itemWhitelist = s.getStringList("item-filter").stream()
                .map(String::toUpperCase)
                .map(m -> {
                    try { return Material.valueOf(m); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // token rules
        List<String> tokens = new ArrayList<>(s.getStringList("remove-entities"));

        List<Warning> warns = new ArrayList<>();
        for (String line : s.getStringList("warnings")) {
            int time = 0;
            String msgLine = "";
            String[] parts = line.split("\\s+");
            for (String p : parts) {
                if (p.startsWith("time:")) {
                    try { time = Integer.parseInt(p.substring(5)); } catch (Exception ignored) {}
                } else if (p.startsWith("msg:")) {
                    int idx = line.indexOf("msg:");
                    msgLine = idx >= 0 ? line.substring(idx + 4) : "";
                    break;
                }
            }
            warns.add(new Warning(time, msgLine));
        }

        return new Removal(enabled, interval, broadcast, msg, worlds, tokens, itemWhitelist,
                boat, falling, exp, painting, projectile, item, itemframe, minecart, tnt, warns);
    }

    public record Broadcast(boolean enabled, boolean async, boolean usePerm, String perm) {}
    public record KillMobs(boolean removeNamed, List<String> mobFilter) {}
    public record Warning(int time, String msg) {}

    public static class Removal {
        public final boolean enabled;
        public final int intervalSec;
        public final boolean broadcastRemoval;
        public final String broadcastMsg;
        public final List<String> worldFilter;
        public final List<String> removeEntities;
        public final Set<Material> itemWhitelist;

        public final boolean flagBoat;
        public final boolean flagFallingBlock;
        public final boolean flagExp;
        public final boolean flagPainting;
        public final boolean flagProjectile;
        public final boolean flagItem;
        public final boolean flagItemFrame;
        public final boolean flagMinecart;
        public final boolean flagPrimedTnt;

        public final List<Warning> warnings;

        public Removal(boolean enabled, int intervalSec, boolean broadcastRemoval, String broadcastMsg, List<String> worldFilter,
                       List<String> removeEntities, Set<Material> itemWhitelist,
                       boolean flagBoat, boolean flagFallingBlock, boolean flagExp, boolean flagPainting,
                       boolean flagProjectile, boolean flagItem, boolean flagItemFrame, boolean flagMinecart,
                       boolean flagPrimedTnt, List<Warning> warnings) {
            this.enabled = enabled;
            this.intervalSec = intervalSec;
            this.broadcastRemoval = broadcastRemoval;
            this.broadcastMsg = broadcastMsg;
            this.worldFilter = worldFilter;
            this.removeEntities = removeEntities;
            this.itemWhitelist = itemWhitelist;
            this.flagBoat = flagBoat;
            this.flagFallingBlock = flagFallingBlock;
            this.flagExp = flagExp;
            this.flagPainting = flagPainting;
            this.flagProjectile = flagProjectile;
            this.flagItem = flagItem;
            this.flagItemFrame = flagItemFrame;
            this.flagMinecart = flagMinecart;
            this.flagPrimedTnt = flagPrimedTnt;
            this.warnings = warnings;
        }
    }

    public static class TpsMeter {
        public final boolean enabled;
        public final int intervalSec;
        public final double trigger, recover;
        public final boolean broadcastEnabled;
        public final String triggerMsg, recoverMsg;
        public final List<String> commands, recoverCommands;

        public TpsMeter(boolean enabled, int intervalSec, double trigger, double recover, boolean broadcastEnabled,
                        String triggerMsg, String recoverMsg, List<String> commands, List<String> recoverCommands) {
            this.enabled = enabled;
            this.intervalSec = intervalSec;
            this.trigger = trigger;
            this.recover = recover;
            this.broadcastEnabled = broadcastEnabled;
            this.triggerMsg = triggerMsg;
            this.recoverMsg = recoverMsg;
            this.commands = commands;
            this.recoverCommands = recoverCommands;
        }
    }
}
