package fr.elias.oreoEssentials.modules.aliases;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

final class DynamicAliasExecutor implements org.bukkit.command.CommandExecutor {

    private final Plugin plugin;
    private final AliasService service;
    private final String alias;

    DynamicAliasExecutor(Plugin plugin, AliasService service, String alias) {
        this.plugin = plugin;
        this.service = service;
        this.alias = alias.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        AliasService.AliasDef def = service.get(alias);
        if (def == null) {
            sender.sendMessage("§cThat alias is missing.");
            return true;
        }
        if (!def.enabled) {
            sender.sendMessage("§cThat alias is disabled.");
            return true;
        }

        if (def.permGate && !sender.hasPermission("oreo.alias.custom." + def.name)) {
            sender.sendMessage("§cYou don’t have permission to use /" + def.name + ".");
            return true;
        }

        if (!sender.hasPermission("oreo.alias.use.*") && !sender.hasPermission("oreo.alias.use." + alias)) {
            sender.sendMessage("§cYou don’t have permission to use /" + alias + ".");
            return true;
        }

        Player player = (sender instanceof Player p) ? p : null;
        if (!service.checkAndTouchCooldown(alias, player == null ? null : player.getUniqueId(), def.cooldownSeconds)) {
            sender.sendMessage("§cYou must wait before using /" + alias + " again.");
            return true;
        }

        if (!service.evaluateAllChecks(sender, def)) {
            String msg = (def.failMessage == null ? "§cYou don't meet the requirements for /%alias%." : def.failMessage);
            try { sender.sendMessage(msg.replace("%alias%", alias)); } catch (Throwable ignored) {}
            return true;
        }

        List<ExecStep> plan = new ArrayList<>();
        InlineContext ctx = new InlineContext(def.runAs);

        final AtomicInteger lineIndex = new AtomicInteger(0);
        for (String raw : def.commands) {
            int idx = lineIndex.getAndIncrement();
            parseLineIntoSteps(sender, args, def, raw, plan, ctx, idx);
        }

        runPlan(sender, player, plan);
        return true;
    }


    private static final class InlineContext {
        AliasService.RunAs defaultRunAs;
        InlineContext(AliasService.RunAs base) { this.defaultRunAs = base; }
    }

    private interface ExecStep {}
    private static final class DelayStep implements ExecStep {
        final long delayTicks;
        DelayStep(long delayTicks) { this.delayTicks = delayTicks; }
    }
    private static final class CommandStep implements ExecStep {
        final String line;
        final AliasService.RunAs runAs;
        final int perLineCooldownSeconds;
        final List<String> inlineChecks;
        final double moneyCost;

        final int lineIndex;

        CommandStep(String line, AliasService.RunAs runAs, int cd, List<String> checks, double moneyCost, int lineIndex) {
            this.line = line;
            this.runAs = runAs;
            this.perLineCooldownSeconds = cd;
            this.inlineChecks = checks;
            this.moneyCost = moneyCost;
            this.lineIndex = lineIndex;
        }
    }

    private void parseLineIntoSteps(CommandSender sender,
                                    String[] args,
                                    AliasService.AliasDef def,
                                    String rawLine,
                                    List<ExecStep> out,
                                    InlineContext ctx,
                                    int lineIndex) {

        String s = rawLine.trim();

        AliasService.RunAs runAsOverride = null;
        int perLineCooldown = 0;
        double moneyCost = 0d;
        List<String> inlineChecks = new ArrayList<>();
        long pendingDelayTicks = 0L;

        boolean changed;
        do {
            changed = false;

            if (startsWithWordIgnoreCase(s, "asConsole!")) {
                runAsOverride = AliasService.RunAs.CONSOLE;
                s = s.substring("asConsole!".length()).trim();
                changed = true;
            } else if (startsWithWordIgnoreCase(s, "asPlayer!")) {
                runAsOverride = AliasService.RunAs.PLAYER;
                s = s.substring("asPlayer!".length()).trim();
                changed = true;
            }

            if (startsWithWordIgnoreCase(s, "delay!") || startsWithWordIgnoreCase(s, "Delay!")) {
                String rem = s.substring(s.indexOf('!') + 1).trim();
                int sp = rem.indexOf(' ');
                String num = (sp == -1 ? rem : rem.substring(0, sp)).trim();
                double sec = parseDoubleSafe(num, 0d);
                pendingDelayTicks += secToTicks(sec);
                s = (sp == -1 ? "" : rem.substring(sp + 1)).trim();
                changed = true;
            }

            if (startsWithWordIgnoreCase(s, "cooldown:")) {
                String rem = s.substring("cooldown:".length()).trim();
                int end = findDirectiveEnd(rem);
                String val = rem.substring(0, end).trim();

                val = val.replaceAll("[?!]+$", "");
                perLineCooldown = (int) Math.max(0, parseDoubleSafe(val, 0d));
                s = rem.substring(end).trim();
                changed = true;
            }

            if (startsWithWordIgnoreCase(s, "moneycost:")) {
                String rem = s.substring("moneycost:".length()).trim();
                int end = findDirectiveEnd(rem);
                String val = rem.substring(0, end).trim();
                val = val.replaceAll("[?!]+$", "");
                moneyCost = Math.max(0, parseDoubleSafe(val, 0d));
                s = rem.substring(end).trim();
                changed = true;
            }

            if (startsWithWordIgnoreCase(s, "check:")) {
                String rem = s.substring("check:".length()).trim();
                int bang = rem.indexOf('!');
                String expr = (bang == -1 ? rem : rem.substring(0, bang)).trim();
                if (!expr.isEmpty()) inlineChecks.add(expr);
                s = (bang == -1 ? "" : rem.substring(bang + 1)).trim();
                changed = true;
            }

        } while (changed && !s.isEmpty());

        if (pendingDelayTicks > 0) {
            out.add(new DelayStep(pendingDelayTicks));
        }

        String expanded = expandVariables(sender, s, args);

        if (sender instanceof Player p && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try { expanded = PlaceholderAPI.setPlaceholders(p, expanded); } catch (Throwable ignored) {}
        }

        expanded = maybeExpandShorthandGive(expanded, sender);

        out.add(new CommandStep(expanded, runAsOverride, perLineCooldown, inlineChecks, moneyCost, lineIndex));
    }

    private void runPlan(CommandSender originalSender, Player player, List<ExecStep> plan) {
        // Execute sequentially using a small recursive scheduler to respect delays
        runStepsRecursive(originalSender, player, plan, 0);
    }

    private void runStepsRecursive(CommandSender sender, Player player, List<ExecStep> plan, int i) {
        if (i >= plan.size()) return;

        ExecStep step = plan.get(i);
        if (step instanceof DelayStep ds) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> runStepsRecursive(sender, player, plan, i + 1), ds.delayTicks);
            return;
        }

        CommandStep cs = (CommandStep) step;

        for (String expr : cs.inlineChecks) {
            if (!service.evaluateSingle(sender, expr)) {
                runStepsRecursive(sender, player, plan, i + 1);
                return;
            }
        }

        if (cs.perLineCooldownSeconds > 0) {
            UUID uid = (player == null ? null : player.getUniqueId());
            if (!service.checkAndTouchLineCooldown(alias, cs.lineIndex, uid, cs.perLineCooldownSeconds)) {
                runStepsRecursive(sender, player, plan, i + 1);
                return;
            }
        }

        if (cs.moneyCost > 0) {
            if (!(sender instanceof Player p)) {
                runStepsRecursive(sender, player, plan, i + 1);
                return;
            }
            var reg = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (reg == null || !reg.getProvider().has(p, cs.moneyCost)) {
                runStepsRecursive(sender, player, plan, i + 1);
                return;
            }
            try { reg.getProvider().withdrawPlayer(p, cs.moneyCost); } catch (Throwable ignored) {}
        }

        boolean ok = false;
        AliasService.RunAs runAs = (cs.runAs != null ? cs.runAs : service.get(alias).runAs);
        try {
            switch (runAs) {
                case PLAYER -> {
                    if (player == null) {
                        sender.sendMessage("§cThis alias step requires a player.");
                        ok = false;
                    } else {
                        ok = player.performCommand(cs.line);
                    }
                }
                case CONSOLE -> ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cs.line);
            }
        } catch (Throwable t) {
            ok = false;
            plugin.getLogger().warning("[Aliases] Step failed for /" + alias + ": " + cs.line + " -> " + t.getMessage());
        }

        if (!ok) {
        }
        runStepsRecursive(sender, player, plan, i + 1);
    }


    private static boolean startsWithWordIgnoreCase(String s, String word) {
        if (s.length() < word.length()) return false;
        String head = s.substring(0, word.length());
        return head.equalsIgnoreCase(word);
    }

    private static int findDirectiveEnd(String rem) {
        // read until whitespace or end
        int i = 0;
        while (i < rem.length() && !Character.isWhitespace(rem.charAt(i))) i++;
        return i;
    }

    private static long secToTicks(double sec) { return (long) Math.max(0, Math.round(sec * 20.0)); }
    private static double parseDoubleSafe(String s, double def) { try { return Double.parseDouble(s); } catch (Exception e) { return def; } }

    private String expandVariables(CommandSender sender, String s, String[] args) {
        String out = s;

        String playerName = (sender instanceof Player p) ? p.getName() : (sender.getName() != null ? sender.getName() : "CONSOLE");
        out = out.replace("[playerName]", playerName);

        // $1 $2 ... $*
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                out = out.replace("$" + (i + 1), args[i]);
            }
            out = out.replace("$*", String.join(" ", args));
        } else {
            out = out.replace("$*", "");
        }

        return out;
    }

    private String maybeExpandShorthandGive(String line, CommandSender sender) {
        String trimmed = line.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("give ")) return line;
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) return line;

        String item = parts[2].toLowerCase(Locale.ROOT);
        item = switch (item) {
            case "iron"    -> "minecraft:iron_ingot";
            case "gold"    -> "minecraft:gold_ingot";
            case "diamond" -> "minecraft:diamond";
            case "emerald" -> "minecraft:emerald";
            default        -> item;
        };
        parts[2] = item;
        return String.join(" ", parts);
    }
}
