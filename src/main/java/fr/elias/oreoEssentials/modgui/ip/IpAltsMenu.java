// package: fr.elias.oreoEssentials.modgui.ip
package fr.elias.oreoEssentials.modgui.ip;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class IpAltsMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final IpTracker tracker;
    private final UUID target;

    public IpAltsMenu(OreoEssentials plugin, IpTracker tracker, UUID target) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.target = target;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        String ip = tracker.getLastIp(target);
        c.set(0, 4, ClickableItem.empty(
                new ItemBuilder(Material.MAP)
                        .name("&fLast IP: &e" + ip)
                        .build()
        ));

        List<UUID> alts = tracker.getAlts(target);
        int row = 2, col = 1;
        for (UUID u : alts) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            String name = op.getName() == null ? u.toString().substring(0, 8) : op.getName();

            c.set(row, col, ClickableItem.empty(
                    new ItemBuilder(Material.PLAYER_HEAD)
                            .name("&b" + name)
                            .lore("&7UUID: " + u.toString())
                            .build()
            ));
            if (++col >= 8) { col = 1; row++; if (row >= 5) break; }
        }
    }

    @Override public void update(Player player, InventoryContents contents) {}
}
