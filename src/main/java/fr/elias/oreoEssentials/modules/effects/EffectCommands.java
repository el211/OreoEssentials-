// File: src/main/java/fr/elias/oreoEssentials/effects/EffectCommands.java
package fr.elias.oreoEssentials.modules.effects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public final class EffectCommands implements TabExecutor {

    private static final List<String> TRUE_FALSE = Arrays.asList("true", "false");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        final String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("effectme")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use /effectme. Use /effectto for others.");
                return true;
            }
            if (!sender.hasPermission("oreo.effect.me")) {
                sender.sendMessage("§cYou don't have permission (oreo.effect.me).");
                return true;
            }
            return handleEffectMe((Player) sender, args);
        }

        if (name.equals("effectto")) {
            if (!sender.hasPermission("oreo.effect.to")) {
                sender.sendMessage("§cYou don't have permission (oreo.effect.to).");
                return true;
            }
            return handleEffectTo(sender, args);
        }

        return false;
    }

    private boolean handleEffectMe(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§eUsage: §f/effectme <effect|clear> [seconds] [level] [hideParticles]");
            player.sendMessage("§7Examples: §f/effectme Speed 120 2  §8|  §f/effectme clear  §8|  §f/effectme jump_boost 60");
            return true;
        }

        // /effectme clear [effect|all]
        if (args[0].equalsIgnoreCase("clear")) {
            if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
                PotionEffectType type = matchEffect(args[1]);
                if (type == null) {
                    player.sendMessage("§cUnknown effect: §f" + args[1]);
                    return true;
                }
                player.removePotionEffect(type);
                player.sendMessage("§aCleared §f" + readable(type) + " §aeffect.");
            } else {
                for (PotionEffectType t : player.getActivePotionEffects().stream().map(PotionEffect::getType).collect(Collectors.toSet())) {
                    player.removePotionEffect(t);
                }
                player.sendMessage("§aCleared all active effects.");
            }
            return true;
        }

        PotionEffectType type = matchEffect(args[0]);
        if (type == null) {
            player.sendMessage("§cUnknown effect: §f" + args[0]);
            return true;
        }

        int seconds = parseInt(args, 1, 60, 1, 60 * 60 * 24); // default 60s, min 1s, max 24h
        int levelHuman = parseInt(args, 2, 1, 1, 255);        // default 1 (Speed I)
        int amplifier = Math.max(0, levelHuman - 1);          // Bukkit amplifier = level-1
        boolean hideParticles = parseBoolean(args, 3, false);

        boolean ok = player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, !hideParticles, true));
        if (!ok) {
            player.sendMessage("§cFailed to apply effect (conflict with stronger/longer effect?).");
            return true;
        }
        player.sendMessage("§aGiven yourself §f" + readable(type) + " §7for §f" + seconds + "s §7at level §f" + levelHuman +
                (hideParticles ? " §8(no particles)" : ""));
        return true;
    }

    private boolean handleEffectTo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §f/effectto <player> <effect|clear> [seconds] [level] [hideParticles]");
            sender.sendMessage("§7Examples: §f/effectto Steve WaterBreathing 120 2  §8|  §f/effectto Alex clear all");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: §f" + args[0]);
            return true;
        }

        // /effectto <player> clear [effect|all]
        if (args[1].equalsIgnoreCase("clear")) {
            if (args.length >= 3 && !args[2].equalsIgnoreCase("all")) {
                PotionEffectType type = matchEffect(args[2]);
                if (type == null) {
                    sender.sendMessage("§cUnknown effect: §f" + args[2]);
                    return true;
                }
                target.removePotionEffect(type);
                sender.sendMessage("§aCleared §f" + readable(type) + " §afrom §f" + target.getName());
                if (sender != target) target.sendMessage("§eYour §f" + readable(type) + " §eeffect was cleared.");
            } else {
                for (PotionEffectType t : target.getActivePotionEffects().stream().map(PotionEffect::getType).collect(Collectors.toSet())) {
                    target.removePotionEffect(t);
                }
                sender.sendMessage("§aCleared all effects from §f" + target.getName());
                if (sender != target) target.sendMessage("§eAll your effects were cleared.");
            }
            return true;
        }

        PotionEffectType type = matchEffect(args[1]);
        if (type == null) {
            sender.sendMessage("§cUnknown effect: §f" + args[1]);
            return true;
        }

        int seconds = parseInt(args, 2, 60, 1, 60 * 60 * 24);
        int levelHuman = parseInt(args, 3, 1, 1, 255);
        int amplifier = Math.max(0, levelHuman - 1);
        boolean hideParticles = parseBoolean(args, 4, false);

        boolean ok = target.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, !hideParticles, true));
        if (!ok) {
            sender.sendMessage("§cFailed to apply effect (conflict with stronger/longer effect?).");
            return true;
        }

        sender.sendMessage("§aGave §f" + target.getName() + " §a§f" + readable(type) + " §7for §f" + seconds + "s §7at level §f" + levelHuman +
                (hideParticles ? " §8(no particles)" : ""));
        if (sender != target) {
            target.sendMessage("§aYou received §f" + readable(type) + " §7for §f" + seconds + "s §7at level §f" + levelHuman +
                    (hideParticles ? " §8(no particles)" : "") + " §7from §f" + sender.getName());
        }
        return true;
    }

    // ---- Helpers ----

    private static int parseInt(String[] a, int idx, int def, int min, int max) {
        if (idx >= a.length) return def;
        try {
            int v = Integer.parseInt(a[idx]);
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static boolean parseBoolean(String[] a, int idx, boolean def) {
        if (idx >= a.length) return def;
        String s = a[idx].toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("no") || s.equals("n")) return false;
        return def;
    }

    private static String readable(PotionEffectType type) {
        String n = type.getKey().getKey().replace('_', ' ');
        return Arrays.stream(n.split(" "))
                .map(w -> w.isEmpty() ? w : (Character.toUpperCase(w.charAt(0)) + w.substring(1)))
                .collect(Collectors.joining(" "));
    }

    /**
     * Flexible matcher: accepts "waterbreathing", "Water_Breathing", "WATER_BREATHING", etc.
     */
    private static PotionEffectType matchEffect(String user) {
        if (user == null) return null;
        String norm = user.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");

        // Try NamespacedKey direct
        try {
            PotionEffectType byKey = PotionEffectType.getByKey(NamespacedKey.minecraft(user.toLowerCase(Locale.ROOT)));
            if (byKey != null) return byKey;
        } catch (Exception ignored) {}

        // Iterate all types and compare normalized names
        for (PotionEffectType t : PotionEffectType.values()) {
            if (t == null) continue;
            NamespacedKey k = t.getKey();
            String keyNorm = k.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
            if (keyNorm.equals(norm)) return t;
        }

        // Fallback: legacy name match if present
        try {
            PotionEffectType legacy = PotionEffectType.getByName(user.toUpperCase(Locale.ROOT));
            if (legacy != null) return legacy;
        } catch (Exception ignored) {}

        return null;
    }

    // ---- Tab completion ----
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("effectme")) {
            if (!(sender instanceof Player) || !sender.hasPermission("oreo.effect.me")) return Collections.emptyList();

            if (args.length == 1) {
                List<String> base = new ArrayList<>();
                base.add("clear");
                base.addAll(allEffectKeys());
                return filterStarts(base, args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
                List<String> base = new ArrayList<>();
                base.add("all");
                base.addAll(allEffectKeys());
                return filterStarts(base, args[1]);
            }
            if (args.length == 2) return Collections.singletonList("60");
            if (args.length == 3) return Collections.singletonList("1");
            if (args.length == 4) return filterStarts(TRUE_FALSE, args[3]);
            return Collections.emptyList();
        }

        if (name.equals("effectto")) {
            if (!sender.hasPermission("oreo.effect.to")) return Collections.emptyList();

            if (args.length == 1) {
                return filterStarts(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[0]);
            }
            if (args.length == 2) {
                List<String> base = new ArrayList<>();
                base.add("clear");
                base.addAll(allEffectKeys());
                return filterStarts(base, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("clear")) {
                List<String> base = new ArrayList<>();
                base.add("all");
                base.addAll(allEffectKeys());
                return filterStarts(base, args[2]);
            }
            if (args.length == 3) return Collections.singletonList("60");
            if (args.length == 4) return Collections.singletonList("1");
            if (args.length == 5) return filterStarts(TRUE_FALSE, args[4]);
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private static List<String> allEffectKeys() {
        return Arrays.stream(PotionEffectType.values())
                .filter(Objects::nonNull)
                .map(t -> t.getKey().getKey()) // e.g., "speed", "water_breathing"
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<String> filterStarts(List<String> base, String prefix) {
        final String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
