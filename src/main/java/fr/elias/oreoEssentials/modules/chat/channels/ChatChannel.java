package fr.elias.oreoEssentials.modules.chat.channels;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;


public class ChatChannel {

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

    private final String joinMessage;

    private final Map<String, String> rankFormats;

    private final String discordWebhook;

    public ChatChannel(String id, boolean enabled, String displayName, String description,
                       String talkPermission, String viewPermission, String joinPermission,
                       ChannelScope scope, int rangeBlocks,
                       boolean formattingEnabled, String format, String joinMessage) {
        this(id, enabled, displayName, description, talkPermission, viewPermission, joinPermission,
                scope, rangeBlocks, formattingEnabled, format, joinMessage, null, null);
    }

    public ChatChannel(String id, boolean enabled, String displayName, String description,
                       String talkPermission, String viewPermission, String joinPermission,
                       ChannelScope scope, int rangeBlocks,
                       boolean formattingEnabled, String format, String joinMessage,
                       Map<String, String> rankFormats) {
        this(id, enabled, displayName, description, talkPermission, viewPermission, joinPermission,
                scope, rangeBlocks, formattingEnabled, format, joinMessage, rankFormats, null);
    }

    public ChatChannel(String id, boolean enabled, String displayName, String description,
                       String talkPermission, String viewPermission, String joinPermission,
                       ChannelScope scope, int rangeBlocks,
                       boolean formattingEnabled, String format, String joinMessage,
                       Map<String, String> rankFormats, String discordWebhook) {
        this.id = Objects.requireNonNull(id);
        this.enabled = enabled;
        this.displayName = displayName == null ? id : displayName;
        this.description = description == null ? "" : description;
        this.talkPermission = talkPermission;
        this.viewPermission = viewPermission;
        this.joinPermission = joinPermission != null ? joinPermission : talkPermission;
        this.scope = scope;
        this.rangeBlocks = rangeBlocks;
        this.formattingEnabled = formattingEnabled;
        this.format = format;
        this.joinMessage = joinMessage;
        this.rankFormats = rankFormats;
        this.discordWebhook = discordWebhook;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getTalkPermission() {
        return talkPermission;
    }

    public String getViewPermission() {
        return viewPermission;
    }

    public String getJoinPermission() {
        return joinPermission;
    }

    public ChannelScope getScope() {
        return scope;
    }

    public int getRangeBlocks() {
        return rangeBlocks;
    }

    public boolean isFormattingEnabled() {
        return formattingEnabled;
    }

    public String getFormat() {
        return format;
    }

    public String getJoinMessage() {
        return joinMessage;
    }

    public boolean hasJoinMessage() {
        return joinMessage != null && !joinMessage.isEmpty();
    }


    public String getDiscordWebhook() {
        return discordWebhook;
    }


    public boolean hasDiscordWebhook() {
        return discordWebhook != null && !discordWebhook.isEmpty();
    }


    public String getFormatForPlayer(Player player) {
        if (rankFormats == null || rankFormats.isEmpty()) {
            return format;
        }

        try {
            String primaryGroup = getPrimaryGroup(player);
            if (primaryGroup != null && rankFormats.containsKey(primaryGroup)) {
                return rankFormats.get(primaryGroup);
            }
        } catch (Exception e) {
        }

        if (rankFormats.containsKey("default")) {
            return rankFormats.get("default");
        }

        return format;
    }


    private String getPrimaryGroup(Player player) {
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getPrimaryGroup();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }


    public boolean canTalk(Player player) {
        return talkPermission == null || talkPermission.isEmpty() || player.hasPermission(talkPermission);
    }


    public boolean canView(Player player) {
        return viewPermission == null || viewPermission.isEmpty() || player.hasPermission(viewPermission);
    }


    public boolean canJoin(Player player) {
        return joinPermission == null || joinPermission.isEmpty() || player.hasPermission(joinPermission);
    }


    public boolean isReadOnly() {
        return talkPermission != null && !talkPermission.isEmpty();
    }

    public enum ChannelScope {
        ALL,
        WORLD,
        RANGE,
        SERVER,
        SHARD
    }
}