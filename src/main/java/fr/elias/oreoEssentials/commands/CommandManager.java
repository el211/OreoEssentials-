package fr.elias.oreoEssentials.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Registers {@link OreoCommand} instances using the CommandsAPI framework.
 *
 * Commands no longer need to be declared in plugin.yml — CommandsAPI injects
 * them directly into Bukkit's CommandMap at runtime. The existing OreoCommand
 * interface and all implementations remain untouched; {@link OreoCommandAdapter}
 * bridges between them.
 *
 * Tab completion is re-wired directly to the PluginCommand after registration
 * so that full String[] context (not just the current token) is available to
 * every OreoCommand.tabComplete() implementation.
 */
public class CommandManager {

    private final OreoEssentials plugin;
    private final fr.traqueur.commands.spigot.CommandManager<OreoEssentials> api;

    public CommandManager(OreoEssentials plugin) {
        this.plugin = plugin;
        this.api = new fr.traqueur.commands.spigot.CommandManager<>(plugin);
    }

    public CommandManager register(OreoCommand cmd) {
        api.registerCommand(new OreoCommandAdapter(plugin, cmd));

        rewireTabCompleter(cmd.name(), cmd);
        for (String alias : cmd.aliases()) {
            rewireTabCompleter(alias, cmd);
        }

        return this;
    }

    public CommandManager registerIf(boolean enabled, OreoCommand cmd) {
        if (enabled) register(cmd);
        return this;
    }

    /**
     * Registers a raw Bukkit {@link CommandExecutor} (and optional {@link TabCompleter})
     * directly into the CommandMap. Use this for commands that don't implement
     * {@link OreoCommand} and therefore can't go through {@link #register(OreoCommand)}.
     * No plugin.yml entry is needed.
     */
    public CommandManager registerLegacy(String name, CommandExecutor exec, TabCompleter tab) {
        CommandMap map = getCommandMap();
        if (map == null) {
            plugin.getLogger().warning("[Commands] CommandMap unavailable — cannot register '" + name + "'");
            return this;
        }
        Command cmd = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return exec.onCommand(sender, this, label, args);
            }
            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (tab == null) return Collections.emptyList();
                List<String> result = tab.onTabComplete(sender, this, alias, args);
                return result != null ? result : Collections.emptyList();
            }
        };
        map.register(plugin.getName().toLowerCase(Locale.ROOT), cmd);
        return this;
    }

    public CommandManager registerLegacy(String name, TabExecutor exec) {
        return registerLegacy(name, exec, exec);
    }

    public CommandManager registerLegacy(String name, CommandExecutor exec) {
        return registerLegacy(name, exec, null);
    }

    /**
     * Overwrites the tab completer on an already-registered command (registered
     * via {@link #register} or {@link #registerLegacy}) without changing its executor.
     * Useful when a dedicated TabCompleter object is richer than the command's own
     * tabComplete() implementation.
     */
    public CommandManager rewireTab(String name, TabCompleter tab) {
        try {
            CommandMap map = getCommandMap();
            if (map == null) return this;
            Command bc = map.getCommand(name.toLowerCase(Locale.ROOT));
            if (bc instanceof PluginCommand pc) {
                pc.setTabCompleter(tab);
            } else if (bc != null) {
                // Plain Command subclass (registered via registerLegacy) — nothing to rewire;
                // its tabComplete() already delegates via the Command.tabComplete override we set.
                // If a richer completer is needed, re-register with registerLegacy(name, exec, tab).
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Commands] rewireTab failed for '" + name + "': " + t.getMessage());
        }
        return this;
    }

    // -------------------------------------------------------------------------

    private void rewireTabCompleter(String label, OreoCommand cmd) {
        try {
            CommandMap map = getCommandMap();
            if (map == null) return;

            Command bc = map.getCommand(label.toLowerCase(Locale.ROOT));
            if (!(bc instanceof PluginCommand pc)) return;

            if (cmd instanceof org.bukkit.command.TabCompleter tc) {
                // Command implements Bukkit's TabCompleter directly (e.g. OeCommand)
                // DC7: wrap to enforce permission check before returning completions
                pc.setTabCompleter((sender, command, alias, args) -> {
                    String perm = cmd.permission();
                    if (perm != null && !perm.isBlank() && !sender.hasPermission(perm)) {
                        return Collections.emptyList();
                    }
                    try { return tc.onTabComplete(sender, command, alias, args); }
                    catch (Throwable t) { return Collections.emptyList(); }
                });
            } else {
                // Delegate to OreoCommand.tabComplete() which receives full args[]
                pc.setTabCompleter((sender, command, alias, args) -> {
                    // DC7: suppress completions for players lacking the command permission
                    String perm = cmd.permission();
                    if (perm != null && !perm.isBlank() && !sender.hasPermission(perm)) {
                        return Collections.emptyList();
                    }
                    try { return cmd.tabComplete(sender, alias, args); }
                    catch (Throwable t) { return Collections.emptyList(); }
                });
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Commands] Tab-completer rewire failed for '" + label + "': " + t.getMessage());
        }
    }

    private CommandMap getCommandMap() {
        // Paper 1.21 exposes getCommandMap() as a public method
        try {
            var m = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) m.invoke(Bukkit.getServer());
        } catch (Throwable ignored) {}
        // Fallback: reflection on the private field (CraftServer)
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Throwable ignored) {}
        return null;
    }
}
