package fr.elias.oreoEssentials.modules.mail;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class MailCommand implements OreoCommand, TabCompleter {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final List<String> SUBS = List.of("send", "read", "delete", "clear");

    private final MailService mail;

    public MailCommand(MailService mail) {
        this.mail = mail;
    }

    @Override public String       name()       { return "mail"; }
    @Override public List<String> aliases()    { return List.of("omail"); }
    @Override public String       permission() { return "oreo.mail"; }
    @Override public String       usage()      { return "send|read|delete|clear"; }
    @Override public boolean      playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length == 0) {
            sendUsage(p, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "send"   -> handleSend(p, label, args);
            case "read"   -> handleRead(p);
            case "delete" -> handleDelete(p, args);
            case "clear"  -> handleClear(p);
            default       -> sendUsage(p, label);
        }
        return true;
    }

    // -----------------------------------------------------------------------

    private void handleSend(Player p, String label, String[] args) {
        if (args.length < 3) {
            Lang.send(p, "mail.send.usage",
                    "<yellow>Usage: /%label% send <player> <message...></yellow>",
                    Map.of("label", label));
            return;
        }

        String targetName = args[1];
        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (message.length() > 256) {
            Lang.send(p, "mail.send.too-long", "<red>Message too long (max 256 characters).</red>");
            return;
        }

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
        mail.sendMail(target.getUniqueId(), p.getName(), p.getUniqueId(), message);

        Lang.send(p, "mail.send.success",
                "<green>Mail sent to <aqua>%player%</aqua>.</green>",
                Map.of("player", displayName));

        // Notify online target
        if (target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null) {
                Lang.send(online, "mail.receive.notify",
                        "<gold>[Mail] <aqua>%sender%</aqua> sent you a mail! Use <white>/mail read</white> to read it.</gold>",
                        Map.of("sender", p.getName()));
            }
        }
    }

    private void handleRead(Player p) {
        List<MailService.MailMessage> msgs = mail.getMail(p.getUniqueId());
        if (msgs.isEmpty()) {
            Lang.send(p, "mail.read.empty", "<gray>Your mailbox is empty.</gray>");
            return;
        }

        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8&m      &r &6&lMailbox &8(&e" + msgs.size() + " message(s)&8) &8&m      "));
        for (int i = 0; i < msgs.size(); i++) {
            MailService.MailMessage m = msgs.get(i);
            String date = DATE_FMT.format(new Date(m.timestamp()));
            String status = m.read() ? "&8[read]" : "&a[new] ";
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&e" + (i + 1) + "&8] " + status + " &7From &e" + m.sender()
                    + " &8| " + date));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "   &f" + m.message()));
        }
        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8&m      &r &7/mail delete <#> &8| &7/mail clear &8&m      "));

        mail.markAllRead(p.getUniqueId());
    }

    private void handleDelete(Player p, String[] args) {
        if (args.length < 2) {
            Lang.send(p, "mail.delete.usage",
                    "<yellow>Usage: /mail delete <number></yellow>");
            return;
        }
        int idx;
        try {
            idx = Integer.parseInt(args[1]) - 1;
        } catch (NumberFormatException e) {
            Lang.send(p, "mail.delete.invalid", "<red>Please provide a valid mail number.</red>");
            return;
        }
        if (mail.deleteMail(p.getUniqueId(), idx)) {
            Lang.send(p, "mail.delete.success",
                    "<green>Mail #%n% deleted.</green>",
                    Map.of("n", String.valueOf(idx + 1)));
        } else {
            Lang.send(p, "mail.delete.not-found",
                    "<red>Mail #%n% not found.</red>",
                    Map.of("n", String.valueOf(idx + 1)));
        }
    }

    private void handleClear(Player p) {
        mail.clearMail(p.getUniqueId());
        Lang.send(p, "mail.clear.success", "<green>Your mailbox has been cleared.</green>");
    }

    // -----------------------------------------------------------------------

    private void sendUsage(Player p, String label) {
        Lang.send(p, "mail.usage",
                "<yellow>/%label% send <player> <message></yellow>\n"
                + "<yellow>/%label% read</yellow>\n"
                + "<yellow>/%label% delete <#></yellow>\n"
                + "<yellow>/%label% clear</yellow>",
                Map.of("label", label));
    }

    private static OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(typed)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
