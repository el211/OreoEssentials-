package fr.elias.oreoEssentials.modules.shop;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;


public final class ShopEconomy {

    private final OreoEssentials plugin;

    public ShopEconomy(OreoEssentials plugin) {
        this.plugin = plugin;
    }


    private Economy vault() {
        return plugin.getVaultEconomy();
    }

    public boolean isSetup() {
        return vault() != null;
    }

    public String getEconomyName() {
        Economy eco = vault();
        return eco != null ? eco.getName() : "None";
    }

    public double getBalance(Player player) {
        Economy eco = vault();
        return eco != null ? eco.getBalance(player) : 0;
    }

    public boolean has(Player player, double amount) {
        Economy eco = vault();
        return eco != null && eco.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        Economy eco = vault();
        if (eco == null || amount <= 0) return false;
        if (!eco.has(player, amount)) return false;
        eco.withdrawPlayer(player, amount);
        return true;
    }

    public void deposit(Player player, double amount) {
        Economy eco = vault();
        if (eco == null || amount <= 0) return;
        eco.depositPlayer(player, amount);
    }

    public String format(double amount) {
        Economy eco = vault();
        if (eco != null) return eco.format(amount);
        String symbol = "$";
        ShopModule active = ShopModule.getActive();
        if (active != null) symbol = active.getShopConfig().getCurrencySymbol();
        BigDecimal bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        return symbol + bd.toPlainString();
    }
}