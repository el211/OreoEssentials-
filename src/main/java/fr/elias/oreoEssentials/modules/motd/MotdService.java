package fr.elias.oreoEssentials.modules.motd;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Handles both the join listener and the /motd command (for viewing/reloading).
 */
public class MotdService implements Listener, OreoCommand, TabCompleter {

    private final OreoEssentials plugin;
    private final MotdConfig     config;

    public MotdService(OreoEssentials plugin, MotdConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // -----------------------------------------------------------------------
    // Join listener
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!config.isEnabled()) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("oreo.motd")) return;

        // First-join override
        if (!p.hasPlayedBefore() && config.isFirstJoinEnabled()) {
            sendDelayed(p, config.firstJoinLines());
            return;
        }

        // Permission-based group override
        for (MotdConfig.MotdGroup group : config.groups()) {
            if (p.hasPermission(group.permission())) {
                sendDelayed(p, group.lines());
                return;
            }
        }

        // Default
        sendDelayed(p, config.defaultLines());
    }

    private void sendDelayed(Player p, List<String> lines) {
        long delay = Math.max(1, config.delayTicks());
        OreScheduler.runLaterForEntity(plugin, p, () -> {
            if (!p.isOnline()) return;
            for (String line : lines) {
                p.sendMessage(apply(p, color(line)));
            }
        }, delay);
    }

    // -----------------------------------------------------------------------
    // /motd command — lets players see the MOTD again, or reload (admin)
    // -----------------------------------------------------------------------

    @Override public String       name()       { return "motd"; }
    @Override public List<String> aliases()    { return List.of(); }
    @Override public String       permission() { return "oreo.motd"; }
    @Override public String       usage()      { return "[reload]"; }
    @Override public boolean      playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("oreo.motd.admin")) {
                sender.sendMessage(color("&cNo permission."));
                return true;
            }
            config.load();
            sender.sendMessage(color("&aMotd config reloaded."));
            return true;
        }

        if (!sender.hasPermission("oreo.motd")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }

        // Show the MOTD relevant to this sender (default for console)
        List<String> lines = config.defaultLines();
        if (sender instanceof Player p) {
            for (MotdConfig.MotdGroup group : config.groups()) {
                if (p.hasPermission(group.permission())) { lines = group.lines(); break; }
            }
        }

        String senderName = sender instanceof Player pp ? pp.getName() : "Console";
        for (String line : lines) {
            sender.sendMessage(apply(senderName, color(line)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("oreo.motd.admin")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }

    // -----------------------------------------------------------------------

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String apply(Player p, String s) {
        return apply(p.getName(), s);
    }

    private static String apply(String name, String s) {
        return s.replace("%player%", name)
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }
}
