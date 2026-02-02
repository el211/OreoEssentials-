package fr.elias.oreoEssentials.modules.currency.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.currency.CurrencyBalance;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MongoDB-based storage for currencies (cross-server support)
 */
public class MongoCurrencyStorage implements CurrencyStorage {

    private final MongoCollection<Document> currenciesCollection;
    private final MongoCollection<Document> balancesCollection;

    public MongoCurrencyStorage(MongoClient client, String database, String prefix) {
        MongoDatabase db = client.getDatabase(database);
        this.currenciesCollection = db.getCollection(prefix + "currencies");
        this.balancesCollection = db.getCollection(prefix + "currency_balances");

        balancesCollection.createIndex(new Document("playerId", 1).append("currencyId", 1));
        balancesCollection.createIndex(new Document("currencyId", 1).append("balance", -1));
    }

    @Override
    public CompletableFuture<Void> saveCurrency(Currency currency) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document()
                    .append("_id", currency.getId())
                    .append("name", currency.getName())
                    .append("symbol", currency.getSymbol())
                    .append("displayName", currency.getDisplayName())
                    .append("defaultBalance", currency.getDefaultBalance())
                    .append("tradeable", currency.isTradeable())
                    .append("crossServer", currency.isCrossServer())
                    .append("allowNegative", currency.isAllowNegative());

            currenciesCollection.replaceOne(
                    Filters.eq("_id", currency.getId()),
                    doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
            );
        });
    }

    @Override
    public CompletableFuture<Currency> loadCurrency(String currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = currenciesCollection.find(Filters.eq("_id", currencyId)).first();
            if (doc == null) return null;

            return Currency.builder()
                    .id(doc.getString("_id"))
                    .name(doc.getString("name"))
                    .symbol(doc.getString("symbol"))
                    .displayName(doc.getString("displayName"))
                    .defaultBalance(doc.getDouble("defaultBalance"))
                    .tradeable(doc.getBoolean("tradeable"))
                    .crossServer(doc.getBoolean("crossServer"))
                    .allowNegative(doc.getBoolean("allowNegative") != null ? doc.getBoolean("allowNegative") : false)
                    .build();
        });
    }

    @Override
    public CompletableFuture<List<Currency>> loadAllCurrencies() {
        return CompletableFuture.supplyAsync(() -> {
            List<Currency> currencies = new ArrayList<>();
            for (Document doc : currenciesCollection.find()) {
                currencies.add(Currency.builder()
                        .id(doc.getString("_id"))
                        .name(doc.getString("name"))
                        .symbol(doc.getString("symbol"))
                        .displayName(doc.getString("displayName"))
                        .defaultBalance(doc.getDouble("defaultBalance"))
                        .tradeable(doc.getBoolean("tradeable"))
                        .crossServer(doc.getBoolean("crossServer"))
                        .allowNegative(doc.getBoolean("allowNegative") != null ? doc.getBoolean("allowNegative") : false)
                        .build());
            }
            return currencies;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteCurrency(String currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            long deleted = currenciesCollection.deleteOne(Filters.eq("_id", currencyId)).getDeletedCount();
            if (deleted > 0) {
                // Delete all balances for this currency
                balancesCollection.deleteMany(Filters.eq("currencyId", currencyId));
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Void> saveBalance(UUID playerId, String currencyId, double balance) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document()
                    .append("playerId", playerId.toString())
                    .append("currencyId", currencyId)
                    .append("balance", balance);

            balancesCollection.replaceOne(
                    Filters.and(
                            Filters.eq("playerId", playerId.toString()),
                            Filters.eq("currencyId", currencyId)
                    ),
                    doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
            );
        });
    }

    @Override
    public CompletableFuture<Double> loadBalance(UUID playerId, String currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = balancesCollection.find(
                    Filters.and(
                            Filters.eq("playerId", playerId.toString()),
                            Filters.eq("currencyId", currencyId)
                    )
            ).first();

            if (doc != null) {
                return doc.getDouble("balance");
            }

            // Return default balance from currency
            Currency currency = loadCurrency(currencyId).join();
            return currency != null ? currency.getDefaultBalance() : 0.0;
        });
    }

    @Override
    public CompletableFuture<List<CurrencyBalance>> loadPlayerBalances(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<CurrencyBalance> balances = new ArrayList<>();
            for (Document doc : balancesCollection.find(Filters.eq("playerId", playerId.toString()))) {
                balances.add(new CurrencyBalance(
                        playerId,
                        doc.getString("currencyId"),
                        doc.getDouble("balance")
                ));
            }
            return balances;
        });
    }

    @Override
    public CompletableFuture<List<CurrencyBalance>> getTopBalances(String currencyId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<CurrencyBalance> balances = new ArrayList<>();
            for (Document doc : balancesCollection.find(Filters.eq("currencyId", currencyId))
                    .sort(Sorts.descending("balance"))
                    .limit(limit)) {
                balances.add(new CurrencyBalance(
                        UUID.fromString(doc.getString("playerId")),
                        currencyId,
                        doc.getDouble("balance")
                ));
            }
            return balances;
        });
    }

    @Override
    public void close() {
    }
}