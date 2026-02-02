package fr.elias.oreoEssentials.modules.trade.rabbit;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.trade.ItemStacksCodec;
import fr.elias.oreoEssentials.modules.trade.TradeIds;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.*;
import fr.elias.oreoEssentials.modules.trade.service.TradeService;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TradeCrossServerBroker {

    private final OreoEssentials plugin;
    private final PacketManager pm;
    private final String serverName;
    private final TradeService tradeService;

    static final class XSession {
        final UUID id;
        final UUID aId;
        final UUID bId;
        volatile boolean aReady;
        volatile boolean bReady;
        volatile ItemStack[] lastOfferA = new ItemStack[0];
        volatile ItemStack[] lastOfferB = new ItemStack[0];

        XSession(UUID id, UUID aId, UUID bId) {
            this.id = id;
            this.aId = aId;
            this.bId = bId;
        }
    }

    private final Map<UUID, XSession> sessions = new ConcurrentHashMap<>();

    public TradeCrossServerBroker(OreoEssentials plugin, PacketManager packetManager,
                                  String serverName, TradeService tradeService) {
        this.plugin = plugin;
        this.pm = packetManager;
        this.serverName = serverName;
        this.tradeService = tradeService;
        log("[TRADE] BROKER init server=" + serverName
                + " pm=" + (pm != null ? pm.getClass().getSimpleName() : "null")
                + " pmInit=" + (pm != null && pm.isInitialized())
                + " tradedebug=" + dbg());
    }

    private boolean dbg() {
        try {
            return tradeService != null && tradeService.getConfig() != null
                    && tradeService.getConfig().debugDeep;
        } catch (Throwable t) {
            return false;
        }
    }

    private void log(String s) {
        if (dbg()) plugin.getLogger().info(s);
    }

    public void sendInvite(Player requester, UUID targetId, String targetName) {
        log("[TRADE] sendInvite requester=" + (requester != null ? requester.getName() : "null")
                + " target=" + targetName + "/" + targetId);

        if (!checkPM(requester) || requester == null || targetId == null) return;

        UUID sid = TradeIds.computeTradeId(requester.getUniqueId(), targetId);
        sessions.putIfAbsent(sid, new XSession(sid, requester.getUniqueId(), targetId));

        String targetServer = null;
        try {
            var dir = plugin.getPlayerDirectory();
            if (dir != null) targetServer = dir.lookupCurrentServer(targetId);
            else if (dbg()) plugin.getLogger().warning("[TRADE] PlayerDirectory null; cannot resolve target server.");
        } catch (Throwable t) {
            plugin.getLogger().warning("[TRADE] lookupCurrentServer failed: " + t.getMessage());
        }

        TradeInvitePacket pkt = new TradeInvitePacket(requester.getUniqueId(), requester.getName(),
                serverName, targetId, targetName);

        if (targetServer != null && !targetServer.isBlank()) {
            log("[TRADE] sendInvite -> INDIVIDUAL(" + targetServer + ") sid=" + sid);
            pm.sendPacket(PacketChannels.individual(targetServer), pkt);
        } else {
            log("[TRADE] sendInvite -> GLOBAL (fallback) sid=" + sid);
            pm.sendPacket(PacketChannels.GLOBAL, pkt);
        }

        requester.sendMessage("§7Trade request sent to §b" + targetName + " §7(cross-server).");
    }

    public void handleRemoteState(TradeStatePacket packet) {
        if (packet == null) return;

        XSession s = sessions.computeIfAbsent(packet.getSessionId(),
                id -> new XSession(id, packet.getFromPlayerId(), packet.getFromPlayerId()));

        ItemStack[] offer = ItemStacksCodec.decodeFromBytes(packet.getOfferBytes());
        boolean fromA = packet.getFromPlayerId().equals(s.aId);

        log("[TRADE] handleRemoteState sid=" + packet.getSessionId()
                + " from=" + packet.getFromPlayerId()
                + " as=" + (fromA ? "A" : "B")
                + " ready=" + packet.isReady()
                + " bytes=" + (packet.getOfferBytes() != null ? packet.getOfferBytes().length : 0));

        if (fromA) {
            s.aReady = packet.isReady();
            s.lastOfferA = (offer != null ? offer : new ItemStack[0]);
            Player b = Bukkit.getPlayer(s.bId);
            if (b != null) {
                b.sendMessage("§7Partner " + (packet.isReady() ? "§ais ready" : "§cis not ready") + "§7.");
            }
        } else {
            s.bReady = packet.isReady();
            s.lastOfferB = (offer != null ? offer : new ItemStack[0]);
            Player a = Bukkit.getPlayer(s.aId);
            if (a != null) {
                a.sendMessage("§7Partner " + (packet.isReady() ? "§ais ready" : "§cis not ready") + "§7.");
            }
        }
    }

    private PacketChannel currentNodeChannel() {
        return PacketChannels.individual(serverName);
    }

    private String findNodeFor(UUID playerId) {
        try {
            var dir = plugin.getPlayerDirectory();
            return (dir != null) ? dir.lookupCurrentServer(playerId) : null;
        } catch (Throwable t) {
            if (dbg()) plugin.getLogger().warning("[TRADE] findNodeFor failed: " + t.getMessage());
            return null;
        }
    }

    private void publishIndividual(String server, Packet pkt) {
        if (server == null || server.isBlank() || pkt == null || pm == null || !pm.isInitialized()) return;
        pm.sendPacket(PacketChannels.individual(server), pkt);
    }

    public void publishStart(UUID sid, UUID requesterId, String requesterName, Player acceptor) {
        String requesterServer = findNodeFor(requesterId);
        String acceptorServer = findNodeFor(acceptor.getUniqueId());
        publishStartToServers(sid, requesterId, requesterName, requesterServer, acceptorServer,
                acceptor.getUniqueId(), acceptor.getName());
    }

    public void publishStartToServers(UUID sid, UUID requesterId, String requesterName,
                                      String requesterServer, String acceptorServer,
                                      UUID acceptorId, String acceptorName) {
        TradeStartPacket pkt = new TradeStartPacket(sid, requesterId, requesterName, acceptorId, acceptorName);
        if (pm != null && pm.isInitialized()) {
            if (requesterServer != null && !requesterServer.isBlank()) {
                pm.sendPacket(PacketChannels.individual(requesterServer), pkt);
            }
            if (!Objects.equals(requesterServer, acceptorServer)
                    && acceptorServer != null && !acceptorServer.isBlank()) {
                pm.sendPacket(PacketChannels.individual(acceptorServer), pkt);
            }
        }
    }

    public void requestStartReplay(UUID sid) {
    }

    public void handleRemoteStart(TradeStartPacket p) {
        if (p == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            tradeService.openOrCreateCrossServerSession(p.getSessionId(),
                    p.getRequesterId(), p.getRequesterName(),
                    p.getAcceptorId(), p.getAcceptorName());
        });
    }

    public void startTrade(UUID sid, UUID requesterId, String requesterName, Player acceptor) {
        publishStart(sid, requesterId, requesterName, acceptor);
    }

    public void acceptInvite(Player acceptor, UUID requesterUuid, String requesterName) {
        log("[TRADE] acceptInvite acceptor=" + (acceptor != null ? acceptor.getName() : "null")
                + " requester=" + requesterName + "/" + requesterUuid);

        if (!checkPM(acceptor) || acceptor == null || requesterUuid == null) return;

        UUID sid = TradeIds.computeTradeId(requesterUuid, acceptor.getUniqueId());
        sessions.putIfAbsent(sid, new XSession(sid, requesterUuid, acceptor.getUniqueId()));

        String requesterServer = findNodeFor(requesterUuid);
        String acceptorServer = serverName;
        TradeStartPacket start = new TradeStartPacket(sid, requesterUuid, requesterName,
                acceptor.getUniqueId(), acceptor.getName());

        boolean sent = false;

        if (requesterServer != null && !requesterServer.isBlank() && pm != null && pm.isInitialized()) {
            log("[TRADE] acceptInvite -> START INDIVIDUAL(" + requesterServer + ") sid=" + sid);
            pm.sendPacket(PacketChannels.individual(requesterServer), start);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pm.sendPacket(PacketChannels.individual(requesterServer), start);
            }, 2L);
            sent = true;
        }

        if (acceptorServer != null && !acceptorServer.isBlank() && pm != null && pm.isInitialized()) {
            if (!Objects.equals(requesterServer, acceptorServer)) {
                log("[TRADE] acceptInvite -> START INDIVIDUAL(" + acceptorServer + ") sid=" + sid);
                pm.sendPacket(PacketChannels.individual(acceptorServer), start);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    pm.sendPacket(PacketChannels.individual(acceptorServer), start);
                }, 2L);
            }
            sent = true;
        }

        if (!sent && pm != null && pm.isInitialized()) {
            log("[TRADE] acceptInvite -> START GLOBAL (fallback) sid=" + sid);
            pm.sendPacket(PacketChannels.GLOBAL, start);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pm.sendPacket(PacketChannels.GLOBAL, start);
            }, 2L);
        }

        acceptor.sendMessage("§aInvite accepted.§7 Opening cross-server trade…");
    }

    public void publishState(UUID sid, Player from, boolean ready, ItemStack[] offer) {
        if (sid == null || from == null) return;
        if (pm == null || !pm.isInitialized()) {
            if (dbg()) plugin.getLogger().warning("[TRADE] publishState: PacketManager not ready.");
            return;
        }

        var session = tradeService != null ? tradeService.getSession(sid) : null;
        if (session == null) {
            if (dbg()) plugin.getLogger().warning("[TRADE] publishState: no TradeSession for sid=" + sid);
            return;
        }

        UUID otherId = from.getUniqueId().equals(session.getAId()) ? session.getBId() : session.getAId();
        if (otherId == null) return;

        String targetServer = null;
        try {
            var dir = plugin.getPlayerDirectory();
            if (dir != null) targetServer = dir.lookupCurrentServer(otherId);
        } catch (Throwable t) {
            if (dbg()) plugin.getLogger().warning("[TRADE] lookupCurrentServer error: " + t.getMessage());
        }

        byte[] bytes = ItemStacksCodec.encodeToBytes(offer);
        TradeStatePacket pkt = new TradeStatePacket(sid, from.getUniqueId(), ready, bytes);

        if (targetServer != null && !targetServer.isBlank()) {
            log("[TRADE] publishState -> INDIVIDUAL(" + targetServer + ") sid=" + sid
                    + " from=" + from.getName() + " ready=" + ready
                    + " bytes=" + (bytes != null ? bytes.length : 0));
            pm.sendPacket(PacketChannels.individual(targetServer), pkt);
        } else {
            log("[TRADE] publishState -> GLOBAL (fallback) sid=" + sid
                    + " from=" + from.getName() + " ready=" + ready
                    + " bytes=" + (bytes != null ? bytes.length : 0));
            pm.sendPacket(PacketChannels.GLOBAL, pkt);
        }

        XSession xs = sessions.computeIfAbsent(sid, id ->
                new XSession(id, session.getAId(), session.getBId()));
        boolean isA = from.getUniqueId().equals(xs.aId);
        if (isA) {
            xs.aReady = ready;
            xs.lastOfferA = (offer != null ? offer : new ItemStack[0]);
        } else {
            xs.bReady = ready;
            xs.lastOfferB = (offer != null ? offer : new ItemStack[0]);
        }
    }

    public void cancel(UUID sid, String reason) {
        XSession s = sessions.remove(sid);
        log("[TRADE] cancel sid=" + sid
                + " reason=" + (reason != null ? reason : "cancelled")
                + " hadSession=" + (s != null)
                + " -> GLOBAL");
        pm.sendPacket(PacketChannels.GLOBAL, new TradeCancelPacket(sid, reason));
    }

    public void sendTradeGrant(UUID sessionId, UUID grantTo, ItemStack[] items) {
        if (sessionId == null || grantTo == null) return;
        if (pm == null || !pm.isInitialized()) return;

        byte[] bytes = ItemStacksCodec.encodeToBytes(items);

        XSession xs = sessions.get(sessionId);
        if (xs == null) {
            log("[TRADE] sendTradeGrant: no XSession found for sid=" + sessionId);
            String recipientNode = findNodeFor(grantTo);
            if (recipientNode != null && !recipientNode.isBlank()) {
                pm.sendPacket(PacketChannels.individual(recipientNode), new TradeGrantPacket(sessionId, grantTo, bytes));
                pm.sendPacket(PacketChannels.individual(recipientNode), new TradeClosePacket(sessionId, grantTo));
            } else {
                pm.sendPacket(PacketChannels.GLOBAL, new TradeGrantPacket(sessionId, grantTo, bytes));
                pm.sendPacket(PacketChannels.GLOBAL, new TradeClosePacket(sessionId, grantTo));
            }
            return;
        }

        String nodeA = findNodeFor(xs.aId);
        String nodeB = findNodeFor(xs.bId);
        String recipientNode = findNodeFor(grantTo);

        log("[TRADE] sendTradeGrant sid=" + sessionId
                + " grantTo=" + grantTo
                + " recipientNode=" + recipientNode
                + " nodeA=" + nodeA
                + " nodeB=" + nodeB);

        if (recipientNode != null && !recipientNode.isBlank()) {
            pm.sendPacket(PacketChannels.individual(recipientNode), new TradeGrantPacket(sessionId, grantTo, bytes));
        } else {
            pm.sendPacket(PacketChannels.GLOBAL, new TradeGrantPacket(sessionId, grantTo, bytes));
        }

        if (nodeA != null && !nodeA.isBlank()) {
            pm.sendPacket(PacketChannels.individual(nodeA), new TradeClosePacket(sessionId, grantTo));
        }
        if (nodeB != null && !nodeB.isBlank() && !nodeB.equals(nodeA)) {
            pm.sendPacket(PacketChannels.individual(nodeB), new TradeClosePacket(sessionId, grantTo));
        }

        if ((nodeA == null || nodeA.isBlank()) && (nodeB == null || nodeB.isBlank())) {
            log("[TRADE] WARNING: couldn't resolve both nodes, sending CLOSE as GLOBAL fallback");
            pm.sendPacket(PacketChannels.GLOBAL, new TradeClosePacket(sessionId, grantTo));
        }
    }

    public void handleRemoteConfirm(TradeConfirmPacket packet) {
        XSession s = sessions.get(packet.getSessionId());
        log("[TRADE] handleRemoteConfirm sid=" + packet.getSessionId() + " hasSession=" + (s != null));
        if (s == null) return;

        if (s.lastOfferA == null || s.lastOfferB == null) {
            log("[TRADE] confirm aborted: missing frozen offers (A=" + (s.lastOfferA != null)
                    + ", B=" + (s.lastOfferB != null) + ")");
            return;
        }

        Player a = Bukkit.getPlayer(s.aId);
        Player b = Bukkit.getPlayer(s.bId);

        String nodeA = findNodeFor(s.aId);
        String nodeB = findNodeFor(s.bId);

        if (a != null && a.isOnline()) {
            byte[] bytes = ItemStacksCodec.encodeToBytes(s.lastOfferB);
            log("[TRADE] send TradeGrant -> A (" + a.getName() + ") items=" + s.lastOfferB.length);
            if (nodeA != null && !nodeA.isBlank()) {
                pm.sendPacket(PacketChannels.individual(nodeA), new TradeGrantPacket(s.id, s.aId, bytes));
                pm.sendPacket(PacketChannels.individual(nodeA), new TradeClosePacket(s.id, s.aId));
            } else {
                log("[TRADE] WARNING: nodeA null, using GLOBAL fallback for grant to A");
                pm.sendPacket(PacketChannels.GLOBAL, new TradeGrantPacket(s.id, s.aId, bytes));
                pm.sendPacket(PacketChannels.GLOBAL, new TradeClosePacket(s.id, s.aId));
            }
        }
        if (b != null && b.isOnline()) {
            byte[] bytes = ItemStacksCodec.encodeToBytes(s.lastOfferA);
            log("[TRADE] send TradeGrant -> B (" + b.getName() + ") items=" + s.lastOfferA.length);
            if (nodeB != null && !nodeB.isBlank()) {
                pm.sendPacket(PacketChannels.individual(nodeB), new TradeGrantPacket(s.id, s.bId, bytes));
                pm.sendPacket(PacketChannels.individual(nodeB), new TradeClosePacket(s.id, s.bId));
            } else {
                log("[TRADE] WARNING: nodeB null, using GLOBAL fallback for grant to B");
                pm.sendPacket(PacketChannels.GLOBAL, new TradeGrantPacket(s.id, s.bId, bytes));
                pm.sendPacket(PacketChannels.GLOBAL, new TradeClosePacket(s.id, s.bId));
            }
        }
    }

    public void handleRemoteCancel(TradeCancelPacket packet) {
        XSession s = sessions.remove(packet.getSessionId());
        log("[TRADE] handleRemoteCancel sid=" + packet.getSessionId()
                + " reason=" + packet.getReason()
                + " hadSession=" + (s != null));

        if (s == null) return;

        Player a = Bukkit.getPlayer(s.aId);
        Player b = Bukkit.getPlayer(s.bId);

        if (a != null) a.sendMessage("§cTrade cancelled: §7" + packet.getReason());
        if (b != null) b.sendMessage("§cTrade cancelled: §7" + packet.getReason());
    }

    public void handleRemoteGrant(TradeGrantPacket packet) {
        if (packet == null) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handleRemoteGrant(packet));
            return;
        }
        tradeService.handleRemoteGrant(packet);
        sessions.remove(packet.getSessionId());
    }

    public void handleRemoteInvite(TradeInvitePacket p, Player targetOnline) {
        log("[TRADE] handleRemoteInvite targetOnline=" + (targetOnline != null ? targetOnline.getName() : "null")
                + " requester=" + p.getRequesterName() + "/" + p.getRequesterId()
                + " reqServer=" + p.getRequesterServer());
        if (targetOnline == null) return;

        tradeService.addIncomingInvite(targetOnline, p.getRequesterId(), p.getRequesterName());
        if (dbg()) plugin.getLogger().info("[TRADE] stored pending invite for " + targetOnline.getName());
    }

    private boolean checkPM(Player notify) {
        boolean ok = (pm != null && pm.isInitialized());
        if (!ok && notify != null) notify.sendMessage("§cCross-server messaging unavailable.");
        log("[TRADE] checkPM ok=" + ok);
        return ok;
    }
}