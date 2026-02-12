package fr.elias.oreoEssentials.modules.ic;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.stream.Collectors;

public final class ICCommand implements TabExecutor {
    private final ICManager mgr;

    public ICCommand(ICManager mgr) { this.mgr = mgr; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("oreo.ic")) { s.sendMessage("§cNo permission (oreo.ic)."); return true; }
        if (a.length == 0) {
            s.sendMessage("§e/ic new <name>  §7| create");
            s.sendMessage("§e/ic addblock <name>  §7| bind looked-at block");
            s.sendMessage("§e/ic addentity <name> §7| bind looked-at entity");
            s.sendMessage("§e/ic addcmd <name> <command...>  §7| add command (no leading /)");
            s.sendMessage("§e/ic public <name> <true|false> §7| toggle public sign mode");
            s.sendMessage("§e/ic list §7| list all");
            s.sendMessage("§e/ic info <name>");
            s.sendMessage("§8Directives: asConsole! | asPlayer! | delay! <sec>  | placeholders: [playerName], $1..$n from sign");
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("new") && a.length >= 2) {
            String name = a[1];
            if (mgr.get(name) != null) { s.sendMessage("§cIC exists."); return true; }
            mgr.create(name);
            s.sendMessage("§aCreated IC §f" + name + "§a. Use §e/ic addblock " + name + "§a or §e/ic addentity " + name);
            return true;
        }
        if (sub.equals("reload")) {
            mgr.reload();
            s.sendMessage("§aInteractive Commands reloaded from file.");
            return true;
        }

        if (sub.equals("addblock") && a.length >= 2) {
            if (!(s instanceof Player)) { s.sendMessage("§cPlayer-only."); return true; }
            Player p = (Player) s;
            Block b = p.getTargetBlockExact(7);
            if (b == null || b.getType() == Material.AIR) { s.sendMessage("§cLook at a block within 7 blocks."); return true; }
            ICEntry e = require(mgr, s, a[1]); if (e == null) return true;
            e.blocks.add(ICPos.of(b)); mgr.save();
            s.sendMessage("§aAdded block §f" + b.getType() + "§7 @ §f" + b.getLocation().getBlockX()+","+b.getLocation().getBlockY()+","+b.getLocation().getBlockZ());
            return true;
        }

        if (sub.equals("addentity") && a.length >= 2) {
            if (!(s instanceof Player)) { s.sendMessage("§cPlayer-only."); return true; }
            Player p = (Player) s;

            Entity eHit = getLookedAtEntity(p, 7.0);
            if (eHit == null) { s.sendMessage("§cLook at an entity within ~7 blocks."); return true; }

            ICEntry e = require(mgr, s, a[1]); if (e == null) return true;
            e.entities.add(eHit.getUniqueId()); mgr.save();
            s.sendMessage("§aAdded entity §f" + eHit.getType() + "§7 (#" + eHit.getUniqueId().toString().substring(0,8) + ")");
            return true;
        }

        if (sub.equals("addcmd") && a.length >= 3) {
            String name = a[1];
            ICEntry e = require(mgr, s, name); if (e == null) return true;
            String cmdLine = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
            e.commands.add(cmdLine); mgr.save();
            s.sendMessage("§aAdded: §f" + cmdLine);
            return true;
        }

        if (sub.equals("public") && a.length >= 3) {
            ICEntry e = require(mgr, s, a[1]); if (e == null) return true;
            e.isPublic = Boolean.parseBoolean(a[2]); mgr.save();
            s.sendMessage("§aPublic set to §f" + e.isPublic + "§7. Players with §6cmi.interactivesign §7can place signs like §f[ic:"+e.name+"]");
            return true;
        }

        if (sub.equals("list")) {
            s.sendMessage("§eInteractive Commands:");
            for (ICEntry e : mgr.all())
                s.sendMessage(" - §f" + e.name + " §8("+e.blocks.size()+" blocks, "+e.entities.size()+" entities, "+e.commands.size()+" cmds)"
                        + (e.isPublic ? ChatColor.DARK_GRAY+" public" : ""));
            return true;
        }

        if (sub.equals("info") && a.length >= 2) {
            ICEntry e = require(mgr, s, a[1]); if (e == null) return true;
            s.sendMessage("§eIC §f"+e.name+"§e — public:"+e.isPublic);
            s.sendMessage("§7Blocks: §f"+e.blocks.size()+" §7Entities: §f"+e.entities.size());
            for (int i=0;i<e.commands.size();i++) s.sendMessage("§8"+(i+1)+") §7"+e.commands.get(i));
            return true;
        }

        s.sendMessage("§cUnknown subcommand. Try §e/ic");
        return true;
    }

    private static ICEntry require(ICManager mgr, CommandSender s, String name) {
        ICEntry e = mgr.get(name);
        if (e == null) s.sendMessage("§cIC not found: §f" + name);
        return e;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if (!s.hasPermission("oreo.ic")) return Collections.emptyList();
        if (a.length == 1) return starts(Arrays.asList("new","addblock","addentity","addcmd","public","list","info"), a[0]);
        if (a.length == 2 && !a[0].equalsIgnoreCase("new")) return starts(mgr.all().stream().map(e->e.name).collect(Collectors.toList()), a[1]);
        if (a.length == 3 && a[0].equalsIgnoreCase("public")) return starts(Arrays.asList("true","false"), a[2]);
        if (a.length == 1) return starts(Arrays.asList(
                "new","addblock","addentity","addcmd","public","list","info","reload"
        ), a[0]);
        return Collections.emptyList();
    }

    private static List<String> starts(List<String> base, String pref) {
        String p = pref==null ? "" : pref.toLowerCase(Locale.ROOT);
        return base.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    /** Spigot-compatible “look at entity” using ray tracing (1.13+). */
    private static Entity getLookedAtEntity(Player p, double maxDistance) {
        RayTraceResult rt = p.getWorld().rayTraceEntities(
                p.getEyeLocation(),
                p.getLocation().getDirection(),
                maxDistance,
                0.3, // thickness to make aiming friendlier
                e -> !e.equals(p) // ignore self
        );
        return rt != null ? rt.getHitEntity() : null;
    }
}
