package fr.elias.oreoEssentials.modules.aliases;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.*;

public final class DynamicAliasRegistry {
    private DynamicAliasRegistry() {}

    private static final List<Command> REGISTERED = new ArrayList<>();


    private static final class InternalDynamicCommand extends Command implements PluginIdentifiableCommand {
        private final Plugin plugin;

        @FunctionalInterface
        interface Runner {
            boolean run(CommandSender sender, String label, String[] args);
        }

        private final Runner runner;
        private final TabCompleter tab;

        InternalDynamicCommand(Plugin plugin, String name, String desc, Runner runner, TabCompleter tab) {
            super(name);
            this.plugin = plugin;
            this.runner = runner;
            this.tab = tab;
            setDescription(desc != null ? desc : "Oreo alias");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return runner != null && runner.run(sender, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (tab != null) {
                try {
                    List<String> out = tab.onTabComplete(sender, this, alias, args);
                    return (out != null) ? out : Collections.emptyList();
                } catch (Throwable ignored) {}
            }
            return Collections.emptyList();
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }


    public static void register(Plugin plugin, String name, DynamicAliasExecutor exec, String desc) {
        register(plugin, name, exec, desc, null);
    }

    public static void register(Plugin plugin, String name, DynamicAliasExecutor exec, String desc, TabCompleter tab) {
        // DynamicAliasExecutor IS a CommandExecutor, so just delegate to the generic overload.
        register(plugin, name, (CommandExecutor) exec, desc, tab);
    }

    public static void register(Plugin plugin, String name, CommandExecutor executor, String desc, TabCompleter tab) {
        if (plugin == null || name == null || executor == null) return;
        CommandMap map = getCommandMap();
        if (map == null) return;

        Map<String, Command> known = getKnownCommands(map);
        if (known != null && known.containsKey(name.toLowerCase(Locale.ROOT))) {
            plugin.getLogger().warning("[Aliases] Command '" + name + "' already exists; skipping.");
            return;
        }

        InternalDynamicCommand.Runner runner = (sender, label, args) ->
                executor.onCommand(sender, new PluginCommandShim(name, plugin), label, args);

        InternalDynamicCommand cmd = new InternalDynamicCommand(plugin, name, desc, runner, tab);
        map.register(plugin.getName(), cmd);
        REGISTERED.add(cmd);
    }

    public static void unregisterAll(Plugin plugin) {
        CommandMap map = getCommandMap();
        if (map == null) return;

        Map<String, Command> known = getKnownCommands(map);

        for (Command c : REGISTERED) {
            try {
                c.unregister(map);

                if (known != null) {
                    String base = c.getName().toLowerCase(Locale.ROOT);
                    String ns   = plugin.getName().toLowerCase(Locale.ROOT) + ":" + base;
                    known.entrySet().removeIf(e -> {
                        String k = e.getKey();
                        Command v = e.getValue();
                        return v == c || k.equals(base) || k.equals(ns);
                    });
                }
            } catch (Throwable ignored) {}
        }
        REGISTERED.clear();
    }



    private static CommandMap getCommandMap() {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Aliases] Failed to access CommandMap: " + t.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> getKnownCommands(CommandMap map) {
        try {
            Field fKnown = SimpleCommandMap.class.getDeclaredField("knownCommands");
            fKnown.setAccessible(true);
            return (Map<String, Command>) fKnown.get(map);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Aliases] Failed to access knownCommands: " + t.getMessage());
            return Collections.emptyMap();
        }
    }


    private static final class PluginCommandShim extends Command implements PluginIdentifiableCommand {
        private final Plugin plugin;

        PluginCommandShim(String name, Plugin owning) {
            super(name);
            this.plugin = owning;
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return false;
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }
}
