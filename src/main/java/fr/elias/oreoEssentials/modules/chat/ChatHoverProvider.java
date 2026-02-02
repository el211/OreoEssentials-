package fr.elias.oreoEssentials.modules.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Provides hover text functionality for chat messages.
 * Supports PlaceholderAPI, legacy color codes, and MiniMessage formatting.
 */
public final class ChatHoverProvider {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final boolean enabled;
    private final List<String> lines;

    public ChatHoverProvider(boolean enabled, List<String> lines) {
        this.enabled = enabled;
        this.lines = (lines == null ? new ArrayList<>() : new ArrayList<>(lines));

        // Add default lines if none provided
        if (this.lines.isEmpty()) {
            this.lines.add("<gold>Player: <white>%player_name%</white></gold>");
            this.lines.add("<gray>Health: <white>%player_health%/%player_max_health%</white></gray>");
        }

        Bukkit.getLogger().info("[ChatHoverProvider] Initialized with " + this.lines.size() + " hover lines");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Creates a hover component for the specified player using configured lines
     */
    public Component createHoverComponent(Player player) {
        StringBuilder hoverText = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = replaceHoverPlaceholders(lines.get(i), player);
            hoverText.append(line);
            if (i < lines.size() - 1) {
                hoverText.append("\n");
            }
        }

        try {
            return MM.deserialize(hoverText.toString(), createHoverResolvers(player));
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[ChatHover] Failed to parse hover text: " + e.getMessage());
            e.printStackTrace();
            return Component.text(hoverText.toString());
        }
    }

    /**
     * Adds hover event to all occurrences of the target name in the message
     */
    public Component addHoverToNameEverywhere(Component message, Component hoverComponent, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            return message;
        }

        String messagePlain = PlainTextComponentSerializer.plainText().serialize(message);

        try {
            boolean hasHeadTag = messagePlain.contains("<head:");

            // Special handling for head tags
            if (hasHeadTag) {
                // Pattern 1: <head:...> followed by name
                Pattern pattern1 = Pattern.compile("<head:[^>]+>\\s*" + Pattern.quote(targetName));
                Component result = message.replaceText(TextReplacementConfig.builder()
                        .match(pattern1)
                        .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hoverComponent)))
                        .build());

                if (result != message) {
                    return result;
                }

                // Pattern 2: > followed by name
                Pattern pattern2 = Pattern.compile(">\\s*" + Pattern.quote(targetName) + "\\b");
                result = message.replaceText(TextReplacementConfig.builder()
                        .match(pattern2)
                        .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hoverComponent)))
                        .build());

                if (result != message) {
                    return result;
                }

                // Pattern 3: whitespace followed by name
                Pattern pattern3 = Pattern.compile("\\s+" + Pattern.quote(targetName) + "\\b");
                result = message.replaceText(TextReplacementConfig.builder()
                        .match(pattern3)
                        .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hoverComponent)))
                        .build());

                if (result != message) {
                    return result;
                }
            }

            // Standard word boundary matching
            Pattern wordBoundary = Pattern.compile("\\b" + Pattern.quote(targetName) + "\\b");
            Component result = message.replaceText(TextReplacementConfig.builder()
                    .match(wordBoundary)
                    .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hoverComponent)))
                    .build());

            // Fallback to literal match if word boundary didn't work
            String plainAfter = PlainTextComponentSerializer.plainText().serialize(result);
            if (!plainAfter.contains(targetName) || result == message) {
                result = message.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(targetName)
                        .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hoverComponent)))
                        .build());
            }

            return result;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[ChatHover] Failed to add hover to name everywhere: " + t.getMessage());
            t.printStackTrace();
            return message;
        }
    }

    /**
     * Adds hover event to a specific section of the message containing the player name
     */
    public Component addHoverToNameSection(Component message, Component hoverComponent, String playerName) {
        String messagePlain = PlainTextComponentSerializer.plainText().serialize(message);
        boolean hasHeadTag = messagePlain.contains("<head:");

        List<Component> children = new ArrayList<>(message.children());
        boolean modified = false;

        // Skip head tag if present
        int startSearchIndex = 0;
        if (hasHeadTag) {
            for (int i = 0; i < children.size(); i++) {
                String childText = PlainTextComponentSerializer.plainText().serialize(children.get(i));
                if (childText.equals(">")) {
                    startSearchIndex = i + 1;
                    break;
                }
            }
        }

        // Find the start of the player name
        int nameStartIndex = -1;
        for (int i = startSearchIndex; i < children.size(); i++) {
            Component child = children.get(i);
            String childText = PlainTextComponentSerializer.plainText().serialize(child);

            if (childText.trim().isEmpty()) continue;

            boolean isPartOfName = playerName.contains(childText) &&
                    (childText.length() > 1 || childText.matches("[a-zA-Z0-9]"));

            if (childText.contains(playerName) || isPartOfName) {
                nameStartIndex = i;
                break;
            }
        }

        if (nameStartIndex == -1) {
            return message;
        }

        // Find the end of the player name
        int nameEndIndex = nameStartIndex;
        for (int i = nameStartIndex; i < children.size(); i++) {
            Component child = children.get(i);
            String childText = PlainTextComponentSerializer.plainText().serialize(child);

            if (childText.length() > 2 || (!childText.matches("[a-zA-Z0-9]") && !childText.isEmpty())) {
                break;
            }

            if (childText.matches("[a-zA-Z0-9]")) {
                nameEndIndex = i;
            }
        }

        // Apply hover to all components in the name range
        for (int i = nameStartIndex; i <= nameEndIndex; i++) {
            children.set(i, addHoverRecursive(children.get(i), hoverComponent));
            modified = true;
        }

        return modified ? message.children(children) : message;
    }

    /**
     * Adds hover events to all other player names mentioned in the message
     */
    public Component addHoverToPlayerNamesInMessage(Component message, Player sender) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            // Skip the sender
            if (target.getUniqueId().equals(sender.getUniqueId())) continue;

            String name = target.getName();
            Component hover = createHoverComponent(target);

            try {
                // Try exact word boundary match first
                Pattern exactPattern = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
                message = message.replaceText(TextReplacementConfig.builder()
                        .match(exactPattern)
                        .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hover)))
                        .build());
            } catch (Throwable t) {
                // Fallback to literal match
                try {
                    message = message.replaceText(TextReplacementConfig.builder()
                            .matchLiteral(name)
                            .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hover)))
                            .build());
                } catch (Throwable ignored) {
                    Bukkit.getLogger().warning("[ChatHover] Failed to add hover for player: " + name);
                }
            }
        }

        return message;
    }

    /**
     * Recursively adds hover event to a component and all its children
     */
    private Component addHoverRecursive(Component component, Component hoverComponent) {
        Component withHover = component.hoverEvent(HoverEvent.showText(hoverComponent));

        if (!component.children().isEmpty()) {
            List<Component> newChildren = new ArrayList<>();
            for (Component child : component.children()) {
                newChildren.add(addHoverRecursive(child, hoverComponent));
            }
            withHover = withHover.children(newChildren);
        }

        return withHover;
    }

    /**
     * Replaces all placeholders in the hover text with actual player data
     */
    private String replaceHoverPlaceholders(String text, Player player) {
        if (text == null) return "";

        // Apply PlaceholderAPI if available
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable e) {
                Bukkit.getLogger().warning("[ChatHover] PlaceholderAPI error: " + e.getMessage());
            }
        }

        // Replace manual placeholders
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_displayname%", player.getDisplayName());
        text = text.replace("%player_uuid%", player.getUniqueId().toString());
        text = text.replace("%player_health%", String.valueOf((int) Math.ceil(player.getHealth())));
        text = text.replace("%player_max_health%", String.valueOf((int) Math.ceil(player.getMaxHealth())));
        text = text.replace("%player_level%", String.valueOf(player.getLevel()));
        text = text.replace("%player_ping%", String.valueOf(getPingSafe(player)));
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%player_x%", String.format(Locale.ENGLISH, "%.1f", player.getLocation().getX()));
        text = text.replace("%player_y%", String.format(Locale.ENGLISH, "%.1f", player.getLocation().getY()));
        text = text.replace("%player_z%", String.format(Locale.ENGLISH, "%.1f", player.getLocation().getZ()));
        text = text.replace("%player_gamemode%", player.getGameMode().name());
        text = text.replace("%luckperms_primary_group%", resolvePrimaryGroup(player));

        // Convert legacy ampersand codes to MiniMessage format
        text = convertLegacyToMiniMessage(text);

        return text;
    }

    /**
     * Converts legacy color codes (&a, &f, etc.) to MiniMessage format
     */
    private String convertLegacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) return text;

        // Replace color codes
        text = text.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&r", "<reset>");

        // Replace formatting codes
        text = text.replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>");

        return text;
    }

    /**
     * Creates tag resolvers for MiniMessage placeholders
     */
    private TagResolver createHoverResolvers(Player player) {
        return TagResolver.resolver(
                Placeholder.unparsed("player_name", player.getName()),
                Placeholder.unparsed("player_displayname", player.getDisplayName()),
                Placeholder.unparsed("player_uuid", player.getUniqueId().toString()),
                Placeholder.unparsed("player_health", String.valueOf((int) Math.ceil(player.getHealth()))),
                Placeholder.unparsed("player_max_health", String.valueOf((int) Math.ceil(player.getMaxHealth()))),
                Placeholder.unparsed("player_level", String.valueOf(player.getLevel())),
                Placeholder.unparsed("player_ping", String.valueOf(getPingSafe(player))),
                Placeholder.unparsed("player_world", player.getWorld().getName()),
                Placeholder.unparsed("player_gamemode", player.getGameMode().name()),
                Placeholder.unparsed("luckperms_primary_group", resolvePrimaryGroup(player))
        );
    }

    private int getPingSafe(Player p) {
        try {
            return p.getPing();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private String resolvePrimaryGroup(Player p) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User u = lp.getPlayerAdapter(Player.class).getUser(p);
            if (u == null) return "default";
            String primary = u.getPrimaryGroup();
            return primary != null ? primary : "default";
        } catch (Throwable ignored) {
            return "default";
        }
    }
}