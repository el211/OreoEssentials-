package fr.elias.oreoEssentials.chat;

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


public final class ChatHoverProvider {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final boolean enabled;
    private final List<String> lines;

    public ChatHoverProvider(boolean enabled, List<String> lines) {
        this.enabled = enabled;
        this.lines = (lines == null ? new ArrayList<>() : new ArrayList<>(lines));

        if (this.lines.isEmpty()) {
            this.lines.add("<gold>Player: <white>%player_name%</white></gold>");
            this.lines.add("<gray>Health: <white>%player_health%/%player_max_health%</white></gray>");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }


    public Component createHoverComponent(Player player) {
        StringBuilder hoverText = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = replaceHoverPlaceholders(lines.get(i), player);
            hoverText.append(line);
            if (i < lines.size() - 1) hoverText.append("\n");
        }

        try {
            return MM.deserialize(hoverText.toString(), createHoverResolvers(player));
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[Chat] Failed to parse hover text: " + e.getMessage());
            return Component.text(hoverText.toString());
        }
    }


    public Component addHoverToNameEverywhere(Component message, Component hoverComponent, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            return message;
        }


        String messagePlain = PlainTextComponentSerializer.plainText().serialize(message);

        try {
            boolean hasHeadTag = messagePlain.contains("<head:");

            if (hasHeadTag) {

                Pattern pattern1 = Pattern.compile("<head:[^>]+>\\s*" + Pattern.quote(targetName));
                Component result = message.replaceText(TextReplacementConfig.builder()
                        .match(pattern1)
                        .replacement((match, builder) -> {
                            return builder.hoverEvent(HoverEvent.showText(hoverComponent));
                        })
                        .build());

                if (result != message) {
                    return result;
                }

                Pattern pattern2 = Pattern.compile(">\\s*" + Pattern.quote(targetName) + "\\b");
                result = message.replaceText(TextReplacementConfig.builder()
                        .match(pattern2)
                        .replacement((match, builder) -> {
                            return builder.hoverEvent(HoverEvent.showText(hoverComponent));
                        })
                        .build());

                if (result != message) {
                    return result;
                }

                Pattern pattern3 = Pattern.compile("\\s+" + Pattern.quote(targetName) + "\\b");
                result = message.replaceText(TextReplacementConfig.builder()
                        .match(pattern3)
                        .replacement((match, builder) -> {
                            return builder.hoverEvent(HoverEvent.showText(hoverComponent));
                        })
                        .build());

                if (result != message) {
                    return result;
                }

            }

            Pattern wordBoundary = Pattern.compile("\\b" + Pattern.quote(targetName) + "\\b");

            Component result = message.replaceText(TextReplacementConfig.builder()
                    .match(wordBoundary)
                    .replacement((match, builder) -> {
                        return builder.hoverEvent(HoverEvent.showText(hoverComponent));
                    })
                    .build());

            // If no matches with word boundary, try literal match as fallback
            String plainAfter = PlainTextComponentSerializer.plainText().serialize(result);
            if (!plainAfter.contains(targetName) || result == message) {
                result = message.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(targetName)
                        .replacement((match, builder) -> {
                            return builder.hoverEvent(HoverEvent.showText(hoverComponent));
                        })
                        .build());
            } else {
            }

            return result;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[ChatHover] Failed to add hover: " + t.getMessage());
            t.printStackTrace();
            return message;
        }
    }


    public Component addHoverToNameSection(Component message, Component hoverComponent, String playerName) {
        String messagePlain = PlainTextComponentSerializer.plainText().serialize(message);
        boolean hasHeadTag = messagePlain.contains("<head:");

        List<Component> children = new ArrayList<>(message.children());
        boolean modified = false;

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

        for (int i = nameStartIndex; i <= nameEndIndex; i++) {
            children.set(i, addHoverRecursive(children.get(i), hoverComponent));
            modified = true;
        }

        if (modified) {
            return message.children(children);
        } else {
            return message;
        }
    }


    public Component addHoverToPlayerNamesInMessage(Component message, Player sender) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(sender.getUniqueId())) continue;

            String name = target.getName();
            Component hover = createHoverComponent(target);

            try {
                Pattern exactPattern = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
                message = message.replaceText(TextReplacementConfig.builder()
                        .match(exactPattern)
                        .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hover)))
                        .build());
            } catch (Throwable t) {
                // If pattern fails, try literal match
                try {
                    message = message.replaceText(TextReplacementConfig.builder()
                            .matchLiteral(name)
                            .replacement((match, builder) -> builder.hoverEvent(HoverEvent.showText(hover)))
                            .build());
                } catch (Throwable ignored) {}
            }
        }

        return message;
    }


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


    private String replaceHoverPlaceholders(String text, Player player) {
        if (text == null) return "";

        // PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {}
        }

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

        return text;
    }


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