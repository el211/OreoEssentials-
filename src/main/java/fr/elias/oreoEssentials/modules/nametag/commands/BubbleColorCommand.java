package fr.elias.oreoEssentials.modules.nametag.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.nametag.ChatBubbleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /bubblecolor <color|reset>
 *
 * Lets players choose a MiniMessage color for their own chat bubbles.
 * Examples:
 *   /bubblecolor red
 *   /bubblecolor #FF5500
 *   /bubblecolor gradient:red:gold
 *   /bubblecolor reset
 *
 * Permission: oe.chatbubble.color
 */
public final class BubbleColorCommand implements OreoCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern TAG_PATTERN = Pattern.compile("<([^<>]+)>");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-f]{6}", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SIMPLE_TAGS = Set.of(
            "white", "gray", "dark_gray", "black",
            "red", "dark_red",
            "gold", "yellow",
            "green", "dark_green",
            "aqua", "dark_aqua",
            "blue", "dark_blue",
            "light_purple", "dark_purple",
            "bold", "italic", "underlined", "strikethrough", "obfuscated",
            "reset"
    );

    // Preset colors shown in tab completion
    private static final List<String> PRESETS = List.of(
            "reset",
            "white", "gray", "dark_gray", "black",
            "red", "dark_red",
            "gold", "yellow",
            "green", "dark_green",
            "aqua", "dark_aqua",
            "blue", "dark_blue",
            "light_purple", "dark_purple",
            "bold:red",
            "bold:gold",
            "gradient:red:gold",
            "gradient:aqua:light_purple",
            "gradient:green:aqua"
    );
    private static final Map<String, String> STYLE_ALIASES = Map.of(
            "underline", "underlined",
            "strike", "strikethrough",
            "magic", "obfuscated"
    );

    private final OreoEssentials plugin;

    public BubbleColorCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override public String name()          { return "bubblecolor"; }
    @Override public List<String> aliases() { return List.of("bccolor", "chatbubblecolor"); }
    @Override public String permission()    { return "oe.chatbubble.color"; }
    @Override public String usage()         { return "<color|reset>"; }
    @Override public boolean playerOnly()   { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        ChatBubbleService svc = plugin.getChatBubbleService();
        if (svc == null || !svc.isEnabled()) {
            p.sendMessage("§cChat bubbles are not enabled on this server.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(p, svc);
            return true;
        }

        String input = String.join(":", args).trim().toLowerCase(Locale.ROOT);

        // ── Reset ─────────────────────────────────────────────────────────────
        if (input.equals("reset") || input.equals("clear") || input.equals("off")) {
            svc.clearPlayerColor(p.getUniqueId());
            p.sendMessage(MM.deserialize("<gray>Your bubble color has been <green>reset</green> to the server default."));
            return true;
        }

        // ── Build the MiniMessage tag ─────────────────────────────────────────
        // Accept with or without angle brackets:
        //   "red"              → <red>
        //   "<red>"            → <red>  (pass through)
        //   "gradient:red:gold"→ <gradient:red:gold>
        //   "#FF5500"          → <#FF5500>
        String colorTag = buildColorTag(input);

        if (!isValidColorTag(colorTag)) {
            p.sendMessage(MM.deserialize("<red>Invalid color tag: <yellow>" + colorTag
                    + "</yellow>. Use a valid MiniMessage color."));
            return true;
        }

        svc.setPlayerColor(p.getUniqueId(), colorTag);

        // Show a preview so the player can see what it looks like
        Component preview = MM.deserialize(colorTag + "This is how your chat bubbles will look.<reset>");
        p.sendMessage(MM.deserialize("<gray>Bubble color set! Preview:"));
        p.sendMessage(preview);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return PRESETS.stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        return List.of();
    }

    private void sendHelp(Player p, ChatBubbleService svc) {
        String current = svc.getPlayerColor(p.getUniqueId());
        p.sendMessage(MM.deserialize("<gold>/bubblecolor <color></gold> <gray>— Set your bubble text color"));
        p.sendMessage(MM.deserialize("<gold>/bubblecolor reset</gold> <gray>— Reset to server default"));
        p.sendMessage(MM.deserialize("<gray>Examples: <white>red, bold:red, #FF5500, gradient:red:gold"));
        if (current != null) {
            p.sendMessage(MM.deserialize("<gray>Current color: " + current + "this text<reset>"));
        } else {
            p.sendMessage(MM.deserialize("<gray>Current color: <white>server default"));
        }
    }

    private String buildColorTag(String input) {
        if (input.startsWith("<") && input.endsWith(">")) {
            return input;
        }

        if (!input.contains(":")) {
            return "<" + normalizeToken(input) + ">";
        }

        String[] parts = input.split(":");
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = normalizeToken(parts[i]);
            if (part.isBlank()) {
                continue;
            }

            if (part.equals("gradient") || part.equals("transition")) {
                StringBuilder complex = new StringBuilder(part);
                for (int j = i + 1; j < parts.length; j++) {
                    String next = normalizeToken(parts[j]);
                    if (!next.isBlank()) {
                        complex.append(':').append(next);
                    }
                }
                out.append('<').append(complex).append('>');
                break;
            }

            out.append('<').append(part).append('>');
        }

        return out.toString();
    }

    private String normalizeToken(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return STYLE_ALIASES.getOrDefault(normalized, normalized);
    }

    private boolean isValidColorTag(String colorTag) {
        if (colorTag == null || colorTag.isBlank()) return false;

        int consumed = 0;
        Matcher matcher = TAG_PATTERN.matcher(colorTag);
        while (matcher.find()) {
            if (matcher.start() != consumed) return false;
            if (!isValidSingleTag(matcher.group(1))) return false;
            consumed = matcher.end();
        }
        return consumed == colorTag.length();
    }

    private boolean isValidSingleTag(String rawTag) {
        String tag = normalizeToken(rawTag.trim());
        if (tag.isEmpty()) return false;
        if (SIMPLE_TAGS.contains(tag) || HEX_COLOR.matcher(tag).matches()) return true;

        String[] parts = tag.split(":");
        if (parts.length < 2) return false;

        if (parts[0].equals("gradient")) {
            if (parts.length < 3) return false;
            for (int i = 1; i < parts.length; i++) {
                if (!isColorToken(parts[i])) return false;
            }
            return true;
        }

        if (parts[0].equals("transition")) {
            if (parts.length < 3) return false;
            for (int i = 1; i < parts.length; i++) {
                if (!isColorToken(parts[i])) return false;
            }
            return true;
        }

        return false;
    }

    private boolean isColorToken(String token) {
        String normalized = normalizeToken(token);
        return HEX_COLOR.matcher(normalized).matches() || switch (normalized) {
            case "white", "gray", "dark_gray", "black",
                    "red", "dark_red", "gold", "yellow",
                    "green", "dark_green", "aqua", "dark_aqua",
                    "blue", "dark_blue", "light_purple", "dark_purple" -> true;
            default -> false;
        };
    }
}
