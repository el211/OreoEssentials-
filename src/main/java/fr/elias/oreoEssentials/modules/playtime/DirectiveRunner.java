package fr.elias.oreoEssentials.modules.playtime;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DirectiveRunner {
    private final OreoEssentials plugin;
    public DirectiveRunner(OreoEssentials plugin){ this.plugin = plugin; }

    public void run(Player player, String raw) {
        String line = raw.trim();
        boolean asPlayer = false;
        long delayTicks = 0L;

        if (line.startsWith("delay! ")) {
            String rest = line.substring(7);
            int sp = rest.indexOf(' ');
            if (sp > 0) {
                try {
                    delayTicks = (long) (Double.parseDouble(rest.substring(0, sp)) * 20d);
                } catch (NumberFormatException ignored) { }
                line = rest.substring(sp + 1).trim();
            }
        }
        if (line.startsWith("asPlayer!")) {
            asPlayer = true;
            line = line.substring("asPlayer!".length()).trim();
        }
        if (line.startsWith("asConsole!")) {
            asPlayer = false;
            line = line.substring("asConsole!".length()).trim();
        }

        final String commandLine = (line.startsWith("/")) ? line.substring(1) : line;
        final boolean runAsPlayer = asPlayer; // snapshot to be effectively final

        Runnable task = () -> {
            CommandSender sender = runAsPlayer ? player : Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(sender, commandLine.replace("[playerName]", player.getName()));
        };

        if (delayTicks > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}
