package fr.elias.oreoEssentials.modules.mail;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MailService {

    public record MailMessage(
            String sender,
            String senderUuid,
            String message,
            long   timestamp,
            boolean read
    ) {}

    private final Plugin             plugin;
    private final File               file;
    private       FileConfiguration  cfg;
    private final Map<UUID, List<MailMessage>> mailbox = new ConcurrentHashMap<>();

    public MailService(Plugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "mail.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("[Mail] Cannot create mail.yml: " + e.getMessage()); }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void sendMail(UUID recipient, String senderName, UUID senderUuid, String message) {
        mailbox.computeIfAbsent(recipient, k -> new ArrayList<>())
               .add(new MailMessage(senderName, senderUuid.toString(), message,
                                    System.currentTimeMillis(), false));
        saveForUuid(recipient);
    }

    public List<MailMessage> getMail(UUID uuid) {
        return Collections.unmodifiableList(mailbox.getOrDefault(uuid, Collections.emptyList()));
    }

    public int unreadCount(UUID uuid) {
        return (int) mailbox.getOrDefault(uuid, Collections.emptyList())
                .stream().filter(m -> !m.read()).count();
    }

    public void markAllRead(UUID uuid) {
        List<MailMessage> msgs = mailbox.get(uuid);
        if (msgs == null) return;
        for (int i = 0; i < msgs.size(); i++) {
            MailMessage m = msgs.get(i);
            if (!m.read()) msgs.set(i, new MailMessage(m.sender(), m.senderUuid(), m.message(), m.timestamp(), true));
        }
        saveForUuid(uuid);
    }

    public boolean deleteMail(UUID uuid, int index) {
        List<MailMessage> msgs = mailbox.get(uuid);
        if (msgs == null || index < 0 || index >= msgs.size()) return false;
        msgs.remove(index);
        saveForUuid(uuid);
        return true;
    }

    public void clearMail(UUID uuid) {
        mailbox.remove(uuid);
        cfg.set("mail." + uuid, null);
        saveFile();
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private void loadAll() {
        var root = cfg.getConfigurationSection("mail");
        if (root == null) return;
        for (String uuidStr : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<Map<?,?>> raw = cfg.getMapList("mail." + uuidStr);
                List<MailMessage> msgs = new ArrayList<>();
                for (Map<?,?> m : raw) {
                    msgs.add(new MailMessage(str(m,"sender"), str(m,"senderUuid"),
                            str(m,"message"), longVal(m,"timestamp"), boolVal(m,"read")));
                }
                if (!msgs.isEmpty()) mailbox.put(uuid, msgs);
            } catch (Exception ignored) {}
        }
    }

    private void saveForUuid(UUID uuid) {
        List<MailMessage> msgs = mailbox.getOrDefault(uuid, Collections.emptyList());
        if (msgs.isEmpty()) {
            cfg.set("mail." + uuid, null);
        } else {
            List<Map<String,Object>> list = new ArrayList<>();
            for (MailMessage m : msgs) {
                Map<String,Object> e = new LinkedHashMap<>();
                e.put("sender",     m.sender());
                e.put("senderUuid", m.senderUuid());
                e.put("message",    m.message());
                e.put("timestamp",  m.timestamp());
                e.put("read",       m.read());
                list.add(e);
            }
            cfg.set("mail." + uuid, list);
        }
        saveFile();
    }

    private void saveFile() {
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().severe("[Mail] Failed to save mail.yml: " + e.getMessage()); }
    }

    // -----------------------------------------------------------------------
    // Map helpers
    // -----------------------------------------------------------------------

    private static String str(Map<?,?> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : "";
    }
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
}
