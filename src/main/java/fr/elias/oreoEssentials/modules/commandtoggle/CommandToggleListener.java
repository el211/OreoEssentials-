package fr.elias.oreoEssentials.modules.commandtoggle;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Safety-net listener for disabled commands.
 *
 * The service physically removes disabled commands from the CommandMap, but this
 * listener still provides a friendly message instead of Minecraft's generic
 * "Unknown command" response.
 */
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
        if (message == null || message.length() <= 1) return;

        int space = message.indexOf(' ');
        String rawLabel = space == -1 ? message.substring(1) : message.substring(1, space);
        String commandLabel = CommandToggleConfig.normalizeLabel(rawLabel);
        if (commandLabel.isEmpty()) return;

        // Primary command names always win over aliases. resolveCommandName() already
        // applies that rule, so collisions such as /clear or /top cannot be blocked
        // merely because another command also lists them as an alias.
        String managedCommand = config.resolveCommandName(commandLabel);
        if (managedCommand == null || config.isCommandEnabled(managedCommand)) {
            return;
        }

        if (player.hasPermission("oreo.commandtoggle.bypass")) {
            return;
        }

        event.setCancelled(true);
        String disabledMsg = ChatColor.translateAlternateColorCodes('&', config.getDisabledMessage());
        player.sendMessage(disabledMsg);

        plugin.getLogger().info(
                "[CommandToggle] Blocked disabled command label '/" + commandLabel
                        + "' (managed as /" + managedCommand + ") from " + player.getName()
        );
    }
}
