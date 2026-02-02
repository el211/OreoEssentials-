package fr.elias.oreoEssentials.modules.sellgui.manager;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.sellgui.provider.SellMenuProvider;
import fr.elias.oreoEssentials.modules.sellgui.config.SellGuiConfig;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class SellGuiManager {

    private final OreoEssentials plugin;
    private final SellGuiConfig config;
    private final InventoryManager invManager;

    public SellGuiManager(OreoEssentials plugin, InventoryManager invManager) {
        this.plugin = plugin;
        this.invManager = invManager;
        this.config = new SellGuiConfig(plugin);
        this.config.reload();
    }

    public SellGuiConfig config() {
        return config;
    }

    public void reload() {
        config.reload();
    }

    public void openSell(Player player) {
        SmartInventory.builder()
                .id("oreo-sellgui")
                .manager(invManager)
                .size(6, 9)
                .title(config.title())
                .provider(new SellMenuProvider(plugin, this))
                .listener(new InventoryListener<>(InventoryCloseEvent.class, event -> {
                    if (event.getPlayer() instanceof Player p) {
                        SellMenuProvider.returnSellItemsStatic(p);
                    }
                }))
                .build()
                .open(player);
    }
}
