package fr.elias.oreoEssentials.tasks;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class AutoMessageScheduler {
    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    public AutoMessageScheduler(Plugin plugin) { this.plugin = plugin; }

    public void start() {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("Automatic_message.enable", false)) return;

        boolean lookLikePlayer = c.getBoolean("Automatic_message.look_like_player", false);
        String playerNameFmt   = c.getString("Automatic_message.player_name", "Server");
        String playerPrefixFmt = c.getString("Automatic_message.player_prefix", "");
        String delimiter       = c.getString("Automatic_message.delimiter", " | ");

        ConfigurationSection root = c.getConfigurationSection("Automatic_message.messages");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;

            String text = sec.getString("text", "");
            long seconds = sec.getLong("timer_loop", 60L);
            long periodTicks = Math.max(1L, seconds) * 20L;

            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                String output = lookLikePlayer
                        ? (playerPrefixFmt + " " + playerNameFmt + " " + delimiter + " " + text)
                        : text;

                String legacyMsg = legacy.serialize(mm.deserialize(output));
                for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(legacyMsg);
            }, periodTicks, periodTicks);
        }
    }
}
