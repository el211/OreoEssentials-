package fr.elias.oreoEssentials.commands.core.admins;



import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.KillallLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class KillallRecorderCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final KillallLogger logger;

    public enum TargetType { ALL, HOSTILE, PASSIVE, NAMED, CUSTOM }

    public KillallRecorderCommand(OreoEssentials plugin, KillallLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cMust be a player.");
            return true;
        }
        if (!sender.hasPermission("oreo.killall.radius")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§eUsage: §f/" + label + " <radius> [--type=ALL|HOSTILE|PASSIVE|NAMED|CUSTOM] [--dry-run] [--reason=\"text\"]");
            return true;
        }

        double radius;
        try {
            radius = Double.parseDouble(args[0]);
            if (radius <= 0 || radius > 512) {
                sender.sendMessage("§cRadius must be between 1 and 512.");
                return true;
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cInvalid radius.");
            return true;
        }

        // Defaults from config
        String defType = plugin.getConfig().getString("killall.default-type", "ALL");
        TargetType type = parseType(optValue(args, "--type").orElse(defType));
        boolean dryRun = hasFlag(args, "--dry-run");
        String reason = optValue(args, "--reason").orElse("");

        boolean skipArmorStands = plugin.getConfig().getBoolean("killall.skip-armor-stands", true);
        boolean skipNamed = plugin.getConfig().getBoolean("killall.skip-named-entities", false);

        Location center = p.getLocation();
        World world = center.getWorld();
        if (world == null) {
            sender.sendMessage("§cWorld is null.");
            return true;
        }

        // Collect targets
        List<Entity> inRange = world.getNearbyEntities(center, radius, radius, radius).stream()
                .filter(e -> !(e instanceof Player)) // never touch players
                .filter(e -> !(skipArmorStands && e instanceof ArmorStand))
                .filter(e -> !(skipNamed && e.getCustomName() != null && !e.getCustomName().isBlank()))
                .filter(e -> matchesType(e, type))
                .collect(Collectors.toList());

        int count = inRange.size();

        if (dryRun) {
            sender.sendMessage("§7[Dry-run] Would remove §e" + count + " §7entities in §b" + String.format(Locale.US, "%.1f", radius) + "§7 radius. Type: §a" + type);
            return true;
        }

        // Remove
        for (Entity e : inRange) {
            // extra safety: don't kill tamed pets unless ALL or NAMED explicitly
            if (e instanceof Tameable tame && tame.isTamed() && (type == TargetType.PASSIVE)) continue;
            e.remove();
        }

        sender.sendMessage("§aRemoved §e" + count + " §aentities (type §b" + type + "§a) within §b" + String.format(Locale.US, "%.1f", radius) + "§a blocks.");

        // Log
        logger.append(
                new KillallLogger.Record(
                        new Date(),
                        p.getName(),
                        world.getName(),
                        center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                        radius,
                        type.name(),
                        count,
                        reason
                )
        );
        return true;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equalsIgnoreCase(flag)) return true;
        return false;
    }

    private static Optional<String> optValue(String[] args, String keyEq) {
        // expects --key=value possibly with quotes
        for (String a : args) {
            if (a.toLowerCase(Locale.ROOT).startsWith(keyEq.toLowerCase(Locale.ROOT) + "=")) {
                String v = a.substring(keyEq.length() + 1);
                if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    private static TargetType parseType(String v) {
        try {
            return TargetType.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return TargetType.ALL;
        }
    }

    private static boolean matchesType(Entity e, TargetType type) {
        if (!(e instanceof LivingEntity le)) {
            // remove projectiles, items, etc. only if ALL
            return type == TargetType.ALL && !(e instanceof Player);
        }

        if (type == TargetType.NAMED) {
            return le.getCustomName() != null && !le.getCustomName().isBlank();
        }

        if (type == TargetType.CUSTOM) {
            // Example heuristic: MythicMobs or non-vanilla? We can check scoreboard tags or metadata.
            // Fallback: entities with custom name OR with persistent data.
            return (le.getCustomName() != null && !le.getCustomName().isBlank()) || !le.getScoreboardTags().isEmpty();
        }

        if (type == TargetType.ALL) return true;

        // classify passive/hostile
        boolean hostile = isHostile(le);
        return (type == TargetType.HOSTILE && hostile) || (type == TargetType.PASSIVE && !hostile);
    }

    private static boolean isHostile(LivingEntity le) {
        // Cover common hostile mobs. Paper has Monster interface; use it when available.
        if (le instanceof Monster) return true;
        // extra: Slime & MagmaCube are hostile, Phantom too
        return (le instanceof Slime) || (le instanceof Phantom) || (le instanceof Hoglin) || (le instanceof Zoglin) || (le instanceof Guardian) || (le instanceof PufferFish);
    }

    // ---- Tab complete ----
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return List.of("16","32","64","96","128");
        if (args.length >= 2) {
            List<String> sugg = List.of("--type=ALL","--type=HOSTILE","--type=PASSIVE","--type=NAMED","--type=CUSTOM","--dry-run","--reason=\"\"");
            return StringUtil.copyPartialMatches(args[args.length-1], sugg, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
