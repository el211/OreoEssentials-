package fr.elias.oreoEssentials.modules.playtime;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class PrewardsCommand implements CommandExecutor, TabCompleter {
    private final OreoEssentials plugin;
    private final PlaytimeRewardsService svc;

    public PrewardsCommand(OreoEssentials plugin, PlaytimeRewardsService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /prewards
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!svc.isEnabled()) {
                sender.sendMessage(svc.color("&cPlaytime Rewards are currently disabled."));
                if (sender.hasPermission("oreo.prewards.admin")) {
                    sender.sendMessage(svc.color("&7Use &f/" + label + " toggle &7to enable it."));
                }
                return true;
            }
            Player p = (Player) sender;
            new PrewardsMenu(svc).inventory(p).open(p);
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("oreo.prewards.admin")) {
                sender.sendMessage("No permission.");
                return true;
            }
            svc.loadConfig(); // start/stop internals based on settings.enable
            sender.sendMessage(svc.color("&aPlaytime rewards reloaded."));
            return true;
        }

        if ("toggle".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("oreo.prewards.admin")) {
                sender.sendMessage("No permission.");
                return true;
            }
            boolean now = svc.toggleEnabled();
            sender.sendMessage(svc.color("&7Playtime Rewards is now " + (now ? "&aENABLED" : "&cDISABLED")));
            return true;
        }

        if ("claim".equalsIgnoreCase(args[0])) {
            if (!svc.isEnabled()) {
                sender.sendMessage(svc.color("&cPlaytime Rewards are disabled."));
                if (sender.hasPermission("oreo.prewards.admin")) {
                    sender.sendMessage(svc.color("&7Use &f/" + label + " toggle &7to enable it."));
                }
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("/" + label + " claim <id> [player]");
                return true;
            }
            String id = args[1];

            if (args.length >= 3) {
                if (!sender.hasPermission("oreo.prewards.claim.others")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }
                boolean ok = svc.claim(target, id, true);
                sender.sendMessage(ok ? "Claimed for " + target.getName() : "Not ready / invalid id");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            boolean ok = svc.claim((Player) sender, id, true);
            sender.sendMessage(ok ? "Claimed." : "Not ready / invalid id");
            return true;
        }

        sender.sendMessage("/" + label + " [claim <id> [player]] | /" + label + " reload | /" + label + " toggle");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            String pref = a[0].toLowerCase(Locale.ROOT);
            List<String> base = new ArrayList<>(Arrays.asList("claim", "reload"));
            if (s.hasPermission("oreo.prewards.admin")) base.add("toggle"); // NEW
            return base.stream().filter(x -> x.startsWith(pref)).collect(Collectors.toList());
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("claim")) {
            String pref = a[1].toLowerCase(Locale.ROOT);
            return new ArrayList<>(svc.rewards.keySet()).stream()
                    .filter(x -> x.toLowerCase(Locale.ROOT).startsWith(pref))
                    .collect(Collectors.toList());
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("claim")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
