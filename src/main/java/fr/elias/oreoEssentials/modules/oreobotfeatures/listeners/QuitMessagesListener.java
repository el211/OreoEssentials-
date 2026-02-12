package fr.elias.oreoEssentials.modules.oreobotfeatures.listeners;

import fr.elias.oreoEssentials.util.RankedMessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class QuitMessagesListener implements Listener {

    private static final String SECTION = "Quit_messages";

    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public QuitMessagesListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);

        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean(SECTION + ".enable", false)) return;
        if (shouldDisableBackend(c, SECTION)) return;

        final Player p = e.getPlayer();

        final boolean lookLikePlayer = c.getBoolean(SECTION + ".look_like_player", false);
        final String playerNameFmt   = c.getString(SECTION + ".player_name", "{name}");
        final String playerPrefixFmt = c.getString(SECTION + ".player_prefix", "");
        final String delimiter       = c.getString(SECTION + ".delimiter", " | ");


        String defaultBody = c.getString(SECTION + ".message", "{name} left the game.");
        String body = RankedMessageUtil.resolveRankedText(c, SECTION, "message", p, defaultBody);

        // Replace placeholders
        final String namePlain = p.getName();
        final String playerName = playerNameFmt.replace("{name}", namePlain);
        body = body.replace("{name}", namePlain);

        final String output = lookLikePlayer
                ? (playerPrefixFmt + " " + playerName + " " + delimiter + " " + body)
                : body;

        String legacyMsg = legacy.serialize(mm.deserialize(output));
        Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(legacyMsg));
    }

    private boolean shouldDisableBackend(FileConfiguration c, String section) {
        if (!c.getBoolean(section + ".disable_on_backend", false)) return false;

        String serverName = c.getString("server.name", "unknown");
        List<String> list = c.getStringList(section + ".backend_server_names");
        String mode = c.getString(section + ".use_backend_list_as", "blacklist");

        if (list == null || list.isEmpty()) return true;

        boolean contains = list.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(serverName));
        if ("whitelist".equalsIgnoreCase(mode)) return !contains;
        return contains;
    }
}
