package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;
import fr.elias.oreoEssentials.services.TeleportService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class TpaCommand implements OreoCommand {

    private final TeleportService teleportService;

    public TpaCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override public String name() { return "tpa"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tpa"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    // ---------- debug helpers ----------
    private static String traceId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(2176782336L), 36).toUpperCase(Locale.ROOT);
    }
    private static String ms(long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        return ms + "ms";
    }
    private boolean dbg() {
        try {
            var c = OreoEssentials.get().getConfig();
            return c.getBoolean("features.tpa.debug", c.getBoolean("debug", false));
        } catch (Throwable ignored) { return false; }
    }
    private boolean echo() {
        try { return OreoEssentials.get().getConfig().getBoolean("features.tpa.debug-echo-to-player", false); }
        catch (Throwable ignored) { return false; }
    }
    private void D(String id, String msg) { if (dbg()) OreoEssentials.get().getLogger().info("[TPA " + id + "] " + msg); }
    private void E(String id, String msg, Throwable t) { if (dbg()) OreoEssentials.get().getLogger().log(Level.WARNING, "[TPA " + id + "] " + msg, t); }
    private void P(Player p, String id, String msg) { if (dbg() && echo()) p.sendMessage("§8[§bTPA§8/§7" + id + "§8] §7" + msg); }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player requester)) return true;

        if (args.length < 1) {
            Lang.send(requester, "tpa.usage", null, Map.of("label", label));
            return true;
        }

        final String id = traceId();
        final long t0 = System.nanoTime();
        final String input = args[0].trim();
        final OreoEssentials plugin = OreoEssentials.get();
        final String localServer = plugin.getConfigService().serverName();
        final var broker = plugin.getTpaBroker();

        D(id, "enter by=" + requester.getName() + " input='" + input + "' server=" + localServer);
        P(requester, id, "start");

        if (input.isEmpty()) {
            Lang.send(requester, "tpa.usage", null, Map.of("label", label));
            D(id, "empty input");
            return true;
        }

        // 1) Try local resolve (exact, UUID, display name)
        long tLocal = System.nanoTime();
        Player local = resolveOnline(input);
        D(id, "localResolve=" + (local == null ? "null" : local.getName()) + " in " + ms(tLocal));
        if (local != null) {
            if (local.equals(requester)) {
                Lang.send(requester, "tpa.self", null, null);
                D(id, "self target");
                return true;
            }
            teleportService.request(requester, local);
            // message "TPA sent to ..."
            Lang.send(requester, "tpa.sent.local", null, Map.of("target", local.getName()));

            D(id, "same-server request queued in " + ms(t0));
            P(requester, id, "same server ✓");
            return true;

        }

        // 2) Directory lookup (Mongo, NO OfflinePlayer)
        long tDir = System.nanoTime();
        var dir = plugin.getPlayerDirectory();
        if (dir == null) {
            D(id, "PlayerDirectory=null (Mongo storage not enabled?)");
            Lang.send(requester, "tpa.not-found.generic", null, Map.of("input", input));
            return true;
        }

        UUID targetUuid = null;
        try {
            targetUuid = dir.lookupUuidByName(input);
            D(id, "lookupUuidByName -> " + targetUuid + " in " + ms(tDir));
            P(requester, id, "directory " + (targetUuid != null ? "hit" : "miss"));
        } catch (Throwable t) {
            E(id, "directory lookup error", t);
        }

        if (targetUuid == null) {
            try {
                targetUuid = UUID.fromString(input);
                D(id, "parsed UUID literal -> " + targetUuid);
            } catch (Exception ignored) {
                D(id, "not a UUID literal");
            }
        }

        if (targetUuid == null) {
            Lang.send(requester, "tpa.not-found.name-hint", null, Map.of("input", input));
            D(id, "no UUID; exit in " + ms(t0));
            return true;
        }

        // 3) Presence (current/last server)
        long tPresence = System.nanoTime();
        String where = null;
        try {
            where = dir.getCurrentOrLastServer(targetUuid);
            D(id, "presence=" + where + " in " + ms(tPresence));
            P(requester, id, "presence: " + (where == null ? "unknown" : where));
        } catch (Throwable t) {
            E(id, "presence lookup error", t);
        }

        // sanity re-check: if presence says "same server" try UUID-online again (race)
        if (where != null && where.equalsIgnoreCase(localServer)) {
            long tUuid = System.nanoTime();
            Player byId = Bukkit.getPlayer(targetUuid);
            D(id, "sameServerPresence -> UUID-online=" + (byId != null) + " in " + ms(tUuid));
            if (byId != null) {
                if (byId.equals(requester)) {
                    Lang.send(requester, "tpa.self", null, null);
                    return true;
                }
                teleportService.request(requester, byId);
                D(id, "same-server via UUID ok in " + ms(t0));
                P(requester, id, "same server (UUID) ✓");
                return true;
            }
            D(id, "presence said same, but not online now (race?)");
        }

        // 4) Known other server → send cross-server TPA request and ping that server
        if (where != null && !where.isBlank() && !where.equalsIgnoreCase(localServer)) {
            String shownName = safeNameLookup(dir, targetUuid, input);

            if (broker != null) {
                // PATCH: pass target name to handle cracked/offline UUIDs
                broker.sendRequestToServer(requester, targetUuid, shownName, where);
                Lang.send(requester, "tpa.sent.cross-server", null, Map.of("target", shownName, "server", where));
                D(id, "cross-server -> request sent via broker to " + where + " (name=" + shownName + ")");
            } else {
                Lang.send(requester, "tpa.broker-unavailable", null, Map.of("target", shownName));
                D(id, "cross-server -> broker null; cannot send request");
            }

            tryPerServerPing(plugin, where, targetUuid,
                    "§b" + requester.getName() + "§7 requested to teleport to you. Type §a/tpaccept§7 or §c/tpdeny§7.", id);
            D(id, "done in " + ms(t0));
            return true;
        }

        // 5) Presence unknown → broadcast GLOBAL request and ping
        if (where == null || where.isBlank()) {
            D(id, "presence unknown -> attempting GLOBAL broker request + ping");
            String shownName = safeNameLookup(dir, targetUuid, input);

            if (broker != null) {
                // PATCH: pass target name on GLOBAL too
                broker.sendRequestGlobal(requester, targetUuid, shownName);
                P(requester, id, "broker global request ✓");
            } else {
                D(id, "broker null; cannot send global request");
            }

            tryGlobalPing(plugin, targetUuid,
                    "§b" + requester.getName() + "§7 requested to teleport to you. Type §a/tpaccept§7 or §c/tpdeny§7.", id);

            Lang.send(requester, "tpa.sent.global", null, Map.of("target", shownName));
            D(id, "presence unknown -> GLOBAL request done in " + ms(t0));
            return true;
        }

        // 6) Last-chance UUID-online (rare race)
        long tUuid = System.nanoTime();
        Player byId = Bukkit.getPlayer(targetUuid);
        D(id, "lastChance UUID-online=" + (byId != null) + " in " + ms(tUuid));
        if (byId != null) {
            if (byId.equals(requester)) {
                Lang.send(requester, "tpa.self", null, null);
                return true;
            }
            teleportService.request(requester, byId);
            D(id, "last-chance UUID accepted in " + ms(t0));
            P(requester, id, "found (late) ✓");
            return true;
        }

        Lang.send(requester, "tpa.not-found.generic", null, Map.of("input", input));
        D(id, "final not found in " + ms(t0));
        return true;
    }

    // ---------- helpers ----------

    private Player resolveOnline(String input) {
        Player p = Bukkit.getPlayerExact(input);
        if (p != null) return p;

        final String want = input.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(want)) return online;
        }
        try {
            UUID id = UUID.fromString(input);
            Player byId = Bukkit.getPlayer(id);
            if (byId != null) return byId;
        } catch (Exception ignored) {}

        for (Player online : Bukkit.getOnlinePlayers()) {
            String dn = online.getDisplayName();
            if (dn != null) {
                String stripped = org.bukkit.ChatColor.stripColor(dn);
                if (stripped != null && stripped.equalsIgnoreCase(input)) return online;
            }
        }
        return null;
    }

    private String safeNameLookup(fr.elias.oreoEssentials.playerdirectory.PlayerDirectory dir,
                                  UUID uuid, String fallback) {
        try {
            String n = dir.lookupNameByUuid(uuid);
            return (n == null || n.isBlank()) ? fallback : n;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private boolean tryPerServerPing(OreoEssentials plugin, String serverName, UUID targetUuid, String msg, String id) {
        long tPing = System.nanoTime();
        PacketManager pm = plugin.getPacketManager();
        if (pm == null) { D(id, "PacketManager=null (Rabbit disabled?)"); return false; }
        if (!pm.isInitialized()) { D(id, "PacketManager !initialized"); return false; }
        try {
            var channel = PacketChannel.individual(serverName);
            D(id, "PUBLISH channel=" + channel + " target=" + targetUuid);
            pm.sendPacket(channel, new SendRemoteMessagePacket(targetUuid, msg));
            D(id, "per-server ping -> server=" + serverName + " uuid=" + targetUuid + " in " + ms(tPing));
            return true;
        } catch (Throwable t) {
            E(id, "per-server ping failed", t);
            return false;
        }
    }

    private boolean tryGlobalPing(OreoEssentials plugin, UUID targetUuid, String msg, String id) {
        long tPing = System.nanoTime();
        PacketManager pm = plugin.getPacketManager();
        if (pm == null) { D(id, "PacketManager=null (Rabbit disabled?)"); return false; }
        if (!pm.isInitialized()) { D(id, "PacketManager !initialized"); return false; }
        try {
            D(id, "PUBLISH channel=[global] target=" + targetUuid);
            pm.sendPacket(PacketChannels.GLOBAL, new SendRemoteMessagePacket(targetUuid, msg));
            D(id, "GLOBAL ping -> uuid=" + targetUuid + " in " + ms(tPing));
            return true;
        } catch (Throwable t) {
            E(id, "GLOBAL ping failed", t);
            return false;
        }
    }
}
