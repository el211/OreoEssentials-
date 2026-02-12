package fr.elias.oreoEssentials.modules.currency.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyBalance;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JSON file-based storage for currencies
 */
public class JsonCurrencyStorage implements CurrencyStorage {

    private final Plugin plugin;
    private final File currenciesFile;
    private final File balancesFile;
    private final Gson gson;

    // Cache
    private final Map<String, Currency> currencyCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> balanceCache = new ConcurrentHashMap<>();

    public JsonCurrencyStorage(Plugin plugin) {
        this.plugin = plugin;
        this.currenciesFile = new File(plugin.getDataFolder(), "currencies.json");
        this.balancesFile = new File(plugin.getDataFolder(), "currency_balances.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        load();
    }

    private void load() {
        loadCurrencies();
        loadBalances();
    }

    private void loadCurrencies() {
        if (!currenciesFile.exists()) {
            saveCurrencies();
            return;
        }

        try (Reader reader = new FileReader(currenciesFile)) {
            Type type = new TypeToken<Map<String, CurrencyData>>() {}.getType();
            Map<String, CurrencyData> data = gson.fromJson(reader, type);

            if (data != null) {
                data.forEach((id, currencyData) -> {
                    Currency currency = Currency.builder()
                            .id(id)
                            .name(currencyData.name)
                            .symbol(currencyData.symbol)
                            .displayName(currencyData.displayName)
                            .defaultBalance(currencyData.defaultBalance)
                            .tradeable(currencyData.tradeable)
                            .crossServer(currencyData.crossServer)
                            .allowNegative(currencyData.allowNegative)
                            .build();
                    currencyCache.put(id, currency);
                });
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[CurrencyStorage] Failed to load currencies: " + e.getMessage());
        }
    }

    private void loadBalances() {
        if (!balancesFile.exists()) {
            saveBalances();
            return;
        }

        try (Reader reader = new FileReader(balancesFile)) {
            Type type = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();
            Map<String, Map<String, Double>> data = gson.fromJson(reader, type);

            if (data != null) {
                data.forEach((uuidStr, currencies) -> {
                    UUID uuid = UUID.fromString(uuidStr);
                    balanceCache.put(uuid, new ConcurrentHashMap<>(currencies));
                });
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[CurrencyStorage] Failed to load balances: " + e.getMessage());
        }
    }

    private void saveCurrencies() {
        try {
            currenciesFile.getParentFile().mkdirs();

            Map<String, CurrencyData> data = new HashMap<>();
            currencyCache.forEach((id, currency) -> {
                CurrencyData currencyData = new CurrencyData();
                currencyData.name = currency.getName();
                currencyData.symbol = currency.getSymbol();
                currencyData.displayName = currency.getDisplayName();
                currencyData.defaultBalance = currency.getDefaultBalance();
                currencyData.tradeable = currency.isTradeable();
                currencyData.crossServer = currency.isCrossServer();
                currencyData.allowNegative = currency.isAllowNegative();
                data.put(id, currencyData);
            });

            try (Writer writer = new FileWriter(currenciesFile)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[CurrencyStorage] Failed to save currencies: " + e.getMessage());
        }
    }

    private void saveBalances() {
        try {
            balancesFile.getParentFile().mkdirs();

            Map<String, Map<String, Double>> data = new HashMap<>();
            balanceCache.forEach((uuid, currencies) -> {
                data.put(uuid.toString(), new HashMap<>(currencies));
            });

            try (Writer writer = new FileWriter(balancesFile)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[CurrencyStorage] Failed to save balances: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> saveCurrency(Currency currency) {
        return CompletableFuture.runAsync(() -> {
            currencyCache.put(currency.getId(), currency);
            saveCurrencies();
        });
    }

    @Override
    public CompletableFuture<Currency> loadCurrency(String currencyId) {
        return CompletableFuture.completedFuture(currencyCache.get(currencyId));
    }

    @Override
    public CompletableFuture<List<Currency>> loadAllCurrencies() {
        return CompletableFuture.completedFuture(
                new ArrayList<>(currencyCache.values())
        );
    }

    @Override
    public CompletableFuture<Boolean> deleteCurrency(String currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            Currency removed = currencyCache.remove(currencyId);
            if (removed != null) {
                saveCurrencies();

                balanceCache.values().forEach(map -> map.remove(currencyId));
                saveBalances();

                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Void> saveBalance(UUID playerId, String currencyId, double balance) {
        return CompletableFuture.runAsync(() -> {
            balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                    .put(currencyId, balance);
            saveBalances();
        });
    }

    @Override
    public CompletableFuture<Double> loadBalance(UUID playerId, String currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> playerBalances = balanceCache.get(playerId);
            if (playerBalances == null) {
                Currency currency = currencyCache.get(currencyId);
                return currency != null ? currency.getDefaultBalance() : 0.0;
            }
            return playerBalances.getOrDefault(currencyId,
                    currencyCache.getOrDefault(currencyId,
                                    Currency.builder().id(currencyId).name(currencyId).build())
                            .getDefaultBalance());
        });
    }

    @Override
    public CompletableFuture<List<CurrencyBalance>> loadPlayerBalances(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> playerBalances = balanceCache.get(playerId);
            if (playerBalances == null) {
                return Collections.emptyList();
            }

            return playerBalances.entrySet().stream()
                    .map(entry -> new CurrencyBalance(playerId, entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        });
    }

    @Override
    public CompletableFuture<List<CurrencyBalance>> getTopBalances(String currencyId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            return balanceCache.entrySet().stream()
                    .filter(entry -> entry.getValue().containsKey(currencyId))
                    .map(entry -> new CurrencyBalance(
                            entry.getKey(),
                            currencyId,
                            entry.getValue().get(currencyId)
                    ))
                    .sorted((a, b) -> Double.compare(b.getBalance(), a.getBalance()))
                    .limit(limit)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public void close() {
        saveCurrencies();
        saveBalances();
    }

    private static class CurrencyData {
        String name;
        String symbol;
        String displayName;
        double defaultBalance;
        boolean tradeable;
        boolean crossServer;
        boolean allowNegative;
    }
}