package fr.elias.oreoEssentials.modules.ic;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.regex.Pattern;

public final class ICListener implements Listener {
    private final ICManager mgr;
    private final Plugin plugin;

    public ICListener(ICManager mgr, Plugin plugin) {
        this.mgr = mgr;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        ICPos pos = ICPos.of(b);
        for (ICEntry ic : mgr.all()) {
            if (ic.blocks.contains(pos)) {
                run(ic, e.getPlayer(), pos);
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        Entity en = e.getRightClicked();
        for (ICEntry ic : mgr.all()) {
            if (ic.entities.contains(en.getUniqueId())) {
                run(ic, e.getPlayer(), null);
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        String l0 = e.getLine(0);
        if (l0 == null) return;
        if (!l0.toLowerCase(Locale.ROOT).startsWith("[ic:")) return;
        if (!e.getPlayer().hasPermission("oreo.ic.sign")) {
            e.getPlayer().sendMessage("§cYou need §foreo.ic.sign §cto create public IC signs.");
            return;
        }
        String name = l0.substring(4, l0.length() - 1);
        ICEntry ic = mgr.get(name);
        if (ic == null || !ic.isPublic) {
            e.getPlayer().sendMessage("§cNo public IC with name §f" + name);
            return;
        }
        Block b = e.getBlock();
        ic.blocks.add(ICPos.of(b));
        mgr.save();
        e.setLine(0, ChatColor.DARK_PURPLE + "[IC]");
        e.setLine(1, ChatColor.GOLD + name);
        e.getPlayer().sendMessage("§aPublic IC sign added for §f" + name + "§a.");
    }

    private void run(ICEntry ic, Player clicker, ICPos posOrNull) {
        List<String> signArgs = posOrNull == null ? Collections.emptyList() : ic.signArgsAt(posOrNull);
        for (String raw : ic.commands) {
            String line = substitute(raw, clicker.getName(), signArgs);
            boolean asConsole = takePrefix(line, "asconsole!");
            boolean asPlayer = takePrefix(line, "asplayer!");
            int delay = takeDelay(line);
            String finalCmd = stripDirectives(line).trim();
            Runnable task = () -> {
                if (finalCmd.isEmpty()) return;
                if (asPlayer) clicker.performCommand(finalCmd);
                else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            };
            if (delay > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, task, delay * 20L);
            } else {
                task.run();
            }
        }
    }

    private static String substitute(String s, String playerName, List<String> signArgs) {
        String out = s.replace("[playerName]", playerName);
        for (int i = 0; i < signArgs.size(); i++) out = out.replace("$" + (i + 1), signArgs.get(i));
        return out;
    }

    private static boolean takePrefix(String s, String prefix) {
        return s.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private static int takeDelay(String s) {
        var m = Pattern.compile("(?i)delay!\\s+(\\d+)").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String stripDirectives(String s) {
        return s.replaceFirst("(?i)^asconsole!\\s*", "")
                .replaceFirst("(?i)^asplayer!\\s*", "")
                .replaceAll("(?i)delay!\\s+\\d+\\s*", "");
    }
}