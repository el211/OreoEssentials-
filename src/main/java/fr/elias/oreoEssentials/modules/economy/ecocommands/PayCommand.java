package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import fr.elias.oreoEssentials.util.Lang;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public class PayCommand implements OreoCommand {

    @Override public String name() { return "pay"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.pay"; }
    @Override public String usage() { return "<player> <amount>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendMessage(Lang.msg("economy.pay.usage", null));
            return true;
        }
        if (args.length < 2) {
            from.sendMessage(Lang.msg("economy.pay.usage", from));
            return true;
        }

        final OreoEssentials plugin = OreoEssentials.get();

        Economy econ = plugin.getVaultEconomy();
        if (econ == null) {
            from.sendMessage(Lang.msg("economy.errors.no-economy", from));
            return true;
        }

        OfflinePlayer target = resolveOffline(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            from.sendMessage(Lang.msg("economy.errors.player-not-found", Map.of("target", args[0]), from));
            return true;
        }
        if (from.getUniqueId().equals(target.getUniqueId())) {
            from.sendMessage(Lang.msg("economy.pay.self", from));
            return true;
        }

        Double amount = parseAmount(args[1]);
        if (amount == null || amount <= 0) {
            from.sendMessage(Lang.msg("economy.errors.not-a-number", from));
            return true;
        }

        double balance = econ.getBalance(from);
        if (balance + 1e-9 < amount) {
            from.sendMessage(Lang.msg("economy.pay.fail-insufficient",
                    Map.of("amount_formatted", fmt(amount), "currency_symbol", currencySymbol()), from));
            return true;
        }

        try { if (!econ.hasAccount(target)) econ.createPlayerAccount(target); } catch (Throwable ignored) {}

        EconomyResponse w = econ.withdrawPlayer(from, amount);
        if (w == null || w.type != EconomyResponse.ResponseType.SUCCESS) {
            from.sendMessage(Lang.msg("economy.errors.no-economy", from));
            return true;
        }

        EconomyResponse d = econ.depositPlayer(target, amount);
        if (d == null || d.type != EconomyResponse.ResponseType.SUCCESS) {
            try { econ.depositPlayer(from, amount); } catch (Throwable ignored) {} // refund best-effort
            from.sendMessage(Lang.msg("economy.errors.no-economy", from));
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[0];

        from.sendMessage(Lang.msg("economy.pay.success-sender",
                Map.of("target", targetName, "amount_formatted", fmt(amount), "currency_symbol", currencySymbol()),
                from));

        // receiver message text (no player context needed)
        String receiverMsg = Lang.msg("economy.pay.success-receiver",
                Map.of("player", from.getName(), "amount_formatted", fmt(amount), "currency_symbol", currencySymbol()),
                null
        );

        // If target is on this server -> send now
        Player local = Bukkit.getPlayer(target.getUniqueId());
        if (local != null) {
            local.sendMessage(receiverMsg);
        } else {
            // Cross-server -> send GLOBAL, any shard hosting the player will deliver it
            try {
                var pm = plugin.getPacketManager();
                if (pm != null && pm.isInitialized()) {
                    pm.sendPacket(
                            fr.elias.oreoEssentials.rabbitmq.PacketChannels.GLOBAL,
                            new fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket(
                                    target.getUniqueId(),
                                    receiverMsg
                            )
                    );

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[PAY][DBG] GLOBAL remote message queued for " + target.getUniqueId());
                    }
                } else {
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[PAY][DBG] Remote message skipped (PacketManager not available).");
                    }
                }
            } catch (Throwable t) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[PAY][DBG] Remote message failed: " + t.getMessage());
                }
            }
        }

        OfflinePlayerCache cache = plugin.getOfflinePlayerCache();
        if (cache != null && target.getUniqueId() != null && targetName != null) {
            cache.add(targetName, target.getUniqueId());
        }

        return true;
    }

    private OfflinePlayer resolveOffline(String nameOrUuid) {
        Player p = Bukkit.getPlayerExact(nameOrUuid);
        if (p != null) return p;

        OfflinePlayerCache cache = OreoEssentials.get().getOfflinePlayerCache();
        if (cache != null) {
            UUID id = cache.getId(nameOrUuid);
            if (id != null) return Bukkit.getOfflinePlayer(id);
        }

        try { return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid)); }
        catch (IllegalArgumentException ignored) {}

        return Bukkit.getOfflinePlayer(nameOrUuid);
    }

    private Double parseAmount(String rawIn) {
        try {
            String raw = rawIn.replace(",", "").trim();
            if (raw.startsWith("-") || raw.toLowerCase(Locale.ROOT).contains("e")) return null;
            int decimals = (int) Math.round(Lang.getDouble("economy.format.decimals", 2.0));
            return new BigDecimal(raw).setScale(decimals, RoundingMode.DOWN).doubleValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String fmt(double v) {
        int decimals = (int) Math.round(Lang.getDouble("economy.format.decimals", 2.0));
        String th = Lang.get("economy.format.thousands-separator", ",");
        String dec = Lang.get("economy.format.decimal-separator", ".");
        String pattern = "#,##0" + (decimals > 0 ? "." + "0".repeat(decimals) : "");
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        if (!th.isEmpty()) sym.setGroupingSeparator(th.charAt(0));
        if (!dec.isEmpty()) sym.setDecimalSeparator(dec.charAt(0));
        DecimalFormat df = new DecimalFormat(pattern, sym);
        df.setGroupingUsed(!th.isEmpty());
        return df.format(v);
    }

    private String currencySymbol() {
        return Lang.get("economy.currency.symbol", "$");
    }
}
