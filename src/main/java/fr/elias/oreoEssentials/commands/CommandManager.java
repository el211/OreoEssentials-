package fr.elias.oreoEssentials.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class CommandManager {
    private final OreoEssentials plugin;

    private final Map<String, OreoCommand> byName = new HashMap<>();

    public CommandManager(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public CommandManager register(OreoCommand cmd) {
        byName.put(cmd.name().toLowerCase(Locale.ROOT), cmd);
        for (String a : cmd.aliases()) {
            byName.put(a.toLowerCase(Locale.ROOT), cmd);
        }

        ensureBukkitCommand(cmd.name(), cmd);
        for (String a : cmd.aliases()) {
            ensureBukkitCommand(a, cmd);
        }
        return this;
    }


    private void ensureBukkitCommand(String label, OreoCommand cmd) {
        PluginCommand pc = plugin.getCommand(label);
        if (pc != null) {
            wire(pc, cmd);
            return;
        }

        try {
            CommandMap map = getCommandMap();
            if (map == null) {
                plugin.getLogger().warning("[Commands] CommandMap not available; '" + label + "' not registered.");
                return;
            }

            PluginCommand created = createPluginCommand(label, plugin);
            if (created == null) {
                plugin.getLogger().warning("[Commands] Could not create PluginCommand for '" + label + "'.");
                return;
            }

            String desc  = metaDescription(cmd);
            String usage = metaUsage(cmd);
            String perm  = metaPermission(cmd);

            if (desc != null && !desc.isEmpty()) created.setDescription(desc);
            created.setUsage("/" + cmd.name() + (usage == null || usage.isEmpty() ? "" : " " + usage));
            if (perm != null && !perm.isEmpty()) created.setPermission(perm);

            boolean ok = map.register(plugin.getDescription().getName(), created);
            if (!ok) {
                plugin.getLogger().warning("[Commands] Failed to register '" + label + "' into CommandMap.");
                return;
            }
            wire(created, cmd);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Commands] Error registering '" + label + "': " + t.getMessage());
        }
    }

    private void wire(PluginCommand pc, OreoCommand cmd) {
        Exec exec = new Exec();
        pc.setExecutor(exec);

        if (cmd instanceof TabCompleter tc) {
            pc.setTabCompleter(tc);
        } else {
            pc.setTabCompleter(exec);
        }
    }
    public CommandManager registerIf(boolean enabled, OreoCommand cmd) {
        if (enabled) {
            register(cmd);
        }
        return this;
    }

    private CommandMap getCommandMap() {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private PluginCommand createPluginCommand(String name, Plugin owningPlugin) {
        try {
            Constructor<PluginCommand> c =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);
            return c.newInstance(name, owningPlugin);
        } catch (Throwable ignored) {
            return null;
        }
    }


    private final class Exec implements TabExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            String key = label.toLowerCase(Locale.ROOT);
            OreoCommand cmd = byName.get(key);
            if (cmd == null) {
                cmd = byName.get(command.getName().toLowerCase(Locale.ROOT));
            }
            if (cmd == null) {
                sender.sendMessage("§cUnknown command.");
                return true;
            }

            String perm = metaPermission(cmd);
            if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (metaPlayerOnly(cmd) && !(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }

            boolean ok = false;
            try {
                ok = cmd.execute(sender, label, args);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Commands] Exception in /" + label + ": " + t.getMessage());
            }
            if (!ok) {
                String usage = metaUsage(cmd);
                sender.sendMessage("§eUsage: §7/" + cmd.name() + (usage == null || usage.isEmpty() ? "" : " " + usage));
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            String key = alias.toLowerCase(Locale.ROOT);
            OreoCommand cmd = byName.getOrDefault(
                    key, byName.get(command.getName().toLowerCase(Locale.ROOT))
            );
            if (cmd instanceof TabCompleter tc) {
                try { return tc.onTabComplete(sender, command, alias, args); }
                catch (Throwable ignored) {}
            }
            return Collections.emptyList();
        }
    }


    private String metaDescription(OreoCommand cmd) { return callString(cmd, "description", "getDescription"); }
    private String metaUsage(OreoCommand cmd)       { return callString(cmd, "usage", "getUsage"); }
    private String metaPermission(OreoCommand cmd)  { return callString(cmd, "permission", "getPermission", "perm", "getPerm"); }

    private boolean metaPlayerOnly(OreoCommand cmd) {
        Boolean b = callBoolean(cmd, "playerOnly", "isPlayerOnly");
        return b != null && b;
    }

    private String callString(Object o, String... methodNames) {
        for (String m : methodNames) {
            try {
                Method mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v instanceof String s) return s;
            } catch (Throwable ignored) {}
        }
        return "";
    }

    private Boolean callBoolean(Object o, String... methodNames) {
        for (String m : methodNames) {
            try {
                Method mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v instanceof Boolean b) return b;
            } catch (Throwable ignored) {}
        }
        return Boolean.FALSE;
    }
}
