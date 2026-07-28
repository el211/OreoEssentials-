package fr.elias.oreoEssentials.modules.mail;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.mail.gui.MailboxMenu;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /mail command.
 *
 * <pre>
 *   /mail                               – open mailbox GUI
 *   /mail send  &lt;player&gt; &lt;message…&gt;    – text mail
 *   /mail senditem &lt;player&gt; [message…]  – send held item + optional text
 *   /mail read                          – text listing (no GUI)
 *   /mail delete &lt;#&gt;                   – delete by index (text listing)
 *   /mail clear                         – clear own mailbox
 *   /mail admin broadcast &lt;message…&gt;   – global text broadcast (oreo.mail.admin)
 *   /mail admin broadcastitem [message…]– global item broadcast (oreo.mail.admin)
 * </pre>
 */
public final class MailCommand implements OreoCommand, TabCompleter {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final List<String> SUBS = List.of("send", "senditem", "read", "delete", "clear", "admin");
    private static final List<String> ADMIN_SUBS = List.of("broadcast", "broadcastitem");

    private final OreoEssentials plugin;
    private final MailService     mail;

    public MailCommand(OreoEssentials plugin, MailService mail) {
        this.plugin = plugin;
        this.mail   = mail;
    }

    @Override public String       name()       { return "mail"; }
    @Override public List<String> aliases()    { return List.of("omail"); }
    @Override public String       permission() { return "oreo.mail"; }
    @Override public String       usage()      { return "send|senditem|read|delete|clear|admin"; }
    @Override public boolean      playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {

        // No args → open GUI (players only) or show usage (console)
        if (args.length == 0) {
            if (sender instanceof Player p) {
                MailboxMenu.getInventory(plugin, mail).open(p);
            } else {
                sendUsage(sender, label);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "send"          -> handleSend(sender, label, args);
            case "senditem"      -> handleSendItem(sender, label, args);
            case "read"          -> handleRead(sender);
            case "delete"        -> handleDelete(sender, args);
            case "clear"         -> {
                boolean confirmed = args.length >= 2 && args[1].equals("--confirmed");
                if (!confirmed && sender instanceof Player cp) {
                    var dm = plugin.getDialogManager();
                    if (dm != null && dm.confirmMailClear(cp)) break;
                }
                handleClear(sender);
            }
            case "admin"         -> handleAdmin(sender, label, args);
            default              -> sendUsage(sender, label);
        }
        return true;
    }

    // ── /mail send ────────────────────────────────────────────────────────────

    private void handleSend(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("oreo.mail.send")) {
            sender.sendMessage(c("&cYou don't have permission to send mail."));
            return;
        }
        if (args.length < 3) {
            Lang.send(sender, "mail.send.usage",
                    "<yellow>Usage: /%label% send <player> <message…></yellow>",
                    Map.of("label", label));
            return;
        }

        String targetName = args[1];
        String message    = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (message.length() > 256) {
            Lang.send(sender, "mail.send.too-long", "<red>Message too long (max 256 characters).</red>");
            return;
        }

        UUID senderUuid = (sender instanceof Player p) ? p.getUniqueId() : null;

        OfflinePlayer target = resolveOffline(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            Lang.send(sender, "mail.send.not-found",
                    "<red>Player <white>%player%</white> not found.</red>",
                    Map.of("player", targetName));
            return;
        }
        if (sender instanceof Player p && target.getUniqueId().equals(p.getUniqueId())) {
            Lang.send(sender, "mail.send.self", "<red>You cannot mail yourself.</red>");
            return;
        }

        String displayName = target.getName() != null ? target.getName() : targetName;
        mail.sendTextMail(senderUuid, sender.getName(), target.getUniqueId(), message)
            .thenRun(() -> {
                Lang.send(sender, "mail.send.success",
                        "<green>Mail sent to <aqua>%player%</aqua>.</green>",
                        Map.of("player", displayName));
            });
    }

    // ── /mail senditem ────────────────────────────────────────────────────────

    private void handleSendItem(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(c("&cThis command is player-only."));
            return;
        }
        if (!p.hasPermission("oreo.mail.senditem")) {
            p.sendMessage(c("&cYou don't have permission to send item mail."));
            return;
        }
        if (args.length < 2) {
            Lang.send(p, "mail.senditem.usage",
                    "<yellow>Usage: /%label% senditem <player> [message…]</yellow>",
                    Map.of("label", label));
            return;
        }

        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            Lang.send(p, "mail.senditem.no-item", "<red>Hold an item in your hand to send it.</red>");
            return;
        }

        String targetName = args[1];
        String message    = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : null;

        OfflinePlayer target = resolveOffline(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            Lang.send(p, "mail.send.not-found",
                    "<red>Player <white>%player%</white> not found.</red>",
                    Map.of("player", targetName));
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            Lang.send(p, "mail.send.self", "<red>You cannot mail yourself.</red>");
            return;
        }

        String displayName = target.getName() != null ? target.getName() : targetName;
        ItemStack toSend = held.clone();
        // Remove item from sender's hand
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        mail.sendItemMail(p.getUniqueId(), p.getName(), target.getUniqueId(), toSend, message)
            .thenRun(() ->
                Lang.send(p, "mail.senditem.success",
                        "<green>Item mailed to <aqua>%player%</aqua>.</green>",
                        Map.of("player", displayName)))
            .exceptionally(ex -> {
                p.sendMessage(c("&cFailed to send item mail: " + ex.getMessage()));
                // Return item on failure
                MailService.giveOrDrop(p, toSend);
                return null;
            });
    }

    // ── /mail read (text-mode listing) ────────────────────────────────────────

    private void handleRead(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(c("&cThis command is player-only."));
            return;
        }

        mail.getMailbox(p.getUniqueId()).thenAccept(msgs -> {
            if (msgs.isEmpty()) {
                Lang.send(p, "mail.read.empty", "<gray>Your mailbox is empty.</gray>");
                return;
            }
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8&m      &r &6&lMailbox &8(&e" + msgs.size() + " message(s)&8) &8&m      "));
            for (int i = 0; i < msgs.size(); i++) {
                var m = msgs.get(i);
                String date   = DATE_FMT.format(new Date(m.getSentAt()));
                String status = m.isRead() ? "&8[read]  " : "&a[new]  ";
                String type   = m.hasItem() ? (m.isItemClaimed() ? " &8[item✓]" : " &6[item]") : "";
                p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&e" + (i + 1) + "&8] " + status + " &7From &e" + m.getSenderName()
                        + " &8| " + date + type));
                if (m.hasMessage()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "   &f" + m.getMessage()));
                }
                if (m.hasItem() && !m.isItemClaimed()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "   &6(Has an item — use &f/mail &6to open your mailbox and claim it)"));
                }
            }
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8&m      &r &7/mail delete <#> &8| &7/mail clear &8| &7/mail (GUI) &8&m      "));
            mail.markAllRead(p.getUniqueId());
        });
    }

    // ── /mail delete ──────────────────────────────────────────────────────────

    private void handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(c("&cThis command is player-only."));
            return;
        }
        if (args.length < 2) {
            Lang.send(p, "mail.delete.usage", "<yellow>Usage: /mail delete <number></yellow>");
            return;
        }
        int idx;
        try { idx = Integer.parseInt(args[1]) - 1; }
        catch (NumberFormatException e) {
            Lang.send(p, "mail.delete.invalid", "<red>Please provide a valid mail number.</red>");
            return;
        }

        mail.getMailbox(p.getUniqueId()).thenAccept(msgs -> {
            if (idx < 0 || idx >= msgs.size()) {
                Lang.send(p, "mail.delete.not-found",
                        "<red>Mail #%n% not found.</red>", Map.of("n", String.valueOf(idx + 1)));
                return;
            }
            mail.deleteMail(p.getUniqueId(), msgs.get(idx).getId()).thenRun(() ->
                    Lang.send(p, "mail.delete.success",
                            "<green>Mail #%n% deleted.</green>", Map.of("n", String.valueOf(idx + 1))));
        });
    }

    // ── /mail clear ───────────────────────────────────────────────────────────

    private void handleClear(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(c("&cThis command is player-only."));
            return;
        }
        if (!p.hasPermission("oreo.mail.clear")) {
            p.sendMessage(c("&cYou don't have permission to clear your mail."));
            return;
        }
        mail.clearMail(p.getUniqueId()).thenRun(() ->
                Lang.send(p, "mail.clear.success", "<green>Your mailbox has been cleared.</green>"));
    }

    // ── /mail admin ───────────────────────────────────────────────────────────

    private void handleAdmin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("oreo.mail.admin")) {
            sender.sendMessage(c("&cYou don't have permission to use admin mail commands."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(c("&eUsage: &f/" + label + " admin broadcast <message>"));
            sender.sendMessage(c("&eUsage: &f/" + label + " admin broadcastitem [message]"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "broadcast"     -> handleAdminBroadcast(sender, args);
            case "broadcastitem" -> handleAdminBroadcastItem(sender, args);
            default              -> {
                sender.sendMessage(c("&eUsage: &f/" + label + " admin broadcast|broadcastitem"));
            }
        }
    }

    private void handleAdminBroadcast(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(c("&eUsage: &f/mail admin broadcast <message…>"));
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        mail.broadcastText(sender.getName(), message);
        sender.sendMessage(c("&a[Mail] Broadcast sent to all players (online now + on next join for offline)."));
    }

    private void handleAdminBroadcastItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(c("&cThis command is player-only (requires a held item)."));
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            p.sendMessage(c("&cHold an item in your hand to broadcast it."));
            return;
        }
        String message = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : null;

        ItemStack toSend = held.clone();
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        mail.broadcastItem(p.getName(), toSend, message);
        p.sendMessage(c("&a[Mail] Item broadcast sent to all players (online now + on next join for offline)."));
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    private static void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(c("&6/&e" + label + " &7send <player> <message>"));
        sender.sendMessage(c("&6/&e" + label + " &7senditem <player> [message]"));
        sender.sendMessage(c("&6/&e" + label + " &7read"));
        sender.sendMessage(c("&6/&e" + label + " &7delete <#>"));
        sender.sendMessage(c("&6/&e" + label + " &7clear"));
        if (sender.hasPermission("oreo.mail.admin")) {
            sender.sendMessage(c("&6/&e" + label + " &7admin broadcast <message>"));
            sender.sendMessage(c("&6/&e" + label + " &7admin broadcastitem [message]"));
        }
    }

    private static OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    private static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();

        if (args.length == 1) {
            List<String> visible = new ArrayList<>(SUBS);
            if (!sender.hasPermission("oreo.mail.admin")) visible.remove("admin");
            return visible.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ((sub.equals("send") || sub.equals("senditem")) && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (sub.equals("admin") && args.length == 2 && sender.hasPermission("oreo.mail.admin")) {
            return ADMIN_SUBS.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
