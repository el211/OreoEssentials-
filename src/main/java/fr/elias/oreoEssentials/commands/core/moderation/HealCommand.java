// File: src/main/java/fr/elias/oreoEssentials/commands/core/moderation/HealCommand.java
package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class HealCommand implements OreoCommand {
    @Override public String name() { return "heal"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.heal"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player target;

        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Lang.send(sender,
                        "moderation.heal.not-found",
                        "§cPlayer not found.",
                        Map.of("target", args[0]));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                Lang.send(sender,
                        "moderation.heal.console-usage",
                        "§cUsage: /%label% <player>",
                        Map.of("label", label));
                return true;
            }
            target = (Player) sender;
        }

        double max = resolveMaxHealth(target);
        if (max <= 0) max = 20.0;

        // Apply healing
        try { target.setHealth(Math.min(max, target.getHealthScale() > 0 ? target.getHealthScale() : max)); }
        catch (Throwable ignored) { target.setHealth(max); }

        target.setFireTicks(0);

        Lang.send(target,
                "moderation.heal.healed",
                "§aHealed.",
                null);

        if (target != sender) {
            Lang.send(sender,
                    "moderation.heal.healed-other",
                    "§eHealed §b%player%",
                    Map.of("player", target.getName()));
        }
        return true;
    }

    /** Prefer enum constants, with a reflection fallback for very old APIs. */
    private static double resolveMaxHealth(Player p) {
        // Try modern constant
        Attribute attr = tryGetAttributeField("GENERIC_MAX_HEALTH");
        if (attr != null) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst != null) return inst.getValue();
        }

        // Try older constant name
        attr = tryGetAttributeField("MAX_HEALTH");
        if (attr != null) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst != null) return inst.getValue();
        }

        // Last resort: legacy getMaxHealth() via reflection
        try {
            Method m = p.getClass().getMethod("getMaxHealth");
            Object r = m.invoke(p);
            if (r instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}

        return 20.0;
    }

    /** Gets Attribute.GENERIC_MAX_HEALTH / MAX_HEALTH via Field without using valueOf(String). */
    private static Attribute tryGetAttributeField(String fieldName) {
        try {
            Field f = Attribute.class.getField(fieldName);
            Object v = f.get(null);
            if (v instanceof Attribute a) return a;
        } catch (Throwable ignored) {}
        return null;
    }
}
