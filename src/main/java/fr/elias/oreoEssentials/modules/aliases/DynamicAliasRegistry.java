package fr.elias.oreoEssentials.modules.aliases;

import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Runtime registration for commandsmodule/aliases.yml aliases. */
public final class DynamicAliasRegistry {
    private DynamicAliasRegistry() {}

    /** Only commands created by this class are tracked here. */
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
            if (tab == null) return Collections.emptyList();
            try {
                List<String> out = tab.onTabComplete(sender, this, alias, args);
                return out != null ? out : Collections.emptyList();
            } catch (Throwable t) {
                return Collections.emptyList();
            }
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }

    public static boolean register(Plugin plugin, String name, DynamicAliasExecutor exec, String desc) {
        return register(plugin, name, exec, desc, null);
    }

    public static boolean register(
            Plugin plugin,
            String name,
            DynamicAliasExecutor exec,
            String desc,
            TabCompleter tab
    ) {
        return register(plugin, name, (CommandExecutor) exec, desc, tab);
    }

    /**
     * Registers one runtime command.
     *
     * @return true only when the plain /name label was actually registered.
     */
    public static boolean register(
            Plugin plugin,
            String name,
            CommandExecutor executor,
            String desc,
            TabCompleter tab
    ) {
        if (plugin == null || executor == null) return false;

        String normalized = normalizeName(name);
        if (normalized == null) {
            plugin.getLogger().warning("[Aliases] Invalid/empty alias command name: '" + name + "'.");
            return false;
        }

        CommandMap map = getCommandMap();
        if (map == null) {
            plugin.getLogger().warning("[Aliases] CommandMap unavailable; cannot register /" + normalized + ".");
            return false;
        }

        Map<String, Command> known = getKnownCommands(map);
        if (known == null) {
            plugin.getLogger().warning("[Aliases] knownCommands unavailable; cannot safely register /" + normalized + ".");
            return false;
        }

        Command existing = known.get(normalized);
        if (existing != null) {
            plugin.getLogger().warning(
                    "[Aliases] Command '/" + normalized + "' already exists (" + existing.getName() + "); skipping custom alias."
            );
            return false;
        }

        InternalDynamicCommand.Runner runner = (sender, label, args) ->
                executor.onCommand(sender, new PluginCommandShim(normalized, plugin), label, args);

        InternalDynamicCommand cmd = new InternalDynamicCommand(plugin, normalized, desc, runner, tab);
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);

        try {
            map.register(fallbackPrefix, cmd);
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "[Aliases] Failed to register /" + normalized + ": " + t.getMessage()
            );
            removeBindingsFor(known, cmd);
            return false;
        }

        // CommandMap#register may fall back to only a namespaced label on collision.
        // For aliases.yml we promise /name, so count it as success only if that label is ours.
        if (known.get(normalized) != cmd) {
            plugin.getLogger().warning(
                    "[Aliases] /" + normalized + " could not claim its plain command label; registration rolled back."
            );
            try { cmd.unregister(map); } catch (Throwable ignored) {}
            removeBindingsFor(known, cmd);
            return false;
        }

        REGISTERED.add(cmd);
        return true;
    }

    /**
     * Unregisters only commands created by this registry.
     * Never removes a key merely because its text matches the alias name; another
     * command may have taken that key since registration.
     */
    public static void unregisterAll(Plugin plugin) {
        CommandMap map = getCommandMap();
        if (map == null) {
            REGISTERED.clear();
            return;
        }

        Map<String, Command> known = getKnownCommands(map);

        for (Command command : new ArrayList<>(REGISTERED)) {
            try {
                command.unregister(map);
            } catch (Throwable t) {
                if (plugin != null) {
                    plugin.getLogger().fine(
                            "[Aliases] Command.unregister failed for /" + command.getName() + ": " + t.getMessage()
                    );
                }
            }

            if (known != null) {
                removeBindingsFor(known, command);
            }
        }

        REGISTERED.clear();
    }

    public static boolean isManagedCommand(Command command) {
        return command != null && REGISTERED.contains(command);
    }

    /** Rebuilds/resends the Brigadier command tree after a runtime registration batch. */
    public static void syncCommands(Plugin plugin) {
        if (plugin == null) return;
        try {
            OreScheduler.run(plugin, () -> syncCommandsNow(plugin));
        } catch (Throwable t) {
            syncCommandsNow(plugin);
        }
    }

    private static void syncCommandsNow(Plugin plugin) {
        Object server = Bukkit.getServer();

        try {
            Method method;
            try {
                method = server.getClass().getMethod("syncCommands");
            } catch (NoSuchMethodException ex) {
                method = server.getClass().getDeclaredMethod("syncCommands");
                method.setAccessible(true);
            }
            method.invoke(server);
            return;
        } catch (Throwable ignored) {
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                OreScheduler.runForEntity(plugin, player, player::updateCommands);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void removeBindingsFor(Map<String, Command> known, Command command) {
        known.entrySet().removeIf(entry -> entry.getValue() == command);
    }

    private static String normalizeName(String name) {
        if (name == null) return null;
        String s = name.trim().toLowerCase(Locale.ROOT);
        while (s.startsWith("/")) s = s.substring(1);
        if (s.isEmpty() || s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0 || s.indexOf(':') >= 0) return null;
        return s;
    }

    private static CommandMap getCommandMap() {
        Object server = Bukkit.getServer();

        // Paper 1.21+ supported accessor.
        try {
            Method method = server.getClass().getMethod("getCommandMap");
            Object result = method.invoke(server);
            if (result instanceof CommandMap map) return map;
        } catch (Throwable ignored) {
        }

        // CraftBukkit/Spigot fallback. Walk superclasses rather than assuming the
        // field is declared directly by the concrete server implementation.
        Class<?> type = server.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("commandMap");
                field.setAccessible(true);
                Object result = field.get(server);
                if (result instanceof CommandMap map) return map;
                break;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[Aliases] Failed to access CommandMap: " + t.getMessage());
                return null;
            }
        }

        Bukkit.getLogger().warning("[Aliases] Failed to access CommandMap.");
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> getKnownCommands(CommandMap map) {
        try {
            if (map instanceof SimpleCommandMap) {
                Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
                field.setAccessible(true);
                Object result = field.get(map);
                if (result instanceof Map<?, ?>) {
                    return (Map<String, Command>) result;
                }
            }

            Class<?> type = map.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField("knownCommands");
                    field.setAccessible(true);
                    Object result = field.get(map);
                    if (result instanceof Map<?, ?>) {
                        return (Map<String, Command>) result;
                    }
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Aliases] Failed to access knownCommands: " + t.getMessage());
        }

        return null;
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
