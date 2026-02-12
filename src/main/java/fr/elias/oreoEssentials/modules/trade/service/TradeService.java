package fr.elias.oreoEssentials.modules.trade.service;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.trade.ItemStacksCodec;
import fr.elias.oreoEssentials.modules.trade.config.TradeConfig;
import fr.elias.oreoEssentials.modules.trade.TradeIds;
import fr.elias.oreoEssentials.modules.trade.TradeSession;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeClosePacket;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeGrantPacket;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeStatePacket;
import fr.elias.oreoEssentials.modules.trade.pending.InMemoryPendingGrantsDao;
import fr.elias.oreoEssentials.modules.trade.pending.PendingGrantsDao;
import fr.elias.oreoEssentials.modules.trade.ui.TradeMenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TradeService implements Listener {

    public static final long INVITE_TTL_SECONDS = 60L;

    private final Map<UUID, Deque<TradeStatePacket>> pendingStates = new ConcurrentHashMap<>();
    private final OreoEssentials plugin;
    private final TradeConfig cfg;
    private int cleanupTaskId = 0;

    private static final class Invite {
        final UUID fromId;
        final String fromName;
        final Instant createdAt = Instant.now();

        Invite(UUID fromId, String fromName) {
            this.fromId = fromId;
            this.fromName = (fromName == null ? "Player" : fromName);
        }

        boolean expired(long ttl) {
            return Instant.now().isAfter(createdAt.plusSeconds(ttl));
        }

        @Override
        public String toString() {
            return "Invite{fromId=" + fromId + ", fromName='" + fromName + "', at=" + createdAt + "}";
        }
    }

    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TradeSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToSession = new ConcurrentHashMap<>();
    private final TradeMenuRegistry menuRegistry = new TradeMenuRegistry();
    private final PendingGrantsDao pendingGrantsDao;
    private final Set<String> deliveredGrants = ConcurrentHashMap.newKeySet();

    public TradeService(OreoEssentials plugin, TradeConfig cfg) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.cfg = Objects.requireNonNull(cfg, "cfg");

        PendingGrantsDao dao = null;
        try {
            Method m = plugin.getClass().getMethod("getPendingGrantsDao");
            Object obj = m.invoke(plugin);
            if (obj instanceof PendingGrantsDao) dao = (PendingGrantsDao) obj;
        } catch (Throwable ignored) {}
        if (dao == null) dao = new InMemoryPendingGrantsDao();
        this.pendingGrantsDao = dao;

        this.cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                () -> { try { purgeExpiredInvites(); } catch (Throwable ignored) {} },
                40L, 40L
        );

        log("[TRADE] TradeService init; cleanupTaskId=" + cleanupTaskId + " tradedebug=" + cfg.debugDeep);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public TradeService() {
        this(OreoEssentials.get(), new TradeConfig(OreoEssentials.get()));
    }

    private boolean dbg() {
        try {
            return cfg != null && cfg.debugDeep;
        } catch (Throwable t) {
            return false;
        }
    }

    private void log(String s) {
        if (dbg()) plugin.getLogger().info(s);
    }

    public TradeConfig getConfig() {
        return cfg;
    }

    public TradeMenuRegistry getMenuRegistry() {
        return menuRegistry;
    }

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

        Player requesterLocal = Bukkit.getPlayer(inv.fromId);
        if (requesterLocal != null && requesterLocal.isOnline()) {
            invites.remove(acceptor.getUniqueId());
            log("[TRADE] starting LOCAL trade " + requesterLocal.getName() + " <-> " + acceptor.getName());
            startLocalTrade(requesterLocal, acceptor);
            return true;
        }

        var broker = plugin.getTradeBroker();
        boolean messaging = plugin.isMessagingAvailable();
        log("[TRADE] cross-server accept path: broker=" + (broker != null) + " messaging=" + messaging);

        if (broker != null && messaging) {
            invites.remove(acceptor.getUniqueId());
            UUID sid = TradeIds.computeTradeId(inv.fromId, acceptor.getUniqueId());
            boolean published = tryPublishStartWithSid(broker, sid, inv.fromId, inv.fromName, acceptor);

            if (!published) {
                try {
                    broker.acceptInvite(acceptor, inv.fromId, inv.fromName);
                    log("[TRADE] broker.acceptInvite(...) called (legacy fallback).");
                } catch (Throwable t) {
                    log("[TRADE] broker legacy acceptInvite failed: " + t.getMessage());
                }
            }

            log("[TRADE] acceptInvite sessionUpsert sid=" + sid
                    + " aId=" + inv.fromId + " bId=" + acceptor.getUniqueId());
            openOrCreateCrossServerSession(sid, inv.fromId, inv.fromName,
                    acceptor.getUniqueId(), acceptor.getName());

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

    public void addIncomingInvite(Player receiver, UUID fromId, String fromName) {
        log("[TRADE] addIncomingInvite receiver=" + safe(receiver) + " from=" + fromName + "/" + fromId);
        if (receiver == null || !receiver.isOnline() || fromId == null) return;

        Invite existing = invites.get(receiver.getUniqueId());
        if (existing != null && existing.fromId.equals(fromId)) {
            log("[TRADE] duplicate invite ignored for " + receiver.getName() + " from " + fromName);
            return;
        }

        invites.put(receiver.getUniqueId(), new Invite(fromId, fromName));
        receiver.sendMessage("§e" + (fromName == null ? "Someone" : fromName)
                + " §7wants to trade §8(cross-server)§7. Type §b/trade "
                + (fromName == null ? "player" : fromName) + " §7to accept (expires in §e"
                + INVITE_TTL_SECONDS + "s§7).");
    }

    public void addIncomingInvite(UUID receiverId, UUID fromId, String fromName) {
        if (receiverId == null || fromId == null) return;

        Invite existing = invites.get(receiverId);
        if (existing != null && existing.fromId.equals(fromId)) {
            log("[TRADE] duplicate invite ignored for " + receiverId + " from " + fromName);
            return;
        }

        invites.put(receiverId, new Invite(fromId, fromName));
        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver != null && receiver.isOnline()) {
            receiver.sendMessage("§e" + (fromName == null ? "Someone" : fromName)
                    + " §7wants to trade §8(cross-server)§7. Type §b/trade "
                    + (fromName == null ? "player" : fromName)
                    + " §7to accept (expires in §e" + INVITE_TTL_SECONDS + "s§7).");
        }
    }

    private void startLocalTrade(Player a, Player b) {
        UUID sid = TradeIds.computeTradeId(a.getUniqueId(), b.getUniqueId());
        clearGrantDedupFor(sid, a.getUniqueId(), b.getUniqueId());
        log("[TRADE] startLocalTrade sid=" + sid);

        TradeSession sess = sessionsById.computeIfAbsent(sid, __ ->
                buildSession(sid, a.getUniqueId(), a.getName(), b.getUniqueId(), b.getName()));

        playerToSession.put(a.getUniqueId(), sid);
        playerToSession.put(b.getUniqueId(), sid);
        sess.open();
        drainPendingStates(sid);
        log("[TRADE] local TradeSession.open() called sid=" + sid);
    }

    public void openOrCreateCrossServerSession(UUID sid, UUID aId, String aName, UUID bId, String bName) {
        if (sid == null || aId == null || bId == null) {
            log("[TRADE] openOrCreateCrossServerSession ABORT: null params");
            return;
        }

        log("[TRADE] openOrCreateCrossServerSession sid=" + sid
                + " A=" + aName + "/" + aId + " B=" + bName + "/" + bId);

        TradeSession existing = sessionsById.get(sid);
        if (existing != null) {
            if (existing.isClosingOrClosed() || existing.isClosed()) {
                log("[TRADE] session already closing/closed, aborting open sid=" + sid);
                return;
            }
            log("[TRADE] session already exists for sid=" + sid + ", reusing");
        }

        TradeSession session = sessionsById.computeIfAbsent(sid, __ ->
                buildSession(sid, aId, aName, bId, bName));

        clearGrantDedupFor(sid, aId, bId);

        UUID oldSidA = playerToSession.put(aId, sid);
        UUID oldSidB = playerToSession.put(bId, sid);

        if (oldSidA != null && !oldSidA.equals(sid)) {
            log("[TRADE] player A was in another session " + oldSidA + ", cleaning up");
            TradeSession oldA = sessionsById.get(oldSidA);
            if (oldA != null && !oldA.isClosed()) {
                try {
                    returnAllAndCleanup(oldA, "New trade started");
                } catch (Throwable t) {
                    log("[TRADE] cleanup old session A failed: " + t.getMessage());
                }
            }
        }

        if (oldSidB != null && !oldSidB.equals(sid)) {
            log("[TRADE] player B was in another session " + oldSidB + ", cleaning up");
            TradeSession oldB = sessionsById.get(oldSidB);
            if (oldB != null && !oldB.isClosed()) {
                try {
                    returnAllAndCleanup(oldB, "New trade started");
                } catch (Throwable t) {
                    log("[TRADE] cleanup old session B failed: " + t.getMessage());
                }
            }
        }

        Player localA = Bukkit.getPlayer(aId);
        Player localB = Bukkit.getPlayer(bId);

        boolean aIsLocal = localA != null && localA.isOnline();
        boolean bIsLocal = localB != null && localB.isOnline();

        log("[TRADE] local presence: A=" + aIsLocal + " B=" + bIsLocal);

        if (!aIsLocal && !bIsLocal) {
            log("[TRADE] ABORT: neither player is local on this server");
            sessionsById.remove(sid);
            playerToSession.remove(aId, sid);
            playerToSession.remove(bId, sid);
            return;
        }

        final TradeSession finalSession = session;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!sessionsById.containsKey(sid)) {
                    log("[TRADE] session removed before GUI open, aborting sid=" + sid);
                    return;
                }

                if (finalSession.isClosingOrClosed() || finalSession.isClosed()) {
                    log("[TRADE] session closed before GUI open, aborting sid=" + sid);
                    return;
                }

                finalSession.open();
                log("[TRADE] GUI opened for local viewers sid=" + sid);
                drainPendingStates(sid);
                log("[TRADE] drained pending states sid=" + sid);

            } catch (Throwable t) {
                log("[TRADE] error opening session GUI: " + t.getMessage());
                t.printStackTrace();
                try {
                    returnAllAndCleanup(finalSession, "Failed to open trade GUI");
                } catch (Throwable ignored) {}
            }
        }, 10L);
    }

    private TradeSession buildSession(UUID sid, UUID aId, String aName, UUID bId, String bName) {
        final UUID capturedSid = sid;

        return new TradeSession(
                sid, plugin, cfg, aId, aName, bId, bName,
                (finished) -> log("[TRADE] onFinish sid=" + capturedSid),
                (s, reason) -> {
                    log("[TRADE] onCancel sid=" + capturedSid + " reason=" + reason);
                    cancelSession(capturedSid, reason);
                },
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

    public OreoEssentials getPlugin() {
        return plugin;
    }

    public TradeSession getSession(UUID sid) {
        return sessionsById.get(sid);
    }

    public UUID getTradeIdByPlayer(UUID playerId) {
        return playerId != null ? playerToSession.get(playerId) : null;
    }

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
            s.setReadyA(false);
        } else if (playerId.equals(s.getBId())) {
            s.setOfferItemB(index, (item == null || item.getType().isAir()) ? null : item.clone());
            s.setReadyB(false);
        } else {
            return;
        }

        publishState(sid, s);
        refreshLocalViewers(s);
    }

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

    public void requestCancel(UUID playerId) {
        if (playerId == null) return;
        UUID sid = getTradeIdByPlayer(playerId);
        log("[TRADE] requestCancel by=" + playerId + " sid=" + sid);
        if (sid == null) return;

        TradeSession s = getSession(sid);
        if (s == null) return;

        returnAllAndCleanup(s, "Trade cancelled.");
    }

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
        returnAllAndCleanup(s, "Trade closed.");
    }

    private void finalizeIfBothReady(UUID sid) {
        TradeSession s = getSession(sid);
        if (s == null) return;

        if (!s.tryMarkGrantingOnce()) {
            log("[TRADE] finalizeIfBothReady skipped (grant already in progress) sid=" + sid);
            return;
        }

        String localServer = plugin.getConfigService().serverName();
        String nodeA = findNodeFor(s.getAId());
        boolean weAreLeader = localServer != null && localServer.equalsIgnoreCase(nodeA);

        if (!weAreLeader) {
            log("[TRADE] finalizeIfBothReady skipped (we are not leader) sid=" + sid
                    + " localServer=" + localServer + " nodeA=" + nodeA);
            s.beginClosing();
            return;
        }

        log("[TRADE] finalizeIfBothReady WE ARE LEADER sid=" + sid);
        s.beginClosing();
        s.lockUiNow();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                var broker = plugin.getTradeBroker();
                if (broker != null && plugin.isMessagingAvailable()) {
                    ItemStack[] itemsForA = getItemsForA(s);
                    ItemStack[] itemsForB = getItemsForB(s);

                    log("[TRADE] finalize sid=" + s.getId()
                            + " toA=" + summarize(itemsForA)
                            + " toB=" + summarize(itemsForB));

                    try {
                        broker.sendTradeGrant(s.getId(), s.getAId(), itemsForA);
                        broker.sendTradeGrant(s.getId(), s.getBId(), itemsForB);
                        log("[TRADE] Published TradeGrant for BOTH sides sid=" + s.getId()
                                + " grantToA=" + countNonAir(itemsForA)
                                + " grantToB=" + countNonAir(itemsForB));
                    } catch (Throwable netErr) {
                        plugin.getLogger().warning("[TRADE] sendTradeGrant failed, falling back to local completion: "
                                + netErr.getMessage());
                        completeTradeLocally(s);
                        return;
                    }

                    s.closeLocalViewers();
                    s.clearOfferA();
                    s.clearOfferB();
                    s.markCompleted();
                    cleanupSession(s);
                } else {
                    completeTradeLocally(s);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[TRADE] finalize failed: " + t.getMessage());
                returnAllAndCleanup(s, "Trade failed, items returned.");
            }
        });
    }

    private String findNodeFor(UUID playerId) {
        try {
            var dir = plugin.getPlayerDirectory();
            return (dir != null) ? dir.lookupCurrentServer(playerId) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String summarize(ItemStack[] arr) {
        if (arr == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            ItemStack it = arr[i];
            if (it != null && !it.getType().isAir()) {
                sb.append(i).append('=').append(it.getType()).append('x').append(it.getAmount()).append(", ");
            }
        }
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        return sb.append(']').toString();
    }

    private void completeTradeLocally(TradeSession s) {
        var itemsA = safeCloneArray(s.viewOfferA());
        var itemsB = safeCloneArray(s.viewOfferB());

        s.beginClosing();
        s.lockUiNow();
        s.clearOfferA();
        s.clearOfferB();

        returnItemsTo(s.getBId(), itemsA);
        returnItemsTo(s.getAId(), itemsB);

        notify(s.getAId(), "§aTrade complete!");
        notify(s.getBId(), "§aTrade complete!");

        closeLocalMenus(s);
        s.markCompleted();
        cleanupSession(s);
    }

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
        clearGrantDedupFor(sid, s.getAId(), s.getBId());
        playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue()));
        publishClose(sid);
    }

    private void cleanupSessionSilent(TradeSession s) {
        if (s == null) return;
        UUID sid = s.getId();
        clearGrantDedupFor(sid, s.getAId(), s.getBId());

        try { sessionsById.remove(sid); } catch (Throwable ignored) {}
        try { playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue())); } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(s.getAId()); } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(s.getBId()); } catch (Throwable ignored) {}
    }

    private void refreshLocalViewers(TradeSession s) {
        var reg = getMenuRegistry();
        if (reg == null) return;
        try { reg.refreshViewer(s.getAId()); } catch (Throwable ignored) {}
        try { reg.refreshViewer(s.getBId()); } catch (Throwable ignored) {}
    }

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
        playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue()));
        s.closeLocalViewers();
    }

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

    public void applyRemoteState(TradeStatePacket p) {
        if (p == null) return;

        TradeSession s = getSession(p.getSessionId());
        if (s == null) {
            enqueueState(p);
            log("[TRADE] applyRemoteState: queued until session exists " + p.getSessionId());
            return;
        }
        if (s.isClosingOrClosed() || s.isUiLocked() || s.isCompleted()) {
            log("[TRADE] ignore state on closed/locked session " + p.getSessionId());
            return;
        }

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

        if (readyA && readyB && !s.isUiLocked() && !s.isCompleted()) {
            finalizeIfBothReady(p.getSessionId());
            return;
        }

        if (!s.isUiLocked() && !s.isCompleted()) {
            try {
                menuRegistry.refreshViewer(s.getAId());
                menuRegistry.refreshViewer(s.getBId());
            } catch (Throwable ignored) {}
        }
    }

    private void clearGrantDedupFor(UUID sid, UUID aId, UUID bId) {
        if (sid == null) return;
        try { deliveredGrants.remove(grantKey(sid, aId)); } catch (Throwable ignored) {}
        try { deliveredGrants.remove(grantKey(sid, bId)); } catch (Throwable ignored) {}
    }

    private static String grantKey(UUID sid, UUID to) {
        return sid + "|" + to;
    }

    public void handleRemoteGrant(TradeGrantPacket p) {
        if (p == null) return;

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handleRemoteGrant(p));
            return;
        }

        try {
            UUID sid = p.getSessionId();
            UUID to = p.getGrantTo();

            String key = grantKey(sid, to);
            if (!deliveredGrants.add(key)) {
                return;
            }

            TradeSession sess = getSession(sid);

            ItemStack[] decoded = ItemStacksCodec.decodeFromBytes(p.getItemsBytes());
            if (decoded == null) decoded = new ItemStack[0];

            ItemStack[] items = java.util.Arrays.stream(decoded)
                    .filter(java.util.Objects::nonNull)
                    .filter(this::isNotDecorativeGuiItem)
                    .toArray(ItemStack[]::new);

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

            grantItems(target, items);

            if (sess != null) {
                sess.markCompleted();
                sess.forceClearAndCloseLocal();
                removeSession(sid);
            }

            try {
                var brokerObj = plugin.getTradeBroker();
                if (brokerObj != null) {
                    try {
                        var m = brokerObj.getClass().getMethod("sendClose", java.util.UUID.class, java.util.UUID.class);
                        m.invoke(brokerObj, sid, to);
                    } catch (NoSuchMethodException noModern) {
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

    public void grantItems(Player player, ItemStack[] items) {
        if (player == null || items == null || items.length == 0) return;

        plugin.getLogger().info("[TRADE] grantItems -> " + player.getName() + " items=" + summarize(items));
        List<ItemStack> give = new ArrayList<>();
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir() || it.getAmount() <= 0) continue;
            give.add(it.clone());
        }
        if (give.isEmpty()) return;

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(give.toArray(new ItemStack[0]));

        if (!leftover.isEmpty()) {
            leftover.values().forEach(rem -> {
                if (rem != null && !rem.getType().isAir() && rem.getAmount() > 0) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rem);
                }
            });
        }

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        } catch (Throwable ignored) {}
    }

    public void removeSession(UUID sid) {
        if (sid == null) return;
        TradeSession s = sessionsById.remove(sid);
        playerToSession.entrySet().removeIf(e -> sid.equals(e.getValue()));
        if (s != null) {
            try { if (menuRegistry != null) menuRegistry.unregister(s.getAId()); } catch (Throwable ignored) {}
            try { if (menuRegistry != null) menuRegistry.unregister(s.getBId()); } catch (Throwable ignored) {}
        }
    }

    private void publishState(UUID sid, TradeSession s) {
        var broker = plugin.getTradeBroker();
        if (broker == null) return;

        if (s == null || s.isClosingOrClosed() || s.isClosed() || s.isUiLocked() || s.isCompleted()) {
            return;
        }

        Player a = Bukkit.getPlayer(s.getAId());
        Player b = Bukkit.getPlayer(s.getBId());

        if (a != null && a.isOnline()) {
            ItemStack[] offerA = filterOfferArray(s.viewOfferA());
            broker.publishState(sid, a, s.isReadyA(), offerA);
        }

        if (b != null && b.isOnline()) {
            ItemStack[] offerB = filterOfferArray(s.viewOfferB());
            broker.publishState(sid, b, s.isReadyB(), offerB);
        }
    }

    private ItemStack[] filterOfferArray(ItemStack[] raw) {
        if (raw == null || raw.length == 0) {
            return new ItemStack[0];
        }

        return Arrays.stream(raw)
                .filter(Objects::nonNull)
                .filter(this::isNotDecorativeGuiItem)
                .toArray(ItemStack[]::new);
    }

    private boolean isNotDecorativeGuiItem(ItemStack item) {
        if (item == null) return false;

        Material type = item.getType();
        if (type == null || type.isAir()) return false;

        String matName = type.name();

        if (matName.contains("GLASS") || matName.contains("PANE")) {
            return false;
        }

        switch (type) {
            case BARRIER:
            case BEDROCK:
            case STRUCTURE_VOID:
            case LIGHT:
                return false;
            default:
                break;
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName())
                    .toLowerCase(java.util.Locale.ROOT);

            if (name.contains("filler") || name.contains("border") || name.contains("trade ui")
                    || name.contains("locked") || name.contains("interface") || name.contains("menu")) {
                return false;
            }
        }

        return true;
    }

    private void publishClose(UUID sid) {
        try {
            var broker = plugin.getTradeBroker();
            if (broker == null) return;

            try {
                Method m = broker.getClass().getMethod("publishClose", UUID.class);
                m.invoke(broker, sid);
            } catch (NoSuchMethodException ignore) {
            }
        } catch (Throwable ignored) {}
    }

    private void purgeExpiredInvites() {
        if (!dbg()) {
            invites.entrySet().removeIf(e -> e.getValue().expired(INVITE_TTL_SECONDS));
            return;
        }
        int before = invites.size();
        invites.entrySet().removeIf(e -> e.getValue().expired(INVITE_TTL_SECONDS));
        int after = invites.size();
        if (before != after) {
            log("[TRADE] purgeExpiredInvites removed=" + (before - after) + " remaining=" + after);
        }
    }

    private static String safe(Player p) {
        return (p == null ? "null" : p.getName() + "/" + p.getUniqueId());
    }

    private boolean tryPublishStartWithSid(Object broker, UUID sid, UUID requesterId, String requesterName, Player acceptor) {
        try {
            Method m = broker.getClass().getMethod("publishStart", UUID.class, UUID.class, String.class, Player.class);
            m.invoke(broker, sid, requesterId, requesterName, acceptor);
            log("[TRADE] broker.publishStart(sid, ...) called.");
            return true;
        } catch (NoSuchMethodException ignore) {
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

    private ItemStack[] getItemsForA(TradeSession s) {
        try {
            ItemStack[] compact = s.getOfferBCompact();
            return safeCloneArray(compact);
        } catch (Throwable ignored) {
            return safeCloneArray(s.viewOfferB());
        }
    }

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

    public void handleRemoteClose(TradeClosePacket pkt) {
        if (pkt == null) return;

        UUID sid = pkt.getSessionId();
        UUID target = pkt.getGrantTo();

        TradeSession s = getSession(sid);
        if (s == null) return;

        try { s.beginClosing(); } catch (Throwable ignored) {}
        try { s.lockUiNow(); } catch (Throwable ignored) {}

        UUID aId = s.getAId();
        UUID bId = s.getBId();

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

        try { if (menuRegistry != null) menuRegistry.unregister(target); } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(aId); } catch (Throwable ignored) {}
        try { if (menuRegistry != null) menuRegistry.unregister(bId); } catch (Throwable ignored) {}
        try { s.markCompleted(); } catch (Throwable ignored) {}

        cleanupSessionSilent(s);
    }

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

    private void enqueueState(TradeStatePacket p) {
        pendingStates.computeIfAbsent(p.getSessionId(), k -> new ArrayDeque<>(8)).addLast(p);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessionsById.get(p.getSessionId()) == null && pendingStates.containsKey(p.getSessionId())) {
                try {
                    var broker = plugin.getTradeBroker();
                    if (broker != null) broker.requestStartReplay(p.getSessionId());
                } catch (Throwable ignored) {}
            }
        }, 20L);
    }

    private void drainPendingStates(UUID sid) {
        Deque<TradeStatePacket> q = pendingStates.remove(sid);
        if (q == null) return;
        while (!q.isEmpty()) applyRemoteState(q.pollFirst());
    }
}