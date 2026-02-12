package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerMenu implements InventoryProvider {

    private final OreoEssentials plugin;

    public PlayerMenu(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        PlayerDirectory dir = plugin.getPlayerDirectory();

        if (dir == null) {
            displayLocalPlayers(p, c);
            return;
        }

        displayNetworkPlayers(p, c, dir);
    }

    private void displayLocalPlayers(Player p, InventoryContents c) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int row = 1, col = 1;
        for (Player target : players) {
            var skull = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("§b" + target.getName())
                    .lore("§7(LOCAL ONLY) Click for actions")
                    .build();

            if (skull.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(target);
                skull.setItemMeta(sm);
            }

            c.set(row, col, ClickableItem.of(skull, e ->
                    SmartInventory.builder()
                            .manager(plugin.getInvManager())
                            .id("modgui-player-" + target.getUniqueId())
                            .provider(new PlayerActionsMenu(plugin, target.getUniqueId()))
                            .title(Lang.color(Lang.get("modgui.player.title-local", "&8Player: %name%")
                                    .replace("%name%", target.getName())))
                            .size(6, 9)
                            .build()
                            .open(p)
            ));

            if (++col >= 8) {
                col = 1;
                row++;
                if (row >= 5) break;
            }
        }
    }

    private void displayNetworkPlayers(Player p, InventoryContents c, PlayerDirectory dir) {
        Collection<UUID> online = dir.onlinePlayers();

        List<PlayerEntry> entries = online.stream()
                .map(uuid -> createPlayerEntry(uuid, dir))
                .sorted(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        int row = 1, col = 1;
        for (PlayerEntry entry : entries) {
            if (row >= 5) break;

            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid);

            var skull = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("§b" + entry.name)
                    .lore(
                            "§7Server: §f" + entry.server,
                            "§7Click for actions."
                    )
                    .build();

            if (skull.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(op);
                skull.setItemMeta(sm);
            }

            c.set(row, col, ClickableItem.of(skull, e ->
                    SmartInventory.builder()
                            .manager(plugin.getInvManager())
                            .id("modgui-player-" + entry.uuid)
                            .provider(new PlayerActionsMenu(plugin, entry.uuid))
                            .title(Lang.color(Lang.get("modgui.player.title-network", "&8Player: %name% &7(%server%)")
                                    .replace("%name%", entry.name)
                                    .replace("%server%", entry.server)))
                            .size(6, 9)
                            .build()
                            .open(p)
            ));

            if (++col >= 8) {
                col = 1;
                row++;
            }
        }
    }

    private PlayerEntry createPlayerEntry(UUID uuid, PlayerDirectory dir) {
        String name = dir.lookupNameByUuid(uuid);
        if (name == null || name.isBlank()) {
            name = uuid.toString().substring(0, 8);
        }

        String server = dir.getCurrentOrLastServer(uuid);
        if (server == null || server.isBlank()) {
            server = "unknown";
        }

        return new PlayerEntry(uuid, name, server);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }

    private static class PlayerEntry {
        final UUID uuid;
        final String name;
        final String server;

        PlayerEntry(UUID uuid, String name, String server) {
            this.uuid = uuid;
            this.name = name;
            this.server = server;
        }
    }
}