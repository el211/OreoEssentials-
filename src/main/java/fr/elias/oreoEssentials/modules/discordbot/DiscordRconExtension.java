package fr.elias.oreoEssentials.modules.discordbot;

import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.services.InventoryService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;


public class DiscordRconExtension implements CommandExecutor {

    private final HomeService           homeService;
    private final PlayerEconomyDatabase economyDb;

    public DiscordRconExtension(HomeService homeService,
                                PlayerEconomyDatabase economyDb) {
        this.homeService = homeService;
        this.economyDb   = economyDb;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("ERR: players cannot run this command");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("ERR: usage: oe-discord <balance|homes|online|inventory> <uuid>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        UUID   uuid;
        try {
            uuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("ERR: invalid uuid: " + args[1]);
            return true;
        }

        switch (sub) {
            case "balance"   -> sender.sendMessage(handleBalance(uuid));
            case "homes"     -> sender.sendMessage(handleHomes(uuid));
            case "online"    -> sender.sendMessage(handleOnline(uuid));
            case "inventory" -> sender.sendMessage(handleInventory(uuid));
            default          -> sender.sendMessage("ERR: unknown sub-command: " + sub);
        }
        return true;
    }



    private String handleBalance(UUID uuid) {
        if (economyDb == null) {
            return "OK: N/A";
        }
        try {
            double balance   = economyDb.getBalance(uuid);
            String formatted = NumberFormat.getNumberInstance(Locale.US).format(balance);
            return "OK: " + formatted;
        } catch (Exception e) {
            return "ERR: could not fetch balance: " + e.getMessage();
        }
    }



    private String handleHomes(UUID uuid) {
        try {
            Map<String, HomeService.StoredHome> homes = homeService.listHomes(uuid);

            if (homes == null || homes.isEmpty()) {
                return "OK: (none)";
            }

            String list = homes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .map(e -> {
                        String server = e.getValue().getServer();
                        if (server == null || server.isBlank()) {
                            server = homeService.localServer();
                        }
                        return e.getKey() + "@" + server;
                    })
                    .collect(Collectors.joining(","));

            return "OK: " + list;
        } catch (Exception e) {
            return "ERR: could not fetch homes: " + e.getMessage();
        }
    }



    private String handleOnline(UUID uuid) {
        try {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                return "OK: ONLINE " + online.getName();
            }

            org.bukkit.OfflinePlayer op   = Bukkit.getOfflinePlayer(uuid);
            String                   name = op.getName() != null ? op.getName() : uuid.toString();
            long                     last = op.getLastPlayed(); // epoch millis, 0 = never

            if (last > 0) {
                return "OK: OFFLINE " + name + " LASTSEEN " + (last / 1000L);
            } else {
                return "OK: OFFLINE " + name + " LASTSEEN never";
            }
        } catch (Exception e) {
            return "ERR: could not fetch online status: " + e.getMessage();
        }
    }



    private String handleInventory(UUID uuid) {
        try {
            ItemStack[] contents = null;

            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                contents = online.getInventory().getContents();
            } else {
                InventoryService invSvc = Bukkit.getServicesManager()
                        .load(InventoryService.class);
                if (invSvc != null) {
                    InventoryService.Snapshot snap = invSvc.load(uuid);
                    if (snap != null) {
                        contents = snap.contents;
                    }
                }
            }

            if (contents == null || contents.length == 0) {
                return "OK: (empty)";
            }

            List<String> items = Arrays.stream(contents)
                    .filter(is -> is != null
                            && is.getType() != org.bukkit.Material.AIR
                            && is.getAmount() > 0)
                    .sorted(Comparator.comparingInt(ItemStack::getAmount).reversed())
                    .limit(12)
                    .map(is -> formatItemName(is.getType().name()) + " x" + is.getAmount())
                    .collect(Collectors.toList());

            return items.isEmpty() ? "OK: (empty)" : "OK: " + String.join(", ", items);

        } catch (Exception e) {
            return "ERR: could not fetch inventory: " + e.getMessage();
        }
    }


    private static String formatItemName(String materialName) {
        return Arrays.stream(materialName.split("_"))
                .map(w -> w.isEmpty() ? w
                        : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}