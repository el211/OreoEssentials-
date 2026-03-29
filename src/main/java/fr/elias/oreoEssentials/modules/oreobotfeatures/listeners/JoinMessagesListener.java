package fr.elias.oreoEssentials.modules.oreobotfeatures.listeners;

import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.RankedMessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);

        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean(SECTION + ".enable", false)) return;
        if (shouldDisableBackend(c, SECTION)) return;

        final Player p = e.getPlayer();
        final boolean firstJoin = !p.hasPlayedBefore();

        final boolean lookLikePlayer = c.getBoolean(SECTION + ".look_like_player", false);
        final String playerNameFmt   = c.getString(SECTION + ".player_name", "{name}");
        final String playerPrefixFmt = c.getString(SECTION + ".player_prefix", "");
        final String delimiter       = c.getString(SECTION + ".delimiter", " | ");
        final String defaultBody     = c.getString(
                firstJoin ? (SECTION + ".first_join") : (SECTION + ".rejoin_message"),
                "{name} joined the game."
        );
        final String key      = firstJoin ? "first_join" : "rejoin_message";
        final String namePlain = p.getName();
        final String playerName = playerNameFmt.replace("{name}", namePlain);

        long delayTicks = Math.max(0L, c.getLong(SECTION + ".join_message_delay", 0L) * 20L);


        Runnable send = () -> {
            String body = RankedMessageUtil.resolveRankedText(c, SECTION, key, p, defaultBody)
                    .replace("{name}", namePlain);

            String output = lookLikePlayer
                    ? (playerPrefixFmt + " " + playerName + " " + delimiter + " " + body)
                    : body;

            output = papi(p, output);

            ResolvedSound sound = resolveSound(c, SECTION, p);

            String legacyMsg = legacy.serialize(mm.deserialize(output));
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.sendMessage(legacyMsg);
                if (sound != null) {
                    pl.playSound(pl.getLocation(), sound.key(), SoundCategory.PLAYERS,
                            sound.volume(), sound.pitch());
                }
            }
        };

        if (delayTicks > 0) OreScheduler.runLater(plugin, send, delayTicks);
        else OreScheduler.run(plugin, send);
    }


    private static ResolvedSound resolveSound(FileConfiguration c, String section, Player p) {
        ConfigurationSection sounds = c.getConfigurationSection(section + ".sounds");
        if (sounds == null) return null;

        float defVolume = (float) sounds.getDouble("volume", 1.0);
        float defPitch  = (float) sounds.getDouble("pitch", 1.0);
        String defSound = sounds.getString("default", null);

        List<?> formats = sounds.getList("formats");
        if (formats != null) {
            ConfigurationSection fmtSec = sounds.getConfigurationSection("formats");
            var rawList = sounds.getMapList("formats");
            for (var map : rawList) {
                Object perm = map.get("permission");
                Object snd  = map.get("sound");
                if (perm == null || snd == null) continue;
                if (p.hasPermission(perm.toString())) {
                    float vol = map.containsKey("volume") ? ((Number) map.get("volume")).floatValue() : defVolume;
                    float pit = map.containsKey("pitch")  ? ((Number) map.get("pitch")).floatValue()  : defPitch;
                    return new ResolvedSound(snd.toString(), vol, pit);
                }
            }
        }

        if (defSound != null && !defSound.isEmpty()) {
            return new ResolvedSound(defSound, defVolume, defPitch);
        }
        return null;
    }

    private record ResolvedSound(String key, float volume, float pitch) {}

    private static String papi(Player p, String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, text);
            }
        } catch (Throwable ignored) {}
        return text;
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