// File: src/main/java/fr/elias/oreoEssentials/mobs/SpawnMobCommand.java
package fr.elias.oreoEssentials.modules.mobs;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class SpawnMobCommand implements TabExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use /spawnmob (spawns at your location).");
            return true;
        }
        if (!sender.hasPermission("oreo.spawnmob")) {
            sender.sendMessage("§cYou don't have permission (oreo.spawnmob).");
            return true;
        }

        Player p = (Player) sender;

        if (args.length < 1) {
            p.sendMessage("§eUsage: §f/spawnmob <EntityType> [amount]");
            p.sendMessage("§7Example: §f/spawnmob zombie 5");
            return true;
        }

        EntityType type = matchEntityType(args[0]);
        if (type == null) {
            p.sendMessage("§cUnknown or unspawnable entity: §f" + args[0]);
            p.sendMessage("§7Tip: try §f/spawnmob " + suggestExamples());
            return true;
        }

        if (!isSpawnable(type)) {
            p.sendMessage("§cThat entity cannot be spawned here: §f" + type.name());
            return true;
        }

        int amount = parseInt(args, 1, 1, 1, 100); // default 1, max 100

        World w = p.getWorld();
        Location loc = p.getLocation();

        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            try {
                Entity e = w.spawnEntity(loc, type);
                if (e != null) spawned++;
            } catch (Throwable t) {

            }
        }

        if (spawned == 0) {
            p.sendMessage("§cNo entities were spawned. The type might need special conditions.");
        } else {
            p.sendMessage("§aSpawned §f" + spawned + " §7x §f" + pretty(type) + " §7at your location.");
        }
        return true;
    }

    // ---------- Tab Complete ----------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("oreo.spawnmob")) return Collections.emptyList();

        if (args.length == 1) {
            return filterStarts(allSpawnableKeys(), args[0]);
        }
        if (args.length == 2) {
            return Arrays.asList("1", "5", "10", "25", "64");
        }
        return Collections.emptyList();
    }

    // ---------- Helpers ----------

    private static int parseInt(String[] a, int idx, int def, int min, int max) {
        if (idx >= a.length) return def;
        try {
            int v = Integer.parseInt(a[idx]);
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static String pretty(EntityType t) {
        String s = t.getKey() != null ? t.getKey().getKey() : t.name().toLowerCase(Locale.ROOT);
        s = s.replace('_', ' ');
        return Arrays.stream(s.split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static boolean isSpawnable(EntityType t) {
        // Prefer modern API if available
        try {
            // Paper/Spigot modern: t.isSpawnable() exists on many versions
            return t.isSpawnable() && t != EntityType.PLAYER && t != EntityType.UNKNOWN;
        } catch (NoSuchMethodError ignored) {
            // Fallback heuristic
            return t != EntityType.PLAYER && t != EntityType.UNKNOWN;
        }
    }

    private static EntityType matchEntityType(String user) {
        if (user == null) return null;
        String norm = user.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("_", "").replace("-", "");

        // Try namespaced key directly (minecraft:zombie)
        try {
            EntityType byKey = EntityType.fromName(user.toLowerCase(Locale.ROOT));
            if (byKey != null && isSpawnable(byKey)) return byKey;
        } catch (Throwable ignored) {}

        // Iterate all to match normalized
        for (EntityType t : EntityType.values()) {
            if (t == null) continue;
            String key = t.getKey() != null ? t.getKey().getKey() : t.name().toLowerCase(Locale.ROOT);
            String kNorm = key.replace("_", "").replace("-", "");
            if (kNorm.equals(norm) && isSpawnable(t)) return t;
        }

        // Fallback: enum name
        try {
            EntityType byEnum = EntityType.valueOf(user.toUpperCase(Locale.ROOT));
            if (isSpawnable(byEnum)) return byEnum;
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    private static List<String> allSpawnableKeys() {
        return Arrays.stream(EntityType.values())
                .filter(Objects::nonNull)
                .filter(SpawnMobCommand::isSpawnable)
                .map(t -> t.getKey() != null ? t.getKey().getKey() : t.name().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<String> filterStarts(List<String> base, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    private static String suggestExamples() {
        // Provide a few common, guaranteed-valid examples
        return "zombie, skeleton, creeper, cow, villager";
    }
}
