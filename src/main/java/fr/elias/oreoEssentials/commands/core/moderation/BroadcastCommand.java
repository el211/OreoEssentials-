// File: src/main/java/fr/elias/oreoEssentials/commands/core/moderation/BroadcastCommand.java
package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BroadcastCommand implements OreoCommand {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    @Override public String name() { return "broadcast"; }
    @Override public List<String> aliases() { return List.of("bc"); }
    @Override public String permission() { return "oreo.broadcast"; }
    @Override public String usage() { return "<message...>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) return false;

        String raw = String.join(" ", args);

        // PlaceholderAPI (optional)
        raw = applyPapi(sender, raw);

        // Colors: hex first, then legacy &
        String msg = translateColors(raw);

        // Get prefix from lang (with default)
        String prefix = Lang.msgWithDefault(
                "moderation.broadcast.prefix",
                "<gold>[Broadcast]</gold> ",
                sender instanceof Player ? (Player) sender : null
        );

        // Send to players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(prefix + msg);
        }

        // Also log to console (stripped)
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.stripColor(msg));

        return true;
    }

    /* ---------------- helpers ---------------- */

    private String applyPapi(CommandSender sender, String text) {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                if (sender instanceof Player player) {
                    return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
                } else {
                    return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, text);
                }
            }
        } catch (Throwable ignored) {}
        return text;
    }

    /**
     * Supports:
     *  - Hex colors: &#RRGGBB  (1.16+ clients)
     *  - Legacy & codes: &a, &6, &l, &r, etc.
     */
    private String translateColors(String input) {
        if (input == null || input.isEmpty()) return input;

        // Replace hex patterns with §x§R§R§G§G§B§B
        Matcher m = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String repl = hexToSection(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);

        // Then legacy & codes
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // Build §x§R§R§G§G§B§B sequence for Minecraft hex
    private String hexToSection(String hex) {
        String h = hex.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder("§x");
        for (char c : h.toCharArray()) {
            out.append('§').append(c);
        }
        return out.toString();
    }
}