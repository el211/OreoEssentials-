package fr.elias.oreoEssentials.modules.commandtoggle;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.*;

public class CommandToggleService {
    private final JavaPlugin plugin;
    private final CommandToggleConfig config;
    private final Map<String, Command> unregisteredCommands = new HashMap<>();

    public CommandToggleService(JavaPlugin plugin, CommandToggleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    public void applyToggles() {
        try {
            CommandMap commandMap = getCommandMap();
            if (commandMap == null) {
                plugin.getLogger().severe("[CommandToggle] Could not access CommandMap!");
                return;
            }

            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands == null) {
                plugin.getLogger().severe("[CommandToggle] Could not access knownCommands!");
                return;
            }

            int disabled = 0;
            int enabled = 0;

            for (Map.Entry<String, CommandToggleConfig.CommandToggleEntry> entry : config.getAllCommands().entrySet()) {
                String cmdName = entry.getKey();
                CommandToggleConfig.CommandToggleEntry toggleEntry = entry.getValue();

                if (!toggleEntry.isEnabled()) {
                    if (unregisterCommand(cmdName, knownCommands)) {
                        disabled++;
                    }
                } else {
                    if (reregisterCommand(cmdName, commandMap, knownCommands)) {
                        enabled++;
                    }
                }
            }

            plugin.getLogger().info("[CommandToggle] Applied toggles: " + disabled + " disabled, " + enabled + " enabled");

        } catch (Exception e) {
            plugin.getLogger().severe("[CommandToggle] Error applying toggles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean unregisterCommand(String commandName, Map<String, Command> knownCommands) {
        String lower = commandName.toLowerCase(Locale.ROOT);

        List<String> toRemove = new ArrayList<>();

        for (String key : knownCommands.keySet()) {
            String keyLower = key.toLowerCase(Locale.ROOT);

            if (keyLower.equals(lower) || keyLower.endsWith(":" + lower)) {
                Command cmd = knownCommands.get(key);

                if (cmd instanceof PluginCommand) {
                    PluginCommand pc = (PluginCommand) cmd;
                    try {
                        if (pc.getPlugin() == plugin) {
                            toRemove.add(key);
                            if (!unregisteredCommands.containsKey(lower)) {
                                unregisteredCommands.put(lower, cmd);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        for (String key : toRemove) {
            knownCommands.remove(key);
        }

        return !toRemove.isEmpty();
    }

    private boolean reregisterCommand(String commandName, CommandMap commandMap, Map<String, Command> knownCommands) {
        String lower = commandName.toLowerCase(Locale.ROOT);

        Command cmd = unregisteredCommands.get(lower);
        if (cmd == null) {
            return false;
        }

        try {
            commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), cmd);
            unregisteredCommands.remove(lower);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[CommandToggle] Failed to re-register command '" + commandName + "': " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private CommandMap getCommandMap() {
        try {
            Object craftServer = Bukkit.getServer();

            try {
                var method = craftServer.getClass().getMethod("getCommandMap");
                Object result = method.invoke(craftServer);
                if (result instanceof CommandMap) {
                    return (CommandMap) result;
                }
            } catch (NoSuchMethodException ignored) {}

            Field field = craftServer.getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            Object result = field.get(craftServer);

            if (result instanceof CommandMap) {
                return (CommandMap) result;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[CommandToggle] Failed to get CommandMap: " + e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap commandMap) {
        try {
            if (!(commandMap instanceof SimpleCommandMap)) {
                return null;
            }

            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            Object result = field.get(commandMap);

            if (result instanceof Map) {
                return (Map<String, Command>) result;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[CommandToggle] Failed to get knownCommands: " + e.getMessage());
        }

        return null;
    }

    /**
     * Reload the configuration and reapply toggles
     */
    public void reload() {
        config.reload();
        applyToggles();
    }
}