package fr.elias.oreoEssentials.modules.nametag.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.nametag.ChatBubbleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

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
            "gradient:red:gold",
            "gradient:aqua:light_purple",
            "gradient:green:aqua"
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

        String input = String.join(":", args).toLowerCase();

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
        String colorTag;
        if (input.startsWith("<") && input.endsWith(">")) {
            colorTag = input;
        } else {
            colorTag = "<" + input + ">";
        }

        // Validate: deserialize a test string; if the tag is unknown MiniMessage just
        // ignores it, so we check that the rendered component is actually styled.
        try {
            MM.deserialize(colorTag + "test<reset>");
        } catch (Exception e) {
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
            String partial = args[0].toLowerCase();
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
        p.sendMessage(MM.deserialize("<gray>Examples: <white>red, #FF5500, gradient:red:gold"));
        if (current != null) {
            p.sendMessage(MM.deserialize("<gray>Current color: " + current + "this text<reset>"));
        } else {
            p.sendMessage(MM.deserialize("<gray>Current color: <white>server default"));
        }
    }
}
