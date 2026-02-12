package fr.elias.oreoEssentials.modules.trade.ui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.trade.config.TradeConfig;
import fr.elias.oreoEssentials.modules.trade.service.TradeService;
import fr.elias.oreoEssentials.modules.trade.TradeSession;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;


public final class TradeMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final TradeConfig config;
    private final TradeService service;
    private final TradeMenuRegistry reg;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Player a;
    private final Player b;

    private final SmartInventory invA;
    private final SmartInventory invB;

    private static final int[] A_AREA_SLOTS = slotsOfRect(2, 1, 3, 3); // left: cols 1,2,3
    private static final int[] B_AREA_SLOTS = slotsOfRect(2, 5, 3, 3); // right: cols 5,6,7

    private final ItemStack[] lastSnapshotA = new ItemStack[9];
    private final ItemStack[] lastSnapshotB = new ItemStack[9];

    public TradeMenu(OreoEssentials plugin, TradeConfig config, Player aLocal, Player bLocal) {
        this.plugin = plugin;
        this.config = config;
        this.service = plugin.getTradeService();
        this.reg = (this.service != null) ? this.service.getMenuRegistry() : null;
        this.a = aLocal;
        this.b = bLocal;

        String rawTitle = config.guiTitle
                .replace("<you>", (a != null ? a.getName() : "You"))
                .replace("<them>", (b != null ? b.getName() : "Them"));
        String title = legacy(rawTitle);

        this.invA = SmartInventory.builder()
                .provider(this)
                .size(6, 9)
                .title(title)
                .id("trade:" + (a != null ? a.getUniqueId() : UUID.randomUUID())
                        + ":" + (b != null ? b.getUniqueId() : UUID.randomUUID()))
                .manager(plugin.getInvManager())
                .closeable(true)
                .build();

        this.invB = SmartInventory.builder()
                .provider(this)
                .size(6, 9)
                .title(title)
                .id("trade:" + (b != null ? b.getUniqueId() : UUID.randomUUID())
                        + ":" + (a != null ? a.getUniqueId() : UUID.randomUUID()))
                .manager(plugin.getInvManager())
                .closeable(true)
                .build();
    }

    private String legacy(String text) {
        if (text == null) return "";
        try {
            return LEGACY.serialize(MM.deserialize(text));
        } catch (Throwable t) {
            return text;
        }
    }

    public OreoEssentials getPlugin() {
        return plugin;
    }

    public void openForBoth() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (a != null) {
                    invA.open(a);
                    registerViewer(a);
                }
                if (b != null && (a == null || !b.getUniqueId().equals(a.getUniqueId()))) {
                    invB.open(b);
                    registerViewer(b);
                }

                if (a != null) {
                    Lang.send(a, "trade.opening",
                            "<green>Opening trade GUI with <white>%partner%</white>…</green>",
                            Map.of("partner", b != null ? b.getName() : "player"));
                }
                if (b != null) {
                    Lang.send(b, "trade.opening",
                            "<green>Opening trade GUI with <white>%partner%</white>…</green>",
                            Map.of("partner", a != null ? a.getName() : "player"));
                }
            } catch (Throwable t) {
                sendFail(a, t);
                sendFail(b, t);
                plugin.getLogger().warning("[TRADE] openForBoth failed: " + t.getMessage());
            }
        });
    }

    private void registerViewer(Player p) {
        try {
            if (reg != null && p != null) reg.register(p.getUniqueId(), this);
        } catch (Throwable ignored) {}
    }

    public void onClose(Player p) {
        try {
            if (reg != null && p != null) reg.unregister(p.getUniqueId());
        } catch (Throwable ignored) {}
    }


    @Override
    public void init(Player viewer, InventoryContents contents) {
        final boolean isAViewer = (a != null && viewer.getUniqueId().equals(a.getUniqueId()));
        final int[] editable = isAViewer ? A_AREA_SLOTS : B_AREA_SLOTS;
        markEditable(contents, editable);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ClickableItem borderItem = ClickableItem.empty(border);

        for (int c = 0; c < 9; c++) contents.set(0, c, borderItem);
        for (int c = 0; c < 9; c++) {
            if (c == 2 || c == 4 || c == 6) continue;
            contents.set(5, c, borderItem);
        }
        for (int r = 1; r < 5; r++) {
            contents.set(r, 0, borderItem);
            contents.set(r, 8, borderItem);
        }

        if (a != null) {
            String aName = Lang.get("trade.player-name", "<yellow>%name%</yellow>")
                    .replace("%name%", a.getName());
            contents.set(1, 1, ClickableItem.empty(playerHead(a.getUniqueId(), legacy(aName))));
        }
        if (b != null) {
            String bName = Lang.get("trade.player-name", "<yellow>%name%</yellow>")
                    .replace("%name%", b.getName());
            contents.set(1, 7, ClickableItem.empty(playerHead(b.getUniqueId(), legacy(bName))));
        }

        ItemStack divider = new ItemStack(config.dividerMaterial);
        ItemMeta dMeta = divider.getItemMeta();
        if (dMeta != null) {
            dMeta.setDisplayName(legacy(config.dividerName));
            divider.setItemMeta(dMeta);
        }

        ClickableItem dividerItem = ClickableItem.empty(divider);
        contents.set(2, 4, dividerItem);
        contents.set(3, 4, dividerItem);
        contents.set(4, 4, dividerItem);

        contents.set(1, 4, ClickableItem.empty(new ItemStack(Material.BOOK)));

        contents.set(5, 2, confirmButton(viewer, false));  // my confirm
        contents.set(5, 4, cancelButton(viewer));          // cancel
        contents.set(5, 6, partnerReadyIndicator(false));  // partner status

        Arrays.fill(lastSnapshotA, null);
        Arrays.fill(lastSnapshotB, null);

        registerViewer(viewer);
    }

    @Override
    public void update(Player viewer, InventoryContents contents) {
        if (service == null) return;

        final boolean isAViewer = (a != null && viewer.getUniqueId().equals(a.getUniqueId()));

        UUID sid = service.getTradeIdByPlayer(viewer.getUniqueId());
        if (sid == null) return;
        TradeSession sess = service.getSession(sid);
        if (sess == null) return;

        Inventory top = viewer.getOpenInventory().getTopInventory();

        if (isAViewer) mirrorEdits(viewer, top, A_AREA_SLOTS, lastSnapshotA);
        else mirrorEdits(viewer, top, B_AREA_SLOTS, lastSnapshotB);

        ItemStack[] other = isAViewer ? sess.viewOfferB() : sess.viewOfferA();
        int[] otherSlots = isAViewer ? B_AREA_SLOTS : A_AREA_SLOTS;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < otherSlots.length; i++) {
                int slot = otherSlots[i];
                ItemStack want = (other != null && i < other.length) ? cloneOrNull(other[i]) : null;
                ItemStack have = safeGet(top, slot);
                if (!isSame(have, want)) top.setItem(slot, want);
            }

            boolean myReady = isAViewer ? sess.isReadyA() : sess.isReadyB();
            boolean othReady = isAViewer ? sess.isReadyB() : sess.isReadyA();

            contents.set(5, 2, confirmButton(viewer, myReady));
            contents.set(5, 4, cancelButton(viewer));
            contents.set(5, 6, partnerReadyIndicator(othReady));
        });
    }

    public void refreshFromSession() {
        if (service == null) return;

        if (a != null && a.isOnline()
                && a.getOpenInventory() != null
                && a.getOpenInventory().getTopInventory() != null) {
            refreshFor(a, /* viewerIsA = */ true);
        }
        if (b != null && b.isOnline()
                && b.getOpenInventory() != null
                && b.getOpenInventory().getTopInventory() != null) {
            refreshFor(b, /* viewerIsA = */ false);
        }
    }

    private void refreshFor(Player viewer, boolean viewerIsA) {
        try {
            UUID sid = service.getTradeIdByPlayer(viewer.getUniqueId());
            if (sid == null) return;
            TradeSession sess = service.getSession(sid);
            if (sess == null) return;

            Inventory top = viewer.getOpenInventory().getTopInventory();

            // Paint opponent area from session
            ItemStack[] other = viewerIsA ? sess.viewOfferB() : sess.viewOfferA();
            int[] otherSlots = viewerIsA ? B_AREA_SLOTS : A_AREA_SLOTS;

            for (int i = 0; i < otherSlots.length; i++) {
                int slot = otherSlots[i];
                ItemStack want = (other != null && i < other.length) ? cloneOrNull(other[i]) : null;
                ItemStack have = safeGet(top, slot);
                if (!isSame(have, want)) top.setItem(slot, want);
            }

            boolean myReady = viewerIsA ? sess.isReadyA() : sess.isReadyB();
            boolean othReady = viewerIsA ? sess.isReadyB() : sess.isReadyA();

            // Buttons (replace items directly)
            top.setItem(5 * 9 + 2, buildConfirmIcon(myReady));           // row 5, col 2
            top.setItem(5 * 9 + 6, buildPartnerIndicatorIcon(othReady)); // row 5, col 6
        } catch (Throwable ignored) {}
    }


    private static void markEditable(InventoryContents contents, int[] slots) {
        for (int raw : slots) {
            int row = raw / 9;
            int col = raw % 9;
            try {
                contents.setEditable(SlotPos.of(row, col), true);
            } catch (Throwable ignored) {}
        }
    }

    private static int[] slotsOfRect(int startRow, int startCol, int height, int width) {
        int[] out = new int[height * width];
        int k = 0;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                out[k++] = (startRow + r) * 9 + (startCol + c);
            }
        }
        return out;
    }

    private void mirrorEdits(Player viewer, Inventory top, int[] slots, ItemStack[] last) {
        for (int i = 0; i < slots.length; i++) {
            int invSlot = slots[i];
            ItemStack now = cloneOrNull(safeGet(top, invSlot));
            ItemStack was = last[i];
            if (!isSame(now, was)) {
                last[i] = cloneOrNull(now);

                UUID sid = service.getTradeIdByPlayer(viewer.getUniqueId());
                if (sid != null) {
                    TradeSession sess = service.getSession(sid);
                    if (sess != null) {
                        sess.playerSetOfferSlot(viewer.getUniqueId(), i, now);
                    }
                }

                final int idx = i;
                final ItemStack itemCopy = cloneOrNull(now);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean viewerIsA = (a != null && viewer.getUniqueId().equals(a.getUniqueId()));
                    Player opp = viewerIsA ? b : a;

                    if (opp != null && opp.isOnline()
                            && opp.getOpenInventory() != null
                            && opp.getOpenInventory().getTopInventory() != null) {
                        int[] oppSlots = viewerIsA ? A_AREA_SLOTS : B_AREA_SLOTS;
                        Inventory oppTop = opp.getOpenInventory().getTopInventory();
                        oppTop.setItem(oppSlots[idx], itemCopy);
                    }
                });
            }
        }
    }

    private ItemStack playerHead(UUID uuid, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            meta.setDisplayName(displayName);
            head.setItemMeta(meta);
        } catch (Throwable ignored) {}
        return head;
    }

    private static ItemStack safeGet(Inventory inv, int slot) {
        try {
            return inv.getItem(slot);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack cloneOrNull(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || it.getAmount() <= 0) return null;
        try {
            return it.clone();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isSame(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        try {
            return a.isSimilar(b) && a.getAmount() == b.getAmount();
        } catch (Throwable t) {
            return a.getType() == b.getType() && a.getAmount() == b.getAmount();
        }
    }

    private static void sendFail(Player p, Throwable t) {
        if (p != null) {
            Lang.send(p, "trade.failed-to-open",
                    "<red>Failed to open trade: <yellow>%error%</yellow></red>",
                    Map.of("error", t != null ? t.getMessage() : "unknown"));
        }
    }



    private ClickableItem confirmButton(Player viewer, boolean ready) {
        ItemStack it = buildConfirmIcon(ready);
        return ClickableItem.of(it, e -> {
            try {
                UUID sid = service.getTradeIdByPlayer(viewer.getUniqueId());
                if (sid != null) {
                    TradeSession sess = service.getSession(sid);
                    if (sess != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> sess.playerToggleConfirm(viewer.getUniqueId()));
                    }
                }
            } catch (Throwable ignored) {}
            try {
                viewer.playSound(viewer.getLocation(), config.confirmSound, 1, 1);
            } catch (Throwable ignored) {}
        });
    }

    private ItemStack buildConfirmIcon(boolean ready) {
        ItemStack it = new ItemStack(ready ? config.confirmedMaterial : config.confirmMaterial);
        var meta = it.getItemMeta();
        if (meta != null) {
            String raw = ready ? config.confirmedText : config.confirmText;
            meta.setDisplayName(legacy(raw));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ClickableItem cancelButton(Player viewer) {
        ItemStack it = new ItemStack(config.cancelMaterial);
        var meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(legacy(config.cancelText));
            it.setItemMeta(meta);
        }

        return ClickableItem.of(it, e -> {
            try {
                service.requestCancel(viewer.getUniqueId());
            } catch (Throwable ignored) {}
            try {
                viewer.playSound(viewer.getLocation(), config.cancelSound, 1, 1);
            } catch (Throwable ignored) {}
        });
    }

    private ClickableItem partnerReadyIndicator(boolean partnerReady) {
        ItemStack it = buildPartnerIndicatorIcon(partnerReady);
        return ClickableItem.empty(it);
    }

    private ItemStack buildPartnerIndicatorIcon(boolean partnerReady) {
        ItemStack it = new ItemStack(partnerReady ? Material.LIME_CONCRETE : Material.RED_DYE);
        var meta = it.getItemMeta();
        if (meta != null) {
            String text = Lang.get(
                    partnerReady ? "trade.partner-ready" : "trade.partner-not-ready",
                    partnerReady ? "<green>Partner Ready</green>" : "<red>Partner Not Ready</red>"
            );
            meta.setDisplayName(legacy(text));
            it.setItemMeta(meta);
        }
        return it;
    }

    public void replaceOffersWithPlaceholdersSafely() {
        try {
            // Build a simple "Locked" pane
            ItemStack ph = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta im = ph.getItemMeta();
            if (im != null) {
                String text = Lang.get("trade.locked", "<gray>Locked</gray>");
                im.setDisplayName(legacy(text));
                ph.setItemMeta(im);
            }

            int[] offerSlotsA = getOfferSlotsAOrEmpty();
            int[] offerSlotsB = getOfferSlotsBOrEmpty();

            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                try {
                    for (int s : offerSlotsA) setItemSafelyA(s, ph);
                    for (int s : offerSlotsB) setItemSafelyB(s, ph);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    public void clearAllOfferSlotsSafely() {
        try {
            int[] offerSlotsA = getOfferSlotsAOrEmpty();
            int[] offerSlotsB = getOfferSlotsBOrEmpty();

            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                try {
                    for (int s : offerSlotsA) setItemSafelyA(s, null);
                    for (int s : offerSlotsB) setItemSafelyB(s, null);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    /* -------------------------- Helpers (stub-safe) -------------------------- */

    private int[] getOfferSlotsAOrEmpty() {
        return A_AREA_SLOTS;
    }

    private int[] getOfferSlotsBOrEmpty() {
        return B_AREA_SLOTS;
    }

    private void setItemSafelyA(int slot, ItemStack stack) {
        try {
            // TODO: plug into your existing painter for side A
        } catch (Throwable ignored) {}
    }

    private void setItemSafelyB(int slot, ItemStack stack) {
        try {
            // TODO: plug into your existing painter for side B
        } catch (Throwable ignored) {}
    }


    public static TradeMenu createForSession(TradeSession session) {
        if (session == null) return null;
        OreoEssentials pl = session.getPlugin();
        TradeConfig cfg = session.getConfig();
        Player aLocal = Bukkit.getPlayer(session.getAId());
        Player bLocal = Bukkit.getPlayer(session.getBId());
        return new TradeMenu(pl, cfg, aLocal, bLocal);
    }

    public void openFor(UUID viewerId) {
        if (viewerId == null) return;
        if (a != null && a.isOnline() && a.getUniqueId().equals(viewerId)) {
            invA.open(a);
            registerViewer(a);
            return;
        }
        if (b != null && b.isOnline() && b.getUniqueId().equals(viewerId)) {
            invB.open(b);
            registerViewer(b);
        }
    }

    public boolean isOpenFor(UUID viewerId) {
        Player p = (viewerId != null ? Bukkit.getPlayer(viewerId) : null);
        if (p == null || !p.isOnline() || p.getOpenInventory() == null) return false;
        try {
            String title = p.getOpenInventory().getTitle();
            return title != null && title.contains("Trade: ");
        } catch (Throwable ignored) {
            return false;
        }
    }
}