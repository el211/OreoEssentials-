package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /weblink — generates a one-time link code and registers it on the web panel.
 *
 * Usage:
 *   1. Player runs /weblink in-game.
 *   2. A 6-character code is generated and POSTed to the panel.
 *   3. Player enters the code on the web panel "Link Account" screen.
 *   4. The panel looks up the code, retrieves their UUID + server, and creates the link.
 *
 * Codes expire in 5 minutes (enforced server-side).
 */
public class WebLinkCommand implements CommandExecutor {

    // Unambiguous character set — avoids 0/O and 1/I confusion
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final WebPanelConfig config;
    private final WebPanelClient client;

    public WebLinkCommand(WebPanelConfig config, WebPanelClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be run in-game.");
            return true;
        }

        if (!config.isEnabled()) {
            player.sendMessage("§cThe web panel is not configured on this server.");
            return true;
        }

        final String code     = generateCode();
        final UUID   uuid     = player.getUniqueId();
        final String name     = player.getName();
        final String panelUrl = config.getPanelUrl();

        player.sendMessage("§7Generating link code…");

        // HTTP call off the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean success = client.registerWebLink(code, uuid, name);

                // Send result back on the main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (success) {
                            player.sendMessage("§6§l✦ OreoStudios Panel §r§6— Link your account");
                            player.sendMessage("§7Your link code: §e§l" + code);
                            player.sendMessage("§7Go to §b" + panelUrl + "§7 and enter this code.");
                            player.sendMessage("§7Code expires in §e5 minutes§7.");
                        } else {
                            player.sendMessage("§cCould not reach the panel. Please try again later.");
                        }
                    }
                }.runTask(OreoEssentials.get());
            }
        }.runTaskAsynchronously(OreoEssentials.get());

        return true;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CHARS.charAt(ThreadLocalRandom.current().nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
