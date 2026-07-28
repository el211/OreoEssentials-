package fr.elias.oreoEssentials.modules.mail.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.mail.MailService;
import fr.elias.oreoEssentials.modules.mail.model.MailMessage;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SmartInvs-based mailbox GUI.
 *
 * Layout (6 rows):
 * <pre>
 *   Row 0: border
 *   Rows 1–4, cols 1–7: mail items (28 per page)
 *   Row 5: border + navigation + delete-all + close
 * </pre>
 */
public final class MailboxMenu implements InventoryProvider {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final int ITEMS_PER_PAGE = 28;

    private final OreoEssentials plugin;
    private final MailService     service;

    private MailboxMenu(OreoEssentials plugin, MailService service) {
        this.plugin  = plugin;
        this.service = service;
    }

    public static SmartInventory getInventory(OreoEssentials plugin, MailService service) {
        return SmartInventory.builder()
                .id("oe_mailbox")
                .provider(new MailboxMenu(plugin, service))
                .manager(plugin.getInvManager())
                .size(6, 9)
                .title(c("&6&lMailbox"))
                .build();
    }

    // ── InventoryProvider ─────────────────────────────────────────────────────

    @Override
    public void init(Player player, InventoryContents contents) {
        Material borderMat = Material.GRAY_STAINED_GLASS_PANE;
        contents.fillBorders(ClickableItem.empty(glass(borderMat)));

        Pagination pagination = contents.pagination();

        List<MailMessage> mails = service.getMailbox(player.getUniqueId()).join();

        if (mails.isEmpty()) {
            contents.set(2, 4, ClickableItem.empty(named(Material.BARRIER, "&7Your mailbox is empty.")));
        } else {
            ClickableItem[] items = mails.stream()
                    .map(m -> mailItem(player, m, contents))
                    .toArray(ClickableItem[]::new);
            pagination.setItems(items);
            pagination.setItemsPerPage(ITEMS_PER_PAGE);

            // Rows 1–4, cols 1–7 (28 inner slots).
            // Blacklist col 0 for rows 2–4 (after wrapping from end of previous row)
            // and col 8 for rows 1–4 (right border).
            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            for (int row = 1; row <= 4; row++) {
                it.blacklist(row, 8);
            }
            for (int row = 2; row <= 4; row++) {
                it.blacklist(row, 0);
            }
            pagination.addToIterator(it);
        }

        renderNav(player, contents, pagination);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // No live-refresh needed for the mailbox.
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void renderNav(Player player, InventoryContents contents, Pagination pagination) {
        // Previous page
        if (!pagination.isFirst()) {
            contents.set(5, 3, ClickableItem.of(named(Material.ARROW, "&e&lPrevious Page"), e -> {
                click(player);
                getInventory(plugin, service).open(player, pagination.previous().getPage());
            }));
        } else {
            contents.set(5, 3, ClickableItem.empty(glass(Material.GRAY_STAINED_GLASS_PANE)));
        }

        // Page indicator
        int total = Math.max(1, (int) Math.ceil(
                service.getMailbox(player.getUniqueId()).join().size() / (double) ITEMS_PER_PAGE));
        contents.set(5, 4, ClickableItem.empty(
                named(Material.PAPER, "&7Page &f" + (pagination.getPage() + 1) + " &7/ &f" + total)));

        // Next page
        if (!pagination.isLast()) {
            contents.set(5, 5, ClickableItem.of(named(Material.ARROW, "&e&lNext Page"), e -> {
                click(player);
                getInventory(plugin, service).open(player, pagination.next().getPage());
            }));
        } else {
            contents.set(5, 5, ClickableItem.empty(glass(Material.GRAY_STAINED_GLASS_PANE)));
        }

        // Delete all
        contents.set(5, 1, ClickableItem.of(named(Material.TNT, "&c&lDelete All Mail"), e -> {
            if (e.isShiftClick()) {
                service.clearMail(player.getUniqueId()).thenRun(() ->
                        OreScheduler.run(plugin, () -> {
                            if (!player.isOnline()) return; // player disconnected before callback
                            player.sendMessage(c("&a[Mail] Your mailbox has been cleared."));
                            getInventory(plugin, service).open(player);
                        }));
            } else {
                player.sendMessage(c("&c[Mail] &7Shift-click the button to confirm clearing your mailbox."));
            }
        }));

        // Close
        contents.set(5, 7, ClickableItem.of(named(Material.BARRIER, "&c&lClose"), e -> {
            click(player);
            player.closeInventory();
        }));
    }

    // ── Mail item builder ─────────────────────────────────────────────────────

    private ClickableItem mailItem(Player player, MailMessage mail, InventoryContents contents) {
        ItemStack icon = buildIcon(mail);
        ItemMeta  meta = icon.getItemMeta();

        // Title: unread = gold/bold, read = gray
        String sender = mail.getSenderName() != null ? mail.getSenderName() : "Unknown";
        String title;
        if (!mail.isRead()) {
            title = c("&e&l✉ From: &f&l" + sender);
        } else {
            title = c("&7✉ From: &f" + sender);
        }
        meta.setDisplayName(title);

        // Lore
        List<String> lore = new ArrayList<>();
        lore.add(c("&8" + DATE_FMT.format(new Date(mail.getSentAt()))));
        lore.add("");

        if (mail.hasMessage()) {
            String raw = mail.getMessage();
            // Word-wrap at 45 chars
            while (raw.length() > 45) {
                int split = raw.lastIndexOf(' ', 45);
                if (split < 0) split = 45;
                lore.add(c("&f" + raw.substring(0, split)));
                raw = raw.substring(split).stripLeading();
            }
            if (!raw.isEmpty()) lore.add(c("&f" + raw));
            lore.add("");
        }

        if (mail.hasItem()) {
            if (mail.isItemClaimed()) {
                lore.add(c("&8[Item already claimed]"));
            } else {
                lore.add(c("&a▶ &fLeft-click to claim item"));
            }
        } else if (!mail.isRead()) {
            lore.add(c("&a▶ &fLeft-click to mark as read"));
        }
        lore.add(c("&c▶ &fShift-click to delete"));

        meta.setLore(lore);
        icon.setItemMeta(meta);

        return ClickableItem.of(icon, e -> {
            if (e.isShiftClick()) {
                service.deleteMail(player.getUniqueId(), mail.getId()).thenRun(() ->
                        OreScheduler.run(plugin, () -> {
                            if (!player.isOnline()) return; // player disconnected before callback
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1f);
                            getInventory(plugin, service).open(player);
                        }));
            } else if (mail.hasItem() && !mail.isItemClaimed()) {
                service.claimItem(player, mail.getId()).thenAccept(success -> {
                    if (!player.isOnline()) return; // player disconnected before callback
                    if (success) {
                        player.sendMessage(c("&a[Mail] Item claimed from &e" + sender + "&a!"));
                        if (mail.hasMessage()) {
                            player.sendMessage(c("&7Message: &f" + mail.getMessage()));
                        }
                        OreScheduler.run(plugin, () -> getInventory(plugin, service).open(player));
                    } else {
                        player.sendMessage(c("&c[Mail] Could not claim item (already claimed or invalid)."));
                    }
                });
            } else {
                // Mark read and show message in chat
                service.markRead(player.getUniqueId(), mail.getId());
                if (mail.hasMessage()) {
                    player.sendMessage(c("&6[Mail] &7From: &e" + sender));
                    player.sendMessage(c("&f" + mail.getMessage()));
                }
                click(player);
                // Refresh to update read state visually
                OreScheduler.run(plugin, () -> getInventory(plugin, service).open(player));
            }
        });
    }

    /** Resolves the icon for a mail entry. */
    private static ItemStack buildIcon(MailMessage mail) {
        if (mail.hasItem() && !mail.isItemClaimed()) {
            ItemStack item = MailService.deserializeItem(mail.getItemData());
            if (item != null) {
                item = item.clone();
                item.setAmount(1);
                return item;
            }
        }
        return new ItemStack(mail.hasItem() ? Material.CHEST : Material.PAPER);
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static ItemStack glass(Material mat) {
        ItemStack i = new ItemStack(mat);
        ItemMeta  m = i.getItemMeta();
        m.setDisplayName(" ");
        i.setItemMeta(m);
        return i;
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack i = new ItemStack(mat);
        ItemMeta  m = i.getItemMeta();
        m.setDisplayName(c(name));
        i.setItemMeta(m);
        return i;
    }

    private static void click(Player p) {
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f); }
        catch (Throwable ignored) {}
    }
}
