package fr.elias.oreoEssentials.modules.oreobotfeatures.listeners;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

public final class ConversationListener implements Listener {
    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    public ConversationListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent e) {
        FileConfiguration c = plugin.getConfig();
        ConfigurationSection root = c.getConfigurationSection("conversations");
        if (root == null) return;

        if (!root.getBoolean("enabled", true)) return;

        final Player sender = e.getPlayer();
        String raw = e.getMessage();
        if (raw == null || raw.isBlank()) return;

        final String msg = raw.toLowerCase(Locale.ROOT);

        for (String botKey : root.getKeys(false)) {
            if (botKey.equalsIgnoreCase("enabled")) continue;

            ConfigurationSection bot = root.getConfigurationSection(botKey);
            if (bot == null) continue;

            if (!bot.getBoolean("enabled", true)) continue;

            String callName = bot.getString("custom_call_name", "").toLowerCase(Locale.ROOT);
            if (callName.isEmpty() || !msg.contains(callName)) continue;

            String selfReply = bot.getString("self_mention_reply", null);

            ConfigurationSection questions = bot.getConfigurationSection("questions");
            boolean matched = false;

            if (questions != null) {
                for (String qKey : questions.getKeys(false)) {
                    ConfigurationSection q = questions.getConfigurationSection(qKey);
                    if (q == null) continue;

                    String keyword = q.getString("keyword", "").toLowerCase(Locale.ROOT);
                    String keyPhrase = q.getString("key_phrase", "").toLowerCase(Locale.ROOT);
                    List<String> replies = q.getStringList("replies");

                    if ((!keyword.isEmpty() && msg.contains(keyword)) ||
                            (!keyPhrase.isEmpty() && msg.contains(keyPhrase))) {

                        if (!replies.isEmpty()) {
                            String reply = replies.get((int) (Math.random() * replies.size()));
                            sayAsBot(bot, reply.replace("{name}", sender.getName()));
                            matched = true;
                            break;
                        }
                    }
                }
            }

            if (!matched && selfReply != null && !selfReply.isEmpty()) {
                sayAsBot(bot, selfReply.replace("{name}", sender.getName()));
            }

            break;
        }
    }

    private void sayAsBot(ConfigurationSection bot, String body) {
        if (!bot.getBoolean("enabled", true)) return;

        boolean lookLikePlayer = bot.getBoolean(
                "look_like_player",
                Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")
        );

        String playerNameFmt   = bot.getString("player_name", "BOT");
        String playerPrefixFmt = bot.getString("player_prefix", "");
        String delimiter       = bot.getString("delimiter", " | ");

        String output = lookLikePlayer
                ? (playerPrefixFmt + " " + playerNameFmt + " " + delimiter + " " + body)
                : body;

        String legacyMsg = legacy.serialize(mm.deserialize(output));
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(legacyMsg)));
    }
}
