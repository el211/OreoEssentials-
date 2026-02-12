package fr.elias.oreoEssentials.modules.currency;

import java.util.UUID;

/**
 * Represents a player's balance in a specific currency
 */
public class CurrencyBalance {

    private final UUID playerId;
    private final String currencyId;
    private double balance;

    public CurrencyBalance(UUID playerId, String currencyId, double balance) {
        this.playerId = playerId;
        this.currencyId = currencyId;
        this.balance = Math.max(0, balance); // Never negative
    }

    public UUID getPlayerId() { return playerId; }
    public String getCurrencyId() { return currencyId; }
    public double getBalance() { return balance; }

    public void setBalance(double balance) {
        this.balance = Math.max(0, balance);
    }

    public boolean deposit(double amount) {
        if (amount <= 0) return false;
        balance += amount;
        return true;
    }

    public boolean withdraw(double amount) {
        if (amount <= 0 || balance < amount) return false;
        balance -= amount;
        return true;
    }

    public boolean has(double amount) {
        return balance >= amount;
    }

    @Override
    public String toString() {
        return "CurrencyBalance{player=" + playerId + ", currency=" + currencyId + ", balance=" + balance + "}";
    }
}