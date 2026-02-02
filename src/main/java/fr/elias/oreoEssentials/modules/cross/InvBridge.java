package fr.elias.oreoEssentials.modules.cross;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.enderchest.EnderChestService;
import fr.elias.oreoEssentials.modules.enderchest.EnderChestStorage;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.cross.rabbit.packets.CrossInvPacket;
import fr.elias.oreoEssentials.services.InventoryService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.function.Consumer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class InvBridge {
    private static final Gson GSON = new Gson();

    public enum Kind { INV, EC }

    private final OreoEssentials plugin;
    private final PacketManager packets;
    private final String thisServer;
    private final Map<String, CompletableFuture<SnapshotResponse>> pending = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ApplyAck>> pendingApply = new ConcurrentHashMap<>();
    private final long TIMEOUT_MS = 1500; // 1.5s
    private static final int MAX_ATTEMPTS = 2;

    public InvBridge(OreoEssentials plugin, PacketManager packets, String thisServer) {
        this.plugin = plugin;
        this.packets = packets;
        this.thisServer = thisServer;

        plugin.getLogger().info("[INV-DEBUG] InvBridge ctor: thisServer=" + thisServer
                + " packetsIsNull=" + (packets == null)
                + " packetsInitialized=" + (packets != null && packets.isInitialized()));

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[INV-BRIDGE] PacketManager not available; running in local-only mode.");
            return; // do not subscribe, publish() will also no-op
        }

        plugin.getLogger().info("[INV-BRIDGE] Subscribing CrossInvPacket on server=" + thisServer);

        packets.subscribe(CrossInvPacket.class, (channel, pkt) -> {
            try {
                final String payload = pkt.getJson();
                Base base = GSON.fromJson(payload, Base.class);
                if (base == null || base.type == null) {
                    plugin.getLogger().warning("[INV-DEBUG] Incoming CrossInvPacket with null/unknown base on server=" + thisServer);
                    return;
                }

                plugin.getLogger().info("[INV-DEBUG] Incoming CrossInvPacket: type=" + base.type
                        + " channel=" + channel + " jsonLen=" + payload.length()
                        + " on server=" + thisServer);

                switch (base.type) {
                    case "INV_REQ"   -> onRequest(payload);
                    case "INV_RESP"  -> onResponse(payload);
                    case "INV_APPLY" -> onApply(payload);
                    case "INV_ACK"   -> onAck(payload);
                    default -> plugin.getLogger().fine("[INV-BRIDGE] Unknown packet type=" + base.type);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-BRIDGE] Incoming packet error: " + t.getMessage());
            }
        });

        plugin.getLogger().info("[INV-BRIDGE] Cross-server bridge ready on server=" + thisServer);
    }


    static class Base {
        @SerializedName("type") public String type;
    }

    static final class SnapshotRequest extends Base {
        public String correlationId;
        public String sourceServer;
        public UUID   target;
        public Kind   kind;
        public int    ecRows;
    }

    static final class SnapshotResponse extends Base {
        public String correlationId;
        public boolean ok;
        public String  error;
        public UUID    target;
        public Kind    kind;
        public byte[]  blob;
        public int     arrayLen;
    }

    static final class ApplyRequest extends Base {
        public String correlationId;
        public String sourceServer;
        public UUID   target;
        public Kind   kind;
        public byte[] blob;
        public int    arrayLen;
        public int    ecRows;
    }

    static final class ApplyAck extends Base {
        public String correlationId;
        public boolean ok;
        public String  error;
    }


    public InventoryService.Snapshot requestLiveInv(UUID target) {
        plugin.getLogger().info("[INV-DEBUG] requestLiveInv(): target=" + target
                + " on server=" + thisServer);

        // Local fast-path
        Player local = Bukkit.getPlayer(target);
        if (local != null && local.isOnline()) {
            plugin.getLogger().info("[INV-DEBUG] requestLiveInv(local): found player="
                    + local.getName() + " uuid=" + local.getUniqueId()
                    + " on server=" + thisServer);
            try {
                InventoryService.Snapshot s = new InventoryService.Snapshot();
                s.contents = Arrays.copyOf(local.getInventory().getContents(), 41);
                s.armor    = Arrays.copyOf(local.getInventory().getArmorContents(), 4);
                s.offhand  = local.getInventory().getItemInOffHand();

                //  XP
                s.level    = local.getLevel();
                s.exp      = local.getExp();
                s.totalExp = local.getTotalExperience();

                return s;
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-BRIDGE] Local requestLiveInv error for " + target + ": " + t.getMessage());
                return null;
            }
        }

        plugin.getLogger().info("[INV-DEBUG] requestLiveInv(): player not local, going remote. target=" + target
                + " on server=" + thisServer);

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[INV-BRIDGE] requestLiveInv(remote) but PacketManager is not initialized.");
            return null;
        }

        try {
            SnapshotResponse resp = requestSnapshot(target, Kind.INV, 0);

            if (resp == null) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveInv: no response for " + target
                        + " on server=" + thisServer);
                return null;
            }
            plugin.getLogger().info("[INV-DEBUG] requestLiveInv(): got resp for target=" + target
                    + " ok=" + resp.ok + " kind=" + resp.kind
                    + " respTarget=" + resp.target + " arrayLen=" + resp.arrayLen);

            if (!resp.ok) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveInv: NACK for " + target + " error=" + resp.error);
                return null;
            }
            if (resp.blob == null) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveInv: empty blob for " + target);
                return null;
            }

            ItemStack[] flat = BukkitSerialization.fromBytes(resp.blob, ItemStack[].class);
            if (flat == null) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveInv: deserialization failed for " + target);
                return null;
            }

            return InvLayouts.fromFlat(flat);
        } catch (Throwable t) {
            plugin.getLogger().warning("[INV-BRIDGE] requestLiveInv error for " + target + ": " + t.getMessage());
            return null;
        }
    }

    public ItemStack[] requestLiveEc(UUID target, int rows) {
        plugin.getLogger().info("[INV-DEBUG] requestLiveEc(): target=" + target
                + " rows=" + rows + " on server=" + thisServer);

        // Local fast-path
        Player local = Bukkit.getPlayer(target);
        if (local != null && local.isOnline()) {
            try {
                EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);
                if (svc != null) {
                    int r = Math.max(1, rows);
                    plugin.getLogger().info("[INV-DEBUG] requestLiveEc(local): found player="
                            + local.getName() + " uuid=" + local.getUniqueId()
                            + " rows=" + r + " on server=" + thisServer);
                    return EnderChestStorage.clamp(svc.loadFor(local.getUniqueId(), r), r);
                } else {
                    plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc(local) but EnderChestService is null.");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc(local) error for " + target + ": " + t.getMessage());
            }
        }

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc(remote) but PacketManager is not initialized.");
            return null;
        }

        try {
            SnapshotResponse resp = requestSnapshot(target, Kind.EC, rows);

            if (resp == null) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc: no response for " + target);
                return null;
            }
            plugin.getLogger().info("[INV-DEBUG] requestLiveEc(): got resp for target=" + target
                    + " ok=" + resp.ok + " kind=" + resp.kind
                    + " respTarget=" + resp.target + " arrayLen=" + resp.arrayLen);

            if (!resp.ok) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc: NACK for " + target + " error=" + resp.error);
                return null;
            }
            if (resp.blob == null) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc: empty blob for " + target);
                return null;
            }

            ItemStack[] ec = BukkitSerialization.fromBytes(resp.blob, ItemStack[].class);
            if (ec == null) {
                plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc: deserialization failed for " + target);
            }
            return ec;
        } catch (Throwable t) {
            plugin.getLogger().warning("[INV-BRIDGE] requestLiveEc error for " + target + ": " + t.getMessage());
            return null;
        }
    }

    public boolean applyLiveInv(UUID target, InventoryService.Snapshot snap) {
        plugin.getLogger().info("[INV-DEBUG] applyLiveInv(): target=" + target
                + " on server=" + thisServer);

        Player local = Bukkit.getPlayer(target);
        if (local != null && local.isOnline()) {
            plugin.getLogger().info("[INV-DEBUG] applyLiveInv(local): found player="
                    + local.getName() + " uuid=" + local.getUniqueId()
                    + " on server=" + thisServer);
            try {
                var pi = local.getInventory();
                ItemStack[] main = Arrays.copyOf(
                        snap.contents == null ? new ItemStack[41] : snap.contents,
                        Math.max(pi.getContents().length, 41)
                );
                pi.setContents(main);
                if (snap.armor != null) {
                    pi.setArmorContents(Arrays.copyOf(snap.armor, 4));
                }
                pi.setItemInOffHand(snap.offhand);
                return true;
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-BRIDGE] applyLiveInv(local) error for " + target + ": " + t.getMessage());
                return false;
            }
        }

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[INV-BRIDGE] applyLiveInv(remote) but PacketManager is not initialized.");
            return false;
        }

        ItemStack[] flat = InvLayouts.toFlat(snap);
        byte[] payload = BukkitSerialization.toBytes(flat);
        plugin.getLogger().info("[INV-DEBUG] applyLiveInv(remote): target=" + target
                + " len=" + flat.length + " on server=" + thisServer);
        return applyRemote(target, Kind.INV, payload, flat.length, 0);
    }

    public boolean applyLiveEc(UUID target, ItemStack[] ec, int rows) {
        plugin.getLogger().info("[INV-DEBUG] applyLiveEc(): target=" + target
                + " rows=" + rows + " on server=" + thisServer);

        // Local fast-path
        Player local = Bukkit.getPlayer(target);
        if (local != null && local.isOnline()) {
            plugin.getLogger().info("[INV-DEBUG] applyLiveEc(local): found player="
                    + local.getName() + " uuid=" + local.getUniqueId()
                    + " rows=" + rows + " on server=" + thisServer);
            try {
                EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);
                if (svc == null) {
                    plugin.getLogger().warning("[INV-BRIDGE] applyLiveEc(local) but EnderChestService is null.");
                    return false;
                }

                ItemStack[] clamp = EnderChestStorage.clamp(ec, rows);

                if (local.getOpenInventory() != null &&
                        EnderChestService.TITLE.equals(local.getOpenInventory().getTitle())) {
                    int allowed = svc.resolveSlots(local);
                    for (int i = 0; i < Math.min(allowed, clamp.length); i++) {
                        local.getOpenInventory().getTopInventory().setItem(i, clamp[i]);
                    }
                    svc.saveFromInventory(local, local.getOpenInventory().getTopInventory());
                } else {
                    svc.saveFor(local.getUniqueId(), Math.max(1, rows), clamp);
                }
                return true;
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-BRIDGE] applyLiveEc(local) error for " + target + ": " + t.getMessage());
                return false;
            }
        }

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[INV-BRIDGE] applyLiveEc(remote) but PacketManager is not initialized.");
            return false;
        }

        ItemStack[] clamp = EnderChestStorage.clamp(ec, rows);
        byte[] payload = BukkitSerialization.toBytes(clamp);
        plugin.getLogger().info("[INV-DEBUG] applyLiveEc(remote): target=" + target
                + " len=" + clamp.length + " rows=" + rows + " on server=" + thisServer);
        return applyRemote(target, Kind.EC, payload, clamp.length, rows);
    }
    public void requestLiveInvAsync(UUID target, Consumer<InventoryService.Snapshot> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            InventoryService.Snapshot result = null;

            try {
                // On ne touche PAS à Bukkit ici, on utilise juste la couche Rabbit
                SnapshotResponse resp = requestSnapshot(target, Kind.INV, 0);
                if (resp != null && resp.ok && resp.blob != null) {
                    ItemStack[] flat = BukkitSerialization.fromBytes(resp.blob, ItemStack[].class);
                    if (flat != null) {
                        result = InvLayouts.fromFlat(flat);
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-DEBUG] requestLiveInvAsync error for " + target + ": " + t.getMessage());
            }

            InventoryService.Snapshot finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        });
    }

    public void applyLiveInvAsync(UUID target,
                                  InventoryService.Snapshot snap,
                                  Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = false;
            try {
                ItemStack[] flat = InvLayouts.toFlat(snap);
                byte[] payload = BukkitSerialization.toBytes(flat);
                ok = applyRemote(target, Kind.INV, payload, flat.length, 0);
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-DEBUG] applyLiveInvAsync error for " + target + ": " + t.getMessage());
            }

            boolean finalOk = ok;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalOk));
        });
    }

    private SnapshotResponse requestSnapshot(UUID target, Kind kind, int ecRows) {
        final int maxAttempts = 3;
        long backoffMs = 200L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String corr = UUID.randomUUID().toString();
            SnapshotRequest req = new SnapshotRequest();
            req.type          = "INV_REQ";
            req.correlationId = corr;
            req.sourceServer  = this.thisServer;
            req.target        = target;
            req.kind          = kind;
            req.ecRows        = ecRows;

            CompletableFuture<SnapshotResponse> fut = new CompletableFuture<>();
            pending.put(corr, fut);

            plugin.getLogger().info("[INV-DEBUG] requestSnapshot(): SEND attempt="
                    + attempt + "/" + maxAttempts
                    + " kind=" + kind
                    + " corr=" + corr
                    + " target=" + target
                    + " ecRows=" + ecRows
                    + " from=" + thisServer);

            publish(req);

            try {
                SnapshotResponse resp = fut.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (resp != null) {
                    plugin.getLogger().info("[INV-DEBUG] requestSnapshot(): GOT resp corr=" + corr
                            + " ok=" + resp.ok + " kind=" + resp.kind
                            + " respTarget=" + resp.target + " arrayLen=" + resp.arrayLen
                            + " attempt=" + attempt + "/" + maxAttempts);
                } else {
                    plugin.getLogger().warning("[INV-DEBUG] requestSnapshot(): FUTURE returned null resp for corr=" + corr
                            + " target=" + target + " kind=" + kind
                            + " attempt=" + attempt + "/" + maxAttempts);
                }
                return resp;
            } catch (TimeoutException te) {
                plugin.getLogger().warning("[INV-DEBUG] requestSnapshot(): TIMEOUT for target=" + target
                        + " kind=" + kind + " corr=" + corr
                        + " attempt=" + attempt + "/" + maxAttempts
                        + " on server=" + thisServer);

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ignored) {}
                    backoffMs *= 2; // exponential backoff
                    continue; // try again with new correlationId
                }
                return null;
            } catch (Exception e) {
                plugin.getLogger().warning("[INV-BRIDGE] Snapshot error for " + target
                        + " kind=" + kind
                        + " corr=" + corr
                        + " attempt=" + attempt + "/" + maxAttempts
                        + " -> " + e.getMessage());
                return null;
            } finally {
                pending.remove(corr);
            }
        }

        return null;
    }

    private boolean applyRemote(UUID target, Kind kind, byte[] payload, int arrayLen, int ecRows) {
        final int maxAttempts = 3;
        long backoffMs = 200L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String corr = UUID.randomUUID().toString();
            ApplyRequest req = new ApplyRequest();
            req.type          = "INV_APPLY";
            req.correlationId = corr;
            req.sourceServer  = this.thisServer;
            req.target        = target;
            req.kind          = kind;
            req.blob          = payload;
            req.arrayLen      = arrayLen;
            req.ecRows        = ecRows;

            CompletableFuture<ApplyAck> fut = new CompletableFuture<>();
            pendingApply.put(corr, fut);

            plugin.getLogger().info("[INV-DEBUG] applyRemote(): SEND attempt="
                    + attempt + "/" + maxAttempts
                    + " kind=" + kind
                    + " corr=" + corr
                    + " target=" + target
                    + " arrayLen=" + arrayLen
                    + " ecRows=" + ecRows
                    + " from=" + thisServer);

            publish(req);

            try {
                ApplyAck ack = fut.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (ack == null) {
                    plugin.getLogger().warning("[INV-DEBUG] applyRemote(): FUTURE returned null ACK for target=" + target
                            + " kind=" + kind + " corr=" + corr
                            + " attempt=" + attempt + "/" + maxAttempts);
                    // will retry if attempt < maxAttempts
                } else {
                    plugin.getLogger().info("[INV-DEBUG] applyRemote(): GOT ACK corr=" + corr
                            + " ok=" + ack.ok + " kind=" + kind
                            + " target=" + target + " error=" + ack.error
                            + " attempt=" + attempt + "/" + maxAttempts);

                    if (!ack.ok) {
                        plugin.getLogger().warning("[INV-BRIDGE] Apply NACK for target=" + target
                                + " kind=" + kind + " error=" + ack.error);
                    }
                    return ack.ok;
                }
            } catch (TimeoutException te) {
                plugin.getLogger().warning("[INV-DEBUG] applyRemote(): TIMEOUT for target=" + target
                        + " kind=" + kind + " corr=" + corr
                        + " attempt=" + attempt + "/" + maxAttempts
                        + " on server=" + thisServer);
            } catch (Exception e) {
                plugin.getLogger().warning("[INV-BRIDGE] Apply error for " + target
                        + " kind=" + kind
                        + " corr=" + corr
                        + " attempt=" + attempt + "/" + maxAttempts
                        + " -> " + e.getMessage());
                return false;
            } finally {
                pendingApply.remove(corr);
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ignored) {}
                backoffMs *= 2;
            } else {
                return false;
            }
        }

        return false;
    }


    private void publish(Object o) {
        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().fine("[INV-BRIDGE] publish() called but PacketManager unavailable. Dropping packet.");
            return;
        }
        String json = GSON.toJson(o);
        String type = (o instanceof Base b && b.type != null) ? b.type : o.getClass().getSimpleName();
        plugin.getLogger().info("[INV-DEBUG] publish(): type=" + type
                + " jsonLen=" + json.length()
                + " on server=" + thisServer);
        packets.sendPacket(PacketChannels.GLOBAL, new CrossInvPacket(json));
    }


    private void onRequest(String json) {
        SnapshotRequest req = GSON.fromJson(json, SnapshotRequest.class);
        if (req == null) {
            plugin.getLogger().warning("[INV-BRIDGE] onRequest: parsed NULL request (jsonLen="
                    + (json == null ? 0 : json.length()) + ") on server=" + thisServer);
            return;
        }

        plugin.getLogger().info("[INV-BRIDGE] onRequest: received INV_REQ "
                + "corr=" + req.correlationId
                + " kind=" + req.kind
                + " target=" + req.target
                + " from=" + req.sourceServer
                + " on server=" + thisServer);

        Bukkit.getScheduler().runTask(plugin, () -> {
            // DEBUG: list all online players on this server
            StringBuilder sb = new StringBuilder();
            sb.append("[INV-DEBUG] onRequest(): online players on server=").append(thisServer).append(" => ");
            for (Player pOnline : Bukkit.getOnlinePlayers()) {
                sb.append(pOnline.getName()).append("(").append(pOnline.getUniqueId()).append(") ");
            }
            plugin.getLogger().info(sb.toString());

            Player p = Bukkit.getPlayer(req.target);
            if (p == null) {
                plugin.getLogger().info("[INV-DEBUG] onRequest(): Bukkit.getPlayer(target) returned null for target="
                        + req.target + " on server=" + thisServer);
            } else {
                plugin.getLogger().info("[INV-DEBUG] onRequest(): Bukkit.getPlayer(target) returned "
                        + p.getName() + "(" + p.getUniqueId() + ") isOnline=" + p.isOnline()
                        + " on server=" + thisServer);
            }

            if (p == null || !p.isOnline()) {
                plugin.getLogger().info("[INV-BRIDGE] onRequest: IGNORE (player not on this server) "
                        + "target=" + req.target + " kind=" + req.kind + " server=" + thisServer);
                return;
            }

            plugin.getLogger().info("[INV-BRIDGE] onRequest: HANDLE target="
                    + p.getName() + " (" + p.getUniqueId() + ") kind=" + req.kind
                    + " on server=" + thisServer);

            SnapshotResponse resp = new SnapshotResponse();
            resp.type = "INV_RESP";
            resp.correlationId = req.correlationId;
            resp.ok = false;
            resp.target = req.target;
            resp.kind = req.kind;

            try {
                switch (req.kind) {
                    case INV -> {
                        InventoryService.Snapshot s = new InventoryService.Snapshot();
                        s.contents = Arrays.copyOf(p.getInventory().getContents(), 41);
                        s.armor    = Arrays.copyOf(p.getInventory().getArmorContents(), 4);
                        s.offhand  = p.getInventory().getItemInOffHand();

                        // NEW
                        s.level    = p.getLevel();
                        s.exp      = p.getExp();
                        s.totalExp = p.getTotalExperience();

                        ItemStack[] flat = InvLayouts.toFlat(s);
                        resp.blob = BukkitSerialization.toBytes(flat);
                        resp.arrayLen = flat.length;
                        resp.ok = true;
                    }

                    case EC -> {
                        EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);
                        if (svc == null) {
                            resp.ok = false;
                            resp.error = "EnderChestService not available on server=" + thisServer;
                            break;
                        }

                        ItemStack[] ec;
                        if (p.getOpenInventory() != null &&
                                EnderChestService.TITLE.equals(p.getOpenInventory().getTitle())) {
                            ec = Arrays.copyOf(
                                    p.getOpenInventory().getTopInventory().getContents(),
                                    Math.max(1, req.ecRows) * 9
                            );
                        } else {
                            int rows = Math.max(1, req.ecRows);
                            ec = EnderChestStorage.clamp(svc.loadFor(p.getUniqueId(), rows), rows);
                        }
                        resp.blob = BukkitSerialization.toBytes(ec);
                        resp.arrayLen = ec.length;
                        resp.ok = true;
                    }
                }
            } catch (Throwable t) {
                resp.ok = false;
                resp.error = "Exception: " + t.getMessage();
                plugin.getLogger().warning("[INV-BRIDGE] onRequest: exception while building resp for target="
                        + req.target + " kind=" + req.kind + " -> " + t.getMessage());
            }

            plugin.getLogger().info("[INV-BRIDGE] onRequest: sending INV_RESP "
                    + "corr=" + resp.correlationId
                    + " ok=" + resp.ok
                    + " kind=" + resp.kind
                    + " target=" + resp.target
                    + " arrayLen=" + resp.arrayLen
                    + " on server=" + thisServer);

            publish(resp);
        });
    }

    private void onResponse(String json) {
        SnapshotResponse resp = GSON.fromJson(json, SnapshotResponse.class);
        if (resp == null) {
            plugin.getLogger().warning("[INV-BRIDGE] onResponse: parsed NULL response (jsonLen="
                    + (json == null ? 0 : json.length()) + ") on server=" + thisServer);
            return;
        }

        plugin.getLogger().info("[INV-DEBUG] onResponse(): corr=" + resp.correlationId
                + " ok=" + resp.ok + " kind=" + resp.kind
                + " target=" + resp.target + " arrayLen=" + resp.arrayLen
                + " on server=" + thisServer);

        CompletableFuture<SnapshotResponse> fut = pending.get(resp.correlationId);
        if (fut != null) {
            plugin.getLogger().info("[INV-DEBUG] onResponse(): completing future for corr=" + resp.correlationId
                    + " on server=" + thisServer);
            fut.complete(resp);
        } else {
            plugin.getLogger().warning("[INV-DEBUG] onResponse(): unknown corr=" + resp.correlationId
                    + " (no pending future) on server=" + thisServer);
        }
    }

    private void onApply(String json) {
        ApplyRequest req = GSON.fromJson(json, ApplyRequest.class);
        if (req == null) {
            plugin.getLogger().warning("[INV-BRIDGE] onApply: parsed NULL request (jsonLen="
                    + (json == null ? 0 : json.length()) + ") on server=" + thisServer);
            return;
        }

        plugin.getLogger().info("[INV-DEBUG] onApply(): received INV_APPLY corr=" + req.correlationId
                + " kind=" + req.kind + " target=" + req.target
                + " arrayLen=" + req.arrayLen + " ecRows=" + req.ecRows
                + " from=" + req.sourceServer + " on server=" + thisServer);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(req.target);
            ApplyAck ack = new ApplyAck();
            ack.type = "INV_ACK";
            ack.correlationId = req.correlationId;
            ack.ok = false;

            // Only owner server should touch inventory
            if (p == null || !p.isOnline()) {
                plugin.getLogger().info("[INV-DEBUG] onApply(): IGNORE target=" + req.target
                        + " kind=" + req.kind + " (not on this server=" + thisServer + ")");
                // NOTE: we DO NOT send a NACK here to avoid confusing other servers.
                return;
            }

            plugin.getLogger().info("[INV-BRIDGE] onApply HANDLE target=" + p.getName()
                    + " (" + p.getUniqueId() + ") kind=" + req.kind + " on server=" + thisServer);

            try {
                switch (req.kind) {
                    case INV -> {
                        ItemStack[] flat = BukkitSerialization.fromBytes(req.blob, ItemStack[].class);
                        InventoryService.Snapshot snap = InvLayouts.fromFlat(flat);

                        var pi = p.getInventory();
                        ItemStack[] main = Arrays.copyOf(
                                snap.contents == null ? new ItemStack[41] : snap.contents,
                                Math.max(pi.getContents().length, 41)
                        );
                        pi.setContents(main);
                        if (snap.armor != null) {
                            pi.setArmorContents(Arrays.copyOf(snap.armor, 4));
                        }
                        p.setItemInHand(snap.offhand); // or setItemInOffHand depending on API version
                        ack.ok = true;
                    }
                    case EC -> {
                        ItemStack[] ec = BukkitSerialization.fromBytes(req.blob, ItemStack[].class);
                        EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);

                        if (svc == null) {
                            ack.ok = false;
                            ack.error = "EnderChestService not available on server=" + thisServer;
                            break;
                        }

                        if (p.getOpenInventory() != null &&
                                EnderChestService.TITLE.equals(p.getOpenInventory().getTitle())) {
                            int allowed = svc.resolveSlots(p);
                            for (int i = 0; i < Math.min(allowed, ec.length); i++) {
                                p.getOpenInventory().getTopInventory().setItem(i, ec[i]);
                            }
                            svc.saveFromInventory(p, p.getOpenInventory().getTopInventory());
                        } else {
                            svc.saveFor(p.getUniqueId(), Math.max(1, req.ecRows), ec);
                        }
                        ack.ok = true;
                    }
                }
            } catch (Throwable t) {
                ack.ok = false;
                ack.error = "Exception: " + t.getMessage();
                plugin.getLogger().warning("[INV-BRIDGE] onApply: exception while applying for target="
                        + req.target + " kind=" + req.kind + " -> " + t.getMessage());
            }

            plugin.getLogger().info("[INV-DEBUG] onApply(): sending INV_ACK corr=" + ack.correlationId
                    + " ok=" + ack.ok + " error=" + ack.error
                    + " on server=" + thisServer);

            publish(ack);
        });
    }

    private void onAck(String json) {
        ApplyAck ack = GSON.fromJson(json, ApplyAck.class);
        if (ack == null) {
            plugin.getLogger().warning("[INV-BRIDGE] onAck: parsed NULL ack (jsonLen="
                    + (json == null ? 0 : json.length()) + ") on server=" + thisServer);
            return;
        }

        plugin.getLogger().info("[INV-DEBUG] onAck(): corr=" + ack.correlationId
                + " ok=" + ack.ok + " error=" + ack.error
                + " on server=" + thisServer);

        // Ignore legacy NACKs from servers that don't own the player
        if (!ack.ok && ack.error != null &&
                ack.error.startsWith("Player not online on this server")) {
            plugin.getLogger().fine("[INV-BRIDGE] onAck legacy NACK ignored: " + ack.error);
            return;
        }

        CompletableFuture<ApplyAck> fut = pendingApply.get(ack.correlationId);
        if (fut != null) {
            plugin.getLogger().info("[INV-DEBUG] onAck(): completing future for corr=" + ack.correlationId
                    + " on server=" + thisServer);
            fut.complete(ack);
        } else {
            plugin.getLogger().warning("[INV-DEBUG] onAck(): unknown corr=" + ack.correlationId
                    + " (no pendingApply future) on server=" + thisServer);
        }
    }


    public static final class InvLayouts {
        public static ItemStack[] toFlat(InventoryService.Snapshot s) {
            ItemStack[] flat = new ItemStack[46];
            ItemStack[] cont = s.contents == null ? new ItemStack[41] : Arrays.copyOf(s.contents, 41);
            ItemStack[] arm  = s.armor == null ? new ItemStack[4]    : Arrays.copyOf(s.armor, 4);
            for (int i = 0; i < 41; i++) flat[i] = cont[i];
            for (int i = 0; i < 4;  i++) flat[41 + i] = arm[i];
            flat[45] = s.offhand;
            return flat;
        }

        public static InventoryService.Snapshot fromFlat(ItemStack[] flat) {
            InventoryService.Snapshot s = new InventoryService.Snapshot();
            ItemStack[] src = flat == null ? new ItemStack[46] : flat;
            s.contents = Arrays.copyOfRange(src, 0, 41);
            s.armor    = Arrays.copyOfRange(src, 41, 45);
            s.offhand  = (src.length > 45 ? src[45] : null);
            return s;
        }
    }

    public static final class BukkitSerialization {
        public static byte[] toBytes(Object o) {
            try (var bos = new java.io.ByteArrayOutputStream();
                 var oos = new org.bukkit.util.io.BukkitObjectOutputStream(bos)) {
                oos.writeObject(o);
                oos.flush();
                return bos.toByteArray();
            } catch (Exception e) {
                return ("ERR:" + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            }
        }

        @SuppressWarnings("unchecked")
        public static <T> T fromBytes(byte[] bytes, Class<T> type) {
            try (var bis = new java.io.ByteArrayInputStream(bytes);
                 var ois = new org.bukkit.util.io.BukkitObjectInputStream(bis)) {
                Object o = ois.readObject();
                return (T) o;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
