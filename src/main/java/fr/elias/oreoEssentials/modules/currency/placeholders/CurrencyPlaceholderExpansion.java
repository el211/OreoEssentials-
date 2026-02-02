package fr.elias.oreoEssentials.modules.currency.placeholders;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion for OreoEssentials Currency System
 *
 * Available placeholders:
 * - %oreo_currency_balance_<id>% - Raw balance (e.g., "1234.56")
 * - %oreo_currency_balance_formatted_<id>% - Formatted with symbol (e.g., "💎 1,234.56")
 * - %oreo_currency_symbol_<id>% - Currency symbol (e.g., "💎")
 * - %oreo_currency_name_<id>% - Currency name (e.g., "Gems")
 * - %oreo_currency_rank_<id>% - Player's rank in leaderboard (e.g., "3")
 * - %oreo_currency_top_<id>_<rank>_name% - Top player name at rank (e.g., "Notch")
 * - %oreo_currency_top_<id>_<rank>_balance% - Top player balance at rank (e.g., "💎 10,000.00")
 */
public class CurrencyPlaceholderExpansion extends PlaceholderExpansion {

    private final OreoEssentials plugin;
    private final CurrencyService currencyService;
    private final DecimalFormat decimalFormat;

    // Cache for balance placeholders (refresh every 5 seconds)
    private final Map<String, CachedValue<Double>> balanceCache = new ConcurrentHashMap<>();
    private final Map<String, CachedValue<Integer>> rankCache = new ConcurrentHashMap<>();
    private final Map<String, CachedValue<List<Map.Entry<String, Double>>>> topCache = new ConcurrentHashMap<>();

    private static final long CACHE_DURATION_MS = 5000; // 5 seconds

    public CurrencyPlaceholderExpansion(OreoEssentials plugin) {
        this.plugin = plugin;
        this.currencyService = plugin.getCurrencyService();
        this.decimalFormat = new DecimalFormat("#,##0.00");

    }

    @Override
    public @NotNull String getIdentifier() {
        return "oreocurrency"; // Changed from "oreo"
    }
    @Override
    public @NotNull String getAuthor() {
        return "OreoEssentials";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return currencyService != null;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {

        if (currencyService == null) {
            return null;
        }

        String[] parts = params.split("_");

        if (parts.length < 2) {
            return null;
        }

        String type = parts[0];

        switch (type.toLowerCase()) {
            case "balance":
                return handleBalance(player, parts);

            case "symbol":
                return handleSymbol(parts);

            case "name":
                return handleName(parts);

            case "rank":
                return handleRank(player, parts);

            case "top":
                return handleTop(parts);

            default:
                return null;
        }
    }

    /**
     * Handle %oreo_currency_balance_<id>%
     * Handle %oreo_currency_balance_formatted_<id>%
     */
    private String handleBalance(Player player, String[] parts) {
        if (player == null || parts.length < 2) {
            return "0.00";
        }

        boolean formatted = parts.length > 2 && "formatted".equalsIgnoreCase(parts[1]);
        String currencyId = formatted ? parts[2] : parts[1];

        Currency currency = currencyService.getCurrency(currencyId);
        if (currency == null) {
            return "Invalid Currency";
        }

        // Check cache
        String cacheKey = player.getUniqueId() + ":" + currencyId;
        CachedValue<Double> cached = balanceCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            double balance = cached.getValue();
            return formatted ? currency.format(balance) : decimalFormat.format(balance);
        }

        // Fetch from service (async, but we need sync result for placeholder)
        // Use a blocking get with timeout
        try {
            CompletableFuture<Double> future = currencyService.getBalance(player.getUniqueId(), currencyId);
            double balance = future.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS);            // Cache result
            balanceCache.put(cacheKey, new CachedValue<>(balance, CACHE_DURATION_MS));

            return formatted ? currency.format(balance) : decimalFormat.format(balance);
        } catch (Exception e) {
            return formatted ? currency.format(0) : "0.00";
        }
    }

    /**
     * Handle %oreo_currency_symbol_<id>%
     */
    private String handleSymbol(String[] parts) {
        if (parts.length < 2) {
            return "";
        }

        String currencyId = parts[1];
        Currency currency = currencyService.getCurrency(currencyId);

        return currency != null ? currency.getSymbol() : "";
    }

    /**
     * Handle %oreo_currency_name_<id>%
     */
    private String handleName(String[] parts) {
        if (parts.length < 2) {
            return "";
        }

        String currencyId = parts[1];
        Currency currency = currencyService.getCurrency(currencyId);

        return currency != null ? currency.getName() : "";
    }

    /**
     * Handle %oreo_currency_rank_<id>%
     */
    private String handleRank(Player player, String[] parts) {
        if (player == null || parts.length < 2) {
            return "N/A";
        }

        String currencyId = parts[1];
        Currency currency = currencyService.getCurrency(currencyId);

        if (currency == null) {
            return "N/A";
        }

        String cacheKey = player.getUniqueId() + ":" + currencyId + ":rank";
        CachedValue<Integer> cached = rankCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            return String.valueOf(cached.getValue());
        }

        try {
            CompletableFuture<List<Map.Entry<String, Double>>> future =
                    currencyService.getTopBalancesForPlaceholders(currencyId, 100);

            List<Map.Entry<String, Double>> topBalances =
                    future.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS);

            for (int i = 0; i < topBalances.size(); i++) {
                if (topBalances.get(i).getKey().equals(player.getName())) {
                    int rank = i + 1;
                    rankCache.put(cacheKey, new CachedValue<>(rank, CACHE_DURATION_MS));
                    return String.valueOf(rank);
                }
            }

            return "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Handle %oreo_currency_top_<id>_<rank>_name%
     * Handle %oreo_currency_top_<id>_<rank>_balance%
     */
    private String handleTop(String[] parts) {
        // Format: top_<id>_<rank>_<name|balance>
        if (parts.length < 4) {
            return "";
        }

        String currencyId = parts[1];
        int rank;

        try {
            rank = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "Invalid Rank";
        }

        String field = parts[3]; // "name" or "balance"

        Currency currency = currencyService.getCurrency(currencyId);
        if (currency == null) {
            return "Invalid Currency";
        }

        // Check cache
        String cacheKey = currencyId + ":top";
        CachedValue<List<Map.Entry<String, Double>>> cached = topCache.get(cacheKey);
        List<Map.Entry<String, Double>> topBalances;

        if (cached != null && !cached.isExpired()) {
            topBalances = cached.getValue();
        } else {
            // Fetch from service
            try {
                CompletableFuture<List<Map.Entry<String, Double>>> future =
                        currencyService.getTopBalancesForPlaceholders(currencyId, 100);

                topBalances = future.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                // Cache result
                topCache.put(cacheKey, new CachedValue<>(topBalances, CACHE_DURATION_MS));
            } catch (Exception e) {
                return "N/A";
            }
        }

        // Check if rank exists
        if (rank < 1 || rank > topBalances.size()) {
            return "N/A";
        }

        Map.Entry<String, Double> entry = topBalances.get(rank - 1);

        if ("name".equalsIgnoreCase(field)) {
            return entry.getKey();
        } else if ("balance".equalsIgnoreCase(field)) {
            return currency.format(entry.getValue());
        }

        return "";
    }

    /**
     * Clear all caches (call this on currency transactions)
     */
    public void clearCache() {
        balanceCache.clear();
        rankCache.clear();
        topCache.clear();
    }

    /**
     * Clear cache for specific player
     */
    public void clearCache(java.util.UUID playerId) {
        balanceCache.keySet().removeIf(key -> key.startsWith(playerId.toString()));
        rankCache.keySet().removeIf(key -> key.startsWith(playerId.toString()));
    }

    /**
     * Simple cache value wrapper with expiration
     */
    private static class CachedValue<T> {
        private final T value;
        private final long expiresAt;

        public CachedValue(T value, long durationMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + durationMs;
        }

        public T getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}