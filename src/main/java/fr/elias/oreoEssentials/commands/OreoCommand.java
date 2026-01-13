package fr.elias.oreoEssentials.commands;


import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public interface OreoCommand {
    String name();
    List<String> aliases();
    String permission();
    String usage();
    boolean playerOnly();

    boolean execute(CommandSender sender, String label, String[] args);
    default List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }
}

