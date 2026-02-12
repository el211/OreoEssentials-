// File: src/main/java/fr/elias/oreoEssentials/modgui/inspect/PlayerInspectMenu.java
package fr.elias.oreoEssentials.modgui.inspect;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;


public class PlayerInspectMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final UUID targetId;

    public PlayerInspectMenu(OreoEssentials plugin, UUID targetId) {
        this.plugin = plugin;
        this.targetId = targetId;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        // Updated live in update()
    }

    @Override
    public void update(Player viewer, InventoryContents c) {
        PlayerDirectory dir = plugin.getPlayerDirectory();

        String name = null;
        String serverName = null;

        if (dir != null) {
            name = dir.lookupNameByUuid(targetId);
            serverName = dir.getCurrentOrLastServer(targetId);
        }

        Player local = Bukkit.getPlayer(targetId);
        boolean isLocal = local != null && local.isOnline();

        // Fallbacks
        if (name == null && isLocal) {
            name = local.getName();
        }
        if (serverName == null && isLocal) {
            try {
                serverName = Bukkit.getServer().getName();
            } catch (Throwable ignored) {
                serverName = "local";
            }
        }

        // Completely offline (no local, no known server) → close
        if (!isLocal && (serverName == null || serverName.isBlank())) {
            viewer.closeInventory();
            Lang.send(viewer, "playerinspect.target-offline",
                    "<red>Target player is offline.</red>",
                    Map.of());
            return;
        }

        if (name == null) {
            name = targetId.toString();
        }
        if (serverName == null || serverName.isBlank()) {
            serverName = "N/A";
        }

        // Effectively-final copy for lambdas
        final String targetName = name;

        // ----- Stats (only if on this Spigot) -----
        double health = isLocal ? local.getHealth() : -1;
        double max    = isLocal ? local.getMaxHealth() : -1;
        int food      = isLocal ? local.getFoodLevel() : -1;
        String gm     = isLocal ? local.getGameMode().name() : "REMOTE";

        int ping = -1;
        if (isLocal) {
            try { ping = local.getPing(); } catch (Throwable ignored) {}
        }

        double tps = -1;
        if (isLocal) {
            try {
                Object server = Bukkit.getServer();
                Method m = server.getClass().getMethod("getTPS");
                double[] arr = (double[]) m.invoke(server);
                if (arr.length > 0) tps = arr[0];
            } catch (Throwable ignored) {
            }
        }

        // ----- Lore lines from lang.yml -----
        String healthLine = isLocal
                ? Lang.get("playerinspect.lore.health", "&7Health: &c%current%&7/&c%max%")
                .replace("%current%", String.format("%.1f", health))
                .replace("%max%", String.format("%.1f", max))
                : Lang.get("playerinspect.lore.health-na", "&7Health: &fN/A (&8remote&f)");

        String foodLine = isLocal
                ? Lang.get("playerinspect.lore.food", "&7Food: &e%food%")
                .replace("%food%", Integer.toString(food))
                : Lang.get("playerinspect.lore.food-na", "&7Food: &fN/A (&8remote&f)");

        String gmLine = Lang.get("playerinspect.lore.gamemode", "&7Gamemode: &f%gm%")
                .replace("%gm%", gm);

        String worldLine = Lang.get("playerinspect.lore.world", "&7World/Server: &a%world%")
                .replace("%world%", isLocal ? local.getWorld().getName() : serverName);

        String locLine = isLocal
                ? Lang.get("playerinspect.lore.location", "&7Location: &f%x% %y% %z%")
                .replace("%x%", Integer.toString(local.getLocation().getBlockX()))
                .replace("%y%", Integer.toString(local.getLocation().getBlockY()))
                .replace("%z%", Integer.toString(local.getLocation().getBlockZ()))
                : Lang.get("playerinspect.lore.location-na", "&7Location: &fN/A (&8remote&f)");

        String pingLine = isLocal
                ? Lang.get("playerinspect.lore.ping", "&7Ping: &f%ping%")
                .replace("%ping%", (ping == -1 ? "N/A" : ping + "ms"))
                : Lang.get("playerinspect.lore.ping-na", "&7Ping: &fN/A (&8remote&f)");

        String tpsLine = isLocal
                ? Lang.get("playerinspect.lore.tps", "&7TPS: &f%tps%")
                .replace("%tps%", tps == -1 ? "N/A" : String.format("%.2f", tps))
                : Lang.get("playerinspect.lore.tps-na", "&7TPS: &fN/A (&8remote&f)");

        // ----- Head / info item -----
        c.set(2, 4, ClickableItem.empty(
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name(
                                Lang.get("playerinspect.head-name", "&b%player% &7(&e%server%&7)")
                                        .replace("%player%", targetName)
                                        .replace("%server%", serverName)
                        )
                        .lore(
                                healthLine,
                                foodLine,
                                gmLine,
                                worldLine,
                                locLine,
                                pingLine,
                                tpsLine
                        )
                        .build()
        ));

        // ----- Inventory (invsee) button -----
        c.set(3, 3, ClickableItem.of(
                new ItemBuilder(Material.CHEST)
                        .name(Lang.get("playerinspect.invsee-name", "&bView inventory"))
                        .lore(
                                Lang.get("playerinspect.invsee-lore1", "&7Click to open"),
                                Lang.get("playerinspect.invsee-lore2", "&7the inventory of &e%player%")
                                        .replace("%player%", targetName)
                        )
                        .build(),
                e -> {
                    if (!viewer.hasPermission("oreo.mod.invsee")) {
                        Lang.send(viewer, "playerinspect.no-permission-invsee",
                                "<red>You don't have permission to view inventories.</red>",
                                Map.of());
                        return;
                    }

                    viewer.closeInventory();
                    viewer.performCommand("invsee " + targetName);
                }
        ));
    }
}