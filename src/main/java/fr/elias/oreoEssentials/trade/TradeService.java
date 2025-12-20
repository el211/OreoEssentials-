// File: src/main/java/fr/elias/oreoEssentials/trade/TradeService.java
package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeClosePacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeGrantPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStatePacket;
import fr.elias.oreoEssentials.trade.pending.InMemoryPendingGrantsDao;
import fr.elias.oreoEssentials.trade.pending.PendingGrantsDao;
import fr.elias.oreoEssentials.trade.ui.TradeMenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import java.io.ByteArrayOutputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Orchestrates trading:
 *  - Stores pending invites (TTL).
 *  - Manages TradeSession instances (local + cross-server).
 *  - Bridges sessions to the broker on state changes.
 *  - Finalizes trades when both sides are ready.
 *  - Returns items on cancel/close.
 *  - Delivers TradeGrant packets to locals (online/offline with persistence).
 *
 * Deep debug via TradeConfig#debugDeep.
 */
public final class TradeService implements Listener {

    /* ---------------------------------------------------------------------
     * Constants
     * --------------------------------------------------------------------- */
    public static final long INVITE_TTL_SECONDS = 60L;

    /** Mailbox for early/late TradeState packets (keyed by SID). */
    private final Map<UUID, Deque<TradeStatePacket>> pendingStates = new ConcurrentHashMap<>();

    /* ---------------------------------------------------------------------
     * Plugin & Config
     * --------------------------------------------------------------------- */
    private final OreoEssentials plugin;
    private final TradeConfig cfg;

    private boolean dbg() {
        try { return cfg != null && cfg.debugDeep; } catch (Throwable t) { return false; }
    }
    private void log(String s) { if (dbg()) plugin.getLogger().info(s); }

    /* ---------------------------------------------------------------------
     * Scheduler
     * --------------------------------------------------------------------- */
    private int cleanupTaskId = 0; // Bukkit task id; 0 = none

    /* ---------------------------------------------------------------------
     * Invites (receiverId -> Invite)
     * --------------------------------------------------------------------- */
    private static final class Invite {
        final UUID fromId;
        final String fromName;
        final Instant createdAt = Instant.now();
        Invite(UUID fromId, String fromName) {
            this.fromId = fromId;
            this.fromName = (fromName == null ? "Player" : fromName);
        }
        boolean expired(long ttl) { return Instant.now().isAfter(createdAt.plusSeconds(ttl)); }
        @Override public String toString() {
            return "Invite{fromId=" + fromId + ", fromName='" + fromName + "', at=" + createdAt + "}";
        }
    }
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    /* ---------------------------------------------------------------------
     * Sessions (SID -> TradeSession) + reverse index (playerId -> SID)
     * --------------------------------------------------------------------- */
    private final ConcurrentMap<UUID, TradeSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToSession = new ConcurrentHashMap<>();

    /* ---------------------------------------------------------------------
     * UI registry for open trade menus
     * --------------------------------------------------------------------- */
    private final TradeMenuRegistry menuRegistry = new TradeMenuRegistry();
    public TradeMenuRegistry getMenuRegistry() { return menuRegistry; }

    /* ---------------------------------------------------------------------
     * Pending grants persistence
     * --------------------------------------------------------------------- */
    private final PendingGrantsDao pendingGrantsDao;

    /* ---------------------------------------------------------------------
     * Ctor
     * --------------------------------------------------------------------- */
    public TradeService(OreoEssentials plugin, TradeConfig cfg) {
        this.plugin  = Objects.requireNonNull(plugin, "plugin");
        this.cfg     = Objects.requireNonNull(cfg, "cfg");

        // Pick DAO from plugin if available, else fallback to in-memory so it compiles.
        PendingGrantsDao dao = null;
        try {
            Method m = plugin.getClass().getMethod("getPendingGrantsDao");
            Object obj = m.invoke(plugin);
            if (obj instanceof PendingGrantsDao) dao = (PendingGrantsDao) obj;
        } catch (Throwable ignored) {}
        if (dao == null) dao = new InMemoryPendingGrantsDao();
        this.pendingGrantsDao = dao;

        // Periodic invite cleanup (every 2s)
        this.cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                () -> { try { purgeExpiredInvites(); } catch (Throwable ignored) {} },
                40L, 40L
        );
        log("[TRADE] TradeService init; cleanupTaskId=" + cleanupTaskId + " tradedebug=" + cfg.debugDeep);

        // Register join listener for flushing pending grants
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Convenience for legacy call sites. */
    public TradeService() {
        this(OreoEssentials.get(), new TradeConfig(OreoEssentials.get()));
    }

    public TradeConfig getConfig() { return cfg; }

    /* ---------------------------------------------------------------------
     * Invite workflow
     * --------------------------------------------------------------------- */
    public void sendInvite(Player requester, Player target) {
        log("[TRADE] sendInvite requester=" + safe(requester) + " -> target=" + safe(target));
        if (requester == null || target == null || !target.isOnline()) {
            if (requester != null) requester.sendMessage("§cThat player is not available.");
            log("[TRADE] sendInvite abort (null/offline target).");
            return;
        }

        invites.put(target.getUniqueId(), new Invite(requester.getUniqueId(), requester.getName()));
        log("[TRADE] invite stored receiver=" + target.getUniqueId() + " -> " + invites.get(target.getUniqueId()));

        requester.sendMessage("§7Trade request sent to §b" + target.getName()
                + "§7. Expires in §e" + INVITE_TTL_SECONDS + "s§7.");
        target.sendMessage("§e" + requester.getName() + " §7wants to trade. Type §b/trade "
                + requester.getName() + " §7to accept (expires in §e" + INVITE_TTL_SECONDS + "s§7).");
    }

    /**
     * Receiver runs /trade <name> to accept.
     * @return true if we handled (accepted/consumed); false if no matching invite.
     */
    public boolean tryAcceptInvite(Player acceptor, String requesterName) {
        log("[TRADE] tryAcceptInvite acceptor=" + safe(acceptor) + " requesterName=" + requesterName);
        if (acceptor == null || requesterName == null || requesterName.isBlank()) return false;

        Invite inv = invites.get(acceptor.getUniqueId());
        log("[TRADE] invite lookup -> " + inv);
        if (inv == null) return false;

        if (inv.expired(INVITE_TTL_SECONDS)) {
            invites.remove(acceptor.getUniqueId());
            acceptor.sendMessage("§cThat trade invite has expired.");
            log("[TRADE] invite expired -> removed");
            return true;
        }
        if (!inv.fromName.equalsIgnoreCase(requesterName)) {
            log("[TRADE] invite exists but name mismatch (have='" + inv.fromName + "')");
            return false;
        }

        // If requester is on THIS server, open a local trade; else publish + open locally for acceptor.
        Player requesterLocal = Bukkit.getPlayer(inv.fromId);
        if (requesterLocal != null && requesterLocal.isOnline()) {
            invites.remove(acceptor.getUniqueId());
            log("[TRADE] starting LOCAL trade " + requesterLocal.getName() + " <-> " + acceptor.getName());
            startLocalTrade(requesterLocal, acceptor);
            return true;
        }

        // Cross-server
        var broker = plugin.getTradeBroker();
        boolean messaging = plugin.isMessagingAvailable();
        log("[TRADE] cross-server accept path: broker=" + (broker != null) + " messaging=" + messaging);

        if (broker != null && messaging) {
            invites.remove(acceptor.getUniqueId());

            // Stable canonical SID (order-independent) for this pair
            UUID sid = TradeIds.computeTradeId(inv.fromId, acceptor.getUniqueId());

            // 1) Publish START so the other server opens for the requester.
            boolean published = tryPublishStartWithSid(broker, sid, inv.fromId, inv.fromName, acceptor);

            if (!published) {
                // Legacy fallback (remote will compute same SID on receive)
                try {
                    broker.acceptInvite(acceptor, inv.fromId, inv.fromName);
                    log("[TRADE] broker.acceptInvite(...) called (legacy fallback).");
                } catch (Throwable t) {
                    log("[TRADE] broker legacy acceptInvite failed: " + t.getMessage());
                }
            }

            // 2) Open immediately on THIS server for the acceptor (idempotent).
            log("[TRADE] acceptInvite sessionUpsert sid=" + sid
                    + " aId=" + inv.fromId + " bId=" + acceptor.getUniqueId());
            openOrCreateCrossServerSession(
                    sid,
                    inv.fromId,   inv.fromName,                // A (requester)
                    acceptor.getUniqueId(), acceptor.getName() // B (acceptor)
            );

            acceptor.sendMessage("§aAccepted.§7 Opening cross-server trade with §f" + inv.fromName + "§7…");
            return true;
        }

        invites.remove(acceptor.getUniqueId());
        acceptor.sendMessage("§cThe requester is no longer available.");
        log("[TRADE] accept failed: no broker/messaging or requester unavailable.");
        return true;
    }

    public boolean tryAcceptInviteAny(Player acceptor) {
        Invite inv = invites.get(acceptor.getUniqueId());
        if (inv == null) return false;
        return tryAcceptInvite(acceptor, inv.fromName);
    }

    /** Called by the broker when a remote invite targets a local player. */
    public void addIncomingInvite(Player receiver, UUID fromId, String fromName) {
        log("[TRADE] addIncomingInvite receiver=" + safe(receiver) + " from=" + fromName + "/" + fromId);
        if (receiver == null || !receiver.isOnline() || fromId == null) return;

        invites.put(receiver.getUniqueId(), new Invite(fromId, fromName));
        receiver.sendMessage("§e" + (fromName == null ? "Someone" : fromName)
                + " §7wants to trade §8(cross-server)§7. Type §b/trade "
                + (fromName == null ? "player" : fromName) + " §7to accept (expires in §e"
                + INVITE_TTL_SECONDS + "s§7).");
    }

    /** Overload used by some handlers that only know UUIDs. */
    public void addIncomingInvite(UUID receiverId, UUID fromId, String fromName) {
        if (receiverId == null || fromId == null) return;
        invites.put(receiverId, new Invite(fromId, fromName));
        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver != null && receiver.isOnline()) {
            receiver.sendMessage("§e" + (fromName == null ? "Someone" : fromName)
                    + " §7wants to trade §8(cross-server)§7. Type §b/trade "
                    + (fromName == null ? "player" : fromName)
                    + " §7to accept (expires in §e" + INVITE_TTL_SECONDS + "s§7).");
        }
    }

    /* ---------------------------------------------------------------------
     * Session lifecycle (SID-first)
     * --------------------------------------------------------------------- */

    private void startLocalTrade(Player a, Player b) {
        UUID sid = TradeIds.computeTradeId(a.getUniqueId(), b.getUniqueId());
        clearGrantDedupFor(sid, a.getUniqueId(), b.getUniqueId());

        log("[TRADE] startLocalTrade sid=" + sid);

        TradeSession sess = sessionsById.computeIfAbsent(sid, __ ->
                buildSession(sid, a.getUniqueId(), a.getName(), b.getUniqueId(), b.getName()));

        playerToSession.put(a.getUniqueId(), sid);
        playerToSession.put(b.getUniqueId(), sid);

        sess.open(); // opens for locals on this server

        // No early remote packets in pure-local path, but harmless to drain.
        drainPendingStates(sid);

        log("[TRADE] local TradeSession.open() called sid=" + sid);
    }

    public void openOrCreateCrossServerSession(
            UUID sid,
            UUID aId, String aName,
            UUID bId, String bName
    ) {
        log("[TRADE] openOrCreateCrossServerSession sid=" + sid
                + " A=" + aName + "/" + aId + " B=" + bName + "/" + bId);

        TradeSession session = sessionsById.computeIfAbsent(sid, __ ->
                buildSession(sid, aId, aName, bId, bName));

        //  reset grant de-dup for this SID so repeated trades between the same two players work
        clearGrantDedupFor(sid, aId, bId);

        playerToSession.put(aId, sid);
        playerToSession.put(bId, sid);

        session.open();

        // Defensive: ensure local viewers actually have an inventory open now.
        try {
            Player a = Bukkit.getPlayer(aId);
            if (a != null && a.isOnline()) menuRegistry.ensureOpen(a.getUniqueId(), session);
        } catch (Throwable ignored) {}
        try {
            Player b = Bukkit.getPlayer(bId);
            if (b != null && b.isOnline()) menuRegistry.ensureOpen(b.getUniqueId(), session);
        } catch (Throwable ignored) {}

        // Process any early state packets that raced the session creation.
        drainPendingStates(sid);
    }



    /** Build a new TradeSession wired to broker + cleanup, stored by SID. */
    private TradeSession buildSession(UUID sid, UUID aId, String aName, UUID bId, String bName) {
        final UUID capturedSid = sid;

        return new TradeSession(
                sid,
                plugin,
                cfg,
                aId, aName,
                bId, bName,
                // onFinish -> toggleReady path already calls finalize
                (finished) -> log("[TRADE] onFinish sid=" + capturedSid),
                // onCancel
                (s, reason) -> {
                    log("[TRADE] onCancel sid=" + capturedSid + " reason=" + reason);
                    cancelSession(capturedSid, reason);
                },
                // onStateChanged: publish each side's state to broker
                (s, ver) -> {
                    var broker = plugin.getTradeBroker();
                    if (broker == null) return;

                    Player a = Bukkit.getPlayer(s.getAId());
                    Player b = Bukkit.getPlayer(s.getBId());

                    if (a != null && a.isOnline()) {
                        broker.publishState(capturedSid, a, s.isReadyA(), s.viewOfferA());
                        log("[TRADE] publish A state sid=" + capturedSid + " ver=" + ver);
                    }
                    if (b != null && b.isOnline()) {
                        broker.publishState(capturedSid, b, s.isReadyB(), s.viewOfferB());
                        log("[TRADE] publish B state sid=" + capturedSid + " ver=" + ver);
                    }
                }
        );
    }

    /* ---------------------------------------------------------------------
     * Core lookups (used by GUI & helpers)
     * --------------------------------------------------------------------- */
    public OreoEssentials getPlugin() { return plugin; }

    public TradeSession getSession(UUID sid) { return sessionsById.get(sid); }

    /** playerId -> SID (or null) */
    public UUID getTradeIdByPlayer(UUID playerId) {
        return playerId != null ? playerToSession.get(playerId) : null;
    }

    /* ---------------------------------------------------------------------
     * GUI hooks (called by TradeMenu)
     * --------------------------------------------------------------------- */

    /** GUI calls this when a viewer changes an item in their 3×3 offer grid. */
    public void setOfferItem(UUID playerId, int index, ItemStack item) {
        if (playerId == null) return;
        UUID sid = getTradeIdByPlayer(playerId);
        log("[TRADE] setOfferItem editor=" + playerId + " sid=" + sid + " slot=" + index
                + " item=" + (item == null ? "null" : item.getType() + "x" + item.getAmount()));
        if (sid == null) return;

        TradeSession s = getSession(sid);
        if (s == null || s.isUiLocked() || s.isCompleted()) return;

        boolean isA = playerId.equals(s.getAId());
        if (isA) {
            s.setOfferItemA(index, (item == null || item.getType().isAir()) ? null : item.clone());
            s.setReadyA(false); // any local edit un-reads A
        } else if (playerId.equals(s.getBId())) {
            s.setOfferItemB(index, (item == null || item.getType().isAir()) ? null : item.clone());
            s.setReadyB(false);
        } else {
            return;
        }

        // publish and repaint locals
        publishState(sid, s);
        refreshLocalViewers(s);
    }

    /** GUI calls this when a viewer presses the ready/unready button. */
    public void toggleReady(UUID playerId) {
        if (playerId == null) return;
        UUID sid = getTradeIdByPlayer(playerId);
        log("[TRADE] toggleReady player=" + playerId + " sid=" + sid);
        if (sid == null) return;

        TradeSession s = getSession(sid);
        if (s == null || s.isUiLocked() || s.isCompleted()) return;

        boolean isA = playerId.equals(s.getAId());
        if (isA) {
            s.setReadyA(!s.isReadyA());
        } else if (playerId.equals(s.getBId())) {
            s.setReadyB(!s.isReadyB());
        } else {
            return;
        }

        publishState(sid, s);
        refreshLocalViewers(s);

        if (s.isReadyA() && s.isReadyB()) {
            finalizeIfBothReady(sid);
        }
    }

    /** GUI cancel button. */
    public void requestCancel(UUID playerId) {
        if (playerId == null) return;

        UUID sid = getTradeIdByPlayer(playerId);
        log("[TRADE] requestCancel by=" + playerId + " sid=" + sid);
        if (sid == null) return;

        TradeSession s = getSession(sid);
        if (s == null) return;

        returnAllAndCleanup(s, "Trade cancelled.");
    }

    /** Called by InventoryClose listener: return closer’s items (policy here cancels whole trade). */
    public void onViewerClosed(UUID playerId) {
        if (playerId == null) return;
        UUID sid = getTradeIdByPlayer(playerId);
        log("[TRADE] onViewerClosed player=" + playerId + " sid=" + sid);
        if (sid == null) return;

        TradeSession s = getSession(sid);
        if (s == null) return;

        boolean isA = playerId.equals(s.getAId());
        if (isA) {
            returnItemsTo(s.getAId(), s.viewOfferA());
            s.clearOfferA();
            s.setReadyA(false);
        } else if (playerId.equals(s.getBId())) {
            returnItemsTo(s.getBId(), s.viewOfferB());
            s.clearOfferB();
            s.setReadyB(false);
        }

        publishState(sid, s);
        refreshLocalViewers(s);

        // Policy: close -> cancel & return everything to be safe
        returnAllAndCleanup(s, "Trade closed.");
    }

    /* ---------------------------------------------------------------------
     * Finalization / return items
     * --------------------------------------------------------------------- */

    /**
     * When both sides are ready, complete the trade.
     * - If a broker is available: publish two TradeGrant packets (A gets B's items, B gets A's).
     * - If broker fails or is unavailable: complete locally.
     * UI is hard-locked immediately to prevent last-millisecond edits.
     */
    private void finalizeIfBothReady(UUID sid) {
        TradeSession s = getSession(sid);
        if (s == null) return;

        // One-time guard so only one node/thread performs the grant
        if (!s.tryMarkGrantingOnce()) {
            log("[TRADE] finalizeIfBothReady skipped (grant already in progress) sid=" + sid);
            return;
        }

        // Freeze UI immediately to avoid dup/race
        s.beginClosing();
        s.lockUiNow();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                var broker = plugin.getTradeBroker();
                if (broker != null && plugin.isMessagingAvailable()) {
                    // Capture offers (cloned/compact if available)
                    ItemStack[] itemsForA = getItemsForA(s); // A receives B's items
                    ItemStack[] itemsForB = getItemsForB(s); // B receives A's items

                    // Compact summary log of what each side will receive
                    log("[TRADE] finalize sid=" + s.getId()
                            + " toA=" + summarize(itemsForA)
                            + " toB=" + summarize(itemsForB));

                    // Try network grants first
                    try {
                        broker.sendTradeGrant(s.getId(), s.getAId(), itemsForA);
                        broker.sendTradeGrant(s.getId(), s.getBId(), itemsForB);
                        log("[TRADE] Published TradeGrant for BOTH sides sid=" + s.getId()
                                + " grantToA=" + countNonAir(itemsForA)
                                + " grantToB=" + countNonAir(itemsForB));
                    } catch (Throwable netErr) {
                        plugin.getLogger().warning("[TRADE] sendTradeGrant failed, falling back to local completion: " + netErr.getMessage());
                        completeTradeLocally(s);
                        return;
                    }

                    // Local tidy-up (grants may already have closed UIs via handler)
                    s.closeLocalViewers();
                    s.clearOfferA();
                    s.clearOfferB();
                    s.markCompleted();
                    cleanupSession(s);
                } else {
                    // No broker: do everything locally
                    completeTradeLocally(s);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[TRADE] finalize failed: " + t.getMessage());
                returnAllAndCleanup(s, "Trade failed, items returned.");
            }
        });
    }



    // --- Debug helpers (safe with debugDeep) ---
    private static String summarize(ItemStack[] arr){
        if (arr == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++){
            ItemStack it = arr[i];
            if (it != null && !it.getType().isAir()){
                sb.append(i).append('=').append(it.getType()).append('x').append(it.getAmount()).append(", ");
            }
        }
        if (sb.length() > 1) sb.setLength(sb.length()-2);
        return sb.append(']').toString();
    }

    private void debugGrid(TradeSession s){
        log("A=" + summarize(s.viewOfferA()) + " | B=" + summarize(s.viewOfferB()));
    }

    /** Fallback/single-server: actually move stacks locally. */
    private void completeTradeLocally(TradeSession s) {
        var itemsA = safeCloneArray(s.viewOfferA());
        var itemsB = safeCloneArray(s.viewOfferB());

        // Lock + clear first to avoid duplication if re-entrant
        s.beginClosing();
        s.lockUiNow();
        s.clearOfferA();
        s.clearOfferB();

        // Give A->B and B->A (with overflow handling)
        returnItemsTo(s.getBId(), itemsA);
        returnItemsTo(s.getAId(), itemsB);

        notify(s.getAId(), "§aTrade complete!");
        notify(s.getBId(), "§aTrade complete!");

        closeLocalMenus(s);
        s.markCompleted();
        cleanupSession(s);
    }

    /** Return all (both sides) items to their respective owners and cleanup session. */
    private void returnAllAndCleanup(TradeSession s, String reason) {
        s.beginClosing();
        s.lockUiNow();

        var itemsA = safeCloneArray(s.viewOfferA());
        var itemsB = safeCloneArray(s.viewOfferB());

        s.clearOfferA();
        s.clearOfferB();

        returnItemsTo(s.getAId(), itemsA);
        returnItemsTo(s.getBId(), itemsB);

        if (reason != null) {
            notify(s.getAId(), "§c" + reason);
            notify(s.getBId(), "§c" + reason);
        }

        closeLocalMenus(s);
        s.markCompleted();
        cleanupSession(s);
    }

    /** Give to player; if full, drop naturally. (If offline, log for now.) */
    private void returnItemsTo(UUID playerId, ItemStack[] items) {
        if (items == null || items.length == 0) return;
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline()) {
            for (var it : items) {
                if (it == null || it.getType().isAir()) continue;
                safeGiveOrDrop(p, it);
            }
        } else {
            plugin.getLogger().info("[TRADE] Player offline; skipping return (player=" + playerId + ")");
        }
    }

    private void safeGiveOrDrop(Player p, ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return;
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(it);
        if (!left.isEmpty()) {
            left.values().forEach(rem -> p.getWorld().dropItemNaturally(p.getLocation(), rem));
        }
    }

    private ItemStack[] safeCloneArray(ItemStack[] src) {
        if (src == null) return new ItemStack[0];
        var out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            var it = src[i];
            out[i] = (it == null || it.getType().isAir()) ? null : it.clone();
        }
        return out;
    }

    private void notify(UUID playerId, String msg) {
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline()) p.sendMessage(msg);
    }

    private void closeLocalMenus(TradeSession s) {
        var reg = getMenuRegistry();
        if (reg == null) return;
        try {
            var aMenu = reg.get(s.getAId());
            if (aMenu != null) {
                Player a = Bukkit.getPlayer(s.getAId());
                if (a != null && a.isOnline()) a.closeInventory();
            }
        } catch (Throwable ignored) {}
        try {
            var bMenu = reg.get(s.getBId());
            if (bMenu != null) {
                Player b = Bukkit.getPlayer(s.getBId());
                if (b != null && b.isOnline()) b.closeInventory();
            }
        } catch (Throwable ignored) {}
    }

    private void cleanupSession(TradeSession s) {
        UUID sid = s.getId();
        sessionsById.remove(sid);
        clearGrantDedupFor(sid, s.getAId(), s.getBId()); // <-- add

        playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue()));
        publishClose(sid); // optional: inform other servers
    }
    /** Same as cleanupSession but without publishClose() to avoid close loops on receivers. */
    /** Same as cleanupSession but without publishClose() to avoid close loops on receivers. */
    private void cleanupSessionSilent(TradeSession s) {
        if (s == null) return;
        UUID sid = s.getId();
        clearGrantDedupFor(sid, s.getAId(), s.getBId()); // <-- add

        try { sessionsById.remove(sid); } catch (Throwable ignored) {}
        try { playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue())); } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(s.getAId()); } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(s.getBId()); } catch (Throwable ignored) {}
    }


    /** Repaint both local viewers, if present on this server. */
    private void refreshLocalViewers(TradeSession s) {
        var reg = getMenuRegistry();
        if (reg == null) return;
        try { reg.refreshViewer(s.getAId()); } catch (Throwable ignored) {}
        try { reg.refreshViewer(s.getBId()); } catch (Throwable ignored) {}
    }

    /* ---------------------------------------------------------------------
     * Cancellation / shutdown (external)
     * --------------------------------------------------------------------- */

    public void cancelTradeFor(UUID playerId, String reason) {
        if (playerId == null) return;
        UUID sid = getTradeIdByPlayer(playerId);
        log("[TRADE] cancelTradeFor player=" + playerId + " sid=" + sid + " reason=" + reason);
        if (sid != null) cancelSession(sid, (reason == null ? "cancelled" : reason));
    }

    public void cancelAll() {
        log("[TRADE] cancelAll()");
        invites.clear();
        sessionsById.values().forEach(TradeSession::closeLocalViewers);
        sessionsById.clear();
        playerToSession.clear();
        try { menuRegistry.closeAll(); } catch (Throwable ignored) {}
        if (cleanupTaskId != 0) {
            try { Bukkit.getScheduler().cancelTask(cleanupTaskId); } catch (Throwable ignored) {}
            cleanupTaskId = 0;
        }
    }

    private void cancelSession(UUID sid, String reason) {
        TradeSession s = sessionsById.remove(sid);
        log("[TRADE] cancelSession sid=" + sid + " hadSession=" + (s != null) + " reason=" + reason);
        if (s == null) return;

        // reverse index cleanup
        playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue()));

        s.closeLocalViewers();
    }

    /* ---------------------------------------------------------------------
     * Remote state/grant apply (broker -> service -> session)
     * --------------------------------------------------------------------- */

    /** Decode an ItemStack[] from bytes (18 slots minimum). */
    private static ItemStack[] decodeOffer(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            int n = (bytes == null || bytes.length == 0) ? 0 : ois.readInt();
            ItemStack[] out = new ItemStack[Math.max(18, n)];
            for (int i = 0; i < n; i++) out[i] = (ItemStack) ois.readObject();
            return out;
        } catch (Throwable t) {
            return new ItemStack[18];
        }
    }

    /**
     * Called by TradeStatePacketHandler (on main thread). Applies remote side’s state
     * to our session. Short-circuits if session is not up yet or is closing/closed.
     */
    public void applyRemoteState(TradeStatePacket p) {
        if (p == null) return;

        TradeSession s = getSession(p.getSessionId());
        if (s == null) {
            // queue once; TradeStart might be racing
            enqueueState(p);
            log("[TRADE] applyRemoteState: queued until session exists " + p.getSessionId());
            return;
        }
        if (s.isClosingOrClosed() || s.isUiLocked() || s.isCompleted()) {
            log("[TRADE] ignore state on closed/locked session " + p.getSessionId());
            return;
        }

        // Start from current state and replace the side that sent the update
        ItemStack[] newA = s.viewOfferA().clone();
        ItemStack[] newB = s.viewOfferB().clone();
        boolean readyA = s.isReadyA();
        boolean readyB = s.isReadyB();

        ItemStack[] decoded = decodeOffer(p.getOfferBytes());

        if (p.getFromPlayerId().equals(s.getAId())) {
            newA = decoded;
            readyA = p.isReady();
        } else if (p.getFromPlayerId().equals(s.getBId())) {
            newB = decoded;
            readyB = p.isReady();
        } else {
            log("[TRADE] applyRemoteState: from id " + p.getFromPlayerId()
                    + " is not A/B of session " + p.getSessionId());
            return;
        }

        long newVersion = s.getVersion() + 1;
        s.applyRemoteState(newA, newB, readyA, readyB, newVersion);
        log("[TRADE] applied remote state sid=" + p.getSessionId() + " v=" + newVersion
                + " Aready=" + readyA + " Bready=" + readyB);

        // If both ready from remote side, finalize here too.
        if (readyA && readyB && !s.isUiLocked() && !s.isCompleted()) {
            finalizeIfBothReady(p.getSessionId());
            return; // finalize will repaint/close as needed
        }

        // Refresh local menus (safe even if not open yet), unless closing.
        if (!s.isUiLocked() && !s.isCompleted()) {
            try {
                menuRegistry.refreshViewer(s.getAId());
                menuRegistry.refreshViewer(s.getBId());
            } catch (Throwable ignored) {}
        }
    }
    private static byte[] encodeOffer(ItemStack[] items) {
        if (items == null) items = new ItemStack[0];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            // write a compact count (skip null/AIR)
            int count = 0;
            for (ItemStack it : items) if (it != null && !it.getType().isAir()) count++;
            oos.writeInt(count);
            for (ItemStack it : items) {
                if (it == null || it.getType().isAir()) continue;
                oos.writeObject(it);
            }
            oos.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }
    private void clearGrantDedupFor(UUID sid, UUID aId, UUID bId) {
        if (sid == null) return;
        try { deliveredGrants.remove(grantKey(sid, aId)); } catch (Throwable ignored) {}
        try { deliveredGrants.remove(grantKey(sid, bId)); } catch (Throwable ignored) {}
    }

    private final Set<String> deliveredGrants = ConcurrentHashMap.newKeySet();
    private static String grantKey(UUID sid, UUID to) {
        return sid + "|" + to;
    }
    /**
     * Receiver for TradeGrantPacket (called by its packet handler on main thread).
     * - If grantTo is online on this server: safely give items, close GUI, delete session.
     * - If offline: persist and clean local session (no GUI left open).
     * Also short-circuits any further state by locking/marking session.
     */

    public void handleRemoteGrant(TradeGrantPacket p) {
        if (p == null) return;

        // Always run on main
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handleRemoteGrant(p));
            return;
        }

        try {
            UUID sid = p.getSessionId();
            UUID to  = p.getGrantTo();

            // De-dupe guard (must happen on main so the set is consistent with granting)
            String key = grantKey(sid, to);
            if (!deliveredGrants.add(key)) {
                // already processed this (sid, to)
                return;
            }

            // Defensive: find (or not) the local session
            TradeSession sess = getSession(sid);

            // Decode items from packet
            ItemStack[] decoded = ItemStacksCodec.decodeFromBytes(p.getItemsBytes());
            if (decoded == null) decoded = new ItemStack[0];

            // --- Safety: filter out nulls & decorative GUI items (glass, panes, etc.) ---
            ItemStack[] items = java.util.Arrays.stream(decoded)
                    .filter(java.util.Objects::nonNull)
                    .filter(this::isNotDecorativeGuiItem) // ⬅️ uses the helper we added earlier
                    .toArray(ItemStack[]::new);

            // If there is literally nothing to give after filtering, just complete/cleanup
            if (items.length == 0) {
                if (sess != null) {
                    sess.markCompleted();
                    sess.forceClearAndCloseLocal();
                    removeSession(sid);
                }
                return;
            }

            Player target = Bukkit.getPlayer(to);
            if (target == null || !target.isOnline()) {
                plugin.getLogger().warning("[TRADE] Grant target offline; saving pending grant. sid=" + sid);
                try {
                    // Store only the filtered items (no GUI junk) as pending
                    pendingGrantsDao.storePending(to, sid, items);
                } catch (Throwable t) {
                    plugin.getLogger().severe("[TRADE] Failed to save pending grant: " + t.getMessage());
                }
                if (sess != null) {
                    sess.markCompleted();
                    sess.forceClearAndCloseLocal();
                    removeSession(sid);
                }
                return;
            }

            // Actually give the items (already filtered)
            grantItems(target, items);

            // Mark the session complete and close UIs locally
            if (sess != null) {
                sess.markCompleted();
                sess.forceClearAndCloseLocal();
                removeSession(sid);
            }

            // Notify the network we're done so the partner cleans up (best-effort)
            try {
                var brokerObj = plugin.getTradeBroker();
                if (brokerObj != null) {
                    try {
                        // Newer API: broker.sendClose(sid, to)
                        var m = brokerObj.getClass().getMethod("sendClose", java.util.UUID.class, java.util.UUID.class);
                        m.invoke(brokerObj, sid, to);
                    } catch (NoSuchMethodException noModern) {
                        // Fallback to legacy publishClose(...)
                        publishClose(sid);
                    }
                } else {
                    publishClose(sid);
                }
            } catch (Throwable ignored) {}

            target.sendMessage("§aTrade complete! Items received.");
        } catch (Throwable t) {
            plugin.getLogger().severe("[TRADE] handleRemoteGrant failed: " + t.getMessage());
            t.printStackTrace();
        }
    }




    /** Puts items into the player's inventory; drops overflow safely at feet. */
    public void grantItems(Player player, ItemStack[] items) {
        if (player == null || items == null || items.length == 0) return;

        // DEBUG: log incoming stacks
        plugin.getLogger().info("[TRADE] grantItems -> " + player.getName()
                + " items=" + summarize(items)); // you already have summarize(...)

        // Filter null/air
        List<ItemStack> give = new ArrayList<>();
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir() || it.getAmount() <= 0) continue;
            give.add(it.clone());
        }
        if (give.isEmpty()) return;

        Map<Integer, ItemStack> leftover = player.getInventory()
                .addItem(give.toArray(new ItemStack[0]));

        if (!leftover.isEmpty()) {
            leftover.values().forEach(rem -> {
                if (rem != null && !rem.getType().isAir() && rem.getAmount() > 0) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rem);
                }
            });
        }

        try { player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f); } catch (Throwable ignored) {}
    }


    /** Public helper: remove a session by ID and drop reverse index + UI mapping. */
    public void removeSession(UUID sid) {
        if (sid == null) return;
        TradeSession s = sessionsById.remove(sid);
        playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue()));
        if (s != null) {
            try { if (menuRegistry != null) menuRegistry.unregister(s.getAId()); } catch (Throwable ignored) {}
            try { if (menuRegistry != null) menuRegistry.unregister(s.getBId()); } catch (Throwable ignored) {}
        }
    }

    /* ---------------------------------------------------------------------
     * Broker bridge helpers (publish)
     * --------------------------------------------------------------------- */

    /** Publish the latest state for both players present. */
    /**
     * Publish the latest state for both players present.
     * Sends ONLY the logical trade offer (filtered) to the cross-server broker,
     * never the full GUI contents with decorative glass.
     */
    private void publishState(UUID sid, TradeSession s) {
        var broker = plugin.getTradeBroker();
        if (broker == null) {
            return;
        }

        // Do not publish states once we are closing/closed
        if (s == null || s.isClosingOrClosed() || s.isClosed() || s.isUiLocked() || s.isCompleted()) {
            return;
        }

        Player a = Bukkit.getPlayer(s.getAId());
        Player b = Bukkit.getPlayer(s.getBId());

        // Side A → publish its (filtered) offer
        if (a != null && a.isOnline()) {
            ItemStack[] offerA = filterOfferArray(s.viewOfferA()); // ⬅️ filter out fillers
            broker.publishState(sid, a, s.isReadyA(), offerA);
        }

        // Side B → publish its (filtered) offer
        if (b != null && b.isOnline()) {
            ItemStack[] offerB = filterOfferArray(s.viewOfferB()); // ⬅️ filter out fillers
            broker.publishState(sid, b, s.isReadyB(), offerB);
        }
    }
    /**
     * Filters a raw offer array so that:
     * - nulls are removed
     * - decorative GUI items (glass, panes, etc.) are stripped
     */
    private ItemStack[] filterOfferArray(ItemStack[] raw) {
        if (raw == null || raw.length == 0) {
            return new ItemStack[0];
        }

        return Arrays.stream(raw)
                .filter(Objects::nonNull)
                .filter(this::isNotDecorativeGuiItem)
                .toArray(ItemStack[]::new);
    }

    /**
     * Returns true if the item should be considered a REAL trade item,
     * false if it looks like a GUI filler/border.
     */
    private boolean isNotDecorativeGuiItem(ItemStack item) {
        if (item == null) return false;

        Material type = item.getType();
        if (type == null || type.isAir()) return false;

        String matName = type.name();

        // 🔒 1) Block ALL glass & panes (any stained, tinted, etc.)
        // This automatically covers:
        // - GLASS, TINTED_GLASS
        // - *_STAINED_GLASS, *_STAINED_GLASS_PANE
        // - GLASS_PANE, etc.
        if (matName.contains("GLASS") || matName.contains("PANE")) {
            return false;
        }

        // 🔒 2) Optional: block some other very typical filler materials (safe but not mandatory)
        // Add/remove as you like.
        switch (type) {
            case BARRIER:
            case BEDROCK:
            case STRUCTURE_VOID:
            case LIGHT:
                return false;
            default:
                break;
        }

        // 🔒 3) Name-based filler detection (covers custom-named GUI items)
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = org.bukkit.ChatColor.stripColor(
                    item.getItemMeta().getDisplayName()
            ).toLowerCase(java.util.Locale.ROOT);

            // Keywords you *definitely* don't want traded
            if (name.contains("filler")
                    || name.contains("border")
                    || name.contains("trade ui")
                    || name.contains("locked")
                    || name.contains("interface")
                    || name.contains("menu")) {
                return false;
            }
        }

        //  Everything else is considered a valid trade item
        return true;
    }




    /** Optional: inform other servers that the session has closed. */
    private void publishClose(UUID sid) {
        try {
            var broker = plugin.getTradeBroker();
            if (broker == null) return;
            // If your broker has a close call, reflect-call it for compatibility
            try {
                Method m = broker.getClass().getMethod("publishClose", UUID.class);
                m.invoke(broker, sid);
            } catch (NoSuchMethodException ignore) {
                // ok if not supported
            }
        } catch (Throwable ignored) {}
    }

    /* ---------------------------------------------------------------------
     * Housekeeping
     * --------------------------------------------------------------------- */
    private void purgeExpiredInvites() {
        if (!dbg()) {
            invites.entrySet().removeIf(e -> e.getValue().expired(INVITE_TTL_SECONDS));
            return;
        }
        int before = invites.size();
        invites.entrySet().removeIf(e -> e.getValue().expired(INVITE_TTL_SECONDS));
        int after = invites.size();
        if (before != after) log("[TRADE] purgeExpiredInvites removed=" + (before - after) + " remaining=" + after);
    }

    /* ---------------------------------------------------------------------
     * Utils
     * --------------------------------------------------------------------- */
    private static String safe(Player p) {
        return (p == null ? "null" : p.getName() + "/" + p.getUniqueId());
    }

    /**
     * Try to call a broker API that accepts a stable SID. Supports either:
     *   publishStart(UUID sid, UUID requesterId, String requesterName, Player acceptor)
     *   startTrade(UUID sid, UUID requesterId, String requesterName, Player acceptor)
     * If not found, returns false (caller should fall back to legacy acceptInvite()).
     */
    private boolean tryPublishStartWithSid(Object broker, UUID sid, UUID requesterId, String requesterName, Player acceptor) {
        try {
            // prefer publishStart(...)
            Method m = broker.getClass().getMethod("publishStart", UUID.class, UUID.class, String.class, Player.class);
            m.invoke(broker, sid, requesterId, requesterName, acceptor);
            log("[TRADE] broker.publishStart(sid, ...) called.");
            return true;
        } catch (NoSuchMethodException ignore) {
            // try startTrade(...)
            try {
                Method m2 = broker.getClass().getMethod("startTrade", UUID.class, UUID.class, String.class, Player.class);
                m2.invoke(broker, sid, requesterId, requesterName, acceptor);
                log("[TRADE] broker.startTrade(sid, ...) called.");
                return true;
            } catch (NoSuchMethodException ignoreToo) {
                return false;
            } catch (Throwable t2) {
                log("[TRADE] broker.startTrade reflect error: " + t2.getMessage());
                return false;
            }
        } catch (Throwable t) {
            log("[TRADE] broker.publishStart reflect error: " + t.getMessage());
            return false;
        }
    }

    /* ---------------------------------------------------------------------
     * Helpers for compact offers and counts (works with older TradeSession)
     * --------------------------------------------------------------------- */

    /** Items that A should receive (B's offer), using compact arrays if available. */
    private ItemStack[] getItemsForA(TradeSession s) {
        try {
            ItemStack[] compact = s.getOfferBCompact();
            return safeCloneArray(compact);
        } catch (Throwable ignored) {
            return safeCloneArray(s.viewOfferB());
        }
    }

    /** Items that B should receive (A's offer), using compact arrays if available. */
    private ItemStack[] getItemsForB(TradeSession s) {
        try {
            ItemStack[] compact = s.getOfferACompact();
            return safeCloneArray(compact);
        } catch (Throwable ignored) {
            return safeCloneArray(s.viewOfferA());
        }
    }

    private int countNonAir(ItemStack[] arr) {
        if (arr == null) return 0;
        int c = 0;
        for (ItemStack it : arr) if (it != null && it.getType() != Material.AIR) c++;
        return c;
    }

    /* ---------------------------------------------------------------------
     * Remote close (optional packet)
     * --------------------------------------------------------------------- */
    public void handleRemoteClose(TradeClosePacket pkt) {
        if (pkt == null) return;

        UUID sid    = pkt.getSessionId();
        UUID target = pkt.getGrantTo();

        TradeSession s = getSession(sid);
        if (s == null) return;


        // Mark closing & lock UI immediately to prevent any last-millisecond edits
        try { s.beginClosing(); } catch (Throwable ignored) {}
        try { s.lockUiNow(); }   catch (Throwable ignored) {}

        UUID aId = s.getAId();
        UUID bId = s.getBId();

        // Hard-close inventories for any local viewers (target + other participant)
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player pt = Bukkit.getPlayer(target);
                if (pt != null && pt.isOnline()) pt.closeInventory();
            } catch (Throwable ignored) {}

            try {
                UUID other = target.equals(aId) ? bId : aId;
                Player po = Bukkit.getPlayer(other);
                if (po != null && po.isOnline()) po.closeInventory();
            } catch (Throwable ignored) {}
        });

        // Drop any SmartInvs/registry mapping (best-effort)
        try { if (menuRegistry != null) menuRegistry.unregister(target); } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(aId);    } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(bId);    } catch (Throwable ignored) {}

        // Mark completed to short-circuit any late state packets on this node
        try { s.markCompleted(); } catch (Throwable ignored) {}

        // IMPORTANT: do NOT publish close back to the network (avoid loops)
        cleanupSessionSilent(s);
    }


    /* ---------------------------------------------------------------------
     * Join hook to flush pending grants
     * --------------------------------------------------------------------- */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        try {
            var grants = pendingGrantsDao.fetchAndDelete(e.getPlayer().getUniqueId());
            if (grants != null && grants.items != null && grants.items.length > 0) {
                for (ItemStack it : grants.items) {
                    if (it == null || it.getType() == Material.AIR) continue;
                    var leftovers = e.getPlayer().getInventory().addItem(it);
                    leftovers.values().forEach(rem ->
                            e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), rem));
                }
                e.getPlayer().sendMessage("§aYou received items from a completed trade while you were offline.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[TRADE] onJoin grant flush failed: " + t.getMessage());
        }
    }

    /* ---------------------------------------------------------------------
     * Early-state mailbox helpers
     * --------------------------------------------------------------------- */
    private void enqueueState(TradeStatePacket p) {
        pendingStates.computeIfAbsent(p.getSessionId(), k -> new ArrayDeque<>(8)).addLast(p);

        // Watchdog: if Start hasn't arrived in ~1s, try re-requesting it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessionsById.get(p.getSessionId()) == null &&
                    pendingStates.containsKey(p.getSessionId())) {
                try {
                    var broker = plugin.getTradeBroker();
                    if (broker != null) broker.requestStartReplay(p.getSessionId()); // implement as no-op safe
                } catch (Throwable ignored) {}
            }
        }, 20L);
    }


    /** Call this right after opening/creating the session to process any queued states. */
    private void drainPendingStates(UUID sid) {
        Deque<TradeStatePacket> q = pendingStates.remove(sid);
        if (q == null) return;
        while (!q.isEmpty()) applyRemoteState(q.pollFirst());
    }
}
