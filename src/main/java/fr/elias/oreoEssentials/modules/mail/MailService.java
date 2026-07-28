package fr.elias.oreoEssentials.modules.mail;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.mail.model.MailMessage;
import fr.elias.oreoEssentials.modules.mail.rabbitmq.MailDeliveryPacket;
import fr.elias.oreoEssentials.modules.mail.repository.MailRepository;
import fr.elias.oreoEssentials.modules.mail.repository.YamlMailRepository;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core business-logic layer for the mail system.
 *
 * Features:
 *  - Text mail and item-attachment mail to any player (online or offline)
 *  - Admin broadcasts: instant delivery to online players, lazy delivery on
 *    join for players who were offline
 *  - Cross-server real-time notification via {@link MailDeliveryPacket}
 */
public final class MailService {

    private final OreoEssentials plugin;
    private final MailRepository  repo;
    private final ConcurrentHashMap<String, Object> claimLocks = new ConcurrentHashMap<>();

    public MailService(OreoEssentials plugin) {
        this.plugin = plugin;
        this.repo   = new YamlMailRepository(plugin);
    }

    // ── Send mail ─────────────────────────────────────────────────────────────

    public CompletableFuture<Void> sendTextMail(UUID senderUuid, String senderName,
                                                 UUID recipientUuid, String message) {
        MailMessage msg = new MailMessage(
                UUID.randomUUID().toString(), recipientUuid, senderUuid,
                senderName, message, null,
                System.currentTimeMillis(), false, false, 0L);
        return repo.save(recipientUuid, msg)
                   .thenRun(() -> notifyDelivery(recipientUuid, senderName, false, false));
    }

    /**
     * @param message optional message alongside the item; may be null
     */
    public CompletableFuture<Void> sendItemMail(UUID senderUuid, String senderName,
                                                  UUID recipientUuid,
                                                  ItemStack item, String message) {
        String data = serializeItem(item);
        if (data == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("Failed to serialize item."));

        MailMessage msg = new MailMessage(
                UUID.randomUUID().toString(), recipientUuid, senderUuid,
                senderName, message, data,
                System.currentTimeMillis(), false, false, 0L);
        return repo.save(recipientUuid, msg)
                   .thenRun(() -> notifyDelivery(recipientUuid, senderName, true, false));
    }

    // ── Broadcast mail ────────────────────────────────────────────────────────

    /**
     * Sends a text broadcast. All currently online players receive it
     * immediately; offline players receive it the next time they join or
     * open their mailbox.
     */
    public void broadcastText(String senderName, String message) {
        MailMessage bcast = newBroadcast(senderName, message, null);
        repo.saveBroadcast(bcast).thenRun(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                deliverBroadcast(p.getUniqueId(), bcast);
            }
        });
    }

    /**
     * Sends an item broadcast. Same delivery rules as {@link #broadcastText}.
     *
     * @param message optional accompanying message; may be null
     */
    public void broadcastItem(String senderName, ItemStack item, String message) {
        String data = serializeItem(item);
        if (data == null) {
            plugin.getLogger().warning("[Mail] broadcastItem: failed to serialize item.");
            return;
        }
        MailMessage bcast = newBroadcast(senderName, message, data);
        repo.saveBroadcast(bcast).thenRun(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                deliverBroadcast(p.getUniqueId(), bcast);
            }
        });
    }

    /**
     * Called on player join to inject any broadcasts they haven't received yet.
     */
    public void checkPendingBroadcasts(UUID playerUuid) {
        repo.loadBroadcasts().thenAccept(list -> {
            for (MailMessage bcast : list) {
                deliverBroadcast(playerUuid, bcast);
            }
        });
    }

    // ── Mailbox operations ────────────────────────────────────────────────────

    /** Returns this player's mails, newest first, with expired ones stripped. */
    public CompletableFuture<List<MailMessage>> getMailbox(UUID uuid) {
        return repo.loadForPlayer(uuid).thenApply(list -> {
            list.removeIf(MailMessage::isExpired);
            list.sort(Comparator.comparingLong(MailMessage::getSentAt).reversed());
            return list;
        });
    }

    /** Async unread count. */
    public CompletableFuture<Long> unreadCountAsync(UUID uuid) {
        return repo.loadForPlayer(uuid).thenApply(list ->
                list.stream().filter(m -> !m.isRead()).count());
    }

    public CompletableFuture<Void> markRead(UUID uuid, String mailId) {
        return repo.loadForPlayer(uuid).thenCompose(list -> {
            for (MailMessage m : list) {
                if (m.getId().equals(mailId) && !m.isRead()) {
                    m.setRead(true);
                    return repo.update(uuid, m);
                }
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> markAllRead(UUID uuid) {
        return repo.loadForPlayer(uuid).thenCompose(list -> {
            boolean changed = false;
            for (MailMessage m : list) {
                if (!m.isRead()) { m.setRead(true); changed = true; }
            }
            return changed ? repo.update(uuid, null) : CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Claims the item from a mail and gives it to the player.
     *
     * @return true if the item was successfully claimed and given
     */
    public CompletableFuture<Boolean> claimItem(Player player, String mailId) {
        Object lock = claimLocks.computeIfAbsent(mailId, k -> new Object());
        return repo.loadForPlayer(player.getUniqueId()).thenCompose(list -> {
            synchronized (lock) {
                for (MailMessage m : list) {
                    if (!m.getId().equals(mailId)) continue;
                    if (!m.hasItem() || m.isItemClaimed()) return cf(false);
                    ItemStack item = deserializeItem(m.getItemData());
                    if (item == null || item.getType() == org.bukkit.Material.AIR || item.getAmount() <= 0) {
                        return cf(false);
                    }
                    m.setItemClaimed(true);
                    m.setRead(true);
                    return repo.update(player.getUniqueId(), m).thenApply(v -> {
                        claimLocks.remove(mailId);
                        OreScheduler.run(plugin, () -> giveOrDrop(player, item));
                        return true;
                    });
                }
                return cf(false);
            }
        });
    }

    public CompletableFuture<Void> deleteMail(UUID uuid, String mailId) {
        return repo.delete(uuid, mailId);
    }

    public CompletableFuture<Void> clearMail(UUID uuid) {
        return repo.clearAll(uuid);
    }

    // ── Item serialisation ────────────────────────────────────────────────────

    public static String serializeItem(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) { return null; }
    }

    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isBlank()) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        } catch (Exception e) { return null; }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void deliverBroadcast(UUID playerUuid, MailMessage bcast) {
        repo.hasReceivedBroadcast(playerUuid, bcast.getId()).thenAccept(received -> {
            if (received) return;
            MailMessage copy = new MailMessage(
                    bcast.getId() + "-" + playerUuid,
                    playerUuid, bcast.getSenderUuid(),
                    bcast.getSenderName(), bcast.getMessage(), bcast.getItemData(),
                    bcast.getSentAt(), false, false, bcast.getExpiresAt());
            repo.save(playerUuid, copy);
            repo.markBroadcastReceived(playerUuid, bcast.getId());
            notifyDelivery(playerUuid, bcast.getSenderName(), bcast.hasItem(), true);
        });
    }

    private void notifyDelivery(UUID recipientUuid, String senderName,
                                  boolean hasItem, boolean isBroadcast) {
        Player online = Bukkit.getPlayer(recipientUuid);
        if (online != null) {
            sendNotification(online, senderName, hasItem, isBroadcast);
            return;
        }
        var pm = plugin.getPacketManager();
        if (pm != null && pm.isInitialized()) {
            pm.sendPacket(new MailDeliveryPacket(recipientUuid, senderName, hasItem, isBroadcast));
        }
    }

    static void sendNotification(Player player, String senderName,
                                   boolean hasItem, boolean isBroadcast) {
        String msg = isBroadcast
                ? "§6[Mail] §eA broadcast mail from §a" + senderName
                  + " §ehas arrived! Use §f/mail §eto open your mailbox."
                : "§6[Mail] §e" + senderName + " §fsent you "
                  + (hasItem ? "an §6item§f!" : "a §fmessage§f!")
                  + " §7Use §f/mail §7to open your mailbox.";
        player.sendMessage(msg);
    }

    private static MailMessage newBroadcast(String senderName, String message, String itemData) {
        return new MailMessage(
                UUID.randomUUID().toString(), null, null,
                senderName, message, itemData,
                System.currentTimeMillis(), false, false, 0L);
    }

    static void giveOrDrop(Player player, ItemStack item) {
        if (player == null || !player.isOnline() || item == null) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private static <T> CompletableFuture<T> cf(T v) {
        return CompletableFuture.completedFuture(v);
    }
}
