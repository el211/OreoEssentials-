// File: src/main/java/fr/elias/oreoEssentials/modgui/menu/PlayerMenu.java
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

/**
 * Player selection menu for moderation.
 *
 * ✅ VERIFIED - Uses Lang.get() + Lang.color() for GUI titles + § for GUI items
 *
 * Features:
 * - Network-wide player listing (via PlayerDirectory)
 * - Fallback to local-only when directory unavailable
 * - Shows player server location
 * - Opens PlayerActionsMenu for each player
 */
public class PlayerMenu implements InventoryProvider {

    private final OreoEssentials plugin;

    public PlayerMenu(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        PlayerDirectory dir = plugin.getPlayerDirectory();

        // ─────────────────────────────────────────
        // Fallback: directory not available → local only
        // ─────────────────────────────────────────
        if (dir == null) {
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
            return;
        }

        // ─────────────────────────────────────────
        // Network-wide listing using PlayerDirectory
        // ─────────────────────────────────────────
        Collection<UUID> online = dir.onlinePlayers();

        // Build a small DTO list with name + server for sorting / display
        class Entry {
            final UUID uuid;
            final String name;
            final String server;
            Entry(UUID uuid, String name, String server) {
                this.uuid = uuid;
                this.name = name;
                this.server = server;
            }
        }

        List<Entry> entries = online.stream()
                .map(uuid -> {
                    String name = dir.lookupNameByUuid(uuid);
                    if (name == null || name.isBlank()) {
                        name = uuid.toString().substring(0, 8);
                    }
                    String server = dir.getCurrentOrLastServer(uuid);
                    if (server == null || server.isBlank()) server = "unknown";
                    return new Entry(uuid, name, server);
                })
                .sorted(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        int row = 1, col = 1;
        for (Entry entry : entries) {
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

            UUID targetUuid = entry.uuid;
            String displayName = entry.name;
            String serverName = entry.server;

            c.set(row, col, ClickableItem.of(skull, e ->
                    SmartInventory.builder()
                            .manager(plugin.getInvManager())
                            .id("modgui-player-" + targetUuid)
                            .provider(new PlayerActionsMenu(plugin, targetUuid))
                            .title(Lang.color(Lang.get("modgui.player.title-network", "&8Player: %name% &7(%server%)")
                                    .replace("%name%", displayName)
                                    .replace("%server%", serverName)))
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

    @Override
    public void update(Player player, InventoryContents contents) {
        // No live updates needed for now
    }
}