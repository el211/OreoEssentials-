package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;


public final class DailyCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private final DailyService svc;
    private final RewardsConfig rewards;

    public DailyCommand(OreoEssentials plugin, DailyConfig cfg, DailyService svc, RewardsConfig rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.svc = svc;
        this.rewards = rewards;
    }

    private void open(Player p) {
        SmartInventory inv = new DailyMenu(plugin, cfg, svc, rewards).inventory(p);
        inv.open(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            if (!sender.hasPermission("oreo.daily.admin")) {
                Lang.send(sender, "daily.no-permission-toggle",
                        "<red>You don't have permission to toggle this feature.</red>");
                return true;
            }

            boolean now = svc.toggleEnabled();
            cfg.setEnabled(now);
            try { cfg.save(); } catch (Throwable ignored) {}

            Lang.send(sender, "daily.toggled",
                    "<yellow>Daily Rewards is now %state%</yellow>",
                    Map.of("state", now ? "<green>ENABLED</green>" : "<red>DISABLED</red>"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            showTop(sender);
            return true;
        }

        if (!(sender instanceof Player p)) {
            Lang.send(sender, "daily.players-only",
                    "<red>This command must be run in-game.</red>");
            return true;
        }

        if (!p.hasPermission("oreo.daily")) {
            Lang.send(p, "daily.no-permission",
                    "<red>You don't have permission to use this.</red>");
            return true;
        }

        if (!svc.isEnabled()) {
            Lang.send(p, "daily.disabled",
                    "<red>Daily Rewards is currently disabled.</red>");

            if (p.hasPermission("oreo.daily.admin")) {
                Lang.send(p, "daily.disabled-hint",
                        "<gray>Use <white>/%command% toggle</white> to enable it.</gray>",
                        Map.of("command", label));
            }
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("claim")) {
            open(p);
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        boolean admin = sender.hasPermission("oreo.daily.admin");

        if (admin) {
            Lang.send(sender, "daily.usage-admin",
                    "<gray>Usage: <white>/%command%</white> or <white>/%command% claim</white> or <white>/%command% top</white> or <white>/%command% toggle</white></gray>",
                    Map.of("command", label));
        } else {
            Lang.send(sender, "daily.usage",
                    "<gray>Usage: <white>/%command%</white> or <white>/%command% claim</white> or <white>/%command% top</white></gray>",
                    Map.of("command", label));
        }
    }

    private void showTop(CommandSender viewer) {
        record Row(String name, int streak, double hours) {}
        List<Row> rows = new ArrayList<>();

        for (Player op : Bukkit.getOnlinePlayers()) {
            double hrs = Math.max(0, op.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0);
            rows.add(new Row(op.getName(), svc.getStreak(op.getUniqueId()), hrs));
        }

        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            UUID id = off.getUniqueId();
            if (id == null) continue;

            String name = (off.getName() != null) ? off.getName() : id.toString();

            boolean dup = rows.stream().anyMatch(r -> r.name.equalsIgnoreCase(name));
            if (dup) continue;

            rows.add(new Row(name, svc.getStreak(id), 0.0));
        }

        rows.sort(Comparator.comparingInt(Row::streak).reversed()
                .thenComparingDouble(Row::hours).reversed());

        Player p = viewer instanceof Player ? (Player) viewer : null;

        Lang.sendRaw(viewer, Lang.color("&8&m-------------------------"));
        Lang.sendRaw(viewer, Lang.msgLegacy("daily.top.title", "&b&lDaily &fTop Streaks", p));

        int i = 1;
        List<Row> top = rows.stream().limit(10).collect(Collectors.toList());

        for (Row r : top) {
            Lang.sendRaw(viewer, Lang.msgLegacy("daily.top.row",
                    "&7#&f" + i + " &b%player% &8» &aStreak: &f%streak% &8(&7%hours%h&8)",
                    Map.of(
                            "rank", String.valueOf(i),
                            "player", r.name(),
                            "streak", String.valueOf(r.streak()),
                            "hours", String.format(Locale.US, "%.1f", r.hours())
                    ),
                    p));
            i++;
        }

        if (top.isEmpty()) {
            Lang.sendRaw(viewer, Lang.msgLegacy("daily.top.empty", "&7No data yet.", p));
        }

        Lang.sendRaw(viewer, Lang.color("&8&m-------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            List<String> base = new ArrayList<>(Arrays.asList("claim", "top"));
            if (sender.hasPermission("oreo.daily.admin")) base.add("toggle");
            return base.stream().filter(s -> s.startsWith(pfx)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}