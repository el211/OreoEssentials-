package fr.elias.oreoEssentials.modules.mail;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

public class MailListener implements Listener {

    private final MailService mail;

    public MailListener(MailService mail) {
        this.mail = mail;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        int unread = mail.unreadCount(e.getPlayer().getUniqueId());
        if (unread > 0) {
            Lang.send(e.getPlayer(), "mail.join.unread",
                    "<gold>[Mail] You have <white>%count%</white> unread mail(s). Use <white>/mail read</white> to read them.</gold>",
                    Map.of("count", String.valueOf(unread)));
        }
    }
}
