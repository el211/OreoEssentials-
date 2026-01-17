package fr.elias.oreoEssentials.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyBootstrap {
    private final Plugin plugin;
    private EconomyService service;

    public EconomyBootstrap(Plugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                RegisteredServiceProvider<Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null && rsp.getProvider() != null) {
                    service = new VaultEconomyService(rsp.getProvider());
                    plugin.getLogger().info("[Economy] Hooked Vault provider: " + rsp.getProvider().getName());
                } else {
                    plugin.getLogger().info("[Economy] No Vault provider registered at boot.");
                }
            } else {
                plugin.getLogger().info("[Economy] Vault not present; using internal fallback.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Vault hook failed: " + t.getClass().getSimpleName());
        }

        if (service == null) {
            service = new YamlEconomyService(plugin);
            plugin.getLogger().info("[Economy] Using internal YAML balances.");
        }


    }

    public void disable() {
        if (service instanceof YamlEconomyService yaml) {
            yaml.save();
        }
    }

    public EconomyService api() {
        if (service == null) {
            throw new IllegalStateException("Economy not initialized. Call enable() first.");
        }
        return service;
    }
}
