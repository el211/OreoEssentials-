package fr.elias.oreoEssentials.modules.currency.storage;

import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyBalance;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage interface for currencies and balances
 */
public interface CurrencyStorage {

    /**
     * Save a currency definition
     */
    CompletableFuture<Void> saveCurrency(Currency currency);

    /**
     * Load a currency by ID
     */
    CompletableFuture<Currency> loadCurrency(String currencyId);

    /**
     * Load all currencies
     */
    CompletableFuture<List<Currency>> loadAllCurrencies();

    /**
     * Delete a currency
     */
    CompletableFuture<Boolean> deleteCurrency(String currencyId);

    /**
     * Save a player's balance in a currency
     */
    CompletableFuture<Void> saveBalance(UUID playerId, String currencyId, double balance);

    /**
     * Load a player's balance in a currency
     */
    CompletableFuture<Double> loadBalance(UUID playerId, String currencyId);

    /**
     * Load all balances for a player
     */
    CompletableFuture<List<CurrencyBalance>> loadPlayerBalances(UUID playerId);

    /**
     * Get top balances for a currency
     */
    CompletableFuture<List<CurrencyBalance>> getTopBalances(String currencyId, int limit);

    /**
     * Close the storage
     */
    void close();
}