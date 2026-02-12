package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import fr.elias.oreoEssentials.util.Lang;
import java.util.List;

public class MainMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;

    public MainMenu(OreoEssentials plugin, ModGuiService svc) {
        this.plugin = plugin;
        this.svc    = svc;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        c.set(2, 2, ClickableItem.of(
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name(Lang.get("modgui.main.player.name", "&ePlayer moderation"))
                        .lore(Lang.getList("modgui.main.player.lore").toArray(new String[0]))

                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())              // REQUIRED
                        .id("modgui-player")
                        .provider(new PlayerMenu(plugin))
                        .title(Lang.color(Lang.get("modgui.main.titles.player", "&8Player moderation")))
                        .size(6, 9)
                        .build()
                        .open(p)
        )) ;


        c.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name(Lang.get("modgui.main.world.name", "&aWorld moderation"))
                        .lore(Lang.getList("modgui.main.world.lore").toArray(new String[0]))

                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())              // REQUIRED
                        .id("modgui-worlds")
                        .provider(new WorldListMenu(plugin, svc))
                        .title(Lang.color(Lang.get("modgui.main.titles.world", "&8Select World")))
                        .size(6, 9)
                        .build()
                        .open(p)
        )) ;


        c.set(2, 6, ClickableItem.of(
                new ItemBuilder(Material.REDSTONE)
                        .name(Lang.get("modgui.main.server.name", "&cServer moderation"))
                        .lore(Lang.getList("modgui.main.server.lore").toArray(new String[0]))

                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .id("modgui-server")
                        .provider(new ServerMenu(plugin, svc))
                        .title(Lang.color(Lang.get("modgui.main.titles.server", "&8Server moderation")))
                        .size(6, 9)
                        .build()
                        .open(p)
        )) ;

    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}
