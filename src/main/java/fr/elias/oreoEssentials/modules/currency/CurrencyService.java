package fr.elias.oreoEssentials.modules.currency;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.storage.CurrencyStorage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main service for managing multiple custom currencies
 */
public class CurrencyService {

    private final OreoEssentials plugin;
    private final CurrencyStorage storage;
    private final CurrencyConfig config;

    private final Map<String, Currency> currencyCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> balanceCache = new ConcurrentHashMap<>();

    public CurrencyService(OreoEssentials plugin, CurrencyStorage storage, CurrencyConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.config = config;

        loadAllCurrencies();
    }

    private void loadAllCurrencies() {
        plugin.getLogger().info("[Currency][DBG] Startup: loading currencies (thread=" + Thread.currentThread().getName() + ")");
        reloadCurrenciesAsync().thenRun(() -> {
            plugin.getLogger().info("[Currency][DBG] Startup load complete. cacheSize=" + currencyCache.size());
            debugDumpCurrencies("startup-after-reload");

            if (currencyCache.isEmpty()) {
                createDefaultCurrency();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Currency] Failed to load currencies at startup: " + ex.getMessage());
            return null;
        });
    }

    private void createDefaultCurrency() {
        plugin.getLogger().info("[Currency][DBG] Creating default currency 'money' (thread=" + Thread.currentThread().getName() + ")");

        Currency defaultCurrency = Currency.builder()
                .id("money")
                .name("Money")
                .symbol("$")
                .displayName("Money")
                .defaultBalance(0.0)
                .tradeable(true)
                .crossServer(config.isCrossServerEnabled())
                .build();

        createCurrency(defaultCurrency).thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("[Currency] Created default currency: Money");
            } else {
                plugin.getLogger().warning("[Currency][DBG] Default currency create returned false (maybe already exists?)");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Currency] Failed to create default currency: " + ex.getMessage());
            return null;
        });
    }


    public CompletableFuture<Boolean> createCurrency(Currency currency) {
        final String id = currency.getId() == null ? "" : currency.getId().toLowerCase(Locale.ROOT).trim();
        if (id.isEmpty()) {
            plugin.getLogger().warning("[Currency][DBG] createCurrency called with empty id");
            return CompletableFuture.completedFuture(false);
        }

        Currency normalized = Currency.builder()
                .id(id)
                .name(currency.getName())
                .symbol(currency.getSymbol())
                .displayName(currency.getDisplayName())
                .defaultBalance(currency.getDefaultBalance())
                .tradeable(currency.isTradeable())
                .crossServer(currency.isCrossServer())
                .build();

        if (currencyCache.containsKey(id)) {
            plugin.getLogger().warning("[Currency][DBG] createCurrency rejected (already in cache): " + id);
            return CompletableFuture.completedFuture(false);
        }

        plugin.getLogger().info("[Currency][DBG] createCurrency saving to storage: id=" + id + ", name=" + normalized.getName()
                + ", crossServerCfg=" + config.isCrossServerEnabled()
                + ", packetManager=" + (plugin.getPacketManager() == null ? "null" : "ok"));

        return storage.saveCurrency(normalized).thenCompose(v -> {
            currencyCache.put(id, normalized);
            plugin.getLogger().info("[Currency] Created: " + normalized.getName() + " (" + normalized.getSymbol() + ")");

            // Broadcast to other servers if cross-server is enabled
            if (config.isCrossServerEnabled() && plugin.getPacketManager() != null) {
                plugin.getLogger().info("[Currency][DBG] Broadcasting CREATE for " + id
                        + " (pmInit=" + plugin.getPacketManager().isInitialized() + ")");
                broadcastCurrencySync(normalized, true);
            } else {
                plugin.getLogger().warning("[Currency][DBG] Not broadcasting CREATE. crossServer="
                        + config.isCrossServerEnabled() + ", packetManager=" + (plugin.getPacketManager() != null));
            }

            return reloadCurrenciesAsync()
                    .thenApply(x -> true);
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Currency][DBG] createCurrency failed: " + ex.getMessage());
            return false;
        });
    }

    public Currency getCurrency(String id) {
        if (id == null) return null;
        final String key = id.toLowerCase(Locale.ROOT).trim();
        Currency c = currencyCache.get(key);
        if (c == null) {
            // Useful to debug "Currency not found" scenarios
            plugin.getLogger().warning("[Currency][DBG] getCurrency MISS for '" + key + "'. CacheKeys=" + currencyCache.keySet());
        }
        return c;
    }

    public List<Currency> getAllCurrencies() {
        return new ArrayList<>(currencyCache.values());
    }

    public CompletableFuture<Boolean> deleteCurrency(String id) {
        final String key = id == null ? "" : id.toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) {
            plugin.getLogger().warning("[Currency][DBG] deleteCurrency called with empty id");
            return CompletableFuture.completedFuture(false);
        }

        Currency currency = currencyCache.get(key);
        if (currency == null) {
            plugin.getLogger().warning("[Currency][DBG] deleteCurrency MISS (not in cache): " + key);
            return CompletableFuture.completedFuture(false);
        }

        plugin.getLogger().info("[Currency][DBG] deleteCurrency deleting from storage: " + key);

        return storage.deleteCurrency(key).thenCompose(success -> {
            if (!success) {
                plugin.getLogger().warning("[Currency][DBG] deleteCurrency storage returned false for: " + key);
                return CompletableFuture.completedFuture(false);
            }

            currencyCache.remove(key);

            balanceCache.values().forEach(map -> map.remove(key));

            plugin.getLogger().info("[Currency] Deleted: " + key);

            if (config.isCrossServerEnabled() && plugin.getPacketManager() != null) {
                plugin.getLogger().info("[Currency][DBG] Broadcasting DELETE for " + key
                        + " (pmInit=" + plugin.getPacketManager().isInitialized() + ")");
                broadcastCurrencySync(currency, false);
            } else {
                plugin.getLogger().warning("[Currency][DBG] Not broadcasting DELETE. crossServer="
                        + config.isCrossServerEnabled() + ", packetManager=" + (plugin.getPacketManager() != null));
            }

            return reloadCurrenciesAsync()
                    .thenApply(x -> true);
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Currency][DBG] deleteCurrency failed: " + ex.getMessage());
            return false;
        });
    }

    public CompletableFuture<Void> reloadCurrenciesAsync() {
        final long start = System.currentTimeMillis();
        plugin.getLogger().info("[Currency][DBG] reloadCurrenciesAsync START (thread=" + Thread.currentThread().getName() + ")");

        return storage.loadAllCurrencies().thenAccept(currencies -> {
            currencyCache.clear();

            int loaded = 0;
            List<String> ids = new ArrayList<>();

            for (Currency currency : currencies) {
                if (currency == null || currency.getId() == null) continue;
                String key = currency.getId().toLowerCase(Locale.ROOT).trim();
                currencyCache.put(key, currency);
                loaded++;
                ids.add(key);

                plugin.getLogger().info("[Currency] Loaded: " + currency.getName() + " (" + currency.getSymbol() + ")");
            }

            long took = System.currentTimeMillis() - start;
            plugin.getLogger().info("[Currency][DBG] reloadCurrenciesAsync DONE loaded=" + loaded
                    + " tookMs=" + took + " ids=" + ids);

            plugin.getLogger().info("[Currency] Reloaded " + currencies.size() + " currencies from storage");
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Currency][DBG] reloadCurrenciesAsync FAILED: " + ex.getMessage());
            return null;
        });
    }


    public CompletableFuture<Double> getBalance(UUID playerId, String currencyId) {
        if (playerId == null || currencyId == null) {
            return CompletableFuture.completedFuture(0.0);
        }

        currencyId = currencyId.toLowerCase(Locale.ROOT).trim();
        final String finalCurrencyId = currencyId;

        if (config.isCrossServerEnabled()) {
            return storage.loadBalance(playerId, finalCurrencyId).thenApply(balance -> {
                balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                        .put(finalCurrencyId, balance);
                return balance;
            });
        }

        Map<String, Double> playerBalances = balanceCache.get(playerId);
        if (playerBalances != null && playerBalances.containsKey(finalCurrencyId)) {
            return CompletableFuture.completedFuture(playerBalances.get(finalCurrencyId));
        }

        return storage.loadBalance(playerId, finalCurrencyId).thenApply(balance -> {
            balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                    .put(finalCurrencyId, balance);
            return balance;
        });
    }


    public CompletableFuture<Void> setBalance(UUID playerId, String currencyId, double amount) {
        if (playerId == null || currencyId == null) {
            return CompletableFuture.completedFuture(null);
        }

        currencyId = currencyId.toLowerCase(Locale.ROOT).trim();

        Currency currency = getCurrency(currencyId);
        if (currency != null && !currency.isAllowNegative()) {
            amount = Math.max(0, amount);
        }

        final String finalCurrencyId = currencyId;
        final double finalAmount = amount;

        balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(finalCurrencyId, finalAmount);

        return storage.saveBalance(playerId, finalCurrencyId, finalAmount).thenApply(v -> {
            clearPlaceholderCache(playerId);
            return null;
        });
    }

    public CompletableFuture<Boolean> deposit(UUID playerId, String currencyId, double amount) {
        if (playerId == null || currencyId == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        currencyId = currencyId.toLowerCase(Locale.ROOT).trim();
        final String finalCurrencyId = currencyId;

        return getBalance(playerId, currencyId).thenCompose(balance -> {
            double newBalance = balance + amount;
            return setBalance(playerId, finalCurrencyId, newBalance)
                    .thenApply(v -> {
                        clearPlaceholderCache(playerId);
                        return true;
                    });
        });
    }

    public CompletableFuture<Boolean> withdraw(UUID playerId, String currencyId, double amount) {
        if (playerId == null || currencyId == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        currencyId = currencyId.toLowerCase(Locale.ROOT).trim();
        final String finalCurrencyId = currencyId;

        return getBalance(playerId, currencyId).thenCompose(balance -> {
            if (balance < amount) {
                return CompletableFuture.completedFuture(false);
            }

            double newBalance = balance - amount;
            return setBalance(playerId, finalCurrencyId, newBalance)
                    .thenApply(v -> {
                        clearPlaceholderCache(playerId);
                        return true;
                    });
        });
    }

    public CompletableFuture<Boolean> transfer(UUID from, UUID to, String currencyId, double amount) {
        if (from == null || to == null || currencyId == null) {
            return CompletableFuture.completedFuture(false);
        }

        currencyId = currencyId.toLowerCase(Locale.ROOT).trim();
        Currency currency = getCurrency(currencyId);

        if (currency == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (!currency.isTradeable()) {
            return CompletableFuture.completedFuture(false);
        }

        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        final String finalCurrencyId = currencyId;

        return withdraw(from, currencyId, amount).thenCompose(success -> {
            if (!success) {
                return CompletableFuture.completedFuture(false);
            }

            return deposit(to, finalCurrencyId, amount).thenApply(depositSuccess -> {
                if (!depositSuccess) {
                    // Rollback (fire-and-forget)
                    deposit(from, finalCurrencyId, amount);
                    return false;
                }
                return true;
            });
        });
    }

    public CompletableFuture<Map<String, Double>> getAllBalances(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        return storage.loadPlayerBalances(playerId).thenApply(balances -> {
            Map<String, Double> result = new HashMap<>();
            balances.forEach(balance -> {
                if (balance == null || balance.getCurrencyId() == null) return;
                result.put(balance.getCurrencyId().toLowerCase(Locale.ROOT).trim(), balance.getBalance());
            });

            // Cache them
            balanceCache.put(playerId, new ConcurrentHashMap<>(result));

            return result;
        });
    }

    public CompletableFuture<List<CurrencyBalance>> getTopBalances(String currencyId, int limit) {
        if (currencyId == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        currencyId = currencyId.toLowerCase(Locale.ROOT).trim();
        return storage.getTopBalances(currencyId, limit);
    }


    public String formatBalance(String currencyId, double amount) {
        Currency currency = getCurrency(currencyId);
        if (currency == null) {
            return String.format(Locale.ROOT, "%.2f", amount);
        }
        return currency.format(amount);
    }

    public void clearCache(UUID playerId) {
        if (playerId == null) return;
        balanceCache.remove(playerId);
    }

    public void shutdown() {
        try {
            storage.close();
            plugin.getLogger().info("[Currency] Service shutdown complete");
        } catch (Exception e) {
            plugin.getLogger().warning("[Currency] Error during shutdown: " + e.getMessage());
        }
    }


    /**
     * Broadcast currency creation/deletion to other servers
     */
    private void broadcastCurrencySync(Currency currency, boolean isCreate) {
        try {
            if (plugin.getPacketManager() == null) {
                plugin.getLogger().warning("[Currency][DBG] broadcastCurrencySync aborted: packetManager is null");
                return;
            }
            if (!plugin.getPacketManager().isInitialized()) {
                plugin.getLogger().warning("[Currency][DBG] broadcastCurrencySync aborted: packetManager not initialized");
                return;
            }

            fr.elias.oreoEssentials.modules.currency.rabbitmq.CurrencySyncPacket packet;

            if (isCreate) {
                packet = new fr.elias.oreoEssentials.modules.currency.rabbitmq.CurrencySyncPacket(
                        fr.elias.oreoEssentials.modules.currency.rabbitmq.CurrencySyncPacket.Action.CREATE,
                        currency.getId(),
                        currency.getName(),
                        currency.getSymbol(),
                        currency.getDisplayName(),
                        currency.getDefaultBalance(),
                        currency.isTradeable(),
                        currency.isCrossServer(),
                        currency.isAllowNegative()
                );
            } else {
                packet = new fr.elias.oreoEssentials.modules.currency.rabbitmq.CurrencySyncPacket(
                        fr.elias.oreoEssentials.modules.currency.rabbitmq.CurrencySyncPacket.Action.DELETE,
                        currency.getId()
                );
            }

            plugin.getPacketManager().sendPacket(packet);
            plugin.getLogger().info("[Currency] Broadcasted " + (isCreate ? "CREATE" : "DELETE") + " for: " + currency.getId());

        } catch (Exception e) {
            plugin.getLogger().warning("[Currency] Failed to broadcast currency sync: " + e.getMessage());
        }
    }

    /**
     * Handle incoming currency sync from other servers
     * Call this from a packet listener
     */
    public void handleCurrencySync(fr.elias.oreoEssentials.modules.currency.rabbitmq.CurrencySyncPacket packet) {
        if (packet == null) {
            plugin.getLogger().warning("[Currency][DBG] handleCurrencySync called with null packet");
            return;
        }

        plugin.getLogger().info("[Currency][DBG] Sync packet RECEIVED action=" + packet.getAction()
                + " id=" + packet.getCurrencyId()
                + " fromThread=" + Thread.currentThread().getName()
                + " crossServerCfg=" + config.isCrossServerEnabled());

        reloadCurrenciesAsync().exceptionally(ex -> {
            plugin.getLogger().warning("[Currency] Failed to reload currencies after sync: " + ex.getMessage());
            return null;
        });
    }
    /**
     * Get top balances as Map.Entry format for placeholders
     */
    public CompletableFuture<List<Map.Entry<String, Double>>> getTopBalancesForPlaceholders(String currencyId, int limit) {
        return getTopBalances(currencyId, limit).thenApply(balances -> {
            return balances.stream()
                    .map(balance -> {
                        // Get player name from UUID
                        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(balance.getPlayerId());
                        String playerName = player.getName() != null ? player.getName() : balance.getPlayerId().toString();
                        return new java.util.AbstractMap.SimpleEntry<>(playerName, balance.getBalance());
                    })
                    .collect(java.util.stream.Collectors.toList());
        });
    }

    private void clearPlaceholderCache(UUID playerId) {
        try {
            var expansion = plugin.getCurrencyPlaceholders();
            if (expansion != null) {
                expansion.clearCache(playerId);
            }
        } catch (Throwable ignored) {}
    }

    private void debugDumpCurrencies(String reason) {
        try {
            plugin.getLogger().info("[Currency][DBG] Dump(" + reason + "): cacheKeys=" + currencyCache.keySet());
        } catch (Throwable ignored) {}
    }
}