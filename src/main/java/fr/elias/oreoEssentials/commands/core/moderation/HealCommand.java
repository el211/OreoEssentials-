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
                Lang.send(sender, "moderation.heal.not-found",
                        "<red>Player not found.</red>",
                        Map.of("target", args[0]));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                Lang.send(sender, "moderation.heal.console-usage",
                        "<red>Usage: /%label% <player></red>",
                        Map.of("label", label));
                return true;
            }
            target = (Player) sender;
        }

        double max = resolveMaxHealth(target);
        if (max <= 0) max = 20.0;

        try {
            target.setHealth(Math.min(max, target.getHealthScale() > 0 ? target.getHealthScale() : max));
        } catch (Throwable ignored) {
            target.setHealth(max);
        }

        target.setFireTicks(0);

        Lang.send(target, "moderation.heal.healed",
                "<green>Healed.</green>");

        if (target != sender) {
            Lang.send(sender, "moderation.heal.healed-other",
                    "<yellow>Healed <aqua>%player%</aqua></yellow>",
                    Map.of("player", target.getName()));
        }

        return true;
    }

    private static double resolveMaxHealth(Player p) {
        Attribute attr = tryGetAttributeField("GENERIC_MAX_HEALTH");
        if (attr != null) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst != null) return inst.getValue();
        }

        attr = tryGetAttributeField("MAX_HEALTH");
        if (attr != null) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst != null) return inst.getValue();
        }

        try {
            Method m = p.getClass().getMethod("getMaxHealth");
            Object r = m.invoke(p);
            if (r instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}

        return 20.0;
    }

    private static Attribute tryGetAttributeField(String fieldName) {
        try {
            Field f = Attribute.class.getField(fieldName);
            Object v = f.get(null);
            if (v instanceof Attribute a) return a;
        } catch (Throwable ignored) {}
        return null;
    }
}