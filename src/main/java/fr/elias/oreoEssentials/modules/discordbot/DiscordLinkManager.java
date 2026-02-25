package fr.elias.oreoEssentials.modules.discordbot;



import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;

public class DiscordLinkManager {

    private final Logger log;
    private final String backend;
    private final File   pendingDir;     // file backend only

    // MongoDB collections – null when backend = "file"
    private MongoCollection<Document> pendingCol;
    private MongoCollection<Document> confirmedCol;
    private MongoCollection<Document> linksCol;


    public DiscordLinkManager(File dataFolder, Logger log) {
        this.log      = log;
        this.backend  = "file";

        this.pendingDir = new File(dataFolder, "../../data/mc_links/pending");
        this.pendingDir.mkdirs();
        log.info("[Discord] Link backend: file (" + pendingDir.getAbsolutePath() + ")");
    }


    public DiscordLinkManager(MongoDatabase db, Logger log) {
        this.log        = log;
        this.backend    = "mongodb";
        this.pendingDir = null;

        this.pendingCol   = db.getCollection("discord_link_pending");
        this.confirmedCol = db.getCollection("discord_link_confirmed");
        this.linksCol     = db.getCollection("discord_links");

        log.info("[Discord] Link backend: mongodb");
    }


    public String confirmLink(Player player, String code) {
        code = code.trim().toUpperCase();
        return "mongodb".equals(backend)
                ? confirmLinkMongo(player, code)
                : confirmLinkFile(player, code);
    }

    public boolean isLinked(UUID uuid) {
        return "mongodb".equals(backend) ? isLinkedMongo(uuid) : isLinkedFile(uuid);
    }

    public String getLinkedDiscordId(UUID uuid) {
        return "mongodb".equals(backend)
                ? getLinkedDiscordIdMongo(uuid)
                : getLinkedDiscordIdFile(uuid);
    }


    private String confirmLinkFile(Player player, String code) {
        File pendingFile = new File(pendingDir, code + ".json");
        if (!pendingFile.exists()) {
            return "§cInvalid or expired code. Use /mc link in Discord to get a new one.";
        }

        try {
            String content = new String(Files.readAllBytes(pendingFile.toPath()));

            String expiresStr = extractJsonString(content, "expires_at");
            if (expiresStr != null && Instant.now().isAfter(Instant.parse(expiresStr))) {
                pendingFile.delete();
                return "§cThis code has expired. Use /mc link in Discord to get a fresh one.";
            }

            String discordId = extractJsonString(content, "discord_id");

            String confirmedJson = "{\n"
                    + "  \"discord_id\": \"" + discordId + "\",\n"
                    + "  \"uuid\": \"" + player.getUniqueId() + "\",\n"
                    + "  \"username\": \"" + player.getName() + "\",\n"
                    + "  \"confirmed\": true,\n"
                    + "  \"confirmed_at\": \"" + Instant.now() + "\"\n"
                    + "}";

            try (FileWriter fw = new FileWriter(pendingFile)) {
                fw.write(confirmedJson);
            }

            log.info("[Discord] " + player.getName() + " confirmed link (file) for Discord ID " + discordId);
            return "§a✅ Linked! Your Discord nickname will update shortly.";

        } catch (IOException e) {
            log.warning("[Discord] File link confirm error for " + player.getName() + ": " + e.getMessage());
            return "§cAn error occurred. Please try again.";
        }
    }

    private boolean isLinkedFile(UUID uuid) {
        File linksDir = new File(pendingDir.getParentFile(), "");
        File[] files  = linksDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return false;
        for (File f : files) {
            try {
                if (new String(Files.readAllBytes(f.toPath())).contains(uuid.toString())) return true;
            } catch (IOException ignored) {}
        }
        return false;
    }

    private String getLinkedDiscordIdFile(UUID uuid) {
        File linksDir = new File(pendingDir.getParentFile(), "");
        File[] files  = linksDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return null;
        for (File f : files) {
            try {
                String content = new String(Files.readAllBytes(f.toPath()));
                if (content.contains(uuid.toString())) {
                    return extractJsonString(content, "discord_id");
                }
            } catch (IOException ignored) {}
        }
        return null;
    }


    private String confirmLinkMongo(Player player, String code) {
        Document pending = pendingCol.find(Filters.eq("code", code)).first();
        if (pending == null) {
            return "§cInvalid or expired code. Use /mc link in Discord to get a new one.";
        }

        Date expiresAt = pending.getDate("expires_at");
        if (expiresAt != null && expiresAt.before(new Date())) {
            pendingCol.deleteOne(Filters.eq("code", code));
            return "§cThis code has expired. Use /mc link in Discord to get a fresh one.";
        }

        String discordId = pending.getString("discord_id");
        if (discordId == null || discordId.isEmpty()) {
            return "§cMalformed pending link. Please try again.";
        }

        confirmedCol.insertOne(new Document()
                .append("code",         code)
                .append("discord_id",   discordId)
                .append("uuid",         player.getUniqueId().toString())
                .append("username",     player.getName())
                .append("confirmed_at", new Date()));

        pendingCol.deleteOne(Filters.eq("code", code));

        log.info("[Discord] " + player.getName() + " confirmed link (mongodb) for Discord ID " + discordId);
        return "§a✅ Linked! Your Discord nickname will update within 15 seconds.";
    }

    private boolean isLinkedMongo(UUID uuid) {
        return linksCol.find(Filters.eq("uuid", uuid.toString())).first() != null;
    }

    private String getLinkedDiscordIdMongo(UUID uuid) {
        Document doc = linksCol.find(Filters.eq("uuid", uuid.toString())).first();
        return doc != null ? doc.getString("discord_id") : null;
    }


    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + search.length() + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}