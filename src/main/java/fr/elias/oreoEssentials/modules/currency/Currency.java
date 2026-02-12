package fr.elias.oreoEssentials.modules.currency;

import java.util.Objects;

/**
 * Represents a custom currency in OreoEssentials
 */
public class Currency {

    private final String id;
    private final String name;
    private final String symbol;
    private final String displayName;
    private final double defaultBalance;
    private final boolean tradeable;
    private final boolean crossServer;
    private final boolean allowNegative; // ← NEW FIELD

    private Currency(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.symbol = builder.symbol;
        this.displayName = builder.displayName;
        this.defaultBalance = builder.defaultBalance;
        this.tradeable = builder.tradeable;
        this.crossServer = builder.crossServer;
        this.allowNegative = builder.allowNegative; // ← NEW
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public double getDefaultBalance() { return defaultBalance; }
    public boolean isTradeable() { return tradeable; }
    public boolean isCrossServer() { return crossServer; }
    public boolean isAllowNegative() { return allowNegative; } // ← NEW

    /**
     * Format a currency amount with the currency symbol
     * IMPORTANT: Space between symbol and amount prevents "410" concatenation bug
     *
     * @param amount The amount to format
     * @return Formatted string (e.g., "💎 10.00" or "$ 25.50")
     */
    public String format(double amount) {
        return symbol + " " + String.format("%.2f", amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Currency)) return false;
        Currency currency = (Currency) o;
        return Objects.equals(id, currency.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Currency{id='" + id + "', name='" + name + "', symbol='" + symbol + "'}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String symbol = "$";
        private String displayName;
        private double defaultBalance = 0.0;
        private boolean tradeable = true;
        private boolean crossServer = false;
        private boolean allowNegative = false; // ← NEW

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder defaultBalance(double defaultBalance) {
            this.defaultBalance = defaultBalance;
            return this;
        }

        public Builder tradeable(boolean tradeable) {
            this.tradeable = tradeable;
            return this;
        }

        public Builder crossServer(boolean crossServer) {
            this.crossServer = crossServer;
            return this;
        }

        public Builder allowNegative(boolean allowNegative) { // ← NEW
            this.allowNegative = allowNegative;
            return this;
        }

        public Currency build() {
            Objects.requireNonNull(id, "Currency ID cannot be null");
            Objects.requireNonNull(name, "Currency name cannot be null");

            if (displayName == null) {
                displayName = name;
            }

            return new Currency(this);
        }
    }
}