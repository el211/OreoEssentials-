package fr.elias.oreoEssentials.modules.afk;

import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

public class AfkListener implements Listener {

    private final Plugin plugin;
    private final AfkService afk;

    public AfkListener(Plugin plugin, AfkService afk) {
        this.plugin = plugin;
        this.afk = afk;
    }


    private void onActivity(Player player) {
        if (player == null) return;

        if (!afk.isAfk(player)) {
            return;
        }

        afk.clearAfk(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        onActivity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        onActivity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) return;

        if (isAfkCommand(msg)) return;

        onActivity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        OreScheduler.runForEntity(plugin, p, () -> onActivity(p));
    }

    // Paper 1.21+: our chat listeners cancel AsyncPlayerChatEvent at HIGHEST so the
    // MONITOR+ignoreCancelled=true handler above never fires. Track AFK via AsyncChatEvent
    // instead (ignoreCancelled=false so it always runs regardless of chat system state).
    @SuppressWarnings("UnstableApiUsage")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChatNew(AsyncChatEvent e) {
        Player p = e.getPlayer();
        OreScheduler.runForEntity(plugin, p, () -> onActivity(p));
    }

    private boolean isAfkCommand(String message) {
        if (message.length() < 4) return false;

        if (!message.regionMatches(true, 0, "/afk", 0, 4)) {
            return false;
        }

        return message.length() == 4 || Character.isWhitespace(message.charAt(4));
    }
}
