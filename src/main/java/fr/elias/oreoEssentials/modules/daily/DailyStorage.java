package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Unified storage interface for Daily Rewards.
 * Automatically uses MongoDB if enabled, otherwise falls back to file storage.
 */
public interface DailyStorage {

    interface Record {
        UUID getUuid();
        String getName();
        int getStreak();
        long getLastClaimEpochDay();
        int getTotalClaims();
        LocalDate getLastClaimDate();
    }

    void connect();


    Record get(UUID uuid);


    Record ensure(UUID uuid, String name);


    void updateOnClaim(UUID uuid, String name, int newStreak, LocalDate date);


    void resetStreak(UUID uuid);


    void close();


    static DailyStorage create(OreoEssentials plugin, DailyConfig cfg) {
        if (cfg.mongo.enabled) {
            DailyMongoStore mongoStore = new DailyMongoStore(plugin, cfg);
            mongoStore.connect();

            if (mongoStore.isConnected()) {
                plugin.getLogger().info("[Daily] Using MongoDB storage.");
                return new MongoStorageAdapter(mongoStore);
            } else {
                plugin.getLogger().warning("[Daily] MongoDB connection failed, falling back to file storage.");
            }
        }

        plugin.getLogger().info("[Daily] Using file-based storage.");
        DailyFileStore fileStore = new DailyFileStore(plugin);
        fileStore.load();
        fileStore.startAutoSave();
        return new FileStorageAdapter(fileStore);
    }


    class MongoStorageAdapter implements DailyStorage {
        private final DailyMongoStore store;

        public MongoStorageAdapter(DailyMongoStore store) {
            this.store = store;
        }

        @Override
        public void connect() {
            store.connect();
        }

        @Override
        public Record get(UUID uuid) {
            DailyMongoStore.Record r = store.get(uuid);
            return r == null ? null : new RecordAdapter(r);
        }

        @Override
        public Record ensure(UUID uuid, String name) {
            return new RecordAdapter(store.ensure(uuid, name));
        }

        @Override
        public void updateOnClaim(UUID uuid, String name, int newStreak, LocalDate date) {
            store.updateOnClaim(uuid, name, newStreak, date);
        }

        @Override
        public void resetStreak(UUID uuid) {
            store.resetStreak(uuid);
        }

        @Override
        public void close() {
            store.close();
        }

        private static class RecordAdapter implements Record {
            private final DailyMongoStore.Record r;

            RecordAdapter(DailyMongoStore.Record r) {
                this.r = r;
            }

            @Override public UUID getUuid() { return r.uuid; }
            @Override public String getName() { return r.name; }
            @Override public int getStreak() { return r.streak; }
            @Override public long getLastClaimEpochDay() { return r.lastClaimEpochDay; }
            @Override public int getTotalClaims() { return r.totalClaims; }
            @Override public LocalDate getLastClaimDate() { return r.lastClaimDate(); }
        }
    }


    class FileStorageAdapter implements DailyStorage {
        private final DailyFileStore store;

        public FileStorageAdapter(DailyFileStore store) {
            this.store = store;
        }

        @Override
        public void connect() {
            store.load();
        }

        @Override
        public Record get(UUID uuid) {
            DailyFileStore.Record r = store.get(uuid);
            return r == null ? null : new RecordAdapter(r);
        }

        @Override
        public Record ensure(UUID uuid, String name) {
            return new RecordAdapter(store.ensure(uuid, name));
        }

        @Override
        public void updateOnClaim(UUID uuid, String name, int newStreak, LocalDate date) {
            store.updateOnClaim(uuid, name, newStreak, date);
        }

        @Override
        public void resetStreak(UUID uuid) {
            store.resetStreak(uuid);
        }

        @Override
        public void close() {
            store.close();
        }

        private static class RecordAdapter implements Record {
            private final DailyFileStore.Record r;

            RecordAdapter(DailyFileStore.Record r) {
                this.r = r;
            }

            @Override public UUID getUuid() { return r.uuid; }
            @Override public String getName() { return r.name; }
            @Override public int getStreak() { return r.streak; }
            @Override public long getLastClaimEpochDay() { return r.lastClaimEpochDay; }
            @Override public int getTotalClaims() { return r.totalClaims; }
            @Override public LocalDate getLastClaimDate() { return r.lastClaimDate(); }
        }
    }
}