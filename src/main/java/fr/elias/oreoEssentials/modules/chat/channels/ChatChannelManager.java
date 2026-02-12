package fr.elias.oreoEssentials.modules.chat.channels;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.CustomConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class ChatChannelManager {

    private final OreoEssentials plugin;
    private final CustomConfig chatConfig;

    private boolean enabled;
    private String defaultChannelId;
    private boolean rememberLastChannel;
    private boolean globalFormattingEnabled;
    private String fallbackFormat;

    private final Map<String, ChatChannel> channels = new LinkedHashMap<>();
    private final List<String> channelOrder = new ArrayList<>();

    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

    private ChannelPersistenceProvider persistence;

    public ChatChannelManager(OreoEssentials plugin, CustomConfig chatConfig) {
        this(plugin, chatConfig, null);
    }

    public ChatChannelManager(OreoEssentials plugin, CustomConfig chatConfig, com.mongodb.client.MongoClient mongoClient) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;

        initializePersistence(mongoClient);

        loadConfig();
        loadPlayerData();
    }

    private void initializePersistence(com.mongodb.client.MongoClient mongoClient) {
        FileConfiguration cfg = chatConfig.getCustomConfig();
        boolean useMongo = cfg.getBoolean("chat.channels.cross_server.enabled", false);

        if (useMongo && mongoClient != null) {
            try {
                // Use MongoDB from global config
                String dbName = plugin.getConfig().getString("storage.mongo.database", "oreo");
                String prefix = plugin.getConfig().getString("storage.mongo.collectionPrefix", "oreo_");

                this.persistence = new MongoChannelPersistence(mongoClient, dbName, prefix);
                plugin.getLogger().info("[Channels] Using MongoDB persistence (cross-server enabled)");
            } catch (Exception e) {
                plugin.getLogger().warning("[Channels] Failed to init MongoDB persistence, falling back to YAML: " + e.getMessage());
                this.persistence = new YamlChannelPersistence(plugin);
            }
        } else {
            this.persistence = new YamlChannelPersistence(plugin);
            if (!useMongo) {
                plugin.getLogger().info("[Channels] Using YAML persistence (local server only)");
            } else {
                plugin.getLogger().warning("[Channels] MongoDB requested but MongoClient is null, using YAML");
            }
        }
    }

    public void reload() {
        chatConfig.reloadCustomConfig();
        channels.clear();
        channelOrder.clear();
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration cfg = chatConfig.getCustomConfig();

        this.enabled = cfg.getBoolean("chat.channels.enabled", false);
        if (!enabled) {
            plugin.getLogger().info("[Channels] Channel system is disabled.");
            return;
        }

        this.defaultChannelId = cfg.getString("chat.channels.default_channel", "global");
        this.rememberLastChannel = cfg.getBoolean("chat.channels.remember_last_channel", true);
        this.globalFormattingEnabled = cfg.getBoolean("chat.channels.formatting.enabled", true);
        this.fallbackFormat = cfg.getString("chat.channels.formatting.fallback_format",
                "<gray>{player}</gray><dark_gray>: </dark_gray><white>{message}</white>");

        List<String> order = cfg.getStringList("chat.channels.list_order");
        if (!order.isEmpty()) {
            channelOrder.addAll(order);
        }

        ConfigurationSection defs = cfg.getConfigurationSection("chat.channels.channel_definitions");
        if (defs == null) {
            plugin.getLogger().warning("[Channels] No channel definitions found! Creating default channels.");
            createDefaultChannels();
            return;
        }

        for (String id : defs.getKeys(false)) {
            ConfigurationSection ch = defs.getConfigurationSection(id);
            if (ch == null) continue;

            boolean chEnabled = ch.getBoolean("enabled", true);
            String displayName = ch.getString("display_name", id);
            String desc = ch.getString("description", "");

            String talkPerm = ch.getString("talk_permission");
            String viewPerm = ch.getString("view_permission");
            String joinPerm = ch.getString("join_permission");

            String scopeStr = ch.getString("scope.type", "ALL");
            ChatChannel.ChannelScope scope;
            try {
                scope = ChatChannel.ChannelScope.valueOf(scopeStr.toUpperCase());
            } catch (Exception e) {
                scope = ChatChannel.ChannelScope.ALL;
            }

            int range = ch.getInt("scope.range_blocks", 0);

            boolean fmtEnabled = ch.getBoolean("formatting.enabled", true);
            String format = ch.getString("formatting.format", fallbackFormat);

            // NEW: Load join message
            String joinMessage = ch.getString("join_message", null);

            // NEW: Load per-rank formats
            Map<String, String> rankFormats = new HashMap<>();
            ConfigurationSection rankSection = ch.getConfigurationSection("formatting.rank_formats");
            if (rankSection != null) {
                for (String rank : rankSection.getKeys(false)) {
                    String rankFormat = rankSection.getString(rank);
                    if (rankFormat != null && !rankFormat.isEmpty()) {
                        rankFormats.put(rank.toLowerCase(), rankFormat);
                    }
                }
            }

            String discordWebhook = ch.getString("discord_webhook", null);

            ChatChannel channel = new ChatChannel(
                    id, chEnabled, displayName, desc,
                    talkPerm, viewPerm, joinPerm,
                    scope, range,
                    fmtEnabled, format, joinMessage, rankFormats, discordWebhook
            );

            channels.put(id, channel);
        }

        if (channelOrder.isEmpty()) {
            channelOrder.addAll(channels.keySet());
        }

        plugin.getLogger().info("[Channels] Loaded " + channels.size() + " channels");
    }

    private void createDefaultChannels() {
        ChatChannel global = new ChatChannel(
                "global", true, "<gradient:#FF1493:#00FF7F>Global</gradient>",
                "<gray>Server-wide chat</gray>",
                null,
                null,
                null,
                ChatChannel.ChannelScope.ALL, 0,
                true, "<gray>[G]</gray> {prefix}{player}{suffix}<dark_gray>: </dark_gray><white>{message}</white>",
                "<gray>You are now chatting in </gray><gradient:#FF1493:#00FF7F>Global</gradient><gray>!</gray>",
                null  // No rank formats by default
        );

        ChatChannel local = new ChatChannel(
                "local", true, "<yellow>Local</yellow>",
                "<gray>Local area chat</gray>",
                null, null, null,
                ChatChannel.ChannelScope.RANGE, 100,
                true, "<yellow>[L]</yellow> {prefix}{player}{suffix}<dark_gray>: </dark_gray><white>{message}</white>",
                "<gray>You are now chatting in </gray><yellow>Local</yellow><gray>!</gray>",
                null  // No rank formats by default
        );

        channels.put("global", global);
        channels.put("local", local);
        channelOrder.add("global");
        channelOrder.add("local");
    }

    private void loadPlayerData() {
        if (!rememberLastChannel) return;
        playerChannels.putAll(persistence.loadAll());
        plugin.getLogger().info("[Channels] Loaded channel data for " + playerChannels.size() + " players");
    }

    public void savePlayerData() {
        if (!rememberLastChannel) return;
        persistence.saveAll(playerChannels);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ChatChannel getChannel(String id) {
        return channels.get(id);
    }

    public ChatChannel getDefaultChannel() {
        return channels.get(defaultChannelId);
    }

    public Collection<ChatChannel> getAllChannels() {
        return channels.values();
    }

    public List<ChatChannel> getOrderedChannels() {
        List<ChatChannel> result = new ArrayList<>();
        for (String id : channelOrder) {
            ChatChannel ch = channels.get(id);
            if (ch != null) result.add(ch);
        }
        for (ChatChannel ch : channels.values()) {
            if (!result.contains(ch)) result.add(ch);
        }
        return result;
    }

    public ChatChannel getPlayerChannel(Player player) {
        String channelId = playerChannels.get(player.getUniqueId());
        if (channelId == null) {
            return getDefaultChannel();
        }
        ChatChannel ch = channels.get(channelId);
        if (ch == null || !ch.isEnabled()) {
            return getDefaultChannel();
        }
        return ch;
    }

    /**
     * Set a player's channel and send join message if configured
     */
    public void setPlayerChannel(Player player, ChatChannel channel) {
        if (channel == null) {
            playerChannels.remove(player.getUniqueId());
            if (rememberLastChannel) {
                persistence.remove(player.getUniqueId());
            }
        } else {
            playerChannels.put(player.getUniqueId(), channel.getId());
            if (rememberLastChannel) {
                persistence.save(player.getUniqueId(), channel.getId());
            }

            if (channel.hasJoinMessage()) {
                try {
                    Component msg = MiniMessage.miniMessage().deserialize(channel.getJoinMessage());
                    player.sendMessage(msg);
                } catch (Exception e) {
                    player.sendMessage(channel.getJoinMessage());
                }
            }
        }
    }

    public boolean isGlobalFormattingEnabled() {
        return globalFormattingEnabled;
    }

    public String getFallbackFormat() {
        return fallbackFormat;
    }

    public void onPlayerQuit(Player player) {
        if (!rememberLastChannel) {
            playerChannels.remove(player.getUniqueId());
        }
    }
}