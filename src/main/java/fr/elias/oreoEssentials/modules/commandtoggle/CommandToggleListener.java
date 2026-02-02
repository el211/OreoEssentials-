package fr.elias.oreoEssentials.modules.commandtoggle;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;


public class CommandToggleListener implements Listener {
    private final JavaPlugin plugin;
    private final CommandToggleConfig config;

    public CommandToggleListener(JavaPlugin plugin, CommandToggleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Parse command name
        String[] parts = message.split(" ");
        if (parts.length == 0) return;

        String commandLabel = parts[0].substring(1).toLowerCase(Locale.ROOT); // Remove '/' and lowercase

        if (commandLabel.contains(":")) {
            String[] namespaceParts = commandLabel.split(":", 2);
            commandLabel = namespaceParts[1];
        }

        String disabledCommand = getDisabledCommandName(commandLabel);

        if (disabledCommand != null) {
            if (player.hasPermission("oreo.commandtoggle.bypass")) {
                return;
            }

            event.setCancelled(true);
            String disabledMsg = ChatColor.translateAlternateColorCodes('&', config.getDisabledMessage());
            player.sendMessage(disabledMsg);

            plugin.getLogger().info("[CommandToggle] Blocked disabled command '" + commandLabel
                    + "' (disabled as: " + disabledCommand + ") from " + player.getName());
        }
    }


    private String getDisabledCommandName(String commandLabel) {
        String lower = commandLabel.toLowerCase(Locale.ROOT);

        if (!config.isCommandEnabled(lower)) {
            return lower;
        }

        for (var entry : config.getAllCommands().entrySet()) {
            String cmdName = entry.getKey();
            CommandToggleConfig.CommandToggleEntry toggleEntry = entry.getValue();

            if (!toggleEntry.isEnabled()) {
                for (String alias : toggleEntry.getAliases()) {
                    if (alias.equalsIgnoreCase(lower)) {
                        return cmdName;
                    }
                }
            }
        }

        return null;
    }
}