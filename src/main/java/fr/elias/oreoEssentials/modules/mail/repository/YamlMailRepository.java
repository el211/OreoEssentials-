package fr.elias.oreoEssentials.modules.mail.repository;

import fr.elias.oreoEssentials.modules.mail.model.MailMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML-backed mail repository.
 * All data is kept in memory (ConcurrentHashMap) and flushed to mail.yml
 * asynchronously after mutations — so read operations never touch disk.
 *
 * Layout of mail.yml:
 * <pre>
 * mail:
 *   <uuid>:
 *     - { id, recipientUuid, senderUuid, senderName, message, itemData, sentAt, read, itemClaimed, expiresAt }
 * broadcasts:
 *   - { id, senderName, message, itemData, sentAt, expiresAt }
 * received_broadcasts:
 *   <uuid>: [broadcastId, ...]
 * </pre>
 */
public final class YamlMailRepository implements MailRepository {

    private final Plugin plugin;
    private final File   file;

    private final Map<UUID, List<MailMessage>> mailbox    = new ConcurrentHashMap<>();
    private final List<MailMessage>            broadcasts = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, Set<String>>       bcastAck   = new ConcurrentHashMap<>();

    public YamlMailRepository(Plugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "mail.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("[Mail] Cannot create mail.yml: " + e.getMessage()); }
        }
        loadAll(YamlConfiguration.loadConfiguration(file));
    }

    // ── MailRepository ────────────────────────────────────────────────────────

    @Override
    public synchronized CompletableFuture<Void> save(UUID recipientUuid, MailMessage msg) {
        mailbox.computeIfAbsent(recipientUuid, k -> new ArrayList<>()).add(msg);
        flushAsync();
        return done();
    }

    @Override
    public CompletableFuture<List<MailMessage>> loadForPlayer(UUID recipientUuid) {
        List<MailMessage> list = mailbox.getOrDefault(recipientUuid, Collections.emptyList());
        return CompletableFuture.completedFuture(new ArrayList<>(list));
    }

    @Override
    public CompletableFuture<Void> update(UUID recipientUuid, MailMessage msg) {
        // The msg object is mutated in-place inside the in-memory list; just flush.
        flushAsync();
        return done();
    }

    @Override
    public synchronized CompletableFuture<Void> delete(UUID recipientUuid, String mailId) {
        List<MailMessage> list = mailbox.get(recipientUuid);
        if (list != null) list.removeIf(m -> m.getId().equals(mailId));
        flushAsync();
        return done();
    }

    @Override
    public synchronized CompletableFuture<Void> clearAll(UUID recipientUuid) {
        mailbox.remove(recipientUuid);
        flushAsync();
        return done();
    }

    @Override
    public synchronized CompletableFuture<Void> saveBroadcast(MailMessage broadcast) {
        broadcasts.add(broadcast);
        flushAsync();
        return done();
    }

    @Override
    public CompletableFuture<List<MailMessage>> loadBroadcasts() {
        broadcasts.removeIf(MailMessage::isExpired);
        return CompletableFuture.completedFuture(new ArrayList<>(broadcasts));
    }

    @Override
    public CompletableFuture<Boolean> hasReceivedBroadcast(UUID playerUuid, String broadcastId) {
        boolean has = bcastAck.getOrDefault(playerUuid, Collections.emptySet()).contains(broadcastId);
        return CompletableFuture.completedFuture(has);
    }

    @Override
    public synchronized CompletableFuture<Void> markBroadcastReceived(UUID playerUuid, String broadcastId) {
        bcastAck.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(broadcastId);
        flushAsync();
        return done();
    }

    // ── Load / flush ──────────────────────────────────────────────────────────

    private void loadAll(FileConfiguration cfg) {
        // Individual mails
        var mailSection = cfg.getConfigurationSection("mail");
        if (mailSection != null) {
            for (String uuidStr : mailSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<Map<?,?>> raw = cfg.getMapList("mail." + uuidStr);
                    List<MailMessage> msgs = new ArrayList<>();
                    for (Map<?,?> m : raw) {
                        MailMessage msg = mapToMsg(m, uuid);
                        if (msg != null && !msg.isExpired()) msgs.add(msg);
                    }
                    if (!msgs.isEmpty()) mailbox.put(uuid, msgs);
                } catch (Exception ignored) {}
            }
        }
        // Broadcasts
        for (Map<?,?> m : cfg.getMapList("broadcasts")) {
            MailMessage msg = mapToMsg(m, null);
            if (msg != null && !msg.isExpired()) broadcasts.add(msg);
        }
        // Broadcast acks
        var ackSection = cfg.getConfigurationSection("received_broadcasts");
        if (ackSection != null) {
            for (String uuidStr : ackSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<?> ids = cfg.getList("received_broadcasts." + uuidStr);
                    if (ids != null) {
                        Set<String> set = new HashSet<>();
                        for (Object o : ids) set.add(o.toString());
                        bcastAck.put(uuid, set);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void flushAsync() {
        plugin.getServer().getAsyncScheduler().runNow(plugin, ctx -> flush());
    }

    private synchronized void flush() {
        FileConfiguration cfg = new YamlConfiguration();
        // Mail per player
        for (Map.Entry<UUID, List<MailMessage>> e : mailbox.entrySet()) {
            cfg.set("mail." + e.getKey(), toMapList(e.getValue()));
        }
        // Broadcasts — take a snapshot to avoid ConcurrentModificationException during iteration
        List<MailMessage> broadcastSnapshot;
        synchronized (broadcasts) {
            broadcastSnapshot = new ArrayList<>(broadcasts);
        }
        cfg.set("broadcasts", toMapList(broadcastSnapshot));
        // Acks
        for (Map.Entry<UUID, Set<String>> e : bcastAck.entrySet()) {
            cfg.set("received_broadcasts." + e.getKey(), new ArrayList<>(e.getValue()));
        }
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            cfg.save(tmp);
            // Atomic rename: delete old file first (required on Windows), then rename
            if (file.exists()) file.delete();
            tmp.renameTo(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("[Mail] Failed to save mail.yml: " + ex.getMessage());
        }
    }

    // ── Serialisation helpers ─────────────────────────────────────────────────

    private static MailMessage mapToMsg(Map<?,?> m, UUID defaultRecipient) {
        try {
            String id       = str(m, "id");
            if (id == null || id.isBlank()) return null;
            String recStr   = str(m, "recipientUuid");
            UUID   rec      = (!recStr.isEmpty()) ? UUID.fromString(recStr) : defaultRecipient;
            String sndStr   = str(m, "senderUuid");
            UUID   snd      = (!sndStr.isEmpty()) ? UUID.fromString(sndStr) : null;
            return new MailMessage(
                    id, rec, snd,
                    str(m, "senderName"),
                    nullIfBlank(str(m, "message")),
                    nullIfBlank(str(m, "itemData")),
                    longVal(m, "sentAt"),
                    boolVal(m, "read"),
                    boolVal(m, "itemClaimed"),
                    longVal(m, "expiresAt"));
        } catch (Exception e) { return null; }
    }

    private static List<Map<String,Object>> toMapList(List<MailMessage> msgs) {
        List<Map<String,Object>> list = new ArrayList<>();
        for (MailMessage m : msgs) {
            Map<String,Object> e = new LinkedHashMap<>();
            e.put("id",            m.getId());
            e.put("recipientUuid", m.getRecipientUuid() != null ? m.getRecipientUuid().toString() : "");
            e.put("senderUuid",    m.getSenderUuid()    != null ? m.getSenderUuid().toString()    : "");
            e.put("senderName",    nvl(m.getSenderName()));
            e.put("message",       nvl(m.getMessage()));
            e.put("itemData",      nvl(m.getItemData()));
            e.put("sentAt",        m.getSentAt());
            e.put("read",          m.isRead());
            e.put("itemClaimed",   m.isItemClaimed());
            e.put("expiresAt",     m.getExpiresAt());
            list.add(e);
        }
        return list;
    }

    private static String str(Map<?,?> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : "";
    }
    private static String nvl(String s)  { return s != null ? s : ""; }
    private static String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static long longVal(Map<?,?> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }
    private static boolean boolVal(Map<?,?> m, String k) {
        Object v = m.get(k);
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(v != null ? v.toString() : "");
    }
    private static CompletableFuture<Void> done() { return CompletableFuture.completedFuture(null); }
}
