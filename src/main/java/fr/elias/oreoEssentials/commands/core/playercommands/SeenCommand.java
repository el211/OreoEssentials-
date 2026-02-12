// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/SeenCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SeenCommand implements OreoCommand {
    @Override public String name() { return "seen"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.seen"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "seen.usage", "<red>Usage: /seen <player></red>");
            return true;
        }

        String name = args[0];
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op == null || (op.getName() == null && !op.hasPlayedBefore())) {
            Lang.send(sender, "seen.no-data", "<red>No data for that player.</red>",
                    Map.of("player", name));
            return true;
        }

        String displayName = op.getName() != null ? op.getName() : name;

        if (op.isOnline()) {
            Lang.send(sender, "seen.online",
                    "<green>%player% is currently online.</green>",
                    Map.of("player", displayName));
            return true;
        }

        long last = tryGetLong(op, "getLastSeen");      // Paper method (if present)
        if (last <= 0L) last = tryGetLong(op, "getLastLogin"); // Paper method (if present)
        if (last <= 0L) last = op.getLastPlayed();      // Bukkit/Spigot

        if (last <= 0L) {
            Lang.send(sender, "seen.never-joined",
                    "<yellow>%player% has never joined.</yellow>",
                    Map.of("player", displayName));
            return true;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(last));
        String ago = formatAgo(System.currentTimeMillis() - last);

        // Use Lang system with proper color handling
        Lang.send(sender, "seen.last-seen",
                "<gold>%player%</gold> <gray>was last seen at</gray> <aqua>%timestamp%</aqua> <gray>(</gray><yellow>%ago%</yellow> <gray>ago)</gray>",
                Map.of(
                        "player", displayName,
                        "timestamp", timestamp,
                        "ago", ago
                )
        );

        return true;
    }

    private long tryGetLong(OfflinePlayer op, String methodName) {
        try {
            Method m = op.getClass().getMethod(methodName);
            Object v = m.invoke(op);
            if (v instanceof Long l) return l;
            if (v instanceof Number n) return n.longValue();
        } catch (Throwable ignored) {}
        return -1L;
    }

    private String formatAgo(long millis) {
        if (millis < 0) millis = 0;
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.minusDays(days).toHours();
        long mins = d.minusDays(days).minusHours(hours).toMinutes();

        if (days > 0) {
            if (hours > 0) return days + "d " + hours + "h";
            return days + "d";
        }
        if (hours > 0) {
            if (mins > 0) return hours + "h " + mins + "m";
            return hours + "h";
        }
        if (mins > 0) return mins + "m";
        long secs = Math.max(0, d.getSeconds());
        return secs + "s";
    }
}