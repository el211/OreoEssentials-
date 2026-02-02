package fr.elias.oreoEssentials.modules.currency.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public class CurrencySendCommand implements OreoCommand {

    private final OreoEssentials plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public CurrencySendCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "currencysend";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("csend", "sendcurrency", "paycurrency");
    }

    @Override
    public String permission() {
        return "oreo.currency.send";
    }

    @Override
    public String usage() {
        return "<player> <currency> <amount>";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final Player from = (Player) sender;

        if (args.length < 3) {
            from.sendMessage("§cUsage: /currencysend <player> <currency> <amount>");
            from.sendMessage("§7Example: §e/csend Nabil gems 100");
            return true;
        }

        final int cooldownSeconds = plugin.getCurrencyConfig().getTransferCooldown();
        if (cooldownSeconds > 0 && !from.hasPermission("oreo.currency.send.bypass")) {
            long lastUse = cooldowns.getOrDefault(from.getUniqueId(), 0L);
            long remainingMs = (lastUse + (cooldownSeconds * 1000L)) - System.currentTimeMillis();
            if (remainingMs > 0) {
                from.sendMessage("§c✖ Please wait " + (remainingMs / 1000L) + " seconds before sending again");
                return true;
            }
        }


        final String targetInput = args[0];

        final String amountRaw = args[args.length - 1];
        final double amount = parsePositive(amountRaw);

        if (amount <= 0) {
            from.sendMessage("§c✖ Invalid amount: " + amountRaw);
            return true;
        }



        StringBuilder currencyBuilder = new StringBuilder();
        for (int i = 1; i < args.length - 1; i++) {
            if (i > 1) currencyBuilder.append(" ");
            currencyBuilder.append(args[i]);
        }

        final String currencyId = (args.length == 3 ? args[1] : currencyBuilder.toString())
                .toLowerCase(Locale.ROOT).trim();

        plugin.getLogger().info("[CurrencySend] Player: " + targetInput + ", Currency: '" + currencyId + "', Amount: " + amount);

        final Currency currency = plugin.getCurrencyService().getCurrency(currencyId);
        if (currency == null) {
            from.sendMessage("§c✖ Currency not found: " + currencyId);
            from.sendMessage("§7Use §e/oecurrency list §7to see all currencies");
            return true;
        }
        if (!currency.isTradeable()) {
            from.sendMessage("§c✖ This currency is not tradeable!");
            return true;
        }

        final double minAmount = plugin.getCurrencyConfig().getMinTransferAmount();
        final double maxAmount = plugin.getCurrencyConfig().getMaxTransferAmount();

        if (amount < minAmount) {
            from.sendMessage("§c✖ Minimum transfer amount: " + currency.format(minAmount));
            return true;
        }
        if (amount > maxAmount && !from.hasPermission("oreo.currency.send.unlimited")) {
            from.sendMessage("§c✖ Maximum transfer amount: " + currency.format(maxAmount));
            return true;
        }

        resolveTarget(targetInput).thenAccept(resolved -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!from.isOnline()) return;

            if (resolved == null || resolved.uuid == null) {
                from.sendMessage("§c✖ Player not found: " + targetInput);
                return;
            }

            if (resolved.uuid.equals(from.getUniqueId())) {
                from.sendMessage("§c✖ You cannot send currency to yourself!");
                return;
            }

            executeTransfer(
                    from,
                    resolved.uuid,
                    resolved.displayName != null && !resolved.displayName.isBlank() ? resolved.displayName : targetInput,
                    currencyId,
                    currency,
                    amount,
                    cooldownSeconds
            );
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!from.isOnline()) return;
                from.sendMessage("§c✖ Failed to resolve player: " + ex.getMessage());
            });
            return null;
        });

        return true;
    }


    private void executeTransfer(Player sender,
                                 UUID targetUuid,
                                 String targetDisplayName,
                                 String currencyId,
                                 Currency currency,
                                 double amount,
                                 int cooldownSeconds) {

        plugin.getCurrencyService().transfer(
                sender.getUniqueId(),
                targetUuid,
                currencyId,
                amount
        ).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!sender.isOnline()) return;

            if (!success) {
                sender.sendMessage("§c✖ Insufficient balance! You need " + currency.format(amount));
                return;
            }

            String formattedAmount = currency.format(amount);

            sender.sendMessage("§a✔ Sent " + formattedAmount + " to §f" + targetDisplayName);

            // receiver msg (local if present, else GLOBAL remote packet)
            String msg = "§a✔ Received " + formattedAmount + " from §f" + sender.getName();
            sendToTargetAnywhere(targetUuid, msg);

            if (cooldownSeconds > 0 && !sender.hasPermission("oreo.currency.send.bypass")) {
                cooldowns.put(sender.getUniqueId(), System.currentTimeMillis());
            }

            plugin.getLogger().info("[Currency] " + sender.getName() + " sent " +
                    formattedAmount + " to " + targetDisplayName + " (" + targetUuid + ")");
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender.isOnline()) sender.sendMessage("§c✖ Transfer failed: " + ex.getMessage());
            });
            return null;
        });
    }

    /**
     * If target is on this server => direct message
     * else => GLOBAL SendRemoteMessagePacket so the correct shard delivers it (no need lookupCurrentServer).
     */
    private void sendToTargetAnywhere(UUID targetUuid, String msg) {
        Player localTarget = Bukkit.getPlayer(targetUuid);
        if (localTarget != null) {
            localTarget.sendMessage(msg);
            return;
        }

        try {
            var pm = plugin.getPacketManager();
            if (pm == null || !pm.isInitialized()) return;

            pm.sendPacket(
                    fr.elias.oreoEssentials.rabbitmq.PacketChannels.GLOBAL,
                    new fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket(targetUuid, msg)
            );
        } catch (Throwable ignored) {}
    }


    private static final class TargetResolved {
        final UUID uuid;
        final String displayName;

        TargetResolved(UUID uuid, String displayName) {
            this.uuid = uuid;
            this.displayName = displayName;
        }
    }

    private CompletableFuture<TargetResolved> resolveTarget(String nameOrUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (nameOrUuid == null || nameOrUuid.isBlank()) return null;

            try {
                UUID id = UUID.fromString(nameOrUuid);
                return new TargetResolved(id, nameOrUuid);
            } catch (IllegalArgumentException ignored) {}

            Player local = Bukkit.getPlayerExact(nameOrUuid);
            if (local != null) {
                return new TargetResolved(local.getUniqueId(), local.getName());
            }

            try {
                var cache = plugin.getOfflinePlayerCache();
                if (cache != null) {
                    UUID cached = cache.getId(nameOrUuid);
                    if (cached != null) {
                        return new TargetResolved(cached, nameOrUuid);
                    }
                }
            } catch (Throwable ignored) {}

            PlayerDirectory dir;
            try {
                dir = plugin.getPlayerDirectory();
            } catch (Throwable t) {
                dir = null;
            }

            if (dir != null) {
                try {
                    UUID id = dir.lookupUuidByName(nameOrUuid);
                    if (id != null) {
                        String dn;
                        try {
                            dn = dir.lookupNameByUuid(id);
                        } catch (Throwable ignored) {
                            dn = nameOrUuid;
                        }
                        if (dn == null || dn.isBlank()) dn = nameOrUuid;
                        return new TargetResolved(id, dn);
                    }
                } catch (Throwable ignored) {}
            }

            return null;
        });
    }


    private double parsePositive(String rawIn) {
        try {
            String raw = rawIn.replace(",", "").trim();
            if (raw.startsWith("-") || raw.toLowerCase(Locale.ROOT).contains("e")) return -1;
            double parsed = Double.parseDouble(raw);
            return parsed > 0 ? parsed : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }
}