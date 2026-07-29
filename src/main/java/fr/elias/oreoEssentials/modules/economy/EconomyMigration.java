package fr.elias.oreoEssentials.modules.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class EconomyMigration {

    private final Plugin plugin;

    public EconomyMigration(Plugin plugin) {
        this.plugin = plugin;
    }


    public int migrate(EconomyService from, EconomyService to) {
        if (from == null || to == null) {
            plugin.getLogger().warning("[Economy Migration] Source or destination service is null!");
            return 0;
        }

        plugin.getLogger().info("[Economy Migration] Starting migration...");
        plugin.getLogger().info("[Economy Migration] From: " + from.getClass().getSimpleName());
        plugin.getLogger().info("[Economy Migration] To: " + to.getClass().getSimpleName());

        try {
            List<EconomyService.TopEntry> allBalances = from.topBalances(Integer.MAX_VALUE);

            if (allBalances.isEmpty()) {
                plugin.getLogger().warning("[Economy Migration] No balances found in source!");
                return 0;
            }

            plugin.getLogger().info("[Economy Migration] Found " + allBalances.size() + " balances to migrate");

            int successCount = 0;
            int failCount = 0;

            for (EconomyService.TopEntry entry : allBalances) {
                try {
                    UUID uuid = entry.uuid();
                    double balance = entry.balance();

                    // E-8: Skip if destination already has data — avoid doubling balances on re-run.
                    double destBalance = to.getBalance(uuid);
                    if (destBalance > 0) {
                        plugin.getLogger().warning("[Economy Migration] Skipping " + entry.name()
                                + " — destination already has balance: " + destBalance);
                        continue;
                    }

                    if (to.deposit(uuid, balance)) {
                        successCount++;
                        plugin.getLogger().info("[Economy Migration] Migrated " + entry.name() + ": " + balance);
                    } else {
                        failCount++;
                        plugin.getLogger().warning("[Economy Migration] Failed to migrate " + entry.name());
                    }
                } catch (Exception e) {
                    failCount++;
                    plugin.getLogger().warning("[Economy Migration] Error migrating " + entry.name() + ": " + e.getMessage());
                }
            }

            plugin.getLogger().info("[Economy Migration] Complete! Success: " + successCount + ", Failed: " + failCount);
            return successCount;

        } catch (Exception e) {
            plugin.getLogger().severe("[Economy Migration] Migration failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }


    /**
     * E-9: Writes all balances to a timestamped YAML backup file on disk
     * before any migration begins, so data is recoverable even if the server crashes mid-migration.
     */
    public File backupToDisk(EconomyService service) {
        File backup = new File(plugin.getDataFolder(),
                "migration-backup-" + System.currentTimeMillis() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            List<EconomyService.TopEntry> all = service.topBalances(Integer.MAX_VALUE);
            for (EconomyService.TopEntry e : all) {
                cfg.set("balances." + e.uuid().toString(), e.balance());
            }
            backup.getParentFile().mkdirs();
            cfg.save(backup);
            plugin.getLogger().info("[Economy Backup] Written to " + backup.getName()
                    + " (" + all.size() + " entries)");
        } catch (Exception e) {
            plugin.getLogger().severe("[Economy Backup] Failed to write backup file: " + e.getMessage());
        }
        return backup;
    }

    public Map<UUID, Double> backup(EconomyService service) {
        Map<UUID, Double> backup = new HashMap<>();

        if (service == null) {
            plugin.getLogger().warning("[Economy Backup] Service is null!");
            return backup;
        }

        try {
            List<EconomyService.TopEntry> allBalances = service.topBalances(Integer.MAX_VALUE);

            for (EconomyService.TopEntry entry : allBalances) {
                backup.put(entry.uuid(), entry.balance());
            }

            plugin.getLogger().info("[Economy Backup] Backed up " + backup.size() + " balances");
            return backup;

        } catch (Exception e) {
            plugin.getLogger().severe("[Economy Backup] Backup failed: " + e.getMessage());
            e.printStackTrace();
            return backup;
        }
    }


    public int restore(EconomyService service, Map<UUID, Double> backup) {
        if (service == null || backup == null || backup.isEmpty()) {
            plugin.getLogger().warning("[Economy Restore] Service is null or backup is empty!");
            return 0;
        }

        plugin.getLogger().info("[Economy Restore] Restoring " + backup.size() + " balances...");

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<UUID, Double> entry : backup.entrySet()) {
            try {
                if (service.deposit(entry.getKey(), entry.getValue())) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                plugin.getLogger().warning("[Economy Restore] Error restoring " + entry.getKey() + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("[Economy Restore] Complete! Success: " + successCount + ", Failed: " + failCount);
        return successCount;
    }
}