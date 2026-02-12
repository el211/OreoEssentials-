package fr.elias.oreoEssentials.modules.economy;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyBootstrap {
    private final OreoEssentials plugin;
    private EconomyService service;
    private final File migrationFile;

    public EconomyBootstrap(OreoEssentials plugin) {
        this.plugin = plugin;
        this.migrationFile = new File(plugin.getDataFolder(), "economy_last_type.yml");
    }

    public void enable() {
        String economyType = plugin.getConfig().getString("economy.type", "yaml").toLowerCase();
        String lastType = getLastEconomyType();

        checkForOrphanedData(economyType);

        boolean backendChanged = lastType != null && !lastType.equals(economyType) && !lastType.equals("none");

        EconomyService oldService = null;
        if (backendChanged) {
            plugin.getLogger().warning("[Economy] Backend changed from '" + lastType + "' to '" + economyType + "'");
            plugin.getLogger().warning("[Economy] Attempting to migrate existing balances...");

            oldService = createService(lastType);
            if (oldService != null) {
                plugin.getLogger().info("[Economy] Old service loaded successfully for migration");
            }
        }

        service = createService(economyType);

        if (backendChanged && oldService != null && service != null) {
            try {
                EconomyMigration migration = new EconomyMigration(plugin);

                Map<UUID, Double> backup = migration.backup(oldService);

                if (!backup.isEmpty()) {
                    plugin.getLogger().info("[Economy] Created backup of " + backup.size() + " balances");

                    int migrated = migration.migrate(oldService, service);

                    if (migrated > 0) {
                        plugin.getLogger().info("[Economy] ✓ Successfully migrated " + migrated + " balances!");
                        plugin.getLogger().info("[Economy] Your players' balances have been preserved.");
                    } else {
                        plugin.getLogger().warning("[Economy] Migration completed but no balances were transferred.");
                    }
                } else {
                    plugin.getLogger().info("[Economy] No balances to migrate (old backend was empty)");
                }

            } catch (Exception e) {
                plugin.getLogger().severe("[Economy] Migration failed: " + e.getMessage());
                e.printStackTrace();
                plugin.getLogger().severe("[Economy] WARNING: Player balances may have been lost!");
            }
        }

        saveLastEconomyType(economyType);

        registerVaultProvider();
    }

    private EconomyService createService(String economyType) {
        switch (economyType.toLowerCase()) {
            case "mongodb" -> {
                PlayerEconomyDatabase database = plugin.getDatabase();
                if (database != null) {
                    plugin.getLogger().info("[Economy] Using MongoDB economy backend.");
                    return new MongoEconomyService(database);
                } else {
                    plugin.getLogger().warning("[Economy] MongoDB configured but database is null! Falling back to YAML...");
                }
            }
            case "json" -> {
                plugin.getLogger().info("[Economy] Using JSON economy backend.");
                return new JsonEconomyService(plugin);
            }
            case "yaml" -> {
                plugin.getLogger().info("[Economy] Using YAML economy backend.");
                return new YamlEconomyService(plugin);
            }
            case "none" -> {
                plugin.getLogger().info("[Economy] Economy disabled (type: none).");
                return null;
            }
        }

        plugin.getLogger().info("[Economy] Unknown type '" + economyType + "', using YAML economy backend (fallback).");
        return new YamlEconomyService(plugin);
    }

    private String getLastEconomyType() {
        if (!migrationFile.exists()) {
            return null;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(migrationFile);
            return config.getString("last-type", null);
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Could not read last economy type: " + e.getMessage());
            return null;
        }
    }

    private void saveLastEconomyType(String type) {
        try {
            if (!migrationFile.exists()) {
                migrationFile.getParentFile().mkdirs();
                migrationFile.createNewFile();
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(migrationFile);
            config.set("last-type", type);
            config.set("last-updated", System.currentTimeMillis());
            config.save(migrationFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Economy] Could not save last economy type: " + e.getMessage());
        }
    }

    private void checkForOrphanedData(String currentType) {
        File yamlFile = new File(plugin.getDataFolder(), "balances.yml");
        File jsonFile = new File(plugin.getDataFolder(), "balances.json");

        boolean hasYaml = yamlFile.exists() && yamlFile.length() > 100; // At least some data
        boolean hasJson = jsonFile.exists() && jsonFile.length() > 100;

        List<String> orphanedFiles = new ArrayList<>();

        if (hasYaml && !currentType.equals("yaml")) {
            orphanedFiles.add("balances.yml");
        }
        if (hasJson && !currentType.equals("json")) {
            orphanedFiles.add("balances.json");
        }

        if (!orphanedFiles.isEmpty()) {
            plugin.getLogger().warning("╔════════════════════════════════════════════════════════════════════╗");
            plugin.getLogger().warning("║           ⚠️  ECONOMY DATA FOUND BUT NOT BEING USED  ⚠️            ║");
            plugin.getLogger().warning("╠════════════════════════════════════════════════════════════════════╣");
            plugin.getLogger().warning("║ Found economy data file(s) that are NOT being used:                ║");
            for (String file : orphanedFiles) {
                plugin.getLogger().warning("║   - " + file + "                                                    ║");
            }
            plugin.getLogger().warning("║                                                                    ║");
            plugin.getLogger().warning("║ Current economy type: " + String.format("%-44s", currentType) + " ║");
            plugin.getLogger().warning("║                                                                    ║");
            plugin.getLogger().warning("║ This means players' balances may appear as 0!                      ║");
            plugin.getLogger().warning("║                                                                    ║");
            plugin.getLogger().warning("║ To recover your data, run one of these commands:                   ║");
            plugin.getLogger().warning("║   /ecorecover          - Auto-detect and show recovery options     ║");
            plugin.getLogger().warning("║   /ecomigrate <from> <to> - Migrate data between backends          ║");
            plugin.getLogger().warning("║                                                                    ║");
            plugin.getLogger().warning("║ Example: /ecomigrate yaml json                                     ║");
            plugin.getLogger().warning("╚════════════════════════════════════════════════════════════════════╝");
        }
    }

    private void registerVaultProvider() {
        if (service == null) {
            plugin.getLogger().info("[Economy] No economy service to register with Vault");
            return;
        }

        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                plugin.getLogger().info("[Economy] Vault not found - other plugins won't be able to use economy through Vault");
                return;
            }

            OreoVaultProvider vaultProvider = new OreoVaultProvider(plugin, service);
            Bukkit.getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class,
                    vaultProvider,
                    plugin,
                    org.bukkit.plugin.ServicePriority.Highest
            );

            plugin.getLogger().info("[Economy] ✓ Registered OreoEssentials economy with Vault!");
            plugin.getLogger().info("[Economy] Other plugins can now use your economy through Vault");

        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Failed to register with Vault: " + t.getMessage());
            plugin.getLogger().warning("[Economy] Other plugins may not be able to use your economy");
        }
    }

    private void unregisterVaultProvider() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                Bukkit.getServicesManager().unregisterAll(plugin);
                plugin.getLogger().info("[Economy] Unregistered from Vault");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Failed to unregister from Vault: " + t.getMessage());
        }
    }

    public void disable() {
        unregisterVaultProvider();

        if (service instanceof YamlEconomyService yaml) {
            yaml.save();
        } else if (service instanceof JsonEconomyService json) {
            json.save();
        }
    }

    public EconomyService api() {
        if (service == null) {
            throw new IllegalStateException("Economy not initialized. Call enable() first.");
        }
        return service;
    }
}