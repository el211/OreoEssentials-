package fr.elias.oreoEssentials.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.traqueur.commands.api.arguments.Arguments;
import fr.traqueur.commands.api.arguments.Infinite;
import org.bukkit.command.CommandSender;
import java.util.logging.Level;

/**
 * Bridges the existing {@link OreoCommand} interface with the CommandsAPI framework.
 */
public final class OreoCommandAdapter extends fr.traqueur.commands.spigot.Command<OreoEssentials> {

    private final OreoCommand delegate;

    public OreoCommandAdapter(OreoEssentials plugin, OreoCommand delegate) {
        super(plugin, delegate.name());
        this.delegate = delegate;

        if (!delegate.aliases().isEmpty()) {
            this.addAlias(delegate.aliases().toArray(new String[0]));
        }

        String perm = delegate.permission();
        if (perm != null && !perm.isBlank()) {
            this.setPermission(perm);
        } else {
            // DC9: flag commands with no permission node so admins can catch oversights early
            plugin.getLogger().warning("[CommandManager] Command '" + delegate.name() + "' has no permission node defined.");
        }

        String usage = delegate.usage();
        if (usage != null && !usage.isBlank()) {
            this.setUsage("/" + delegate.name() + " " + usage);
        }

        if (delegate.playerOnly()) {
            this.setGameOnly(true);
        }

        this.addOptionalArg("_args", Infinite.class);
    }

    @Override
    public void execute(CommandSender sender, Arguments arguments) {
        String raw = arguments.<String>getOptional("_args").orElse("");
        String[] args = raw.isBlank() ? new String[0] : raw.split(" ");

        try {
            boolean ok = delegate.execute(sender, delegate.name(), args);
            if (!ok) {
                String u = delegate.usage();
                sender.sendMessage("§eUsage: §7/" + delegate.name()
                        + (u == null || u.isBlank() ? "" : " " + u));
            }
        } catch (Throwable t) {
            // DC8: log full stack trace, not just the message
            getPlugin().getLogger().log(Level.SEVERE, "Error executing command " + delegate.name(), t);
        }
    }

    public OreoCommand getDelegate() {
        return delegate;
    }
}
