package fr.elias.oreoEssentials.modules.shop.logger;

import fr.elias.oreoEssentials.modules.shop.ShopModule;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public final class TransactionLogger {

    private final ShopModule module;
    private final Logger log;
    private PrintWriter writer;

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public TransactionLogger(ShopModule module) {
        this.module = module;
        this.log    = module.getPlugin().getLogger();
        open();
    }


    private void open() {
        if (!module.getShopConfig().isLoggingEnabled()) return;
        try {
            File f = new File(module.getPlugin().getDataFolder(),
                    module.getShopConfig().getLogFile());
            f.getParentFile().mkdirs();
            writer = new PrintWriter(new FileWriter(f, true));
        } catch (IOException e) {
            log.warning("[Shop] Could not open transaction log: " + e.getMessage());
        }
    }

    public void logTransaction(String player, String action, int amount,
                               String item, double price, String economy) {
        if (!module.getShopConfig().isLoggingEnabled() || writer == null) return;

        String entry = module.getShopConfig().getLogFormat()
                .replace("{date}",    FMT.format(new Date()))
                .replace("{player}",  player)
                .replace("{action}",  action)
                .replace("{amount}",  String.valueOf(amount))
                .replace("{item}",    item)
                .replace("{price}",   module.getEconomy().format(price))
                .replace("{economy}", economy);

        writer.println(entry);
        writer.flush();
    }

    public void reload() { close(); open(); }

    public void close() {
        if (writer != null) { writer.close(); writer = null; }
    }
}