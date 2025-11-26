// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/GamemodeCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.VisitorService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class GamemodeCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final VisitorService visitors;

    public GamemodeCommand(VisitorService visitors) {
        this.visitors = visitors;
    }

    @Override public String name() { return "gamemode"; }
    @Override public List<String> aliases() { return List.of("gm"); }
    @Override public String permission() { return "oreo.gamemode"; }
    @Override public String usage() {
        return "<survival|creative|adventure|spectator|visitor|0|1|2|3|4|s|c|a|sp|v> [player]";
    }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;

        // Parse mode
        String m = args[0].toLowerCase(Locale.ROOT);
        Mode mode = Mode.fromString(m);
        if (mode == null) {
            sender.sendMessage(ChatColor.RED + "Unknown mode: " + m);
            return true;
        }

        // Target
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("oreo.gamemode.others")) {
                sender.sendMessage("§cYou don't have permission to change others' gamemode.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) return false;
            target = (Player) sender;
        }

        // Apply
        applyMode(target, mode);

        if (target == sender) {
            sender.sendMessage("§aGamemode set to §b" + mode.display + "§a.");
        } else {
            sender.sendMessage("§aSet §b" + target.getName() + "§a to §b" + mode.display + "§a.");
            target.sendMessage("§aYour gamemode was set to §b" + mode.display + "§a by §e" + sender.getName());
        }
        return true;
    }

    private void applyMode(Player p, Mode mode) {
        // Clear “visitor” by default, re-enable only if choosing visitor
        visitors.setVisitor(p.getUniqueId(), false);

        switch (mode) {
            case SURVIVAL -> p.setGameMode(GameMode.SURVIVAL);
            case CREATIVE -> p.setGameMode(GameMode.CREATIVE);
            case ADVENTURE -> p.setGameMode(GameMode.ADVENTURE);
            case SPECTATOR -> p.setGameMode(GameMode.SPECTATOR);
            case VISITOR -> {
                p.setGameMode(GameMode.SURVIVAL);
                visitors.setVisitor(p.getUniqueId(), true);
            }
        }
    }

    enum Mode {
        // vanilla mapping: 0=SURVIVAL, 1=CREATIVE, 2=ADVENTURE, 3=SPECTATOR, 4=VISITOR (custom)
        SURVIVAL("survival", "s", "0"),
        CREATIVE("creative", "c", "1"),
        ADVENTURE("adventure", "a", "2"),
        SPECTATOR("spectator", "sp", "3"),
        VISITOR("visitor", "v", "4"); // survival + blocked interactions

        final String display;
        final String shortKey;
        final String numericKey;

        Mode(String display, String shortKey, String numericKey) {
            this.display = display;
            this.shortKey = shortKey;
            this.numericKey = numericKey;
        }

        static Mode fromString(String s) {
            for (Mode m : values()) {
                if (m.display.equalsIgnoreCase(s)
                        || m.shortKey.equalsIgnoreCase(s)
                        || (m.numericKey != null && m.numericKey.equalsIgnoreCase(s))) {
                    return m;
                }
            }
            return null;
        }
    }

    /* ---- Tab completion ---- */
    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of(
                    "survival", "creative", "adventure", "spectator", "visitor",
                    "0", "1", "2", "3", "4",
                    "s", "c", "a", "sp", "v"
            );
        }
        if (args.length == 2 && sender.hasPermission("oreo.gamemode.others")) {
            String pfx = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return java.util.List.of();
    }
}
