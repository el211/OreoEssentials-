package fr.elias.oreoEssentials.modules.jail;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.Location;

import java.util.*;

import static com.mongodb.client.model.Filters.eq;

/**
 * MongoDB-backed storage for jails and sentences.
 * Enables cross-server synchronization of jail data.
 */
public final class MongoJailStorage implements JailStorage {

    private final MongoClient client;
    private final MongoCollection<Document> jailsCol;
    private final MongoCollection<Document> sentencesCol;

    public MongoJailStorage(String uri, String database) {
        this.client = MongoClients.create(uri);
        MongoDatabase db = client.getDatabase(database);
        this.jailsCol = db.getCollection("jails");
        this.sentencesCol = db.getCollection("jail_sentences");
    }


    @Override
    public Map<String, JailModels.Jail> loadJails() {
        Map<String, JailModels.Jail> out = new HashMap<>();

        for (Document doc : jailsCol.find()) {
            try {
                JailModels.Jail jail = new JailModels.Jail();
                jail.name = doc.getString("_id");
                jail.world = doc.getString("world");

                Document regionDoc = doc.get("region", Document.class);
                if (regionDoc != null) {
                    JailModels.Cuboid cuboid = new JailModels.Cuboid();
                    cuboid.x1 = regionDoc.getDouble("x1");
                    cuboid.y1 = regionDoc.getDouble("y1");
                    cuboid.z1 = regionDoc.getDouble("z1");
                    cuboid.x2 = regionDoc.getDouble("x2");
                    cuboid.y2 = regionDoc.getDouble("y2");
                    cuboid.z2 = regionDoc.getDouble("z2");
                    jail.region = cuboid;
                }

                Document cellsDoc = doc.get("cells", Document.class);
                if (cellsDoc != null) {
                    for (String cellId : cellsDoc.keySet()) {
                        Document cellDoc = cellsDoc.get(cellId, Document.class);
                        if (cellDoc != null) {
                            Location loc = JailModels.loc(
                                    jail.world,
                                    cellDoc.getDouble("x"),
                                    cellDoc.getDouble("y"),
                                    cellDoc.getDouble("z"),
                                    cellDoc.getDouble("yaw").floatValue(),
                                    cellDoc.getDouble("pitch").floatValue()
                            );
                            if (loc != null) {
                                jail.cells.put(cellId, loc);
                            }
                        }
                    }
                }

                if (jail.isValid()) {
                    out.put(jail.name.toLowerCase(Locale.ROOT), jail);
                }
            } catch (Exception ex) {
            }
        }

        return out;
    }

    @Override
    public void saveJails(Map<String, JailModels.Jail> all) {
        // Clear existing jails
        jailsCol.deleteMany(new Document());

        if (all.isEmpty()) return;

        List<Document> docs = new ArrayList<>();

        for (JailModels.Jail jail : all.values()) {
            Document jailDoc = new Document("_id", jail.name.toLowerCase(Locale.ROOT))
                    .append("world", jail.world);

            // Save region
            if (jail.region != null) {
                Document regionDoc = new Document()
                        .append("x1", jail.region.x1)
                        .append("y1", jail.region.y1)
                        .append("z1", jail.region.z1)
                        .append("x2", jail.region.x2)
                        .append("y2", jail.region.y2)
                        .append("z2", jail.region.z2);
                jailDoc.append("region", regionDoc);
            }

            if (!jail.cells.isEmpty()) {
                Document cellsDoc = new Document();
                for (Map.Entry<String, Location> entry : jail.cells.entrySet()) {
                    Location loc = entry.getValue();
                    Document cellDoc = new Document()
                            .append("x", loc.getX())
                            .append("y", loc.getY())
                            .append("z", loc.getZ())
                            .append("yaw", (double) loc.getYaw())
                            .append("pitch", (double) loc.getPitch());
                    cellsDoc.append(entry.getKey(), cellDoc);
                }
                jailDoc.append("cells", cellsDoc);
            }

            docs.add(jailDoc);
        }

        if (!docs.isEmpty()) {
            jailsCol.insertMany(docs);
        }
    }


    @Override
    public Map<UUID, JailModels.Sentence> loadSentences() {
        Map<UUID, JailModels.Sentence> out = new HashMap<>();

        for (Document doc : sentencesCol.find()) {
            try {
                UUID uuid = UUID.fromString(doc.getString("_id"));

                JailModels.Sentence sentence = new JailModels.Sentence();
                sentence.player = uuid;
                sentence.jailName = doc.getString("jailName");
                sentence.cellId = doc.getString("cellId");
                sentence.endEpochMs = doc.getLong("endEpochMs");
                sentence.reason = doc.getString("reason");
                sentence.by = doc.getString("by");

                out.put(uuid, sentence);
            } catch (Exception ignored) {
            }
        }

        return out;
    }

    @Override
    public void saveSentences(Map<UUID, JailModels.Sentence> sentences) {
        sentencesCol.deleteMany(new Document());

        if (sentences.isEmpty()) return;

        List<Document> docs = new ArrayList<>();

        for (JailModels.Sentence sentence : sentences.values()) {
            Document sentenceDoc = new Document("_id", sentence.player.toString())
                    .append("jailName", sentence.jailName)
                    .append("cellId", sentence.cellId)
                    .append("endEpochMs", sentence.endEpochMs)
                    .append("reason", sentence.reason)
                    .append("by", sentence.by);

            docs.add(sentenceDoc);
        }

        if (!docs.isEmpty()) {
            sentencesCol.insertMany(docs);
        }
    }


    /**
     * Save or update a single sentence (upsert).
     * More efficient than rewriting all sentences.
     */
    public void saveSentence(JailModels.Sentence sentence) {
        Document doc = new Document("_id", sentence.player.toString())
                .append("jailName", sentence.jailName)
                .append("cellId", sentence.cellId)
                .append("endEpochMs", sentence.endEpochMs)
                .append("reason", sentence.reason)
                .append("by", sentence.by);

        sentencesCol.replaceOne(
                eq("_id", sentence.player.toString()),
                doc,
                new ReplaceOptions().upsert(true)
        );
    }

    /**
     * Delete a single sentence by player UUID.
     * More efficient than rewriting all sentences.
     */
    public void deleteSentence(UUID player) {
        sentencesCol.deleteOne(eq("_id", player.toString()));
    }

    /**
     * Save or update a single jail (upsert).
     * More efficient than rewriting all jails.
     */
    public void saveJail(JailModels.Jail jail) {
        Document jailDoc = new Document("_id", jail.name.toLowerCase(Locale.ROOT))
                .append("world", jail.world);

        // Save region
        if (jail.region != null) {
            Document regionDoc = new Document()
                    .append("x1", jail.region.x1)
                    .append("y1", jail.region.y1)
                    .append("z1", jail.region.z1)
                    .append("x2", jail.region.x2)
                    .append("y2", jail.region.y2)
                    .append("z2", jail.region.z2);
            jailDoc.append("region", regionDoc);
        }

        // Save cells
        if (!jail.cells.isEmpty()) {
            Document cellsDoc = new Document();
            for (Map.Entry<String, Location> entry : jail.cells.entrySet()) {
                Location loc = entry.getValue();
                Document cellDoc = new Document()
                        .append("x", loc.getX())
                        .append("y", loc.getY())
                        .append("z", loc.getZ())
                        .append("yaw", (double) loc.getYaw())
                        .append("pitch", (double) loc.getPitch());
                cellsDoc.append(entry.getKey(), cellDoc);
            }
            jailDoc.append("cells", cellsDoc);
        }

        jailsCol.replaceOne(
                eq("_id", jail.name.toLowerCase(Locale.ROOT)),
                jailDoc,
                new ReplaceOptions().upsert(true)
        );
    }

    /**
     * Delete a single jail by name.
     */
    public void deleteJail(String jailName) {
        jailsCol.deleteOne(eq("_id", jailName.toLowerCase(Locale.ROOT)));
    }


    @Override
    public void close() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception ignored) {

        }
    }
}