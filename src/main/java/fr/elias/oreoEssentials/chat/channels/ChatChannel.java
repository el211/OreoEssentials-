package fr.elias.oreoEssentials.chat.channels;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a chat channel with configurable scope, permissions, and formatting
 * Supports per-rank formatting for different LuckPerms groups
 */
public class ChatChannel {

    public enum ChannelScope {
        ALL,      // Everyone on all servers
        WORLD,    // Same world only
        RANGE,    // Within X blocks
        SERVER,   // Same server only
        SHARD     // Same shard only
    }

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final String description;

    private final String talkPermission;
    private final String viewPermission;
    private final String joinPermission;

    private final ChannelScope scope;
    private final int rangeBlocks;

    private final boolean formattingEnabled;
    private final String format;

    // NEW: Per-rank formats (key = luckperms group name, value = format)
    private final Map<String, String> rankFormats;

    public ChatChannel(
            String id,
            boolean enabled,
            String displayName,
            String description,
            String talkPermission,
            String viewPermission,
            String joinPermission,
            ChannelScope scope,
            int rangeBlocks,
            boolean formattingEnabled,
            String format
    ) {
        this(id, enabled, displayName, description, talkPermission, viewPermission, joinPermission,
                scope, rangeBlocks, formattingEnabled, format, new HashMap<>());
    }

    public ChatChannel(
            String id,
            boolean enabled,
            String displayName,
            String description,
            String talkPermission,
            String viewPermission,
            String joinPermission,
            ChannelScope scope,
            int rangeBlocks,
            boolean formattingEnabled,
            String format,
            Map<String, String> rankFormats
    ) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.description = description;
        this.talkPermission = talkPermission;
        this.viewPermission = viewPermission;
        this.joinPermission = joinPermission;
        this.scope = scope;
        this.rangeBlocks = rangeBlocks;
        this.formattingEnabled = formattingEnabled;
        this.format = format;
        this.rankFormats = rankFormats != null ? rankFormats : new HashMap<>();
    }

    public boolean canTalk(Player player) {
        return talkPermission == null || player.hasPermission(talkPermission);
    }

    public boolean canView(Player player) {
        return viewPermission == null || player.hasPermission(viewPermission);
    }

    public boolean canJoin(Player player) {
        return joinPermission == null || player.hasPermission(joinPermission);
    }

    /**
     * Get the format for a specific player based on their LuckPerms primary group
     * Falls back to default format if no rank-specific format is defined
     */
    public String getFormatForPlayer(Player player) {
        // Try to get player's primary group from LuckPerms
        String primaryGroup = getPrimaryGroup(player);

        // Check if we have a custom format for this rank
        if (primaryGroup != null && rankFormats.containsKey(primaryGroup.toLowerCase())) {
            return rankFormats.get(primaryGroup.toLowerCase());
        }

        // Fall back to default format
        return format;
    }

    /**
     * Get player's primary LuckPerms group
     */
    private String getPrimaryGroup(Player player) {
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getPlayerAdapter(Player.class).getUser(player);
            if (user != null) {
                return user.getPrimaryGroup();
            }
        } catch (Throwable ignored) {}
        return "default";
    }

    // Getters
    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ChannelScope getScope() { return scope; }
    public int getRangeBlocks() { return rangeBlocks; }
    public boolean isFormattingEnabled() { return formattingEnabled; }
    public String getFormat() { return format; }
    public Map<String, String> getRankFormats() { return rankFormats; }
}