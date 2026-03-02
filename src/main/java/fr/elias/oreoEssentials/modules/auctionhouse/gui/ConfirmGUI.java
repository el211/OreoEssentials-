package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.TimeFormatter;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import static fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI.*;

public class ConfirmGUI implements InventoryProvider {

    private final AuctionHouseModule module;
    private final Auction auction;

    public ConfirmGUI(AuctionHouseModule module, Auction auction) {
        this.module = module;
        this.auction = auction;
    }

    public static SmartInventory getInventory(AuctionHouseModule module, Auction auction) {
        return SmartInventory.builder()
                .id("oe_ah_confirm_" + auction.getId())
                .provider(new ConfirmGUI(module, auction))
                .manager(module.getPlugin().getInvManager())
                .size(3, 9)
                .title(c(module.getConfig().getGui().getString("confirm.title", "&e&lConfirm Purchase")))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        var cfg = module.getConfig();
        ItemStack pane = glass(cfg.guiBorder("confirm", Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i < 27; i++) contents.set(i / 9, i % 9, ClickableItem.empty(pane));

        ItemStack display = auction.getItem().clone();
        ItemMeta dm = display.getItemMeta();
        dm.setLore(List.of("",
                c("&7Seller: &e" + auction.getSellerName()),
                c("&7Price: &a" + module.formatMoney(auction.getPrice())),
                c("&7Time Left: &e" + TimeFormatter.format(auction.getTimeRemaining())),
                "", c("&6&lPurchase Confirmation"), ""));
        display.setItemMeta(dm);
        contents.set(1, 4, ClickableItem.empty(display));

        int confirmSlot = cfg.guiSlot("confirm", "confirm", 11);
        contents.set(confirmSlot / 9, confirmSlot % 9, ClickableItem.of(confirmBtn(player, cfg), e -> {
            click(player); player.closeInventory();
            if (module.purchaseAuction(player, auction.getId())) {
                try { player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f); } catch (Throwable ignored) {}
            } else {
                try { player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); } catch (Throwable ignored) {}
            }
        }));

        int cancelSlot = cfg.guiSlot("confirm", "cancel", 15);
        contents.set(cancelSlot / 9, cancelSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("confirm", "cancel", Material.RED_WOOL),
                        cfg.guiNameRaw("confirm", "cancel", "&c&l✗ CANCEL")),
                e -> { click(player); BrowseGUI.getInventory(module, auction.getCategory()).open(player); }));

        int infoSlot = cfg.guiSlot("confirm", "info", 22);
        ItemStack info = named(cfg.guiMaterial("confirm", "info", Material.PAPER),
                cfg.guiNameRaw("confirm", "info", "&e&lPurchase Information"));
        ItemMeta im = info.getItemMeta();
        im.setLore(List.of("",
                c("&7• Item will be added to your inventory"),
                c("&7• Money will be deducted from your balance"),
                c("&7• Seller will receive payment"),
                c("&7• Transaction cannot be reversed"), ""));
        info.setItemMeta(im);
        contents.set(infoSlot / 9, infoSlot % 9, ClickableItem.empty(info));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        var cfg = module.getConfig();
        int confirmSlot = cfg.guiSlot("confirm", "confirm", 11);
        contents.set(confirmSlot / 9, confirmSlot % 9, ClickableItem.of(confirmBtn(player, cfg), e -> {
            click(player); player.closeInventory();
            module.purchaseAuction(player, auction.getId());
        }));
    }

    private ItemStack confirmBtn(Player p, fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseConfig cfg) {
        ItemStack btn = new ItemStack(cfg.guiMaterial("confirm", "confirm", Material.GREEN_WOOL));
        ItemMeta m = btn.getItemMeta();
        m.setDisplayName(c(cfg.guiNameRaw("confirm", "confirm", "&a&l✔ CONFIRM PURCHASE")));
        m.setLore(List.of("",
                c("&7You will pay: &a" + module.formatMoney(auction.getPrice())),
                c("&7Your balance: &e" + module.formatMoney(module.getEconomy().getBalance(p))),
                "", c("&a&lClick to confirm!"), ""));
        btn.setItemMeta(m);
        return btn;
    }
}