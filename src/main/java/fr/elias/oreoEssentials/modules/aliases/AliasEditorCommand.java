package fr.elias.oreoEssentials.modules.aliases;

import fr.minuskube.inv.InventoryManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class AliasEditorCommand implements CommandExecutor, TabCompleter {

    private final AliasService service;
    private final InventoryManager invManager;

    public AliasEditorCommand(AliasService service, InventoryManager invManager) {
        this.service = service;
        this.invManager = invManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.alias.editor")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {

            case "gui" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can use the GUI.");
                    return true;
                }
                new AliasEditorGUI(service, invManager).openMain(p);
                return true;
            }

            case "list" -> {
                var map = service.all();
                if (map.isEmpty()) { sender.sendMessage("§7No aliases defined."); return true; }
                sender.sendMessage("§8—— §7Aliases (" + map.size() + ") §8——");
                map.values().stream()
                        .sorted(Comparator.comparing(a -> a.name))
                        .forEach(a ->
                                sender.sendMessage("§f/" + a.name + " §7→ §8" + a.runAs +
                                        " §7" + (a.enabled ? "§aENABLED" : "§cDISABLED") +
                                        " §8| §7cd:§f" + a.cooldownSeconds + "s" +
                                        " §8| §7checks:§f" + (a.checks == null ? 0 : a.checks.size()) +
                                        " §8(" + (a.logic == null ? "AND" : a.logic.name()) + ")" +
                                        " §8| §7permGate: " + (a.permGate ? "§aON" : "§cOFF") +
                                        " §8| §7tabs: " + (a.addTabs ? "§aON" : "§cOFF"))
                        );
                return true;
            }

            case "create" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: /aliaseditor create <name> <PLAYER|CONSOLE> <command...>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (service.exists(name)) { sender.sendMessage("§cAlias already exists."); return true; }
                AliasService.AliasDef def = new AliasService.AliasDef();
                def.name = name;
                try { def.runAs = AliasService.RunAs.valueOf(args[2].toUpperCase(Locale.ROOT)); } catch (Throwable t) {
                    sender.sendMessage("§cRun-as must be PLAYER or CONSOLE."); return true;
                }
                def.commands = new ArrayList<>();
                def.commands.add(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                service.put(def);
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAlias /" + name + " created.");
                return true;
            }

            case "set" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor set <name> <command...>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                var def = service.get(name);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.commands = new ArrayList<>();
                def.commands.add(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAlias /" + name + " updated.");
                return true;
            }

            case "addline" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor addline <name> <command...>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                var def = service.get(name);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.commands.add(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                service.save();
                sender.sendMessage("§aAdded line to /" + name + ".");
                return true;
            }

            case "enable" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor enable <name>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.enabled = true; service.save(); service.applyRuntimeRegistration();
                sender.sendMessage("§aEnabled /" + def.name + ".");
                return true;
            }

            case "disable" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor disable <name>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.enabled = false; service.save(); service.applyRuntimeRegistration();
                sender.sendMessage("§eDisabled /" + def.name + ".");
                return true;
            }

            case "runas" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor runas <name> <PLAYER|CONSOLE>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                try { def.runAs = AliasService.RunAs.valueOf(args[2].toUpperCase(Locale.ROOT)); } catch (Throwable t) {
                    sender.sendMessage("§cRun-as must be PLAYER or CONSOLE."); return true;
                }
                service.save(); service.applyRuntimeRegistration();
                sender.sendMessage("§a/" + def.name + " now runs as " + def.runAs + ".");
                return true;
            }

            case "cooldown" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor cooldown <name> <seconds>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                try { def.cooldownSeconds = Math.max(0, Integer.parseInt(args[2])); } catch (Exception e) {
                    sender.sendMessage("§cSeconds must be a number."); return true;
                }
                service.save();
                sender.sendMessage("§aCooldown set for /" + def.name + ": " + def.cooldownSeconds + "s.");
                return true;
            }

            case "delete" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor delete <name>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!service.exists(name)) { sender.sendMessage("§cAlias not found."); return true; }
                service.remove(name);
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAlias /" + name + " deleted.");
                return true;
            }

            case "info" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor info <name>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                sender.sendMessage("§8—— §7/" + def.name + " §8——");
                sender.sendMessage("§7Enabled: " + (def.enabled ? "§atrue" : "§cfalse"));
                sender.sendMessage("§7Run-as: §f" + def.runAs);
                sender.sendMessage("§7Cooldown: §f" + def.cooldownSeconds + "s");
                sender.sendMessage("§7Logic: §f" + (def.logic == null ? "AND" : def.logic.name()));
                sender.sendMessage("§7Fail message: §f" + (def.failMessage == null ? "§7(none)" : def.failMessage));
                sender.sendMessage("§7Perm gate: " + (def.permGate ? "§aON" : "§cOFF"));
                sender.sendMessage("§7Tab-complete: " + (def.addTabs ? "§aON" : "§cOFF") +
                        " §8(§7groups:§f " + (def.customTabs == null ? 0 : def.customTabs.size()) + "§8)");
                int i = 1;
                int checks = (def.checks == null) ? 0 : def.checks.size();
                sender.sendMessage("§7Checks: §f" + checks);
                if (checks > 0) {
                    for (AliasService.Check ch : def.checks) {
                        sender.sendMessage("§7  " + (i++) + ") §f" + ch.expr);
                    }
                }
                for (String cLine : def.commands) sender.sendMessage("§7• §f" + cLine);
                return true;
            }

            case "reload" -> {
                service.load();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAliases reloaded.");
                return true;
            }

            case "addcheck" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor addcheck <name> <expr...>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                String expr = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                if (expr.isEmpty()) { sender.sendMessage("§cExpression cannot be empty."); return true; }
                AliasService.Check ch = new AliasService.Check(); ch.expr = expr;
                def.checks.add(ch);
                service.save();
                sender.sendMessage("§aAdded check #" + def.checks.size() + " to /" + def.name + ".");
                return true;
            }

            case "delcheck" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor delcheck <name> <index>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                int idx; try { idx = Integer.parseInt(args[2]) - 1; } catch (Exception e) { sender.sendMessage("§cIndex must be a number."); return true; }
                if (idx < 0 || idx >= def.checks.size()) { sender.sendMessage("§cInvalid index."); return true; }
                def.checks.remove(idx);
                service.save();
                sender.sendMessage("§aRemoved check #" + (idx + 1) + " from /" + def.name + ".");
                return true;
            }

            case "listchecks" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor listchecks <name>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                sender.sendMessage("§8—— §7Checks for /" + def.name + " §8(Logic: §f" + (def.logic == null ? "AND" : def.logic.name()) + "§8) ——");
                if (def.checks.isEmpty()) { sender.sendMessage("§7(no checks)"); return true; }
                int i = 1;
                for (AliasService.Check ch : def.checks) sender.sendMessage("§7" + (i++) + ") §f" + ch.expr);
                return true;
            }

            case "setfailmsg" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor setfailmsg <name> <message...>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.failMessage = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                service.save();
                sender.sendMessage("§aFail message set for /" + def.name + ".");
                return true;
            }

            case "setlogic" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor setlogic <name> <AND|OR>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                try { def.logic = AliasService.LogicType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
                catch (Throwable t) { sender.sendMessage("§cLogic must be AND or OR."); return true; }
                service.save();
                sender.sendMessage("§aLogic for /" + def.name + " set to " + def.logic + ".");
                return true;
            }

            case "permgate" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor permgate <name> <true|false>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.permGate = parseBoolean(args[2], null);
                service.save();
                sender.sendMessage("§aPerm gate for /" + def.name + " set to " + (def.permGate ? "§aON" : "§cOFF") + ".");
                return true;
            }

            case "tabs" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor tabs <name> <true|false>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.addTabs = parseBoolean(args[2], null);
                service.save();
                // re-register to attach/detach tab completer on runtime command
                service.applyRuntimeRegistration();
                sender.sendMessage("§aTab-complete for /" + def.name + " set to " + (def.addTabs ? "§aON" : "§cOFF") + ".");
                return true;
            }

            case "addtab" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: /aliaseditor addtab <name> <index> <value1,value2,...>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                int index;
                try { index = Integer.parseInt(args[2]); } catch (Exception e) { sender.sendMessage("§cIndex must be a number (1..n)."); return true; }
                if (index <= 0) { sender.sendMessage("§cIndex must be >= 1."); return true; }
                String joined = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
                if (joined.isEmpty()) { sender.sendMessage("§cProvide comma-separated values."); return true; }

                // ensure list size
                while (def.customTabs.size() < index) def.customTabs.add(new ArrayList<>());

                List<String> vals = Arrays.stream(joined.replace(';', ',').split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();

                def.customTabs.set(index - 1, new ArrayList<>(vals));
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aSet tab group #" + index + " for /" + def.name + " to: §f" + String.join(", ", vals));
                return true;
            }

            case "deltag" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor deltag <name> <index>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                int index;
                try { index = Integer.parseInt(args[2]); } catch (Exception e) { sender.sendMessage("§cIndex must be a number (1..n)."); return true; }
                if (index <= 0 || index > def.customTabs.size()) { sender.sendMessage("§cInvalid index."); return true; }
                def.customTabs.remove(index - 1);
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aRemoved tab group #" + index + " for /" + def.name + ".");
                return true;
            }

            case "cleartabs" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor cleartabs <name>"); return true; }
                var def = service.get(args[1]); if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.customTabs.clear();
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aCleared all custom tab groups for /" + def.name + ".");
                return true;
            }

            default -> {
                help(sender);
                return true;
            }
        }
    }



    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("oreo.alias.editor")) return List.of();

        List<String> subs = List.of(
                "gui","list","create","set","addline","enable","disable","runas","cooldown",
                "delete","info","reload","addcheck","delcheck","listchecks","setfailmsg","setlogic",
                // new
                "permgate","tabs","addtab","deltag","cleartabs"
        );

        if (args.length == 1) {
            return prefix(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Set<String> names = service.all().keySet();

        switch (sub) {
            case "create" -> {
                if (args.length == 3) return prefix(List.of("PLAYER","CONSOLE"), args[2]);
                return List.of();
            }
            case "set", "addline", "enable", "disable", "delete", "info",
                 "addcheck", "listchecks", "setfailmsg", "setlogic", "runas", "cooldown", "delcheck",
                 // 
                 "permgate","tabs","addtab","deltag","cleartabs" -> {

                if (args.length == 2) return prefix(names, args[1]);

                if (sub.equals("runas") && args.length == 3) return prefix(List.of("PLAYER","CONSOLE"), args[2]);
                if (sub.equals("setlogic") && args.length == 3) return prefix(List.of("AND","OR"), args[2]);
                if ((sub.equals("permgate") || sub.equals("tabs")) && args.length == 3)
                    return prefix(List.of("true","false"), args[2]);

                if (sub.equals("delcheck") && args.length == 3) {
                    var def = service.get(args[1]);
                    if (def != null && def.checks != null && !def.checks.isEmpty()) {
                        List<String> idx = new ArrayList<>();
                        for (int i = 1; i <= def.checks.size(); i++) idx.add(String.valueOf(i));
                        return prefix(idx, args[2]);
                    }
                }
                if (sub.equals("cooldown") && args.length == 3) return prefix(List.of("0","5","30","60"), args[2]);

                if (sub.equals("addtab")) {
                    if (args.length == 3) {
                        var def = service.get(args[1]);
                        int next = (def == null ? 1 : Math.max(1, def.customTabs.size() + 1));
                        List<String> suggestions = new ArrayList<>();
                        if (def != null) {
                            for (int i = 1; i <= Math.max(1, def.customTabs.size()); i++) suggestions.add(String.valueOf(i));
                        }
                        suggestions.add(String.valueOf(next));
                        return prefix(suggestions, args[2]);
                    } else if (args.length == 4) {
                        return List.of("value1,value2,value3");
                    }
                }

                // deltag name <index>
                if (sub.equals("deltag") && args.length == 3) {
                    var def = service.get(args[1]);
                    if (def != null && !def.customTabs.isEmpty()) {
                        List<String> idx = new ArrayList<>();
                        for (int i = 1; i <= def.customTabs.size(); i++) idx.add(String.valueOf(i));
                        return prefix(idx, args[2]);
                    }
                }
                return List.of();
            }
            default -> { return List.of(); }
        }
    }


    private static Boolean parseBoolean(String s, Boolean def) {
        if (s == null) return def;
        String v = s.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true","on","yes","1","enable","enabled" -> true;
            case "false","off","no","0","disable","disabled" -> false;
            default -> def != null ? def : false;
        };
    }

    private static List<String> prefix(Collection<String> in, String token) {
        final String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return in.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).sorted().collect(Collectors.toList());
    }

    private void help(CommandSender s) {
        s.sendMessage("§8—— §dAlias Editor §8——");
        s.sendMessage("§f/aliaseditor gui §7(Open GUI)");
        s.sendMessage("§f/aliaseditor list");
        s.sendMessage("§f/aliaseditor create <name> <PLAYER|CONSOLE> <command...>");
        s.sendMessage("§f/aliaseditor set <name> <command...>");
        s.sendMessage("§f/aliaseditor addline <name> <command...>");
        s.sendMessage("§f/aliaseditor runas <name> <PLAYER|CONSOLE>");
        s.sendMessage("§f/aliaseditor cooldown <name> <seconds>");
        s.sendMessage("§f/aliaseditor enable|disable <name>");
        s.sendMessage("§f/aliaseditor info <name>");
        s.sendMessage("§f/aliaseditor delete <name>");
        s.sendMessage("§f/aliaseditor reload");
        s.sendMessage("§8—— §dChecks & Logic §8——");
        s.sendMessage("§f/aliaseditor addcheck <name> <expr...> §7(e.g. %ping%>=100 | permission:vip.kit | money>=1000 | %world%<-lobby-)");
        s.sendMessage("§f/aliaseditor delcheck <name> <index>");
        s.sendMessage("§f/aliaseditor listchecks <name>");
        s.sendMessage("§f/aliaseditor setlogic <name> <AND|OR>");
        s.sendMessage("§f/aliaseditor setfailmsg <name> <message...>");
        s.sendMessage("§8—— §dPerms & Tabs §8——");
        s.sendMessage("§f/aliaseditor permgate <name> <true|false> §7(require oreo.alias.custom.<name>)");
        s.sendMessage("§f/aliaseditor tabs <name> <true|false> §7(enable custom tab-complete)");
        s.sendMessage("§f/aliaseditor addtab <name> <index> <value1,value2,...>");
        s.sendMessage("§f/aliaseditor deltag <name> <index>");
        s.sendMessage("§f/aliaseditor cleartabs <name>");
        s.sendMessage("§7Tips: %player%, %uuid%, %world%, $1, $2, $* — ';' chains commands.");
    }
}
