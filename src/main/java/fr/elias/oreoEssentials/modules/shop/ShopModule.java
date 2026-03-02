package fr.elias.oreoEssentials.modules.shop;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.shop.commands.SellCommand;
import fr.elias.oreoEssentials.modules.shop.commands.ShopCommand;
import fr.elias.oreoEssentials.modules.shop.gui.AmountSelectionGUI;
import fr.elias.oreoEssentials.modules.shop.gui.MainMenuGUI;
import fr.elias.oreoEssentials.modules.shop.gui.ShopGUI;
import fr.elias.oreoEssentials.modules.shop.gui.TransactionProcessor;
import fr.elias.oreoEssentials.modules.shop.hooks.ItemsAdderHook;
import fr.elias.oreoEssentials.modules.shop.hooks.NexoHook;
import fr.elias.oreoEssentials.modules.shop.listeners.AntiDupeListener;
import fr.elias.oreoEssentials.modules.shop.logger.TransactionLogger;
import fr.elias.oreoEssentials.modules.shop.managers.DynamicPricingManager;
import fr.elias.oreoEssentials.modules.shop.managers.PriceModifierManager;
import fr.elias.oreoEssentials.modules.shop.managers.ShopManager;


public final class ShopModule {

    private final OreoEssentials plugin;

    private ShopConfig            shopConfig;
    private ShopManager           shopManager;
    private ShopEconomy           economy;
    private PriceModifierManager  priceModifierManager;
    private DynamicPricingManager dynamicPricingManager;
    private TransactionLogger     transactionLogger;

    private ItemsAdderHook itemsAdderHook;
    private NexoHook       nexoHook;

    private MainMenuGUI          mainMenuGUI;
    private ShopGUI              shopGUI;
    private AmountSelectionGUI   amountSelectionGUI;
    private TransactionProcessor transactionProcessor;


    private boolean enabled = false;

    private static ShopModule activeInstance;
    public  static ShopModule getActive() { return activeInstance; }


    public ShopModule(OreoEssentials plugin) {
        this.plugin = plugin;
        load();
    }


    private void load() {
        this.shopConfig = new ShopConfig(plugin);

        if (!shopConfig.isEnabled()) {
            plugin.getLogger().info("[Shop] Disabled by config.");
            enabled = false;
            return;
        }

        this.itemsAdderHook = new ItemsAdderHook(plugin);
        this.nexoHook       = new NexoHook(plugin);

        this.economy              = new ShopEconomy(plugin);
        this.priceModifierManager = new PriceModifierManager(this);
        this.dynamicPricingManager = new DynamicPricingManager(this);
        this.transactionLogger    = new TransactionLogger(this);
        this.shopManager          = new ShopManager(this);

        this.mainMenuGUI          = new MainMenuGUI(this);
        this.shopGUI              = new ShopGUI(this);
        this.amountSelectionGUI   = new AmountSelectionGUI(this);
        this.transactionProcessor = new TransactionProcessor(this);

        plugin.getServer().getPluginManager()
                .registerEvents(new AntiDupeListener(this), plugin);

        registerCommands();

        enabled = true;
        activeInstance = this;

        plugin.getLogger().info("[Shop] Module loaded — " +
                shopManager.getShopCount() + " shop(s), economy=" +
                economy.getEconomyName() + ", dynamic-pricing=" +
                (dynamicPricingManager.isEnabled() ? "on" : "off"));
    }


    public void reload() {
        shutdown();
        load();
    }


    public void shutdown() {
        if (dynamicPricingManager != null) dynamicPricingManager.shutdown();
        if (transactionLogger     != null) transactionLogger.close();
        activeInstance = null;
        enabled = false;
    }


    private void registerCommands() {
        ShopCommand shopCmd = new ShopCommand(this);
        if (plugin.getCommand("shop") != null) {
            plugin.getCommand("shop").setExecutor(shopCmd);
            plugin.getCommand("shop").setTabCompleter(shopCmd);
        } else {
            plugin.getLogger().warning("[Shop] Command 'shop' not found in plugin.yml.");
        }

        SellCommand sellCmd = new SellCommand(this);
        if (plugin.getCommand("sell") != null) {
            plugin.getCommand("sell").setExecutor(sellCmd);
            plugin.getCommand("sell").setTabCompleter(sellCmd);
        } else {
            plugin.getLogger().warning("[Shop] Command 'sell' not found in plugin.yml.");
        }
    }


    public boolean isEnabled()                              { return enabled; }
    public OreoEssentials getPlugin()                       { return plugin; }
    public ShopConfig getShopConfig()                       { return shopConfig; }
    public ShopEconomy getEconomy()                         { return economy; }
    public ShopManager getShopManager()                     { return shopManager; }
    public PriceModifierManager getPriceModifierManager()   { return priceModifierManager; }
    public DynamicPricingManager getDynamicPricingManager() { return dynamicPricingManager; }
    public TransactionLogger getTransactionLogger()         { return transactionLogger; }
    public MainMenuGUI getMainMenuGUI()                     { return mainMenuGUI; }
    public ShopGUI getShopGUI()                             { return shopGUI; }
    public AmountSelectionGUI getAmountSelectionGUI()       { return amountSelectionGUI; }
    public TransactionProcessor getTransactionProcessor()   { return transactionProcessor; }



    public ItemsAdderHook getItemsAdderHook() { return itemsAdderHook; }
    public NexoHook       getNexoHook()       { return nexoHook; }

    public boolean hasItemsAdder() {
        return itemsAdderHook != null && itemsAdderHook.isEnabled();
    }

    public boolean hasNexo() {
        return nexoHook != null && nexoHook.isEnabled();
    }
}