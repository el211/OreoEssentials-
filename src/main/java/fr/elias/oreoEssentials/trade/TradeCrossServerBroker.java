// File: src/main/java/fr/elias/oreoEssentials/trade/TradeCrossServerBroker.java
package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.*;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-server trade broker.
 * - Sends/receives lightweight trade packets via RabbitMQ.
 * - Keeps a minimal mirror of session readiness/offers to drive auto-confirm/grant.
 * - Actual GUI lifecycle lives in TradeService/TradeSession (SmartInvs).
 *
 * Deep debug logs gated by TradeConfig#debugDeep.
 */
public final class TradeCrossServerBroker {

    private final OreoEssentials plugin;
    private final PacketManager pm;
    private final String serverName;
    private final TradeService tradeService;

    public TradeCrossServerBroker(OreoEssentials plugin,
                                  PacketManager packetManager,
                                  String serverName,
                                  TradeService tradeService) {
        this.plugin = plugin;
        this.pm = packetManager;
        this.serverName = serverName;
        this.tradeService = tradeService;
        log("[TRADE] BROKER init"
                + " server=" + serverName
                + " pm=" + (pm != null ? pm.getClass().getSimpleName() : "null")
                + " pmInit=" + (pm != null && pm.isInitialized())
                + " tradedebug=" + dbg());
    }

    /* ---------------------------------------------------------------------
     * Debug helpers
     * --------------------------------------------------------------------- */
    private boolean dbg() {
        try {
            return tradeService != null
                    && tradeService.getConfig() != null
                    && tradeService.getConfig().debugDeep;
        } catch (Throwable t) {
            return false;
        }
    }
    private void log(String s) { if (dbg()) plugin.getLogger().info(s); }

    /* ---------------------------------------------------------------------
     * Minimal in-memory mirror for cross-server sessions
     * --------------------------------------------------------------------- */
    static final class XSession {
        final UUID id;
        final UUID aId;
        final UUID bId;
        volatile boolean aReady;
        volatile boolean bReady;
        volatile ItemStack[] lastOfferA = new ItemStack[0];
        volatile ItemStack[] lastOfferB = new ItemStack[0];
        XSession(UUID id, UUID aId, UUID bId) { this.id = id; this.aId = aId; this.bId = bId; }
    }

    private final Map<UUID, XSession> sessions = new ConcurrentHashMap<>();

    /* =====================================================================
     * Outbound (called by commands/services)
     * ===================================================================== */

    /** /trade -> cross-server invite (no GUI yet). */
    public void sendInvite(Player requester, UUID targetId, String targetName) {
        log("[TRADE] sendInvite"
                + " requester=" + (requester != null ? requester.getName() : "null")
                + " target=" + targetName + "/" + targetId);

        if (!checkPM(requester) || requester == null || targetId == null) return;

        // Stable SID for this pair (order-independent)
        UUID sid = TradeIds.computeTradeId(requester.getUniqueId(), targetId);
        sessions.putIfAbsent(sid, new XSession(sid, requester.getUniqueId(), targetId));

        // Prefer routing directly to target's server
        String targetServer = null;
        try {
            var dir = plugin.getPlayerDirectory();
            if (dir != null) targetServer = dir.lookupCurrentServer(targetId);
            else if (dbg()) plugin.getLogger().warning("[TRADE] PlayerDirectory null; cannot resolve target server.");
        } catch (Throwable t) {
            plugin.getLogger().warning("[TRADE] lookupCurrentServer failed: " + t.getMessage());
        }

        TradeInvitePacket pkt = new TradeInvitePacket(
                requester.getUniqueId(),
                requester.getName(),
                serverName,
                targetId,
                targetName
        );

        if (targetServer != null && !targetServer.isBlank()) {
            log("[TRADE] sendInvite -> INDIVIDUAL(" + targetServer + ") sid=" + sid);
            pm.sendPacket(PacketChannels.individual(targetServer), pkt);
        } else {
            log("[TRADE] sendInvite -> GLOBAL (fallback) sid=" + sid);
            pm.sendPacket(PacketChannels.GLOBAL, pkt);
        }

        requester.sendMessage("§7Trade request sent to §b" + targetName + " §7(cross-server).");
    }
    /** Remote state update (ready/offer changes). */
    public void handleRemoteState(TradeStatePacket packet) {
        if (packet == null) return;

        // Ensure we have a mirror entry for this session
        XSession s = sessions.computeIfAbsent(
                packet.getSessionId(),
                id -> new XSession(id, packet.getFromPlayerId(), packet.getFromPlayerId()) // temp until known
        );

        // Decode the remote offer
        ItemStack[] offer = ItemStacksCodec.decodeFromBytes(packet.getOfferBytes());
        boolean fromA = packet.getFromPlayerId().equals(s.aId);

        log("[TRADE] handleRemoteState sid=" + packet.getSessionId()
                + " from=" + packet.getFromPlayerId()
                + " as=" + (fromA ? "A" : "B")
                + " ready=" + packet.isReady()
                + " bytes=" + (packet.getOfferBytes() != null ? packet.getOfferBytes().length : 0));

        // Update mirror + optionally notify the other side about readiness
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

        // (GUI updates are handled by TradeService.applyRemoteState(...) in the handler)
    }
    private PacketChannel currentNodeChannel() {
        // route to this server's own Individual channel
        return PacketChannels.individual(serverName);
    }
    private String findNodeFor(UUID playerId) {
        try {
            var dir = plugin.getPlayerDirectory(); // your existing directory
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
    /**
     * Canonical START published with given SID.
     * Both servers open local GUIs for their participant.
     */
    public void publishStart(UUID sid, UUID requesterId, String requesterName, Player acceptor) {
        String requesterServer = findNodeFor(requesterId);
        String acceptorServer  = findNodeFor(acceptor.getUniqueId());
        publishStartToServers(sid, requesterId, requesterName, requesterServer, acceptorServer,
                acceptor.getUniqueId(), acceptor.getName());
    }

    // Broker API
    public void publishStartToServers(UUID sid,
                                      UUID requesterId, String requesterName,
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



    // Optional request used by TradeService when state arrives before START.
// Safe no-op if you don't support it.
    public void requestStartReplay(UUID sid) {
        // Intentionally left blank (or send a lightweight replay request packet if you add one later)
    }

    // In TradeCrossServerBroker (recommended place)
    public void handleRemoteStart(TradeStartPacket p) {
        if (p == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            tradeService.openOrCreateCrossServerSession(
                    p.getSessionId(),
                    p.getRequesterId(), p.getRequesterName(),
                    p.getAcceptorId(),  p.getAcceptorName()
            );
        });
    }


    /** Alias used by reflection fallback from TradeService. */
    public void startTrade(UUID sid, UUID requesterId, String requesterName, Player acceptor) {
        publishStart(sid, requesterId, requesterName, acceptor);
    }

    /** Legacy accept path -> computes SID then sends START. */
    /** Accept path -> computes SID then START to both per-server channels (no GLOBAL unless needed). */
    public void acceptInvite(Player acceptor, UUID requesterUuid, String requesterName) {
        log("[TRADE] acceptInvite"
                + " acceptor=" + (acceptor != null ? acceptor.getName() : "null")
                + " requester=" + requesterName + "/" + requesterUuid);

        if (!checkPM(acceptor) || acceptor == null || requesterUuid == null) return;

        // Stable SID for the pair
        UUID sid = TradeIds.computeTradeId(requesterUuid, acceptor.getUniqueId());
        sessions.putIfAbsent(sid, new XSession(sid, requesterUuid, acceptor.getUniqueId()));

        // Resolve node names
        String requesterServer = findNodeFor(requesterUuid);        // e.g. "AfeliaV7"
        String acceptorServer  = serverName;                        // this JVM, e.g. "Ressource"
        TradeStartPacket start = new TradeStartPacket(
                sid, requesterUuid, requesterName, acceptor.getUniqueId(), acceptor.getName()
        );

        boolean sent = false;

        // Send to requester’s home node if known
        if (requesterServer != null && !requesterServer.isBlank() && pm != null && pm.isInitialized()) {
            log("[TRADE] acceptInvite -> START INDIVIDUAL(" + requesterServer + ") sid=" + sid);
            pm.sendPacket(PacketChannels.individual(requesterServer), start);
            sent = true;
        }

        // Send to acceptor’s node (this server) if known (and not same as requester’s already sent)
        if (acceptorServer != null && !acceptorServer.isBlank() && pm != null && pm.isInitialized()) {
            // avoid duplicate if both names resolve to same node
            if (!Objects.equals(requesterServer, acceptorServer)) {
                log("[TRADE] acceptInvite -> START INDIVIDUAL(" + acceptorServer + ") sid=" + sid);
                pm.sendPacket(PacketChannels.individual(acceptorServer), start);
            }
            sent = true;
        }

        // Last-resort fallback (only if neither server resolved)
        if (!sent && pm != null && pm.isInitialized()) {
            log("[TRADE] acceptInvite -> START GLOBAL (fallback) sid=" + sid);
            pm.sendPacket(PacketChannels.GLOBAL, start);
        }

        acceptor.sendMessage("§aInvite accepted.§7 Opening cross-server trade…");
    }



    /**
     * Called by TradeService on *every* local change (items/ready).
     * Sends a TradeStatePacket to the OTHER player’s server using INDIVIDUAL routing.
     * Falls back to GLOBAL if the directory can’t resolve a server.
     */
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

        // Determine the "other" participant
        UUID otherId = from.getUniqueId().equals(session.getAId()) ? session.getBId() : session.getAId();
        if (otherId == null) return;

        // Where is the other player?
        String targetServer = null;
        try {
            var dir = plugin.getPlayerDirectory();
            if (dir != null) targetServer = dir.lookupCurrentServer(otherId);
        } catch (Throwable t) {
            if (dbg()) plugin.getLogger().warning("[TRADE] lookupCurrentServer error: " + t.getMessage());
        }

        byte[] bytes = ItemStacksCodec.encodeToBytes(offer);
        TradeStatePacket pkt = new TradeStatePacket(
                sid,
                from.getUniqueId(),  // who sent this update
                ready,
                bytes
        );

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

        // Maintain a light mirror for auto-confirm
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

        // Optional: auto-confirm if both are ready
        if (xs.aReady && xs.bReady) {
            log("[TRADE] autoConfirm sid=" + sid + " -> TradeConfirmPacket GLOBAL");
            pm.sendPacket(PacketChannels.GLOBAL, new TradeConfirmPacket(sid, from.getUniqueId()));
        }
    }

    /** Cancel session and notify network. */
    public void cancel(UUID sid, String reason) {
        XSession s = sessions.remove(sid);
        log("[TRADE] cancel sid=" + sid
                + " reason=" + (reason != null ? reason : "cancelled")
                + " hadSession=" + (s != null)
                + " -> GLOBAL");
        pm.sendPacket(PacketChannels.GLOBAL, new TradeCancelPacket(sid, reason));
    }

    /* =====================================================================
     * Grants (public API + inbound handling)
     * ===================================================================== */

    /**
     * PUBLIC API used by TradeService when both sides are ready.
     * Publishes a TradeGrantPacket giving {@code items} to {@code grantTo}.
     */
    public void sendTradeGrant(UUID sessionId, UUID grantTo, ItemStack[] items) {
        if (sessionId == null || grantTo == null) return;
        if (pm == null || !pm.isInitialized()) return;

        byte[] bytes = ItemStacksCodec.encodeToBytes(items);

        String node = findNodeFor(grantTo); // use your directory
        if (node != null && !node.isBlank()) {
            pm.sendPacket(PacketChannels.individual(node), new TradeGrantPacket(sessionId, grantTo, bytes));
            pm.sendPacket(PacketChannels.individual(node), new TradeClosePacket(sessionId, grantTo));
        } else {
            // last-resort fallback
            pm.sendPacket(PacketChannels.GLOBAL, new TradeGrantPacket(sessionId, grantTo, bytes));
            pm.sendPacket(PacketChannels.GLOBAL, new TradeClosePacket(sessionId, grantTo));
        }
    }

    /** Both sides confirmed; emit TradeGrant for whichever participant is local here. */
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

        if (a != null && a.isOnline()) {
            byte[] bytes = ItemStacksCodec.encodeToBytes(s.lastOfferB);
            log("[TRADE] send TradeGrant -> A (" + a.getName() + ") items=" + s.lastOfferB.length);
            pm.sendPacket(PacketChannels.GLOBAL, new TradeGrantPacket(s.id, s.aId, bytes));
            pm.sendPacket(PacketChannels.GLOBAL, new TradeClosePacket(s.id, s.aId));

        }
        if (b != null && b.isOnline()) {
            byte[] bytes = ItemStacksCodec.encodeToBytes(s.lastOfferA);
            log("[TRADE] send TradeGrant -> B (" + b.getName() + ") items=" + s.lastOfferA.length);
            pm.sendPacket(PacketChannels.GLOBAL, new TradeGrantPacket(s.id, s.bId, bytes));
            pm.sendPacket(PacketChannels.GLOBAL, new TradeClosePacket(s.id, s.bId));

        }
    }

    /** Remote cancel (user aborted or timeout). */
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
        // GUI cleanup/refund is handled by TradeService on cancel packet.
    }

    /** Remote grant delivering items to a local player. */
    public void handleRemoteGrant(TradeGrantPacket packet) {
        if (packet == null) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handleRemoteGrant(packet));
            return;
        }
        // single source of truth handles give/GUI/session/dedupe
        tradeService.handleRemoteGrant(packet);
        // clean lightweight mirror
        sessions.remove(packet.getSessionId());
    }



    /* =====================================================================
     * Invites (inbound handling)
     * ===================================================================== */

    /**
     * Remote invite arrived for a local player (targetOnline is already resolved by the handler).
     * Mirrors the invite into TradeService so /trade <name> can accept it locally,
     * and optionally notifies the player.
     */
    public void handleRemoteInvite(TradeInvitePacket p, Player targetOnline) {
        log("[TRADE] handleRemoteInvite targetOnline=" + (targetOnline != null ? targetOnline.getName() : "null")
                + " requester=" + p.getRequesterName() + "/" + p.getRequesterId()
                + " reqServer=" + p.getRequesterServer());
        if (targetOnline == null) return;

        // Store invite so the player can /trade <requesterName>
        tradeService.addIncomingInvite(targetOnline, p.getRequesterId(), p.getRequesterName());
        if (dbg()) plugin.getLogger().info("[TRADE] stored pending invite for " + targetOnline.getName());
    }

    /* ---------------------------------------------------------------------
     * Helpers
     * --------------------------------------------------------------------- */
    private boolean checkPM(Player notify) {
        boolean ok = (pm != null && pm.isInitialized());
        if (!ok && notify != null) notify.sendMessage("§cCross-server messaging unavailable.");
        log("[TRADE] checkPM ok=" + ok);
        return ok;
    }
}
