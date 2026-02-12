package fr.elias.oreoEssentials.modules.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vault Economy Provider - Bridges OreoEssentials economy to Vault API
 * This allows other plugins to use your economy through Vault
 */
public class OreoVaultProvider implements Economy {

    private final Plugin plugin;
    private final EconomyService service;
    private final String currencyName;
    private final String currencyPlural;

    public OreoVaultProvider(Plugin plugin, EconomyService service) {
        this.plugin = plugin;
        this.service = service;

        this.currencyName = plugin.getConfig().getString("economy.currency.name", "Dollar");
        this.currencyPlural = plugin.getConfig().getString("economy.currency.plural", "Dollars");
    }

    @Override
    public boolean isEnabled() {
        return service != null;
    }

    @Override
    public String getName() {
        return "OreoEssentials Economy";
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
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return "$" + df.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return currencyPlural;
    }

    @Override
    public String currencyNameSingular() {
        return currencyName;
    }


    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && player.getUniqueId() != null;
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
    public double getBalance(String playerName) {
        OfflinePlayer player = resolvePlayer(playerName);
        return player != null ? service.getBalance(player.getUniqueId()) : 0.0;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) return 0.0;
        return service.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }
    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }
    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }
    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
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
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = resolvePlayer(playerName);
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        }
        return depositPlayer(player, amount);
    }
    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null || player.getUniqueId() == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid player");
        }

        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }

        UUID uuid = player.getUniqueId();
        boolean success = service.deposit(uuid, amount);

        if (success) {
            double newBalance = service.getBalance(uuid);
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Deposit failed");
        }
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
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = resolvePlayer(playerName);
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
        }
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null || player.getUniqueId() == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid player");
        }

        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }

        UUID uuid = player.getUniqueId();
        double balance = service.getBalance(uuid);

        if (balance < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }

        boolean success = service.withdraw(uuid, amount);

        if (success) {
            double newBalance = service.getBalance(uuid);
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Withdrawal failed");
        }
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
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
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
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }


    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String playerName) {
        for (OfflinePlayer player : Bukkit.getOnlinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return Bukkit.getOfflinePlayer(playerName);
    }
}