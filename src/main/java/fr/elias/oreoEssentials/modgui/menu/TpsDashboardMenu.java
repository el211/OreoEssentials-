package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.List;

public class TpsDashboardMenu implements InventoryProvider {

    private final OreoEssentials plugin;

    public TpsDashboardMenu(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.set(0, 4, ClickableItem.empty(
                new ItemBuilder(Material.COMPARATOR)
                        .name("&cServer performance")
                        .lore("&7Live TPS, memory and world stats.")
                        .build()
        ));

        update(player, contents);
    }

    @Override
    public void update(Player viewer, InventoryContents contents) {
        displayTpsInfo(contents);
        displayMemoryInfo(contents);
        displaySystemLoad(contents);
        displayWorldStats(contents);
    }

    private void displayTpsInfo(InventoryContents contents) {
        double[] tpsArr = getTpsArray();
        String tps1 = formatTpsValue(tpsArr[0]);
        String tps5 = formatTpsValue(tpsArr[1]);
        String tps15 = formatTpsValue(tpsArr[2]);

        contents.set(1, 3, ClickableItem.empty(
                new ItemBuilder(Material.REDSTONE)
                        .name("&cTPS")
                        .lore(
                                "&7Last 1m: &f" + tps1,
                                "&7Last 5m: &f" + tps5,
                                "&7Last 15m: &f" + tps15,
                                "",
                                "&7(Values above 20.0 are clamped by Minecraft.)"
                        )
                        .build()
        ));
    }

    private void displayMemoryInfo(InventoryContents contents) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        long freeMb = rt.freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;

        contents.set(1, 5, ClickableItem.empty(
                new ItemBuilder(Material.EMERALD)
                        .name("&aMemory usage")
                        .lore(
                                "&7Used: &f" + usedMb + " MB",
                                "&7Allocated: &f" + totalMb + " MB",
                                "&7Max JVM: &f" + maxMb + " MB"
                        )
                        .build()
        ));
    }

    private void displaySystemLoad(InventoryContents contents) {
        String loadStr = getSystemLoadAverage();
        contents.set(2, 4, ClickableItem.empty(
                new ItemBuilder(Material.REPEATER)
                        .name("&6System load")
                        .lore("&7OS load (1m avg): &f" + loadStr)
                        .build()
        ));
    }

    private void displayWorldStats(InventoryContents contents) {
        List<World> worlds = Bukkit.getWorlds();
        int worldRow = 3;
        int col = 2;

        for (World w : worlds) {
            int entities = w.getEntities().size();
            int players = w.getPlayers().size();
            int chunks = w.getLoadedChunks().length;

            contents.set(worldRow, col, ClickableItem.empty(
                    new ItemBuilder(Material.MAP)
                            .name("&bWorld: &f" + w.getName())
                            .lore(
                                    "&7Players: &f" + players,
                                    "&7Entities: &f" + entities,
                                    "&7Loaded chunks: &f" + chunks
                            )
                            .build()
            ));

            col++;
            if (col > 6) {
                col = 2;
                worldRow++;
                if (worldRow >= 5) break;
            }
        }
    }

    private String formatTpsValue(double tps) {
        return tps < 0 ? "N/A" : String.format("%.2f", tps);
    }

    private double[] getTpsArray() {
        try {
            Object server = Bukkit.getServer();
            Method m = server.getClass().getMethod("getTPS");
            double[] tps = (double[]) m.invoke(server);
            if (tps == null || tps.length < 3) {
                return new double[]{-1, -1, -1};
            }
            return new double[]{tps[0], tps[1], tps[2]};
        } catch (Throwable ignored) {
            return new double[]{-1, -1, -1};
        }
    }

    private String getSystemLoadAverage() {
        try {
            OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            double load = bean.getSystemLoadAverage();
            if (load < 0) return "N/A";
            return String.format("%.2f", load);
        } catch (Throwable ignored) {
            return "N/A";
        }
    }
}