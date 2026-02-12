package fr.elias.oreoEssentials.modules.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.trade.config.TradeConfig;
import fr.elias.oreoEssentials.modules.trade.ui.TradeMenu;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public final class TradeSession {

    private final UUID sid;

    private final UUID aId;
    private final String aName;


    private final UUID bId;
    private final String bName;

    private boolean isA(UUID uid) { return uid != null && uid.equals(aId); }
    private boolean isB(UUID uid) { return uid != null && uid.equals(bId); }


    private volatile boolean closing = false;
    private volatile boolean closed  = false;

    public boolean isClosingOrClosed() { return closing || closed; }
    public void beginClosing() { this.closing = true; }
    public void markClosed()   { this.closed = true; this.closing = true; }


    private final OreoEssentials plugin;
    private final TradeConfig cfg;

    private final Consumer<TradeSession> onFinish;

    private final BiConsumer<TradeSession, String> onCancel;

    private final BiConsumer<TradeSession, Long> onStateChanged;

    private volatile TradeMenu menu;


    private long version = 0L;         // monotonic session version (local + remote edits)
    private boolean aReady = false;     // A confirmed?
    private boolean bReady = false;     // B confirmed?

    private final ItemStack[] offerA = new ItemStack[18];
    private final ItemStack[] offerB = new ItemStack[18];

    private volatile boolean uiLocked = false;

    private volatile boolean completed = false;

    private volatile boolean grantedOnce = false;
    public boolean tryMarkGrantingOnce() {
        synchronized (this) {
            if (grantedOnce) return false;
            grantedOnce = true;
            return true;
        }
    }

    public TradeSession(
            UUID sid,
            OreoEssentials plugin,
            TradeConfig cfg,
            UUID aId, String aName,
            UUID bId, String bName,
            Consumer<TradeSession> onFinish,
            BiConsumer<TradeSession, String> onCancel,
            BiConsumer<TradeSession, Long> onStateChanged
    ) {
        this.sid  = Objects.requireNonNull(sid, "sid");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.cfg  = Objects.requireNonNull(cfg, "cfg");
        this.aId  = Objects.requireNonNull(aId, "aId");
        this.bId  = Objects.requireNonNull(bId, "bId");
        this.aName = (aName != null ? aName : "PlayerA");
        this.bName = (bName != null ? bName : "PlayerB");
        this.onFinish = Objects.requireNonNull(onFinish, "onFinish");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        this.onStateChanged = Objects.requireNonNull(onStateChanged, "onStateChanged");
    }


    public void open() {
        final Player pa = Bukkit.getPlayer(aId);
        final Player pb = Bukkit.getPlayer(bId);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                TradeMenu m = new TradeMenu(plugin, cfg, pa, pb);
                this.menu = m; // store reference for lock/refresh/close
                m.openForBoth(); // menu will only open for locally-present players

                if (pa != null && pa.isOnline()) safePlay(pa, cfg.openSound, 1f, 1f);
                if (pb != null && pb.isOnline()) safePlay(pb, cfg.openSound, 1f, 1f);
            } catch (Throwable t) {
                plugin.getLogger().warning("[TRADE] open() failed: " + t.getMessage());
            }
        });
    }

    public void closeLocalViewers() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player a = Bukkit.getPlayer(aId);
            Player b = Bukkit.getPlayer(bId);
            if (a != null) a.closeInventory();
            if (b != null) b.closeInventory();
        });
    }
    public boolean tryClose() {
        if (closed) return false;
        synchronized (this) {
            if (closed) return false;
            closed = true;
            return true;
        }
    }
    public boolean isClosed() { return closed; }
    public void forceClearAndCloseLocal() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (menu != null) {
                    menu.clearAllOfferSlotsSafely();
                }
            } catch (Throwable ignored) {}
            closeLocalViewers();
            this.menu = null;

        });
    }


    public void clickConfirm(boolean forA) {
        log("[TRADE] clickConfirm side=" + (forA ? "A" : "B") + " before A=" + aReady + " B=" + bReady);

        if (uiLocked || completed) return;

        Player p = Bukkit.getPlayer(forA ? aId : bId);
        if (p != null && cfg.requireEmptyCursorOnConfirm && !isEmpty(p.getItemOnCursor())) {
            p.sendMessage("§cEmpty your cursor before confirming.");
            return;
        }

        if (forA) {
            aReady = !aReady;
            safePlay(p, aReady ? cfg.confirmSound : cfg.clickSound, 1f, 1f);
        } else {
            bReady = !bReady;
            safePlay(p, bReady ? cfg.confirmSound : cfg.clickSound, 1f, 1f);
        }

        bumpVersionAndFire();

        if (aReady && bReady) {
            lockUiNow();

            beginClosing();

            Bukkit.getScheduler().runTask(plugin, () -> {
                closeLocalViewers();
                onFinish.accept(this);
            });
        }

    }

    public void clickCancel() {
        if (completed || uiLocked || isClosingOrClosed()) return;
        log("[TRADE] clickCancel");
        closeLocalViewers();
        onCancel.accept(this, "cancelled");
    }
    public void playerToggleConfirm(UUID who) {
        if (who == null) return;
        if (isA(who)) {
            clickConfirm(true);
        } else if (isB(who)) {
            clickConfirm(false);
        } else {

        }
    }

    public void playerSetOfferSlot(UUID who, int slot, ItemStack item) {
        if (who == null) return;
        if (isA(who)) {
            setOfferSlot(true, slot, item);
        } else if (isB(who)) {
            setOfferSlot(false, slot, item);
        } else {

        }
    }


    public void setOfferSlot(boolean forA, int slot, ItemStack item) {
        if (slot < 0 || slot >= 18) return;
        if (uiLocked || completed) return;
        // Optional: enforce empty cursor on edit like confirm (prevents weird client timing)
        if (cfg.requireEmptyCursorOnConfirm) {
            Player p = Bukkit.getPlayer(forA ? aId : bId);
            if (p != null && !isEmpty(p.getItemOnCursor())) {
                p.sendMessage("§cEmpty your cursor before editing the offer.");
                return;
            }
        }

        if (forA) {
            offerA[slot] = cloneOrNull(item);
            aReady = false;
        } else {
            offerB[slot] = cloneOrNull(item);
            bReady = false;
        }

        log("[TRADE] setOfferSlot side=" + (forA ? "A" : "B") + " slot=" + slot
                + " now Aready=" + aReady + " Bready=" + bReady);

        bumpVersionAndFire();
    }

    public void applyRemoteState(ItemStack[] newA, ItemStack[] newB, boolean readyA, boolean readyB, long newVersion) {
        log("[TRADE] applyRemoteState NEW v=" + newVersion + " (old " + version
                + "), Aready=" + readyA + " Bready=" + readyB);

        if (newVersion <= version) return;        // stale
        if (uiLocked || completed) return;
        if (isClosingOrClosed()) return;
        copy18(newA, offerA);
        copy18(newB, offerB);
        this.aReady = readyA;
        this.bReady = readyB;
        this.version = newVersion;
    }


    public void lockUiNow() {
        if (uiLocked) return;
        uiLocked = true;

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (menu != null) {
                    menu.replaceOffersWithPlaceholdersSafely(); // no-op if menu not open
                }
                // Always clear cursors, even if menu was closed already
                try {
                    Player a = Bukkit.getPlayer(aId);
                    Player b = Bukkit.getPlayer(bId);
                    if (a != null) a.setItemOnCursor(null);
                    if (b != null) b.setItemOnCursor(null);
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        });
    }

    public void markCompleted() { completed = true; }

    public boolean isUiLocked()  { return uiLocked; }
    public boolean isCompleted() { return completed; }

    private boolean dbg() { return cfg != null && cfg.debugDeep; }
    private void log(String s) { if (dbg()) plugin.getLogger().info(s); }

    private void bumpVersionAndFire() {
        version++;
        try { onStateChanged.accept(this, version); } catch (Throwable ignored) {}
    }

    public UUID getSid() { return sid; }
    public UUID getId()  { return sid; }
    public UUID getTradeId() { return sid; }

    public UUID getAId()   { return aId; }
    public UUID getBId()   { return bId; }
    public String getAName() { return aName; }
    public String getBName() { return bName; }

    public boolean isReadyA() { return aReady; }
    public boolean isReadyB() { return bReady; }
    public boolean isConfirmed(boolean forA) { return forA ? aReady : bReady; }
    public OreoEssentials getPlugin() {
        return plugin;
    }

    public TradeConfig getConfig() {
        return cfg;
    }

    public long getVersion() { return version; }

    public ItemStack[] viewOfferA() { return offerA.clone(); }
    public ItemStack[] viewOfferB() { return offerB.clone(); }

    public ItemStack[] getOfferACompact() { return compact(offerA); }
    public ItemStack[] getOfferBCompact() { return compact(offerB); }


    public void setOfferItemA(int index, ItemStack item) {
        if (index < 0 || index >= 18) return;
        if (uiLocked || completed) return;
        offerA[index] = cloneOrNull(item);
        aReady = false;
        bumpVersionAndFire();
    }

    public void setOfferItemB(int index, ItemStack item) {
        if (index < 0 || index >= 18) return;
        if (uiLocked || completed) return;
        offerB[index] = cloneOrNull(item);
        bReady = false;
        bumpVersionAndFire();
    }

    public void setReadyA(boolean ready) {
        if (uiLocked || completed) return;
        if (this.aReady != ready) {
            this.aReady = ready;
            bumpVersionAndFire();
        }
    }

    public void setReadyB(boolean ready) {
        if (uiLocked || completed) return;
        if (this.bReady != ready) {
            this.bReady = ready;
            bumpVersionAndFire();
        }
    }

    public void clearOfferA() {
        Arrays.fill(offerA, null);
        aReady = false;
        bumpVersionAndFire();
    }

    public void clearOfferB() {
        Arrays.fill(offerB, null);
        bReady = false;
        bumpVersionAndFire();
    }


    private static ItemStack[] compact(ItemStack[] src) {
        if (src == null) return new ItemStack[0];
        return Arrays.stream(src)
                .filter(i -> i != null && !i.getType().isAir() && i.getAmount() > 0)
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new);
    }

    private static void copy18(ItemStack[] from, ItemStack[] to) {
        if (to == null || to.length != 18) return;
        if (from == null) {
            Arrays.fill(to, null);
            return;
        }
        for (int i = 0; i < 18; i++) {
            to[i] = (i < from.length && from[i] != null) ? from[i].clone() : null;
        }
    }

    private static ItemStack cloneOrNull(ItemStack it) {
        return (it == null || it.getType().isAir() || it.getAmount() <= 0) ? null : it.clone();
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private static void safePlay(Player p, Sound s, float v, float pch) {
        if (p != null && s != null) {
            try { p.playSound(p.getLocation(), s, v, pch); } catch (Throwable ignored) {}
        }
    }
}
