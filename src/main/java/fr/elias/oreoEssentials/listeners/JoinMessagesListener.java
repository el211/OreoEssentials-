// File: src/main/java/fr/elias/oreoEssentials/listeners/JoinMessagesListener.java
package fr.elias.oreoEssentials.listeners;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class JoinMessagesListener implements Listener {

    private static final String SECTION = "Join_messages";

    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public JoinMessagesListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // Always clear vanilla join line (prevents “fake reconnect spam”)
        e.setJoinMessage(null);

        FileConfiguration c = plugin.getConfig();

        // Master toggle for custom join messages
        if (!c.getBoolean(SECTION + ".enable", false)) return;

        // NEW: backend suppression (recommended for Velocity networks)
        if (shouldDisableBackend(c, SECTION)) return;

        final Player p = e.getPlayer();
        final boolean firstJoin = !p.hasPlayedBefore();

        final boolean lookLikePlayer = c.getBoolean(SECTION + ".look_like_player", false);
        final String playerNameFmt   = c.getString(SECTION + ".player_name", "{name}");
        final String playerPrefixFmt = c.getString(SECTION + ".player_prefix", "");
        final String delimiter       = c.getString(SECTION + ".delimiter", " | ");

        String body = c.getString(
                firstJoin ? (SECTION + ".first_join") : (SECTION + ".rejoin_message"),
                "{name} joined the game."
        );

        final String namePlain = p.getName();
        final String playerName = playerNameFmt.replace("{name}", namePlain);
        body = body.replace("{name}", namePlain);

        final String output = lookLikePlayer
                ? playerPrefixFmt + " " + playerName + " " + delimiter + " " + body
                : body;

        long delayTicks = Math.max(0L, c.getLong(SECTION + ".join_message_delay", 0L) * 20L);

        Runnable send = () -> {
            String legacyMsg = legacy.serialize(mm.deserialize(output));
            Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(legacyMsg));
        };

        if (delayTicks > 0) Bukkit.getScheduler().runTaskLater(plugin, send, delayTicks);
        else Bukkit.getScheduler().runTask(plugin, send);
    }

    /**
     * If disable_on_backend=true:
     * - blacklist mode: disable on servers listed in backend_server_names
     * - whitelist mode: disable everywhere except servers listed
     *
     * If backend_server_names is empty, we disable everywhere (safe default for networks).
     */
    private boolean shouldDisableBackend(FileConfiguration c, String section) {
        if (!c.getBoolean(section + ".disable_on_backend", false)) return false;

        String serverName = Bukkit.getServer().getName();
        List<String> list = c.getStringList(section + ".backend_server_names");
        String mode = c.getString(section + ".use_backend_list_as", "blacklist");

        if (list == null || list.isEmpty()) {
            // Safe default: if you enabled backend suppression but gave no list,
            // assume “disable everywhere on this backend”.
            return true;
        }

        boolean contains = list.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(serverName));
        if ("whitelist".equalsIgnoreCase(mode)) return !contains;
        return contains; // blacklist
    }
}
