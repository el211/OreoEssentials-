package fr.elias.oreoEssentials.modules.commandcontrol;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.Iterator;
import java.util.Locale;

public final class CommandControlTabHideListener implements Listener {

    private final CommandControlService service;

    public CommandControlTabHideListener(CommandControlService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSend(PlayerCommandSendEvent e) {
        if (!service.isEnabled() || !service.isHideFromTab()) return;

        Player p = e.getPlayer();
        if (p == null) return;
        if (service.canBypass(p)) return;

        Iterator<String> it = e.getCommands().iterator();
        while (it.hasNext()) {
            String cmd = it.next();
            if (cmd == null) continue;

            if (service.shouldHideRootFromSend(p, cmd)) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBrigadier(AsyncPlayerSendCommandsEvent e) {
        if (!service.isEnabled() || !service.isHideFromTab()) return;

        Player p = e.getPlayer();
        if (p == null) return;
        if (service.canBypass(p)) return;

        RootCommandNode<?> rootNode = e.getCommandNode();

        rootNode.getChildren().removeIf(node -> service.shouldHideRootFromSend(p, node.getName()));

        for (CommandNode<?> node : rootNode.getChildren()) {
            String root = node.getName().toLowerCase(Locale.ROOT);

            node.getChildren().removeIf(child -> {
                String sub = child.getName().toLowerCase(Locale.ROOT);
                return !service.canUseSub(p, root, sub);
            });
        }
    }
    public void refreshAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.updateCommands(); } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTab(TabCompleteEvent e) {
        if (!service.isEnabled() || !service.isHideFromTab()) return;
        if (!(e.getSender() instanceof Player p)) return;
        if (service.canBypass(p)) return;

        String buffer = e.getBuffer();
        if (buffer == null) return;

        String raw = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        raw = raw.trim();

        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return;

        String root = parts[0].toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0 && colon + 1 < root.length()) root = root.substring(colon + 1);

        if (parts.length >= 2 || buffer.endsWith(" ")) {
            String finalRoot = root;

            e.getCompletions().removeIf(sug -> {
                if (sug == null) return true;

                String sub = sug.trim().toLowerCase(Locale.ROOT);
                if (sub.isEmpty()) return true;

                return !service.canUseSub(p, finalRoot, sub);
            });
        } else {
            e.getCompletions().removeIf(sug -> service.shouldHideRootFromSend(p, sug));
        }
    }
}
