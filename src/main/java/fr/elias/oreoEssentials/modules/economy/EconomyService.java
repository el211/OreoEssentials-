package fr.elias.oreoEssentials.modules.economy;

import java.util.List;
import java.util.UUID;

public interface EconomyService {
    double getBalance(UUID player);
    boolean deposit(UUID player, double amount);
    boolean withdraw(UUID player, double amount);

    /**
     * Transfers {@code amount} from {@code from} to {@code to}.
     *
     * <p><b>Atomicity requirement:</b> implementations of {@link #withdraw} MUST
     * perform the balance check and deduction atomically (e.g. via a conditional
     * {@code findOneAndUpdate} in MongoDB, or via a {@code synchronized} method in
     * YAML/file-backed services).  The two-step pattern used here — withdraw then
     * deposit — is safe only when {@code withdraw} itself cannot over-draft due to a
     * concurrent call that races between the check and the deduct.  If the deposit
     * fails after a successful withdraw, the amount is refunded to {@code from}.</p>
     */
    default boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0) return false;
        if (!withdraw(from, amount)) return false;
        if (!deposit(to, amount)) {
            deposit(from, amount);
            return false;
        }
        return true;
    }


    List<TopEntry> topBalances(int limit);


    record TopEntry(UUID uuid, String name, double balance) {}
}
