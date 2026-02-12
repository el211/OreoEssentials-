package fr.elias.oreoEssentials.modules.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.InventoryManager;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public final class OeCraftCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final InventoryManager invMgr;
    private final CustomCraftingService service;
    private final CraftActionsConfig actionsConfig;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public OeCraftCommand(Plugin plugin, InventoryManager invMgr, CustomCraftingService service) {
        this(plugin, invMgr, service, null);
    }

    public OeCraftCommand(Plugin plugin, InventoryManager invMgr, CustomCraftingService service, CraftActionsConfig actionsConfig) {
        this.plugin = plugin;
        this.invMgr = invMgr;
        this.service = service;
        this.actionsConfig = actionsConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("oreo.craft")) {
                send(sender, "customcraft.errors.no-permission",
                        "<red>You don't have permission.</red>", Map.of());
                return true;
            }

            service.loadAllAndRegister();

            // Reload craft actions if available
            if (actionsConfig != null) {
                actionsConfig.reload();
                send(sender, "customcraft.messages.reloaded",
                        "<green>Custom recipes and craft actions reloaded.</green>", Map.of());
            } else {
                send(sender, "customcraft.messages.reloaded",
                        "<green>Custom recipes reloaded.</green>", Map.of());
            }
            return true;
        }

        if (sub.equals("list")) {
            var names = new TreeSet<>(service.allNames());
            send(sender, "customcraft.messages.list",
                    "<yellow>Recipes</yellow> (<white>%count%</white>): <gray>%names%</gray>",
                    Map.of("count", String.valueOf(names.size()), "names", String.join(", ", names)));
            return true;
        }

        if (sub.equals("delete")) {
            if (!sender.hasPermission("oreo.craft")) {
                send(sender, "customcraft.errors.no-permission",
                        "<red>You don't have permission.</red>", Map.of());
                return true;
            }
            if (args.length < 2) {
                send(sender, "customcraft.errors.usage-delete",
                        "<red>Usage:</red> /%label% delete <n>",
                        Map.of("label", label));
                return true;
            }
            String name = sanitize(args[1]);
            boolean ok = service.delete(name);
            if (ok) {
                send(sender, "customcraft.messages.deleted",
                        "<green>Deleted recipe <yellow>%name%</yellow>.</green>",
                        Map.of("name", name));
            } else {
                send(sender, "customcraft.messages.invalid",
                        "<red>Recipe not found: <yellow>%name%</yellow>.</red>",
                        Map.of("name", name));
            }
            return true;
        }

        if (!(sender instanceof Player p)) {
            send(sender, "customcraft.errors.player-only",
                    "<red>This subcommand requires a player.</red>", Map.of());
            return true;
        }
        if (!p.hasPermission("oreo.craft")) {
            send(p, "customcraft.errors.no-permission",
                    "<red>You don't have permission.</red>", Map.of());
            return true;
        }

        if (sub.equals("browse")) {
            RecipeListMenu.open(p, plugin, invMgr, service);
            return true;
        }

        if (sub.equals("craft")) {
            if (args.length < 2) {
                send(p, "customcraft.errors.usage-craft",
                        "<red>Usage:</red> /%label% craft <n>",
                        Map.of("label", label));
                return true;
            }
            String name = sanitize(args[1]);
            CraftDesignerMenu.build(plugin, invMgr, service, name).open(p);
            return true;
        }

        send(sender, "customcraft.errors.unknown-subcommand",
                "<red>Unknown subcommand.</red> <yellow>Use</yellow> /%label% help",
                Map.of("label", label));
        return true;
    }

    private static String sanitize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private void sendUsage(CommandSender sender, String label) {
        // Build usage from lang keys for better i18n
        send(sender, "customcraft.help.header",
                "<yellow>OreoEssentials Custom Crafting</yellow>", Map.of());
        send(sender, "customcraft.help.browse",
                "<yellow>/%label% browse</yellow> <gray>→ open recipe browser (GUI)</gray>",
                Map.of("label", label));
        send(sender, "customcraft.help.craft",
                "<yellow>/%label% craft <n></yellow> <gray>→ open designer GUI</gray>",
                Map.of("label", label));
        send(sender, "customcraft.help.list",
                "<yellow>/%label% list</yellow> <gray>→ list recipe names</gray>",
                Map.of("label", label));
        send(sender, "customcraft.help.reload",
                "<yellow>/%label% reload</yellow> <gray>→ reload recipes.yml & re-register</gray>",
                Map.of("label", label));
        send(sender, "customcraft.help.delete",
                "<yellow>/%label% delete <n></yellow> <gray>→ delete a recipe</gray>",
                Map.of("label", label));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> base = List.of("browse", "craft", "list", "reload", "delete");
            String pref = args[0].toLowerCase(Locale.ROOT);
            out = base.stream().filter(s -> s.startsWith(pref)).collect(Collectors.toList());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("craft") || sub.equals("delete")) {
                String pref = args[1].toLowerCase(Locale.ROOT);
                out = service.allNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pref))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }

        return out;
    }


    private static void send(CommandSender to, String key, String def, Map<String, String> vars) {
        if (to instanceof Player p) {
            Component comp = mm(key, def, vars, p);
            p.sendMessage(comp);
        } else {
            // console: plain fallback
            String raw = resolve(key, def, vars, null);
            String plain = PLAIN.serialize(MM.deserialize(raw));
            to.sendMessage(plain);
        }
    }

    private static Component mm(String key, String def, Map<String, String> vars, Player player) {
        String raw = resolve(key, def, vars, player);
        return MM.deserialize(raw);
    }

    private static String resolve(String key, String def, Map<String, String> vars, Player player) {
        String raw = Lang.get(key, def);
        if (vars != null) {
            for (var e : vars.entrySet()) raw = raw.replace("%" + e.getKey() + "%", e.getValue());
        }
        if (player != null && isPapiPresent()) {
            try { raw = PlaceholderAPI.setPlaceholders(player, raw); } catch (Throwable ignored) {}
        }
        return raw;
    }

    private static boolean isPapiPresent() {
        try { return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"); }
        catch (Throwable t) { return false; }
    }
}