package fr.elias.oreoEssentials.modules.economy;

import java.util.List;
import java.util.UUID;

public interface EconomyService {
    double getBalance(UUID player);
    boolean deposit(UUID player, double amount);
    boolean withdraw(UUID player, double amount);

    default boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0) return false;
        if (!withdraw(from, amount)) return false;
        if (!deposit(to, amount)) {
            withdraw(to, amount);
            return false;
        }
        return true;
    }


    List<TopEntry> topBalances(int limit);


    record TopEntry(UUID uuid, String name, double balance) {}
}