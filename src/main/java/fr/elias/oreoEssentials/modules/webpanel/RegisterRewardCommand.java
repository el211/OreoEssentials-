package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.shop.hooks.ItemsAdderHook;
import fr.elias.oreoEssentials.modules.shop.hooks.NexoHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /registerreward — opens the RegisterReward GUI.
 *
 * If the player has already claimed, the GUI opens immediately (no API call needed).
 * Otherwise an async registration check is performed first, then the GUI is opened
 * on the main thread with the correct state.
 */
public class RegisterRewardCommand implements CommandExecutor {

    private final OreoEssentials plugin;
    private final RegisterRewardConfig cfg;
    private final RegisterRewardService svc;
    private final ItemsAdderHook iaHook;
    private final NexoHook nexoHook;

    public RegisterRewardCommand(OreoEssentials plugin,
                                  RegisterRewardConfig cfg,
                                  RegisterRewardService svc,
                                  ItemsAdderHook iaHook,
                                  NexoHook nexoHook) {
        this.plugin   = plugin;
        this.cfg      = cfg;
        this.svc      = svc;
        this.iaHook   = iaHook;
        this.nexoHook = nexoHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be run in-game.");
            return true;
        }

        if (!cfg.isEnabled()) {
            player.sendMessage(cfg.msgNotEnabled());
            return true;
        }

        // Already claimed — open GUI immediately without an API call
        if (svc.hasClaimed(player.getUniqueId())) {
            new RegisterRewardMenu(plugin, cfg, svc, iaHook, nexoHook, false)
                    .inventory(player).open(player);
            return true;
        }

        player.sendMessage("§7Checking your registration status…");

        // Async registration check → open GUI on main thread
        svc.checkRegistered(player.getUniqueId(), registered -> {
            if (!player.isOnline()) return;
            new RegisterRewardMenu(plugin, cfg, svc, iaHook, nexoHook, registered)
                    .inventory(player).open(player);
        });

        return true;
    }
}
