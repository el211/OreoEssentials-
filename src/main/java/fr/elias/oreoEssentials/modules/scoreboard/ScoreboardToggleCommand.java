package fr.elias.oreoEssentials.modules.scoreboard;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class ScoreboardToggleCommand implements OreoCommand {
    private final ScoreboardService service;

    public ScoreboardToggleCommand(ScoreboardService service) {
        this.service = service;
    }

    @Override public String name() { return "scoreboard"; }
    @Override public List<String> aliases() { return List.of("sb"); }
    @Override public String permission() { return "oreo.scoreboard.toggle"; }
    @Override public String usage() { return "toggle"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        service.toggle(p);
        p.sendMessage(service.isShown(p)
                ? ChatColor.GREEN + "Scoreboard enabled."
                : ChatColor.YELLOW + "Scoreboard disabled.");
        return true;
    }
}
