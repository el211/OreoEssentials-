package fr.elias.oreoEssentials.modules.tp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class TphereCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final OreoEssentials plugin;

    public TphereCommand(OreoEssentials plugin) { this.plugin = plugin; }

    @Override public String name() { return "tphere"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tphere"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;
        if (args.length < 1) {
            Lang.send(self, "admin.tphere.usage", "<yellow>Usage: /%label% <player></yellow>", Map.of("label", label));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(self, "admin.tphere.not-found", "<red>Player not found: <yellow>%target%</yellow>.</red>", Map.of("target", args[0]));
            return true;
        }

        OreScheduler.runForEntity(plugin, self, () -> {
            if (!self.isOnline()) return;
            final Location destination = self.getLocation().clone();
            final String selfName = self.getName();
            final String targetName = target.getName();

            OreScheduler.runForEntity(plugin, target, () -> {
                if (!target.isOnline()) return;
                if (OreScheduler.isFolia()) {
                    target.teleportAsync(destination).whenComplete((ok, err) -> {
                        OreScheduler.runForEntity(plugin, self, () -> {
                            if (err == null && Boolean.TRUE.equals(ok)) {
                                Lang.send(self, "admin.tphere.brought", "<green>Brought <aqua>%target%</aqua> to you.</green>", Map.of("target", targetName));
                            } else {
                                Lang.send(self, "admin.tphere.failed", "<red>Teleport failed.</red>");
                            }
                        });
                        if (!target.equals(self) && err == null && Boolean.TRUE.equals(ok)) {
                            OreScheduler.runForEntity(plugin, target, () -> Lang.send(target, "admin.tphere.notice", "<yellow>You were teleported to <aqua>%player%</aqua>.</yellow>", Map.of("player", selfName)));
                        }
                    });
                } else {
                    boolean ok = target.teleport(destination);
                    if (ok) {
                        Lang.send(self, "admin.tphere.brought", "<green>Brought <aqua>%target%</aqua> to you.</green>", Map.of("target", targetName));
                        if (!target.equals(self)) Lang.send(target, "admin.tphere.notice", "<yellow>You were teleported to <aqua>%player%</aqua>.</yellow>", Map.of("player", selfName));
                    } else {
                        Lang.send(self, "admin.tphere.failed", "<red>Teleport failed.</red>");
                    }
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String want = args[0].toLowerCase(Locale.ROOT);
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) out.add(n);
        }
        // Deliberately do not query PlayerDirectory/MongoDB from tab completion.
        return out.stream().limit(50).collect(Collectors.toList());
    }
}
