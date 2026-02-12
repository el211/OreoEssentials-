package fr.elias.oreoEssentials.vault;

import fr.elias.oreoEssentials.OreoEssentials;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;

@SuppressWarnings("deprecation")
public class VaultEconomyProvider implements Economy {
    private final OreoEssentials plugin;
    private final String databaseType;

    public VaultEconomyProvider(OreoEssentials plugin) {
        this.plugin = plugin;

        String type = plugin.getConfig().getString("economy.type", null);

        if (type == null) {
            type = plugin.getConfig().getString("database.type", "mongodb");
            plugin.getLogger().warning(
                    "[ECON] Using legacy config key 'database.type'. " +
                            "Please move it under 'economy.type' in config.yml."
            );
        }

        this.databaseType = type.toLowerCase(Locale.ROOT);
    }


    private double getBalanceFromDatabase(UUID playerUUID) {
        return plugin.getDatabase().getBalance(playerUUID);
    }

    private void setBalanceInDatabase(UUID playerUUID, String name, double amount) {
        plugin.getDatabase().setBalance(playerUUID, safeName(name, playerUUID), sanitize(amount));
    }

    private static boolean invalidAmount(double amount) {
        return Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0.0;
    }

    private static double sanitize(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String safeName(String name, UUID uuid) {
        if (name != null && !name.isEmpty()) return name;
        String b = Bukkit.getOfflinePlayer(uuid).getName();
        return (b != null && !b.isEmpty()) ? b : uuid.toString();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "OreoEssentials";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return String.format("$%.2f", amount);
    }

    @Override
    public String currencyNamePlural() {
        return "Dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "Dollar";
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return getBalanceFromDatabase(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName) {
        UUID id = plugin.getOfflinePlayerCache().getId(playerName);
        if (id == null) {
            var online = Bukkit.getPlayerExact(playerName);
            if (online != null) id = online.getUniqueId();
        }
        if (id != null) return getBalanceFromDatabase(id);

        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        if (op.getName() == null && !op.hasPlayedBefore()) return 0.0;
        return getBalance(op);
    }


    @Override
    public double getBalance(String playerName, String worldName) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        if (invalidAmount(amount)) return false;
        return getBalance(playerName) + 1e-9 >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (invalidAmount(amount)) return false;
        return getBalance(player) + 1e-9 >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        UUID playerUUID = player.getUniqueId();
        double balance = getBalanceFromDatabase(playerUUID);

        if (invalidAmount(amount)) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Invalid amount");
        }
        if (balance + 1e-9 < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }

        setBalanceInDatabase(playerUUID, player.getName(), balance - amount);
        double newBal = balance - amount;
        return new EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        UUID playerUUID = player.getUniqueId();
        double balance = getBalanceFromDatabase(playerUUID);

        if (invalidAmount(amount)) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Invalid amount");
        }

        setBalanceInDatabase(playerUUID, player.getName(), balance + amount);
        double newBal = balance + amount;
        return new EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID playerId = plugin.getOfflinePlayerCache().getId(playerName);

        if (playerId != null) {
            double balance = getBalanceFromDatabase(playerId);
            if (invalidAmount(amount)) {
                return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Invalid amount");
            }
            setBalanceInDatabase(playerId, playerName, balance + amount);
            return new EconomyResponse(amount, balance + amount, EconomyResponse.ResponseType.SUCCESS, "");
        }

        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not enabled");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not enabled");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not enabled");
    }

    @Override
    public EconomyResponse bankBalance(String bank) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not enabled");
    }

    @Override
    public EconomyResponse bankHas(String bank, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not enabled");
    }

    @Override
    public EconomyResponse bankDeposit(String bank, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not enabled");
    }

    @Override
    public EconomyResponse bankWithdraw(String bank, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support is not enabled");
    }

    @Override
    public EconomyResponse isBankOwner(String bank, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "");
    }

    @Override
    public EconomyResponse isBankOwner(String bank, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "");
    }

    @Override
    public EconomyResponse isBankMember(String bank, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "");
    }

    @Override
    public EconomyResponse isBankMember(String bank, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "");
    }
}
